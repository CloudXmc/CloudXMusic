package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 本地独立队列。所有公开状态操作由客户端线程调用，网络与声卡只通过异步确认交接。 */
public final class KugouPlaybackController implements AutoCloseable {
    @FunctionalInterface interface Resolver {KugouPlaybackResolver.Link resolve(UnifiedSong song,KugouQuality quality)throws Exception;}
    public record View(UnifiedSong song,boolean loading,boolean playing,boolean paused,double position,double duration,
                       float volume,String quality,String status,boolean error,KugouQuality actualQuality){}
    private final KugouPlaybackAudio audio;
    private final Supplier<CompletableFuture<Void>> pauseOthers;
    private final Consumer<Runnable> main;
    private final Resolver resolver;
    private final LongSupplier revision;
    private final ThreadPoolExecutor network=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),
            r->{Thread t=new Thread(r,"CloudXMusic-Kugou-PlaybackResolve");t.setDaemon(true);return t;});
    private volatile BooleanSupplier sourceAllowed=()->true;
    private List<UnifiedSong> queue=List.of();private int index=-1;
    private volatile long generation,account;
    private volatile boolean owner,closed;
    private UnifiedSong song;private boolean loading,error,resumePaused,isSeeking;
    private double resumePosition,knownDuration,seekTarget;
    private String status="";
    private KugouQuality actual;
    private PlaybackMode mode=PlaybackMode.REPEAT_LIST;
    private Future<?> pending;
    private CompletableFuture<Void> handoff=CompletableFuture.completedFuture(null);
    private long audioEpoch,finishedEpoch=-1;
    private Consumer<String> notice=message->{};
    private final Set<String> lockedSongs=new HashSet<>();
    private boolean skipPending;private long skipAt,skipToken;
    private LongSupplier clock=System::nanoTime;
    public void notices(Consumer<String> notice){this.notice=Objects.requireNonNull(notice);}
    void clock(LongSupplier clock){this.clock=clock;}
    public KugouPlaybackController(KugouProvider provider,Supplier<CompletableFuture<Void>> pauseOthers,Consumer<Runnable> main,LongSupplier revision){
        this(new KugouAudioPlayer(),pauseOthers,main,provider::playbackLink,revision);
    }
    KugouPlaybackController(KugouPlaybackAudio audio,Supplier<CompletableFuture<Void>> pauseOthers,Consumer<Runnable> main,Resolver resolver,LongSupplier revision){
        this.audio=Objects.requireNonNull(audio);this.pauseOthers=Objects.requireNonNull(pauseOthers);this.main=Objects.requireNonNull(main);this.resolver=Objects.requireNonNull(resolver);this.revision=Objects.requireNonNull(revision);account=revision.getAsLong();
    }
    public void sourceGuard(BooleanSupplier allowed){sourceAllowed=Objects.requireNonNull(allowed);}
    public boolean hasTrack(){return song!=null;}
    public List<UnifiedSong> queue(){return queue;}
    public KugouQuality preferredQuality(){return KugouQuality.HIGH;}
    public PlaybackMode mode(){return mode;}
    public void mode(PlaybackMode value){mode=Objects.requireNonNull(value);}
    public void cycleMode(){mode=switch(mode){case REPEAT_LIST->PlaybackMode.REPEAT_ONE;case REPEAT_ONE->PlaybackMode.SHUFFLE;case SHUFFLE->PlaybackMode.REPEAT_LIST;};}
    public View view(){
        var a=audio.view();boolean permitted=owner&&sourceAllowed.getAsBoolean();boolean waiting=loading||permitted&&!error&&a.status()==KugouPlaybackAudio.Status.LOADING;
        return new View(song,waiting,permitted&&!waiting&&!error&&a.status()==KugouPlaybackAudio.Status.PLAYING,
                loading?resumePaused:hasTrack()&&!waiting&&!error&&(!permitted||a.status()==KugouPlaybackAudio.Status.PAUSED),
                isSeeking?seekTarget:loading?resumePosition:a.position(),loading?knownDuration:a.duration(),a.volume(),actual==null?"":actual.menuLabel(),
                a.status()==KugouPlaybackAudio.Status.ERROR?a.error():status,error||a.status()==KugouPlaybackAudio.Status.ERROR,actual);
    }
    public void play(List<UnifiedSong> songs,int selected){
        if(closed||!sourceAllowed.getAsBoolean()||selected<0||selected>=songs.size())return;
        List<UnifiedSong> copy=List.copyOf(songs);if(copy.stream().anyMatch(s->!s.source().equals("kugou")))throw new IllegalArgumentException("酷狗队列只能包含酷狗歌曲");
        lockedSongs.clear();skipPending=false;queue=copy;index=selected;begin(copy.get(selected),0,false);
    }
    private void begin(UnifiedSong selected,double position,boolean paused){
        if(closed||!sourceAllowed.getAsBoolean())return;
        knownDuration=song!=null&&song.platformId().equals(selected.platformId())?view().duration():0;
        long token=claim(),epoch=++audioEpoch;account=revision.getAsLong();owner=true;song=selected;loading=true;error=false;actual=null;isSeeking=false;
        resumePosition=position;resumePaused=paused;status="正在获取酷狗音频…";
        CompletableFuture<Void> quiet;
        try{quiet=CompletableFuture.allOf(handoff,pauseOthers.get(),audio.stop()).orTimeout(8,TimeUnit.SECONDS);handoff=quiet;}
        catch(RuntimeException e){fail(token,"声卡交接失败，未启动酷狗音乐");return;}
        try{pending=network.submit(()->{
            try{
                var link=resolver.resolve(selected,KugouQuality.HIGH);
                quiet.whenComplete((v,failure)->main.accept(()->{
                    if(!current(token))return;
                    if(failure!=null){fail(token,"声卡交接失败，未启动酷狗音乐");return;}
                    if(link==null||link.url()==null||link.url().isBlank()||link.actualQuality()==null){fail(token,"酷狗暂无可播放资源");return;}
                    URI uri;try{uri=URI.create(link.url());if(!KugouAudioUrlVerifier.allowed(uri))throw new IllegalArgumentException();}catch(IllegalArgumentException e){fail(token,"酷狗音频地址无效");return;}
                    actual=link.actualQuality();knownDuration=link.durationSeconds();
                    audio.playFrom(uri,link.durationSeconds(),position,paused,()->current(token),
                            ()->main.accept(()->started(token)),()->main.accept(()->finished(epoch)))
                            .whenComplete((ignored,e)->main.accept(()->{
                                if(!current(token))return;if(e!=null){fail(token,"酷狗播放器启动失败");return;}
                                loading=false;if(paused)isSeeking=false;
                                status="";
                            }));
                }));
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(KugouPlaybackAccessException e){main.accept(()->{if(!current(token))return;if(e.reason()==KugouPlaybackAccessException.Reason.SONG_LOCKED)locked(token);else fail(token,e.getMessage());});}
            catch(Exception e){org.slf4j.LoggerFactory.getLogger("CloudXMusic/Kugou").warn("酷狗取链失败",KugouAudioFailure.safe(e));main.accept(()->fail(token,"酷狗取链失败，请稍后重试"));}
        });}catch(RejectedExecutionException e){fail(token,"酷狗取链任务正在取消，请稍后重试");}
    }
    private void started(long token){if(current(token)){loading=false;isSeeking=false;lockedSongs.clear();}}
    private void locked(long token){
        if(!current(token)||skipPending)return;
        lockedSongs.add(song.platformId());fail(token,"当前歌曲为 VIP 专属，暂无播放资格");notice.accept(status);
        // 只在 tick 到期后处理下一首；单曲循环也跳过，整队列最多扫描一遍。
        skipToken=token;skipAt=clock.getAsLong()+TimeUnit.MILLISECONDS.toNanos(900);skipPending=true;
    }
    private void skipLocked(){
        if(!skipPending||clock.getAsLong()<skipAt)return;skipPending=false;
        if(closed||generation!=skipToken||revision.getAsLong()!=account||!sourceAllowed.getAsBoolean())return;
        var candidates=new ArrayList<Integer>();for(int offset=1;offset<=queue.size();offset++){int row=Math.floorMod(index+offset,queue.size());if(!lockedSongs.contains(queue.get(row).platformId()))candidates.add(row);}
        if(candidates.isEmpty()){status="队列中的歌曲均暂无播放资格，已停止自动切歌";return;}
        index=mode==PlaybackMode.SHUFFLE?candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())):candidates.getFirst();begin(queue.get(index),0,false);
    }
    private void finished(long epoch){
        if(closed||epoch!=audioEpoch||finishedEpoch==epoch||!current(generation)||loading||error||!hasTrack()||audio.view().status()!=KugouPlaybackAudio.Status.FINISHED)return;
        finishedEpoch=epoch;play(queue,mode==PlaybackMode.REPEAT_ONE?index:nextIndex());
    }
    public void toggle(){
        if(closed||!sourceAllowed.getAsBoolean())return;
        if(!hasTrack()){if(!queue.isEmpty())play(queue,0);return;}
        View v=view();
        if(v.playing()||v.loading()){suspend();return;}
        if(audio.view().status()==KugouPlaybackAudio.Status.PAUSED&&!v.error()&&actual!=null){
            long token=claim();owner=true;loading=true;account=revision.getAsLong();
            try{handoff=CompletableFuture.allOf(handoff,pauseOthers.get()).orTimeout(8,TimeUnit.SECONDS);
                handoff.thenCompose(x->current(token)?audio.resume(()->current(token)):CompletableFuture.completedFuture(null)).whenComplete((x,e)->main.accept(()->{if(!current(token))return;loading=false;if(e!=null)fail(token,"声卡交接失败，无法恢复酷狗音乐");}));
            }catch(RuntimeException e){fail(token,"声卡交接失败，无法恢复酷狗音乐");}
        }else begin(song,v.position(),false);
    }
    public CompletableFuture<Void> suspend(){
        skipPending=false;resumePosition=view().position();claim();owner=false;loading=false;isSeeking=false;status=hasTrack()?"酷狗音乐已暂停":"";
        handoff=CompletableFuture.allOf(handoff,audio.pause());return handoff;
    }
    public void beginSeek(double position){if(hasTrack()&&Double.isFinite(position)){seekTarget=clamp(position);isSeeking=true;}}
    public void cancelSeek(){isSeeking=false;}
    public boolean seeking(){return isSeeking;}
    private double clamp(double value){return Math.max(0,Math.min(value,Math.max(0,view().duration()-1)));}
    public void seek(double position){
        if(closed||!sourceAllowed.getAsBoolean()||!hasTrack()||!Double.isFinite(position))return;
        double target=clamp(position);boolean paused=loading?resumePaused:!view().playing();
        begin(song,target,paused);isSeeking=true;seekTarget=target;
    }
    public void quality(KugouQuality value){
        // 兼容旧偏好恢复入口；自动模式下不重新取链或打断当前播放 / 拖拽。
        Objects.requireNonNull(value);
    }
    public void volume(float value){audio.volume(value);}
    public void enqueue(UnifiedSong value){if(!value.source().equals("kugou"))return;if(queue.stream().noneMatch(s->s.platformId().equals(value.platformId()))){var next=new ArrayList<>(queue);next.add(value);queue=List.copyOf(next);}}
    public void remove(int row){if(row<0||row>=queue.size())return;if(row==index)stop();else if(row<index)index--;var next=new ArrayList<>(queue);next.remove(row);queue=List.copyOf(next);}
    public void clearQueue(){stop();queue=List.of();}
    public void next(){if(!queue.isEmpty())play(queue,nextIndex());}
    public void previous(){if(!queue.isEmpty())play(queue,mode==PlaybackMode.SHUFFLE?nextIndex():Math.floorMod(index-1,queue.size()));}
    private int nextIndex(){if(mode!=PlaybackMode.SHUFFLE||queue.size()<2)return Math.floorMod(index+1,queue.size());return Math.floorMod(index+1+ThreadLocalRandom.current().nextInt(queue.size()-1),queue.size());}
    public void stop(){skipPending=false;lockedSongs.clear();claim();owner=false;loading=false;error=false;isSeeking=false;index=-1;song=null;actual=null;status="";long token=generation;audio.stop().whenComplete((v,e)->main.accept(()->{if(e!=null)fail(token,"酷狗声卡停止失败");}));}
    public void tick(){if(closed)return;if(account!=revision.getAsLong()){clearQueue();account=revision.getAsLong();return;}if(!sourceAllowed.getAsBoolean()){skipPending=false;if(owner)suspend();return;}skipLocked();if(audio.view().status()==KugouPlaybackAudio.Status.FINISHED)finished(audioEpoch);}
    private long claim(){long token=++generation;if(pending!=null){pending.cancel(true);pending=null;}network.getQueue().clear();network.purge();return token;}
    private boolean current(long token){return !closed&&owner&&sourceAllowed.getAsBoolean()&&generation==token&&revision.getAsLong()==account;}
    private void fail(long token,String message){if(closed||generation!=token)return;owner=false;loading=false;error=true;isSeeking=false;status=message;}
    @Override public void close(){if(closed)return;stop();closed=true;network.shutdownNow();audio.close();queue=List.of();}
}
