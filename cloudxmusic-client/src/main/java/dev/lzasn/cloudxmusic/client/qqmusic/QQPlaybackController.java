package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** QQ 独立会话与音源交接。状态在客户端线程操作；网络/声卡异步完成后再发布。 */
public final class QQPlaybackController implements AutoCloseable {
    record Resolved(String url,int duration,QQQuality actualQuality) {
        Resolved(String url,int duration){this(url,duration,null);}
    }
    @FunctionalInterface interface Resolver { Resolved resolve(UnifiedSong song) throws Exception; }
    @FunctionalInterface interface QualityResolver { Resolved resolve(UnifiedSong song,QQQuality quality) throws Exception; }
    public record View(UnifiedSong song,boolean loading,boolean playing,boolean paused,double position,double duration,
                       float volume,String quality,String status,boolean error,QQQuality actualQuality) {
        public View(UnifiedSong song,boolean loading,boolean playing,boolean paused,double position,double duration,
                    float volume,String quality,String status,boolean error){this(song,loading,playing,paused,position,duration,volume,quality,status,error,null);}
    }
    private final QQPlaybackAudio audio;
    private final Supplier<CompletableFuture<Void>> pauseNetease;
    private final Consumer<Runnable> main;
    private QualityResolver resolver;
    private final LongSupplier revision;
    private final ThreadPoolExecutor network=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),
            r->{Thread t=new Thread(r,"CloudXMusic-QQ-PlaybackResolve");t.setDaemon(true);return t;});
    private List<UnifiedSong> playlist=List.of();
    private int index=-1;
    private final Set<String> unavailable=new HashSet<>();
    private IntUnaryOperator random=bound->ThreadLocalRandom.current().nextInt(bound);
    private volatile long queueRevision;
    long queueRevision(){return queueRevision;}
    private Consumer<String> notice=message->{};
    void random(IntUnaryOperator random){this.random=Objects.requireNonNull(random);}
    void notice(Consumer<String> notice){this.notice=Objects.requireNonNull(notice);}
    private volatile long generation;
    private volatile boolean closed;
    private volatile long account;
    private volatile boolean qqOwner;
    private boolean loading,error;
    private String status="",quality="QQ";
    private Future<?> pending;
    private CompletableFuture<Void> handoff=CompletableFuture.completedFuture(null);
    private Consumer<String> neteaseFailure=message->{};
    private BooleanSupplier sourceAllowed=()->true;
    private BooleanSupplier neteaseAllowed=()->true;
    private QQQuality preferred=QQQuality.SQ;
    private QQQuality actualQuality;
    private dev.lzasn.cloudxmusic.client.state.PlaybackMode mode=dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST;
    private double resumePosition;
    private double knownDuration;
    private boolean resumePaused;
    private long audioEpoch,finishedEpoch=-1;
    private BiConsumer<QQQuality,QQQuality> qualityFeedback=(requested,actual)->{};
    void qualityFeedback(BiConsumer<QQQuality,QQQuality> feedback){qualityFeedback=Objects.requireNonNull(feedback);}
    public void sourceGuards(BooleanSupplier qq,BooleanSupplier netease){sourceAllowed=qq;neteaseAllowed=netease;}
    void qualityAware(){resolver=(song,quality)->{QQProvider provider=(QQProvider)OmniRouter.getProvider("qq");var link=provider.playbackLink(song,quality);return link==null?null:new Resolved(link.url(),provider.durationSeconds(song.platformId()),link.actualQuality());};}
    public QQQuality preferredQuality(){return preferred;}
    public dev.lzasn.cloudxmusic.client.state.PlaybackMode mode(){return mode;}
    public void mode(dev.lzasn.cloudxmusic.client.state.PlaybackMode value){mode=Objects.requireNonNull(value);}
    public void cycleMode(){if(closed||!sourceAllowed.getAsBoolean())return;var modes=dev.lzasn.cloudxmusic.client.state.PlaybackMode.values();mode=modes[(mode.ordinal()+1)%modes.length];}
    public List<UnifiedSong> queue(){return playlist;}
    void neteaseFailure(Consumer<String> callback){neteaseFailure=Objects.requireNonNull(callback);}
    QQPlaybackController(QQPlaybackAudio audio,Supplier<CompletableFuture<Void>> pauseNetease,Consumer<Runnable> main,LongSupplier revision) {
        this(audio,pauseNetease,main,song->{
            OnlineMusicProvider provider=OmniRouter.getProvider("qq");String url=provider.getPlaybackUrl(song);
            return new Resolved(url,provider instanceof QQProvider qq?qq.durationSeconds(song.platformId()):0);
        },revision);
    }
    QQPlaybackController(QQPlaybackAudio audio,Supplier<CompletableFuture<Void>> pauseNetease,Consumer<Runnable> main,Resolver resolver,LongSupplier revision) {
        this(audio,pauseNetease,main,(song,quality)->resolver.resolve(song),revision);
    }
    QQPlaybackController(QQPlaybackAudio audio,Supplier<CompletableFuture<Void>> pauseNetease,Consumer<Runnable> main,QualityResolver resolver,LongSupplier revision) {
        this.audio=audio;this.pauseNetease=pauseNetease;this.main=main;this.resolver=resolver;this.revision=revision;this.account=revision.getAsLong();
    }
    public boolean hasTrack(){return index>=0&&index<playlist.size();}
    public View view(){
        QQPlaybackAudio.View a=audio.view();boolean waiting=loading||(qqOwner&&!error&&a.status()==QQPlaybackAudio.Status.LOADING);boolean active=qqOwner&&!waiting&&!error;
        return new View(hasTrack()?playlist.get(index):null,waiting,active&&a.status()==QQPlaybackAudio.Status.PLAYING,
                loading?resumePaused:hasTrack()&&!waiting&&!error&&(!qqOwner||a.status()==QQPlaybackAudio.Status.PAUSED),
                loading?resumePosition:a.position(),loading?knownDuration:a.duration(),a.volume(),quality,
                !loading&&a.status()==QQPlaybackAudio.Status.ERROR?a.error():status,error||(!loading&&a.status()==QQPlaybackAudio.Status.ERROR),hasTrack()?actualQuality:null);
    }
    public void play(List<UnifiedSong> songs,int selected) {
        queueRevision++;unavailable.clear();begin(songs,selected,0,false);
    }
    private void begin(List<UnifiedSong> songs,int selected,double position,boolean paused) {
        if(closed||!sourceAllowed.getAsBoolean()||selected<0||selected>=songs.size())return;
        List<UnifiedSong> copy=List.copyOf(songs);if(copy.stream().anyMatch(s->!s.source().equals("qq")))throw new IllegalArgumentException("QQ 播放列表只能包含 QQ 歌曲");
        boolean same=hasTrack()&&playlist.get(index).equals(copy.get(selected));
        knownDuration=same?view().duration():0;resumePosition=position;resumePaused=paused;
        long token=claim();playlist=copy;index=selected;account=revision.getAsLong();qqOwner=true;loading=true;error=false;quality="QQ";actualQuality=null;
        long epoch=++audioEpoch;
        status=position>0?"正在取链并按样本恢复进度…":"正在取链并准备播放…";UnifiedSong song=playlist.get(index);
        QQQuality requested=preferred;
        CompletableFuture<Void> quiet;
        try {quiet=CompletableFuture.allOf(handoff,pauseNetease.get(),audio.stop());handoff=quiet;}
        catch(Exception e){fail(token,"暂停网易云失败，未启动 QQ 音乐");return;}
        network.purge();
        try {pending=network.submit(()->{
            try {
                Resolved result=resolver.resolve(song,requested);
                Resolved resolved=result;
                quiet.whenComplete((v,e)->main.accept(()->{
                    if(!current(token))return;
                    if(e!=null){fail(token,"声卡交接失败，未启动 QQ 音乐");return;}
                    if(resolved==null||resolved.url()==null||resolved.url().isBlank()){skipUnavailable(token);return;}
                    URI uri;try{uri=URI.create(resolved.url());}catch(IllegalArgumentException bad){fail(token,"QQ 返回无效音频地址");return;}
                    QQQuality actual=resolved.actualQuality()!=null?resolved.actualQuality():uri.getPath().toLowerCase(Locale.ROOT).endsWith(".flac")?QQQuality.SQ:QQQuality.HQ;
                    actualQuality=actual;quality=actual.barLabel();
                    unavailable.clear();knownDuration=resolved.duration();
                    audio.playFrom(uri,resolved.duration(),position,paused,()->current(token),()->main.accept(()->finished(epoch))).whenComplete((ignored,failure)->main.accept(()->{
                        if(!current(token))return;if(failure!=null){fail(token,"QQ 播放器启动失败");return;}
                        loading=false;status=paused?"音质已选择 · 保持暂停":position>0?"正在按样本恢复进度…":"已暂停网易云播放 · QQ 音频加载中";
                        qualityFeedback.accept(requested,actual);
                    }));
                }));
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(QQPlaybackApi.BusinessFailure e){main.accept(()->{
                if(e.songUnavailable())skipUnavailable(token);
                else fail(token,"QQ 取链失败："+e.getMessage());
            });}
            catch(Exception e){main.accept(()->fail(token,"QQ 取链失败："+(e.getMessage()==null?"网络或响应异常":e.getMessage())));}
        });}catch(RejectedExecutionException e){fail(token,"取链任务正在取消，请稍后重试");}
    }
    /** 网易云显式播放、恢复、自动下一首均通过此入口，不靠 UI 观察事后补暂停。 */
    public void beforeNetease(Runnable start) {
        if(closed||!neteaseAllowed.getAsBoolean())return;long token=claim();qqOwner=false;loading=false;error=false;
        if(hasTrack())status="已暂停 QQ 音乐播放";
        handoff=CompletableFuture.allOf(handoff,audio.pause());
        handoff.whenComplete((v,e)->main.accept(()->{
            if(closed||generation!=token||!neteaseAllowed.getAsBoolean())return;
            if(e!=null){String message="QQ 声卡未能暂停，未启动网易云";fail(token,message);neteaseFailure.accept(message);return;}
            start.run();
        }));
    }
    public void toggle(){
        if(closed||!sourceAllowed.getAsBoolean())return;
        if(!hasTrack()){if(!playlist.isEmpty())play(playlist,0);return;}
        View v=view();
        if(v.loading()||v.playing()) {
            long token=claim();loading=false;qqOwner=false;
            audio.pause().whenComplete((x,e)->main.accept(()->{if(generation!=token||closed)return;if(e!=null)fail(token,"QQ 暂停失败");else status="QQ 音乐已暂停";}));
        }else if(audio.view().status()==QQPlaybackAudio.Status.PAUSED&&!v.error()&&actualQuality!=null) {
            long token=claim();account=revision.getAsLong();qqOwner=true;loading=true;
            handoff=CompletableFuture.allOf(handoff,pauseNetease.get());
            handoff.thenCompose(x->audio.resume(()->current(token))).whenComplete((x,e)->main.accept(()->{
                if(!current(token))return;loading=false;if(e!=null)fail(token,"音源交接失败，未恢复 QQ 音乐");else status="已暂停网易云播放";
            }));
        }else begin(playlist,index,resumePosition,false);
    }
    public void next(){if(closed||!sourceAllowed.getAsBoolean()||playlist.isEmpty())return;play(playlist,nextIndex(false,Set.of()));}
    public void previous(){if(closed||!sourceAllowed.getAsBoolean()||playlist.isEmpty())return;play(playlist,mode==dev.lzasn.cloudxmusic.client.state.PlaybackMode.SHUFFLE?nextIndex(false,Set.of()):index<0?0:Math.floorMod(index-1,playlist.size()));}
    public void volume(float value){audio.volume(value);}
    public void progressHint(){status="QQ 跳转会从头解码并按样本跳过，请稍候";}
    public void seek(double position){if(hasTrack()&&Double.isFinite(position))begin(playlist,index,Math.max(0,Math.min(position,Math.max(0,view().duration()-1))),loading?resumePaused:!view().playing());}
    public void quality(QQQuality value){
        Objects.requireNonNull(value);
        // 偏好仍是 SQ、当前已降级时，再选 SQ 必须允许重新取链；请求进行中避免重复提交。
        if(preferred==value&&(!hasTrack()||loading||actualQuality==value))return;
        View v=view();preferred=value;if(hasTrack())begin(playlist,index,v.position(),loading?resumePaused:!v.playing()&&!v.loading());
    }
    public CompletableFuture<Void> suspend(){resumePosition=view().position();claim();qqOwner=false;loading=false;status=hasTrack()?"QQ 音乐已暂停":"";handoff=CompletableFuture.allOf(handoff,audio.pause());return handoff;}
    public void enqueue(UnifiedSong song){if(!song.source().equals("qq"))return;queueRevision++;ArrayList<UnifiedSong> next=new ArrayList<>(playlist);if(!next.contains(song))next.add(song);playlist=List.copyOf(next);}
    public void remove(int row){if(row<0||row>=playlist.size())return;queueRevision++;if(row==index)stop();else if(row<index)index--;ArrayList<UnifiedSong> next=new ArrayList<>(playlist);next.remove(row);playlist=List.copyOf(next);}
    public void clearQueue(){stop();playlist=List.of();}
    public void stop(){queueRevision++;unavailable.clear();claim();loading=false;qqOwner=false;error=false;index=-1;status="";
        long token=generation;audio.stop().whenComplete((v,e)->main.accept(()->{if(e!=null)fail(token,"QQ 声卡未能停止");}));}
    public void tick(){
        if(closed)return;
        if(revision.getAsLong()!=account){clearQueue();account=revision.getAsLong();return;}
        if(qqOwner&&!loading&&!error&&audio.view().status()==QQPlaybackAudio.Status.PLAYING)status="";
        // 保留轮询兜底；与显式回调共用一次性消费，关闭界面后也继续工作。
        if(audio.view().status()==QQPlaybackAudio.Status.FINISHED)finished(audioEpoch);
    }
    private void finished(long epoch){
        if(closed||epoch!=audioEpoch||finishedEpoch==epoch||!qqOwner||!sourceAllowed.getAsBoolean()||loading||error||!hasTrack()||revision.getAsLong()!=account||audio.view().status()!=QQPlaybackAudio.Status.FINISHED)return;
        finishedEpoch=epoch;
        int next=nextIndex(true,Set.of());
        play(playlist,next);
    }
    /** 手动下一首、自然结束和无资源跳过共用 QQ 策略，绝不读写网易云的队列。 */
    private int nextIndex(boolean automatic,Set<String> excluded){
        if(playlist.isEmpty())return -1;
        if(automatic&&mode==dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_ONE&&hasTrack()&&excluded.isEmpty())return index;
        List<Integer> candidates=new ArrayList<>();
        for(int step=1;step<=playlist.size();step++){
            int candidate=Math.floorMod(index+step,playlist.size());
            if(!excluded.contains(playlist.get(candidate).platformId()))candidates.add(candidate);
        }
        if(candidates.isEmpty())return -1;
        if(mode!=dev.lzasn.cloudxmusic.client.state.PlaybackMode.SHUFFLE)return candidates.getFirst();
        if(candidates.size()>1)candidates.remove(Integer.valueOf(index));
        return candidates.get(random.applyAsInt(candidates.size()));
    }
    private void skipUnavailable(long token){
        if(!current(token)||!hasTrack())return;
        unavailable.add(playlist.get(index).platformId());
        int next=nextIndex(false,unavailable);
        if(next<0){fail(token,"队列中的歌曲均无可用音源，已停止尝试");notice.accept("队列中的歌曲均无可用音源，已停止尝试");return;}
        notice.accept("该歌曲无可用音源，已跳过");
        // 不重置失败集合；每个 MID 本轮最多尝试一次，单曲循环也不会困在不可播放歌曲中。
        begin(playlist,next,0,false);
    }
    private long claim(){long token=++generation;if(pending!=null){pending.cancel(true);pending=null;}network.getQueue().clear();return token;}
    private boolean current(long token){return !closed&&qqOwner&&sourceAllowed.getAsBoolean()&&generation==token&&revision.getAsLong()==account;}
    private void fail(long token,String message){if(closed||generation!=token)return;loading=false;error=true;qqOwner=false;status=message;}
    @Override public void close(){if(closed)return;stop();closed=true;network.shutdownNow();audio.close();}
}
