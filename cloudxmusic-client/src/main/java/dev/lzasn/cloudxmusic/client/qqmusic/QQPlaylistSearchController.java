package dev.lzasn.cloudxmusic.client.qqmusic;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 歌单搜索的 Screen 会话；账号和页面撤销在客户端线程完成，过期回调不可写回。 */
public final class QQPlaylistSearchController implements AutoCloseable {
    @FunctionalInterface interface Search {QQPlaylistSearchApi.Page load(String query,int page)throws Exception;}
    public record View(List<QQPlaylistSearchApi.Item> items,int scroll,boolean loading,boolean error,String status){public View{items=List.copyOf(items);}}
    private final Search search;private final Consumer<Runnable> main;private final Runnable changed;private final LongSupplier account;
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{var t=new Thread(r,"CloudXMusic-QQ-PlaylistSearch");t.setDaemon(true);return t;});
    private View view=new View(List.of(),0,false,false,"搜索 QQ 音乐歌单");
    private boolean active,closed,playlists,hasMore;private long generation,revision=Long.MIN_VALUE;private int nextPage=1;
    private String draft="",query="";private Future<?> pending;
    public QQPlaylistSearchController(Consumer<Runnable> main,Runnable changed,LongSupplier account){this(new QQPlaylistSearchApi()::search,main,changed,account);}
    QQPlaylistSearchController(Search search,Consumer<Runnable> main,Runnable changed,LongSupplier account){this.search=search;this.main=main;this.changed=changed;this.account=account;}
    public View view(){return view;}public boolean playlists(){return playlists;}public void playlists(boolean value){playlists=value;}
    public String draft(){return draft;}public void remember(String value){draft=value;}
    public void update(boolean enabled){
        if(closed)return;long rev=account.getAsLong();
        if(revision!=rev){cancel();revision=rev;view=new View(List.of(),0,false,false,"搜索 QQ 音乐歌单");nextPage=1;hasMore=false;query="";changed.run();}
        if(active&&!enabled){cancel();view=new View(view.items(),view.scroll(),false,view.error(),view.status());changed.run();}
        active=enabled;
    }
    public boolean submit(String value){
        if(closed||!active||value==null||value.isBlank()||view.loading())return false;
        cancel();query=value.strip();draft=value;nextPage=1;hasMore=true;view=new View(List.of(),0,false,false,"");return request();
    }
    public void loadMore(){if(active&&hasMore&&!view.loading()&&view.items().size()<1200)request();}
    public void scroll(int delta){int next=Math.clamp(view.scroll()+delta*4,0,Math.max(0,((view.items().size()+3)/4-2)*4));view=new View(view.items(),next,view.loading(),view.error(),view.status());changed.run();if(delta>0&&next+8>=view.items().size())loadMore();}
    private boolean request(){
        long token=generation,rev=revision;String q=query;int page=nextPage;
        view=new View(view.items(),view.scroll(),true,false,page==1?"正在搜索歌单…":"正在加载更多歌单…");changed.run();
        try{pending=io.submit(()->{try{var result=search.load(q,page);main.accept(()->{
            if(!current(token,rev))return;var all=new LinkedHashMap<String,QQPlaylistSearchApi.Item>();view.items().forEach(v->all.put(v.playlist().key(),v));result.items().forEach(v->all.putIfAbsent(v.playlist().key(),v));
            hasMore=result.hasMore()&&result.nextPage()>page;nextPage=result.nextPage();
            view=new View(List.copyOf(all.values()),view.scroll(),false,false,"已加载 "+all.size()+" 个歌单 · "+(hasMore?"下拉加载更多":"已到底"));changed.run();
        });}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();main.accept(()->{if(current(token,rev)){view=new View(view.items(),view.scroll(),false,true,"歌单搜索失败，请检查网络后重试");changed.run();}});}});}
        catch(RejectedExecutionException e){view=new View(view.items(),view.scroll(),false,true,"搜索任务正在取消，请重试");changed.run();}return true;
    }
    private boolean current(long token,long rev){return !closed&&active&&generation==token&&account.getAsLong()==rev;}
    private void cancel(){generation++;if(pending!=null)pending.cancel(true);pending=null;io.getQueue().clear();}
    @Override public void close(){if(closed)return;closed=true;cancel();io.shutdownNow();view=new View(List.of(),0,false,false,"");}
}
