package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.util.concurrent.*;
import java.util.function.*;

/** 账号资料和当前歌曲权益独立刷新；不参与播放权限决策，不持有跨账号缓存。 */
public final class KugouAccountDetails implements AutoCloseable {
    private final KugouAccountStore accounts;private final KugouProvider provider;private final Consumer<Runnable> main;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{var t=new Thread(r,"CloudXMusic-Kugou-Details");t.setDaemon(true);return t;});
    private long epoch=-1,generation,profileTime,privilegeTime;private String hash="";private KugouProfile profile=KugouProfile.empty();private KugouPrivileges privileges=KugouPrivileges.empty();private boolean closed;
    private Future<?> profileRequest,privilegeRequest;private long privilegeReceived;
    KugouAccountDetails(KugouAccountStore accounts,KugouProvider provider,Consumer<Runnable> main){this.accounts=accounts;this.provider=provider;this.main=main;}
    public KugouProfile profile(){return profile;}
    public KugouPrivileges privileges(){return privileges.merge(provider.observed(hash,epoch,privilegeReceived));}
    public void update(UnifiedSong song,boolean active){
        if(closed)return;long revision=accounts.revision(),now=System.nanoTime();
        if(epoch!=revision){epoch=revision;generation++;profile=accounts.account().profile();privileges=KugouPrivileges.empty();privilegeReceived=0;hash="";profileTime=0;privilegeTime=0;if(profileRequest!=null)profileRequest.cancel(true);if(privilegeRequest!=null)privilegeRequest.cancel(true);worker.purge();}
        var account=accounts.account();
        if(active&&account.loggedIn()&&(profileTime==0||now-profileTime>TimeUnit.MINUTES.toNanos(5))){
            profileTime=now;long expected=epoch;try{profileRequest=worker.submit(()->{try{var result=provider.profile(account);main.accept(()->{if(!closed&&accounts.revision()==expected)profile=result;});}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){}});}catch(RejectedExecutionException e){profileTime=0;}
        }
        String next=song==null?"":song.platformId();boolean changed=!next.equals(hash);
        if(changed){hash=next;generation++;privileges=KugouPrivileges.empty();privilegeReceived=0;privilegeTime=0;if(privilegeRequest!=null)privilegeRequest.cancel(true);worker.purge();}
        if(active&&song!=null&&(privilegeTime==0||now-privilegeTime>TimeUnit.MINUTES.toNanos(1))){
            privilegeTime=now;long token=generation,expected=epoch;try{privilegeRequest=worker.submit(()->{try{var result=provider.privileges(song,account);long received=System.nanoTime();main.accept(()->{if(!closed&&generation==token&&accounts.revision()==expected){privileges=result;privilegeReceived=received;}});}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){}});}catch(RejectedExecutionException e){privilegeTime=0;}
        }
    }
    @Override public void close(){closed=true;generation++;if(profileRequest!=null)profileRequest.cancel(true);if(privilegeRequest!=null)privilegeRequest.cancel(true);worker.shutdownNow();profile=KugouProfile.empty();privileges=KugouPrivileges.empty();}
}
