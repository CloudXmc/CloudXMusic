package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.*;

/** QQ 独立账号生命周期；网络/文件 IO 串行后台执行，渲染只读不可变快照。 */
public final class QQAccountManager implements AutoCloseable {
    public record View(boolean loggedIn,String nickname,String status,boolean error,QQAccountProfile profile) {
        public View(boolean loggedIn,String nickname,String status,boolean error){this(loggedIn,nickname,status,error,QQAccountProfile.EMPTY);}
    }
    @FunctionalInterface interface ProfileLoader {QQAccountProfile load(QQCredentials account,java.util.function.BooleanSupplier active)throws Exception;}
    private final ProfileLoader profiles;
    private final QQCredentialStore store;
    private final QQSessionVerifier verifier;
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), task -> { Thread t = new Thread(task, "CloudXMusic-QQ-Account"); t.setDaemon(true); return t; });
    private volatile View view = new View(false, "", "未登录 · 可匿名搜索", false);
    private QQCredentials credentials;
    private long generation;
    private boolean closed, restored;
    private long importGeneration,lastRenewal;
    private Future<?> importing;
    private View beforeImport;
    private boolean maintaining;
    private LongSupplier clock=System::currentTimeMillis;
    private Consumer<String> notice=message->{};
    private final ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1,r->{var t=new Thread(r,"CloudXMusic-QQ-SessionCheck");t.setDaemon(true);return t;});
    QQAccountManager(QQCredentialStore store,QQSessionVerifier verifier){this(store,verifier,(account,active)->account.profile());}
    QQAccountManager(QQCredentialStore store,QQSessionVerifier verifier,ProfileLoader profiles){this.store=store;this.verifier=verifier;this.profiles=profiles;timer.setRemoveOnCancelPolicy(true);timer.scheduleWithFixedDelay(this::maintain,30,30,TimeUnit.MINUTES);}
    synchronized void clock(LongSupplier clock){this.clock=clock;}
    synchronized void notices(Consumer<String> notice){this.notice=notice;}
    private static View logged(QQCredentials account,String status,boolean error){return new View(true,account.nickname(),status,error,account.profile());}
    private void refreshProfile(long token,QQCredentials account){
        if(!current(token))return;
        try{
            QQAccountProfile profile=profiles.load(account,()->current(token));
            synchronized(this){if(current(token)&&view.loggedIn())view=new View(true,view.nickname(),view.status(),view.error(),profile);}
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception e){synchronized(this){if(current(token)&&view.loggedIn())view=new View(true,view.nickname(),view.status()+" · 会员信息暂不可用",view.error(),view.profile());}}
    }
    public View view() { return view; }
    record Snapshot(QQCredentials credentials, long generation) {
        @Override public String toString() { return "QQAccountSnapshot[redacted]"; }
    }
    synchronized Snapshot snapshot() throws IOException {
        if (closed || credentials == null || credentials.expired()) throw new QQPlaybackAccessException(QQPlaybackAccessException.Reason.LOGIN_REQUIRED);
        return new Snapshot(credentials, generation);
    }
    synchronized boolean isCurrent(Snapshot snapshot) {
        return !closed && snapshot != null && generation == snapshot.generation()
                && credentials == snapshot.credentials() && !credentials.expired();
    }
    synchronized long revision() { return generation; }

    public synchronized void restore() {
        if (closed || restored) return;
        restored = true; long token = generation;
        view = new View(false, "", "正在恢复 QQ 音乐账号…", false);
        enqueue(() -> {
            try {
                Optional<QQCredentials> saved = store.load();
                if (!current(token)) return;
                QQCredentials checked=null;boolean rotated=false;
                if(saved.isPresent()){
                    var candidate=saved.get();
                    boolean renewable=candidate.renewal().available()&&!candidate.cookieLogin();
                    if(candidate.expired()&&renewable){checked=verifier.renew(candidate);rotated=true;}
                    else try{checked=verifier.verify(candidate);}
                    catch(QQSessionVerifier.Rejected e){if(e.expired()&&renewable){checked=verifier.renew(candidate);rotated=true;}else throw e;}
                }
                synchronized (this) {
                    if (!current(token)) return;
                    // 先持久化换票结果，防止重启再次使用已经被轮换的旧密钥。
                    if(rotated)store.save(checked);
                    credentials = checked;
                    lastRenewal=checked==null?0:checked.renewal().issuedAt();
                    view = checked == null ? new View(false, "", "未登录 · 可匿名搜索", false)
                            : logged(checked,"已恢复并验证 QQ 音乐账号",false);
                }
                if(checked!=null)refreshProfile(token,checked);
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (QQSessionVerifier.Rejected e) {
                synchronized(this){if(current(token)){view=new View(false,"",e.expired()?"QQ 凭证已失效，请重新扫码或重新粘贴 Cookie":e.getMessage(),true);if(e.expired())notice.accept(view.status());}}
            } catch (IOException | RuntimeException e) {
                update(token, new View(false, "", "QQ 账号恢复失败，原文件已保留；可重新扫码", true));
            }
        });
    }

    /** 仅在当前扫码代次完成服务端验证后调用；保存失败不谎称已持久化。 */
    synchronized void acceptVerified(QQCredentials account) {
        if (closed) return;
        cancelCookieImport();long token = ++generation; credentials = account;lastRenewal=account.renewal().issuedAt();
        view = logged(account,"QQ 音乐已登录，正在保存…",false);
        enqueue(() -> {
            if (!current(token)) return;
            try { store.save(account); update(token, logged(account,"QQ 音乐已登录 · 凭证已保存",false)); }
            catch (IOException | RuntimeException e) {
                update(token, logged(account,"本次已登录，但保存失败；重启后需重新扫码",true));
            }
            refreshProfile(token,account);
        });
    }
    /** 只在校验成功后替换旧账号；取消、退出或新扫码使迟到的导入失效。 */
    public synchronized void importCookie(String raw){
        if(closed)return;cancelCookieImport();long attempt=importGeneration,epoch=generation;
        var oldView=view;beforeImport=oldView;view=new View(oldView.loggedIn(),oldView.nickname(),"正在验证 QQ Cookie…",false,oldView.profile());
        try{importing=io.submit(()->{
            try{
                var candidate=QQCookieLogin.parse(raw);var checked=verifier.verify(candidate);long token;
                synchronized(this){if(!importCurrent(attempt,epoch))return;store.save(checked);token=++generation;credentials=checked;beforeImport=null;view=logged(checked,"QQ Cookie 已验证并保存",false);}
                refreshProfile(token,checked);
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception e){synchronized(this){if(importCurrent(attempt,epoch)){beforeImport=null;String message=e instanceof IllegalArgumentException?e.getMessage():e instanceof QQSessionVerifier.Rejected rejected&&rejected.expired()?"QQ Cookie 已失效，请重新粘贴 Cookie":"QQ Cookie 验证或保存失败，请检查网络后重试";view=new View(oldView.loggedIn(),oldView.nickname(),message,true,oldView.profile());}}}
        });}catch(RejectedExecutionException e){beforeImport=null;view=new View(oldView.loggedIn(),oldView.nickname(),"QQ 登录后台繁忙，请稍后重试",true,oldView.profile());}
    }
    private boolean importCurrent(long attempt,long epoch){return !closed&&generation==epoch&&importGeneration==attempt&&!Thread.currentThread().isInterrupted();}
    public synchronized boolean cookieBusy(){return importing!=null&&!importing.isDone();}
    public synchronized void cancelCookieImport(){importGeneration++;if(importing!=null)importing.cancel(true);importing=null;if(beforeImport!=null){view=beforeImport;beforeImport=null;}io.purge();}

    /** 定时只读检测；仅扫码且持有服务端续期字段的会话每 12 小时换票一次。 */
    synchronized void maintain(){
        if(closed||credentials==null||maintaining||importing!=null&&!importing.isDone())return;
        var account=credentials;long epoch=generation,now=clock.getAsLong();
        boolean renew=!account.cookieLogin()&&account.renewal().available()&&now-Math.max(lastRenewal,account.renewal().issuedAt())>=TimeUnit.HOURS.toMillis(12);
        if(renew)lastRenewal=now;maintaining=true;
        try{io.execute(()->{
            try{
                QQCredentials checked;boolean rotated=renew;
                try{checked=renew?verifier.renew(account):verifier.verify(account);}
                catch(QQSessionVerifier.Rejected e){if(!renew)throw e;checked=verifier.verify(account);rotated=false;}
                synchronized(this){
                    if(!current(epoch)||credentials!=account)return;
                    if(rotated){store.save(checked);credentials=checked;generation++;}
                    view=new View(true,checked.nickname(),rotated?"QQ 音乐会话已检查并续期":"QQ 音乐登录状态已验证",false,view.profile());
                }
            }catch(QQSessionVerifier.Rejected e){synchronized(this){if(current(epoch)&&e.expired()){credentials=null;generation++;view=new View(false,"","QQ 凭证已失效，请重新扫码或重新粘贴 Cookie",true);notice.accept(view.status());}}}
            catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception e){synchronized(this){if(current(epoch))view=new View(view.loggedIn(),view.nickname(),"QQ 登录状态暂时无法验证，已保留凭证",true,view.profile());}}
            finally{synchronized(this){maintaining=false;}}
        });}catch(RejectedExecutionException e){maintaining=false;}
    }
    public synchronized void logout() {
        if (closed) return;
        cancelCookieImport();long token = ++generation; credentials = null;
        view = new View(false, "", "正在清除 QQ 本地凭证…", false);
        enqueue(() -> {
            if (!current(token)) return;
            try { store.delete(); update(token, new View(false, "", "已退出 QQ 音乐并清除本地凭证", false)); }
            catch (IOException | RuntimeException e) { update(token, new View(false, "", "已退出；本地凭证删除失败，请手动删除 account.json", true)); }
        });
    }
    private synchronized boolean current(long token) { return !closed && generation == token; }
    private synchronized void update(long token, View next) { if (current(token)) view = next; }
    private void enqueue(Runnable task) {
        try { io.execute(task); }
        catch (RejectedExecutionException e) {
            view = new View(credentials != null, credentials == null ? "" : credentials.nickname(), "QQ 账号后台任务繁忙，请稍后重试", true);
        }
    }
    @Override public synchronized void close() {
        if (closed) return;
        cancelCookieImport();closed = true; generation++; credentials = null;timer.shutdownNow();io.shutdownNow();
        view = new View(false, "", "QQ 账号服务已关闭", false);
    }
}
