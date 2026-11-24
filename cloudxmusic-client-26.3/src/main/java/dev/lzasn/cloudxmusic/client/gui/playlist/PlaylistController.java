package dev.lzasn.cloudxmusic.client.gui.playlist;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.*;

/** 页面状态只在客户端提交；后台仅持有不可变参数，完整收齐后才允许替换队列。 */
public final class PlaylistController implements AutoCloseable {
    private static final Set<PlaylistController> ACTIVE=ConcurrentHashMap.newKeySet();
    @FunctionalInterface public interface Factory { PlaylistSource open(BooleanSupplier active) throws Exception; }
    private static final int MAX_ITEMS=20000,MAX_PAGES=400;
    private final Factory factory;
    private final Consumer<Runnable> main;
    private final LongSupplier revision;
    private final BooleanSupplier loggedIn;
    private final Consumer<String> notice;
    private final ExecutorService worker;
    private volatile long generation;
    private volatile boolean visible,closed;
    private long account=Long.MIN_VALUE;
    private PlaylistSource session;
    private Future<?> pending;
    private int cursor;
    private PlaylistView view=PlaylistView.empty(),observed=PlaylistView.empty();

    public PlaylistController(Factory factory,Consumer<Runnable> main,LongSupplier revision,BooleanSupplier loggedIn,Consumer<String> notice){
        this(factory,main,revision,loggedIn,notice,new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"CloudXMusic-Playlists");t.setDaemon(true);return t;}));
    }
    PlaylistController(Factory factory,Consumer<Runnable> main,LongSupplier revision,BooleanSupplier loggedIn,Consumer<String> notice,ExecutorService worker){
        this.factory=factory;this.main=main;this.revision=revision;this.loggedIn=loggedIn;this.notice=notice;this.worker=worker;
        ACTIVE.add(this);
    }
    public PlaylistView view(){return view;}
    public boolean update(boolean shown){
        if(closed)return false;
        long next=revision.getAsLong();
        if(account!=next||visible!=shown){
            cancel();session=null;account=next;visible=shown;cursor=0;view=PlaylistView.empty();
            if(shown){if(loggedIn.getAsBoolean())loadMore();else status("登录后查看账号收藏",false);}
        }
        boolean changed=!observed.equals(view);observed=view;return changed;
    }
    public void select(Section section){
        if(!visible||closed)return;
        if(!loggedIn.getAsBoolean()){notice.accept("请先登录账号");return;}
        cancel();cursor=0;
        view=new PlaylistView(section,null,List.of(),List.of(),false,false,true,false,"",0,0);loadMore();
    }
    public void open(Entry entry){
        if(!visible||closed||!loggedIn.getAsBoolean())return;
        cancel();cursor=0;view=new PlaylistView(view.section(),entry,List.of(),List.of(),false,false,true,false,"",0,entry.count());loadMore();
    }
    public void back(){select(view.section());}
    public void refresh(){if(view.selected()!=null)open(view.selected());else select(view.section());}
    public void cancelPlayAll(){if(view.playingAll()){cancel();status("已取消歌单播放加载",false);}}
    private PlaylistSource session() throws Exception {
        if(session==null){long epoch=account;session=factory.open(()->!closed&&visible&&revision.getAsLong()==epoch);}
        return session;
    }
    private boolean current(long token,long epoch){return !closed&&visible&&generation==token&&revision.getAsLong()==epoch&&loggedIn.getAsBoolean();}
    public void loadMore(){
        if(closed||!visible||!loggedIn.getAsBoolean()||view.loading()||view.playingAll())return;
        // 首次进入“我喜欢”没有游标，其余请求只在 hasMore 时继续。
        if(!view.hasMore()&&(cursor!=0||!view.tracks().isEmpty()||!view.playlists().isEmpty()))return;
        long token=generation,epoch=account;int from=cursor;var before=view;
        try{
            var source=session();busy(false,"正在加载…");
            submit(()->{
                try{
                    if(!current(token,epoch))return;
                    if(before.detail()){
                        Page<Track> page=before.selected()==null?source.liked(from):source.tracks(before.selected(),from);
                        var all=new LinkedHashMap<String,Track>();before.tracks().forEach(t->all.put(key(t),t));page.items().forEach(t->all.putIfAbsent(key(t),t));
                        checkPage(from,page.nextCursor(),page.hasMore(),all.size()-before.tracks().size(),all.size());
                        main.accept(()->{if(!current(token,epoch))return;cursor=page.nextCursor();view=new PlaylistView(before.section(),before.selected(),List.of(),List.copyOf(all.values()),false,false,page.hasMore(),false,"已加载 "+all.size()+" 首"+(page.hasMore()?" · 下拉继续加载":""),before.scroll(),page.total());});
                    }else{
                        Page<Entry> page=source.playlists(before.section(),from);
                        var all=new LinkedHashMap<String,Entry>();before.playlists().forEach(e->all.put(e.key(),e));page.items().forEach(e->all.putIfAbsent(e.key(),e));
                        if(page.hasMore()&&!page.items().isEmpty()&&all.size()==before.playlists().size())throw new IOException("歌单目录分页重复");
                        // 分类页允许本页全是另一分类；游标仍必须向前，目录总数也有硬上限。
                        checkPage(from,page.nextCursor(),page.hasMore(),1,all.size());
                        main.accept(()->{if(!current(token,epoch))return;cursor=page.nextCursor();view=new PlaylistView(before.section(),null,List.copyOf(all.values()),List.of(),false,false,page.hasMore(),false,all.isEmpty()&&!page.hasMore()?"暂无歌单":"已加载 "+all.size()+" 个歌单",before.scroll(),page.total());});
                    }
                }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){failed(token,epoch);}
            });
        }catch(Exception e){status("歌单加载失败，请刷新重试",true);}
    }
    public void playAll(){collect(-1);}
    public void playFrom(int index){
        if(index<0||index>=view.tracks().size())return;
        collect(index);
    }
    private void collect(int index){
        if(closed||!visible||view.playingAll()||view.loading()||!view.detail())return;
        if(!loggedIn.getAsBoolean()){notice.accept("请先登录账号");return;}
        if(view.tracks().isEmpty()){notice.accept("歌单暂无可播放歌曲");return;}
        long token=++generation,epoch=account;var before=view;int from=cursor;
        try{
            var source=session();Object stamp=source.playbackStamp();busy(true,progress(before.tracks().size(),before.total()));
            submit(()->{
                try{
                    var all=new LinkedHashMap<String,Track>();before.tracks().forEach(t->all.put(key(t),t));
                    boolean more=before.hasMore();int next=from,total=before.total();
                    for(int pageNumber=0;more;pageNumber++){
                        if(!current(token,epoch))return;
                        if(pageNumber>=MAX_PAGES)throw new IOException("分页超出上限");
                        Page<Track> page=before.selected()==null?source.liked(next):source.tracks(before.selected(),next);
                        int oldSize=all.size();page.items().forEach(t->all.putIfAbsent(key(t),t));
                        checkPage(next,page.nextCursor(),page.hasMore(),all.size()-oldSize,all.size());next=page.nextCursor();more=page.hasMore();total=page.total();
                        int received=all.size(),expected=total;
                        main.accept(()->{if(current(token,epoch))busy(true,progress(received,expected));});
                    }
                    var complete=new ArrayList<>(all.values());String selectedKey=index>=0&&index<before.tracks().size()?key(before.tracks().get(index)):"";
                    if(!complete.isEmpty()&&complete.stream().allMatch(t->t.order()!=null))complete.sort(Comparator.comparing(Track::order));
                    int selected=0;if(index>=0)for(int n=0;n<complete.size();n++)if(key(complete.get(n)).equals(selectedKey)){selected=n;break;}
                    int finalIndex=selected,finalCursor=next,finalTotal=total;
                    main.accept(()->{
                        if(!current(token,epoch))return;
                        if(!Objects.equals(stamp,source.playbackStamp())){status("播放已变化，已取消歌单播放",false);return;}
                        cursor=finalCursor;view=new PlaylistView(before.section(),before.selected(),List.of(),complete,false,false,false,false,"已加载 "+complete.size()+" 首",before.scroll(),finalTotal);
                        if(!complete.isEmpty())source.play(List.copyOf(complete),finalIndex);
                    });
                }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){failed(token,epoch);}
            });
        }catch(Exception e){status("歌单加载失败，原播放队列已保留，请重试",true);}
    }
    private static String progress(int loaded,int total){return "正在加载 "+loaded+(total>0?"/"+total:"")+" 首";}
    private static String key(Track track){return track.song().source()+":"+track.song().platformId();}
    private static void checkPage(int from,int next,boolean more,int added,int count)throws IOException{
        if(count>MAX_ITEMS||next>MAX_ITEMS||next<from||more&&(next<=from||added==0))throw new IOException("分页重复或超出上限");
    }
    private void failed(long token,long epoch){main.accept(()->{if(current(token,epoch))status("歌单加载失败，原播放队列已保留，请刷新重试",true);});}
    private void busy(boolean all,String message){view=new PlaylistView(view.section(),view.selected(),view.playlists(),view.tracks(),!all,all,view.hasMore(),false,message,view.scroll(),view.total());}
    private void status(String message,boolean error){view=new PlaylistView(view.section(),view.selected(),view.playlists(),view.tracks(),false,false,view.hasMore(),error,message,view.scroll(),view.total());}
    public void scroll(int delta){
        int count=view.detail()?view.tracks().size():((view.playlists().size()+3)/4),visibleRows=view.detail()?7:2;
        int next=Math.clamp(view.scroll()+delta,0,Math.max(0,count-visibleRows));
        view=new PlaylistView(view.section(),view.selected(),view.playlists(),view.tracks(),view.loading(),view.playingAll(),view.hasMore(),view.error(),view.status(),next,view.total());
        if(delta>0&&next>=Math.max(0,count-visibleRows-1)&&view.hasMore())loadMore();
    }
    private void submit(Runnable task){pending=worker.submit(task);}
    private void cancel(){generation++;if(pending!=null){pending.cancel(true);pending=null;}if(worker instanceof ThreadPoolExecutor pool){pool.getQueue().clear();pool.purge();}}
    public static void closeAll(){ACTIVE.forEach(PlaylistController::close);}
    @Override public void close(){if(closed)return;closed=true;visible=false;cancel();worker.shutdownNow();session=null;view=PlaylistView.empty();ACTIVE.remove(this);}
}
