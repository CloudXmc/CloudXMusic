package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 客户端持有云收藏视图；一次仅一个写操作，失败恢复确认值，账号切换丢弃全部旧结果。 */
public final class KugouFavoritesController implements AutoCloseable {
    private final Supplier<KugouAccount> accounts;private final LongSupplier revision;private final Consumer<Runnable> main;private final Consumer<String> notice;
    private final KugouFavoritesService.Remote remote;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"CloudXMusic-Kugou-Favorites");t.setDaemon(true);return t;});
    private volatile long generation;private volatile boolean closed;private long account=-1;private String songKey="";private long refreshed;
    private KugouFavoritesService.Snapshot confirmed=new KugouFavoritesService.Snapshot(0,List.of());
    private boolean loading,writing,refreshNeeded;private String pendingHash="",status="";private boolean desired;private Future<?> pending;
    KugouFavoritesController(KugouFavoritesService.Remote remote,Supplier<KugouAccount> accounts,LongSupplier revision,Consumer<Runnable> main,Consumer<String> notice){this.remote=remote;this.accounts=accounts;this.revision=revision;this.main=main;this.notice=notice;}
    public List<KugouTrack> tracks(){return confirmed.entries().stream().map(KugouFavoritesService.Entry::track).toList();}
    public boolean contains(UnifiedSong song){return song!=null&&(writing&&song.platformId().equalsIgnoreCase(pendingHash)?desired:confirmed.contains(song.platformId()));}
    public String status(){return status;}
    public boolean busy(){return loading||writing;}
    private void resetAccount(){long next=revision.getAsLong();if(account==next)return;account=next;++generation;if(pending!=null)pending.cancel(true);worker.purge();confirmed=new KugouFavoritesService.Snapshot(0,List.of());songKey="";pendingHash="";loading=false;writing=false;refreshNeeded=true;refreshed=0;status="";}
    public void update(UnifiedSong song,boolean active){
        if(closed)return;resetAccount();String next=song==null?"":song.platformId();
        refreshNeeded|=!songKey.equals(next);songKey=next;
        if(active&&accounts.get().loggedIn()&&!busy()&&(refreshNeeded||refreshed==0||System.nanoTime()-refreshed>TimeUnit.MINUTES.toNanos(2)))refresh();
    }
    public void refresh(){
        if(closed)return;resetAccount();if(busy()){refreshNeeded=true;return;}var snapshot=accounts.get();if(!snapshot.loggedIn()){status="登录酷狗音乐后查看账号收藏";return;}refreshNeeded=false;
        long token=++generation,epoch=account;loading=true;status="正在同步酷狗账号收藏…";
        pending=worker.submit(()->{try{var result=remote.load(snapshot,()->current(token,epoch));main.accept(()->{if(!current(token,epoch))return;confirmed=result;loading=false;refreshed=System.nanoTime();status="";});}
            catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){main.accept(()->{if(!current(token,epoch))return;loading=false;refreshed=System.nanoTime();status="酷狗账号收藏同步失败，请刷新重试";});}});
    }
    public void toggle(KugouTrack track){
        if(closed)return;resetAccount();var snapshot=accounts.get();if(!snapshot.loggedIn()){notice.accept("请先登录酷狗音乐后再收藏");return;}if(busy()){notice.accept("酷狗收藏正在同步，请稍后重试");return;}
        long token=++generation,epoch=account;desired=!confirmed.contains(track.song().platformId());boolean target=desired;pendingHash=track.song().platformId();writing=true;status="正在同步酷狗账号收藏…";
        pending=worker.submit(()->{try{var result=remote.set(snapshot,track,target,()->current(token,epoch));main.accept(()->{if(!current(token,epoch))return;confirmed=result;writing=false;pendingHash="";refreshed=System.nanoTime();status="";notice.accept(target?"已添加到酷狗账号收藏":"已取消酷狗账号收藏");});}
            catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){main.accept(()->{if(!current(token,epoch))return;writing=false;pendingHash="";status="酷狗收藏同步失败，已恢复原状态，请刷新重试";notice.accept(status);});}});
    }
    private boolean current(long token,long epoch){return !closed&&generation==token&&revision.getAsLong()==epoch;}
    @Override public void close(){if(closed)return;closed=true;++generation;if(pending!=null)pending.cancel(true);worker.shutdownNow();confirmed=new KugouFavoritesService.Snapshot(0,List.of());}
}
