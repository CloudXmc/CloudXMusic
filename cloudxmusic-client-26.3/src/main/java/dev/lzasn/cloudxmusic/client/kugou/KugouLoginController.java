package dev.lzasn.cloudxmusic.client.kugou;

import java.util.concurrent.*;
import java.util.function.*;

/** UI 仅提交动作和读取不可变视图；刷新、关闭、退出登录都会取消旧扫码。 */
public final class KugouLoginController implements AutoCloseable {
    public enum State { IDLE, LOADING, WAITING, SCANNED, SUCCESS, EXPIRED, ERROR }
    public record View(State state,byte[] png,String status){public View{png=png.clone();}public byte[] png(){return png.clone();}}
    private final KugouAccountStore accounts;
    private final Consumer<Runnable> main;
    private final ScheduledThreadPoolExecutor worker=new ScheduledThreadPoolExecutor(1,r->{Thread t=new Thread(r,"CloudXMusic-Kugou-Login");t.setDaemon(true);return t;});
    private volatile View view=new View(State.IDLE,new byte[0],"");
    private volatile boolean closed;
    private long generation;
    private ScheduledFuture<?> pending;
    private KugouLoginService session;
    private final Supplier<KugouLoginService> sessions;
    private final LongSupplier clock;
    private final Consumer<String> notice;
    private long lastRenewal;
    private final ScheduledFuture<?> maintenance;
    public KugouLoginController(KugouAccountStore accounts,Consumer<Runnable> main){this(accounts,main,()->new KugouLoginService(accounts.account().deviceId()),System::currentTimeMillis,message->main.accept(()->dev.lzasn.cloudxmusic.client.gui.login.LoginNotice.show("酷狗音乐",message)),true);}
    KugouLoginController(KugouAccountStore accounts,Consumer<Runnable> main,Supplier<KugouLoginService> sessions,LongSupplier clock,Consumer<String> notice){this(accounts,main,sessions,clock,notice,false);}
    private KugouLoginController(KugouAccountStore accounts,Consumer<Runnable> main,Supplier<KugouLoginService> sessions,LongSupplier clock,Consumer<String> notice,boolean restore){this.accounts=accounts;this.main=main;this.sessions=sessions;this.clock=clock;this.notice=notice;worker.setRemoveOnCancelPolicy(true);maintenance=worker.scheduleWithFixedDelay(this::maintain,restore?0:30,30,TimeUnit.MINUTES);}
    public View view(){return view;}
    public synchronized void start(){
        if(closed)return;cancel();long token=generation,revision=accounts.revision();
        view=new View(State.LOADING,new byte[0],"正在获取酷狗二维码…");
        session=sessions.get();KugouLoginService login=session;
        pending=worker.schedule(()->{try{var qr=login.create();post(token,State.WAITING,qr.png(),"请用酷狗音乐 App 扫码");poll(token,revision,login,qr,System.nanoTime()+TimeUnit.SECONDS.toNanos(180));}catch(Exception e){failed(token);}},0,TimeUnit.SECONDS);
    }
    public synchronized void importCookie(String raw){
        if(closed)return;cancel();long token=generation,revision=accounts.revision();String device=accounts.account().deviceId();
        view=new View(State.LOADING,new byte[0],"正在验证酷狗 Cookie…");
        pending=worker.schedule(()->{
            try(var auth=sessions.get()){
                var verified=auth.validate(KugouCookieLogin.parse(raw,device));
                synchronized(this){if(closed||generation!=token||Thread.currentThread().isInterrupted())return;if(!accounts.commit(revision,verified)){post(token,State.ERROR,new byte[0],"账号已变化，请重新粘贴 Cookie");return;}}
                post(token,State.SUCCESS,new byte[0],"酷狗 Cookie 已验证并保存");
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception e){post(token,State.ERROR,new byte[0],e instanceof IllegalArgumentException?e.getMessage():e instanceof KugouPlaybackAccessException denied&&denied.reason()==KugouPlaybackAccessException.Reason.LOGIN_REQUIRED?"酷狗 Cookie 已失效，请重新粘贴 Cookie":"酷狗 Cookie 验证或保存失败，请检查网络后重试");}
        },0,TimeUnit.SECONDS);
    }
    /** Cookie 永远只验证；扫码会话用已确认的 Web 换票协议，每 12 小时最多一次。 */
    void maintain(){
        KugouAccount account;long epoch,token;boolean renew;
        synchronized(this){if(closed||session!=null||view.state()==State.LOADING)return;account=accounts.account();epoch=accounts.revision();token=generation;if(!account.loggedIn())return;long now=clock.getAsLong();renew=!account.cookieLogin()&&now-Math.max(lastRenewal,account.refreshedAt())>=TimeUnit.HOURS.toMillis(12);if(renew)lastRenewal=now;}
        try(var auth=sessions.get()){
            KugouAccount checked;
            try{checked=renew?auth.renew(account):auth.validate(account);}catch(KugouPlaybackAccessException e){if(!renew)throw e;checked=auth.validate(account);}
            synchronized(this){if(closed||token!=generation||accounts.revision()!=epoch)return;if(renew)accounts.commit(epoch,checked);}
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(KugouPlaybackAccessException e){if(e.reason()==KugouPlaybackAccessException.Reason.LOGIN_REQUIRED)synchronized(this){if(!closed&&token==generation&&accounts.invalidate(epoch)){String message="酷狗凭证已失效，请重新扫码或重新粘贴 Cookie";post(token,State.EXPIRED,new byte[0],message);notice.accept(message);}}}
        catch(Exception ignored){/* 网络或安全验证失败保留原会话；下一轮只读复查，不连续换票。 */}
    }
    private synchronized void poll(long token,long revision,KugouLoginService login,KugouLoginService.Qr qr,long deadline){
        if(closed||token!=generation)return;
        pending=worker.schedule(()->{
            try{
                if(System.nanoTime()>deadline){post(token,State.EXPIRED,qr.png(),"二维码已过期，请刷新");finish(token,login);return;}
                var result=login.poll(qr.key());
                synchronized(this){
                    if(closed||token!=generation)return;
                    if(result.status()==4){
                        // 与 cancel 共用锁；取消后旧扫码不能继续写入账号文件。
                        if(accounts.commit(revision,result.account()))post(token,State.SUCCESS,new byte[0],"酷狗音乐登录成功");
                        else post(token,State.EXPIRED,new byte[0],"账号状态已变化，请重新扫码");
                        finish(token,login);return;
                    }
                }
                if(result.status()!=1&&result.status()!=2){post(token,State.EXPIRED,qr.png(),"二维码已失效，请刷新");finish(token,login);return;}
                post(token,result.status()==2?State.SCANNED:State.WAITING,qr.png(),result.status()==2?"请在手机确认登录":"等待扫码");poll(token,revision,login,qr,deadline);
            }catch(Exception e){failed(token);}
        },2,TimeUnit.SECONDS);
    }
    private synchronized void finish(long token,KugouLoginService login){if(token==generation&&session==login){session=null;login.close();}}
    private void failed(long token){post(token,State.ERROR,new byte[0],"酷狗登录失败，请稍后刷新二维码");synchronized(this){if(token==generation&&session!=null){session.close();session=null;}}}
    private void post(long token,State state,byte[] png,String status){main.accept(()->{synchronized(this){if(!closed&&token==generation)view=new View(state,png,status);}});}
    public synchronized void cancel(){generation++;if(pending!=null)pending.cancel(true);pending=null;if(session!=null)session.close();session=null;view=new View(State.IDLE,new byte[0],"");}
    public synchronized void logout(){
        if(closed)return;cancel();long token=generation;
        worker.execute(()->{try{
            synchronized(this){if(closed||token!=generation)return;accounts.logout();}
            post(token,State.IDLE,new byte[0],"酷狗音乐已退出登录");
        }catch(Exception e){failed(token);}});
    }
    @Override public synchronized void close(){if(closed)return;cancel();closed=true;maintenance.cancel(true);worker.shutdownNow();}
}
