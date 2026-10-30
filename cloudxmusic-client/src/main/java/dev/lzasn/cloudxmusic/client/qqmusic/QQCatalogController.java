package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 每个界面的 QQ 目录会话。页面、账号、音源三重代次验证后才在客户端线程提交。 */
public final class QQCatalogController implements AutoCloseable {
    private static final Set<QQCatalogController> ACTIVE=ConcurrentHashMap.newKeySet();
    public enum Area {NONE,DISCOVER,FAVORITES}
    public enum Mode {HOME,PLAYLIST,DAILY,LIKED}
    public record View(Mode mode,String title,List<QQCatalogApi.Playlist> playlists,List<QQCatalogApi.Track> tracks,
                       boolean loading,boolean hasMore,boolean error,String status,int scroll,int cardOffset) {
        public View {playlists=List.copyOf(playlists);tracks=List.copyOf(tracks);}
    }
    interface DataSource {QQCatalogApi api();QQCatalogApi.Playlist daily()throws Exception;}
    @FunctionalInterface interface Factory {DataSource open(BooleanSupplier active)throws Exception;}
    private final Factory factory;
    private final Consumer<Runnable> main;
    private final LongSupplier account;
    private final BooleanSupplier source;
    private final QQPlaybackController playback;
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"CloudXMusic-QQ-Catalog");t.setDaemon(true);return t;});
    private volatile long generation;
    private volatile boolean closed;
    private long revision=Long.MIN_VALUE;
    private Area area=Area.NONE;
    private View view=empty();
    private Future<?> pending;
    private QQCatalogApi.Playlist selected;
    private int offset;
    private View observed=empty();
    private static View empty(){return new View(Mode.HOME,"QQ 音乐",List.of(),List.of(),false,false,false,"",0,0);}
    QQCatalogController(Factory factory,Consumer<Runnable> main,LongSupplier account,BooleanSupplier source,QQPlaybackController playback){this.factory=factory;this.main=main;this.account=account;this.source=source;this.playback=playback;ACTIVE.add(this);}
    public View view(){return view;}
    public boolean update(Area requested){
        if(!source.getAsBoolean())requested=Area.NONE;
        long rev=account.getAsLong();
        if(revision!=rev||area!=requested){cancel();revision=rev;area=requested;view=empty();selected=null;offset=0;
            if(area==Area.DISCOVER)home();else if(area==Area.FAVORITES)favorites();
        }
        // 回调发生在两帧之间；必须与上次已通知的快照比较，不能与本帧入口的 view 比较。
        boolean changed=!observed.equals(view);observed=view;return changed;
    }
    public void home(){
        cancel();selected=null;offset=0;view=new View(Mode.HOME,"QQ 音乐推荐",List.of(),List.of(),false,true,false,"",0,0);loadMore();
    }
    public void favorites(){
        cancel();selected=null;offset=0;view=new View(Mode.LIKED,"我喜欢",List.of(),List.of(),false,true,false,"",0,0);loadMore();
    }
    public void daily(boolean play){
        cancel();selected=null;offset=0;view=new View(Mode.DAILY,"每日30首 · 今日私享",List.of(),List.of(),false,true,false,"",0,0);load(play);
    }
    public void open(QQCatalogApi.Playlist playlist,boolean play){
        cancel();selected=playlist;offset=0;view=new View(Mode.PLAYLIST,playlist.title(),List.of(),List.of(),false,true,false,"",0,0);load(play);
    }
    public void refresh(){if(view.mode()==Mode.HOME)home();else if(view.mode()==Mode.LIKED)favorites();else if(view.mode()==Mode.DAILY)daily(false);else if(selected!=null)open(selected,false);}
    public void loadMore(){load(false);}
    private record Loaded(QQCatalogApi.Playlist selected,QQCatalogApi.Page<QQCatalogApi.Playlist> lists,QQCatalogApi.Page<QQCatalogApi.Track> songs){}
    private void load(boolean play){
        if(closed||area==Area.NONE||view.loading()||!view.hasMore()||!source.getAsBoolean())return;
        long token=generation,rev=account.getAsLong(),queueLease=playback==null?-1:playback.queueRevision();Mode mode=view.mode();int from=offset;QQCatalogApi.Playlist target=selected;
        view=new View(mode,view.title(),view.playlists(),view.tracks(),true,true,false,"正在加载…",view.scroll(),view.cardOffset());
        try{pending=io.submit(()->{
            try{
                DataSource session=factory.open(()->current(token,rev));QQCatalogApi api=session.api();Loaded loaded;
                if(mode==Mode.HOME)loaded=new Loaded(null,api.recommend(from),null);
                else{QQCatalogApi.Playlist ref=target;
                    if(ref==null)ref=mode==Mode.DAILY?session.daily():api.liked();
                    loaded=new Loaded(ref,null,api.tracks(ref,from));}
                main.accept(()->{
                    if(!current(token,rev))return;selected=loaded.selected();
                    if(loaded.lists()!=null){
                        var all=new LinkedHashMap<String,QQCatalogApi.Playlist>();view.playlists().forEach(p->all.put(p.key(),p));loaded.lists().items().forEach(p->all.putIfAbsent(p.key(),p));
                        boolean stuck=loaded.lists().hasMore()&&all.size()==view.playlists().size();offset=loaded.lists().nextOffset();
                        view=new View(mode,view.title(),List.copyOf(all.values()),List.of(),false,!stuck&&loaded.lists().hasMore(),stuck,stuck?"QQ 重复返回同一页，请刷新重试":"推荐歌单 · "+all.size()+" 个",view.scroll(),view.cardOffset());
                    }else{
                        var all=new LinkedHashMap<String,QQCatalogApi.Track>();view.tracks().forEach(t->all.put(t.song().platformId(),t));loaded.songs().items().forEach(t->all.putIfAbsent(t.song().platformId(),t));
                        boolean stuck=loaded.songs().hasMore()&&all.size()==view.tracks().size();offset=loaded.songs().nextOffset();
                        view=new View(mode,view.title(),List.of(),List.copyOf(all.values()),false,!stuck&&loaded.songs().hasMore(),stuck,stuck?"QQ 重复返回歌曲页，请刷新重试":all.isEmpty()?"歌单暂无歌曲":"已加载 "+all.size()+" 首"+(loaded.songs().hasMore()?" · 下拉继续加载":""),view.scroll(),0);
                        if(play&&!view.tracks().isEmpty()&&playback!=null&&playback.queueRevision()==queueLease)play(0);
                    }
                });
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception e){main.accept(()->{if(current(token,rev))view=new View(mode,view.title(),view.playlists(),view.tracks(),false,true,true,message(e),view.scroll(),view.cardOffset());});}
        });}catch(RejectedExecutionException e){view=new View(mode,view.title(),view.playlists(),view.tracks(),false,true,true,"目录任务正在取消，请稍后刷新",view.scroll(),view.cardOffset());}
    }
    public void scroll(int delta){
        int rows=view.mode()==Mode.HOME?(view.playlists().size()+QQCatalogLayout.COLUMNS-1)/QQCatalogLayout.COLUMNS:view.tracks().size(),visible=view.mode()==Mode.HOME?QQCatalogLayout.VISIBLE_ROWS:7;
        int next=Math.clamp(view.scroll()+delta,0,Math.max(0,rows-visible));
        view=new View(view.mode(),view.title(),view.playlists(),view.tracks(),view.loading(),view.hasMore(),view.error(),view.status(),next,view.cardOffset());
        if(delta>0&&next>=Math.max(0,rows-visible-1)&&view.hasMore())loadMore();
    }
    public void play(int row){
        if(row<0||row>=view.tracks().size()||!source.getAsBoolean()||account.getAsLong()!=revision)return;
        playback.play(view.tracks().stream().map(QQCatalogApi.Track::song).toList(),row);
    }
    private boolean current(long token,long rev){return !closed&&generation==token&&account.getAsLong()==rev&&source.getAsBoolean();}
    private static String message(Exception error){return error instanceof java.io.IOException&&error.getMessage()!=null?error.getMessage():"QQ 目录加载失败，请稍后刷新";}
    private void cancel(){generation++;if(pending!=null){pending.cancel(true);pending=null;}io.getQueue().clear();io.purge();}
    static void closeAll(){ACTIVE.forEach(QQCatalogController::close);}
    @Override public void close(){if(closed)return;closed=true;ACTIVE.remove(this);cancel();io.shutdownNow();view=empty();}
}
