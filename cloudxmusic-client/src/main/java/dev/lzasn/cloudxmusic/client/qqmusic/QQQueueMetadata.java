package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 队列元数据预取：仅两个在途请求，缓存随队列删除；状态只在客户端线程发布。 */
final class QQQueueMetadata implements AutoCloseable {
    @FunctionalInterface interface Loader {QQTrackMetadata.Info load(UnifiedSong song)throws Exception;}
    private record Failure(long retryAt){}
    private record Job(Object ticket,FutureTask<Void> task){}
    private final Loader loader;
    private final Consumer<Runnable> main;
    private final LongSupplier account;
    private final Predicate<String> cover;
    private final LongSupplier clock;
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(2),r->{
        Thread t=new Thread(r,"CloudXMusic-QQ-QueueMetadata");t.setDaemon(true);return t;});
    private final Map<String,QQTrackMetadata.Info> cache=new HashMap<>();
    private final Map<String,Job> pending=new HashMap<>();
    private final Map<String,Failure> failed=new HashMap<>();
    private final Set<String> prefetched=new HashSet<>();
    private Map<String,UnifiedSong> wanted=Map.of();
    private long generation,revision=Long.MIN_VALUE;
    private boolean active,closed;
    QQQueueMetadata(Loader loader,Consumer<Runnable> main,LongSupplier account,Predicate<String> cover){this(loader,main,account,cover,System::nanoTime);}
    QQQueueMetadata(Loader loader,Consumer<Runnable> main,LongSupplier account,Predicate<String> cover,LongSupplier clock){
        this.loader=loader;this.main=main;this.account=account;this.cover=cover;this.clock=clock;
    }
    void update(List<UnifiedSong> queue,boolean enabled){
        if(closed)return;
        long nextRevision=account.getAsLong();
        if(nextRevision!=revision){cancel();cache.clear();failed.clear();prefetched.clear();revision=nextRevision;}
        if(active!=enabled){cancel();active=enabled;}
        Map<String,UnifiedSong> next=new LinkedHashMap<>();
        for(UnifiedSong song:queue)if(song.source().equals("qq"))next.putIfAbsent(song.platformId(),song);
        wanted=next;
        cache.keySet().retainAll(next.keySet());failed.keySet().retainAll(next.keySet());
        prefetched.retainAll(next.keySet());
        pending.entrySet().removeIf(e->{if(next.containsKey(e.getKey()))return false;e.getValue().task().cancel(true);return true;});
        io.purge();
        if(!active)return;
        // 图片缓存也有并发上限；未入队的封面在后续 tick 再尝试。
        for(var entry:cache.entrySet())prefetch(entry.getKey(),entry.getValue());
        schedule();
    }
    private void schedule(){
        if(closed||!active)return;
        for(var entry:wanted.entrySet()){
            if(pending.size()>=2)return;
            String mid=entry.getKey();Failure failure=failed.get(mid);
            if(cache.containsKey(mid)||pending.containsKey(mid)||(failure!=null&&clock.getAsLong()<failure.retryAt()))continue;
            long token=generation,rev=revision;UnifiedSong song=entry.getValue();
            Object ticket=new Object();
            FutureTask<Void> task=new FutureTask<>(()->{
                QQTrackMetadata.Info info=null;
                try{info=loader.load(song);if(info==null)throw new IllegalStateException("空元数据");}
                catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(Exception ignored){/* 仅记录可重试状态，不把失败伪装成成功的空元数据。 */}
                QQTrackMetadata.Info loaded=info;
                main.accept(()->{
                    Job job=pending.get(mid);
                    if(closed||generation!=token||account.getAsLong()!=rev||!wanted.containsKey(mid)||job==null||job.ticket()!=ticket)return;
                    pending.remove(mid);
                    if(loaded!=null){cache.put(mid,loaded);failed.remove(mid);prefetch(mid,loaded);}
                    else failed.put(mid,new Failure(clock.getAsLong()+TimeUnit.SECONDS.toNanos(30)));
                });
                return null;
            });
            pending.put(mid,new Job(ticket,task));
            try{io.execute(task);}catch(RejectedExecutionException e){pending.remove(mid);}
        }
    }
    SongView song(UnifiedSong song){
        var info=cache.get(song.platformId());
        return SongView.from(song,info==null?0:info.durationSeconds(),info==null?"":info.coverUrl(),info!=null&&info.vip());
    }
    private void prefetch(String mid,QQTrackMetadata.Info info){
        if(!info.coverUrl().isBlank()&&!prefetched.contains(mid)&&cover.test(info.coverUrl()))prefetched.add(mid);
    }
    String status(){return failed.isEmpty()?"":"QQ 队列信息加载失败（"+failed.size()+" 首），稍后自动重试";}
    int pendingCount(){return pending.size();}
    private void cancel(){generation++;for(var job:pending.values())job.task().cancel(true);pending.clear();io.getQueue().clear();}
    @Override public void close(){if(closed)return;closed=true;cancel();cache.clear();failed.clear();prefetched.clear();wanted=Map.of();io.shutdownNow();}
}
