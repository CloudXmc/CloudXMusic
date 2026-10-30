package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.OmniRouter;
import dev.lzasn.cloudxmusic.client.omni.OnlineMusicProvider;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.Track;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/** 一个 Screen 的搜索会话。可变界面状态只在客户端线程读写，网络仅在专属工作线程执行。 */
public final class QQSearchController implements AutoCloseable {
    public enum Source {
        NETEASE("netease", "网易云"), QQ("qq", "QQ 音乐");
        private final String id, label;
        Source(String id, String label) { this.id = id; this.label = label; }
        public String id() { return id; }
        public String label() { return label; }
    }

    public record View(Source source, List<UnifiedSong> songs, int scroll,
                       boolean loading, boolean error, String status) {}
    private int visibleRows = 8;
    public void visibleRows(int count){visibleRows=Math.max(1,Math.min(8,count));Session qq=sessions.get(Source.QQ);qq.scroll=Math.min(qq.scroll,Math.max(0,qq.songs.size()-visibleRows));}
    private static final Set<QQSearchController> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final class Session {
        String draft = "", status = "";
        List<UnifiedSong> songs = List.of();
        List<Track> tracks = List.of();
        int scroll, nextOffset;
        String query = "";
        boolean hasMore;
        long generation;
        boolean loading, error;
        Future<?> pending;
    }

    private final EnumMap<Source, Session> sessions = new EnumMap<>(Source.class);
    private final EnumMap<Source, ExecutorService> workers = new EnumMap<>(Source.class);
    private final Consumer<Runnable> main;
    private final Runnable changed;
    private final Consumer<Exception> log;
    private final Function<String, OnlineMusicProvider> router;
    private Source selected = Source.NETEASE;
    private boolean closed;

    public QQSearchController(Consumer<Runnable> main, Runnable changed, Consumer<Exception> log) {
        this(main, changed, log, OmniRouter::getProvider);
    }

    QQSearchController(Consumer<Runnable> main, Runnable changed, Consumer<Exception> log,
                       Function<String, OnlineMusicProvider> router) {
        this.main = Objects.requireNonNull(main);
        this.changed = Objects.requireNonNull(changed);
        this.log = Objects.requireNonNull(log);
        this.router = Objects.requireNonNull(router);
        for (Source source : Source.values()) {
            sessions.put(source, new Session());
            workers.put(source, new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(1), task -> {
                        Thread thread = new Thread(task, "CloudXMusic-Search-" + source.id());
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy()));
        }
    }

    public void seedNetease(List<Track> tracks, String keyword) {
        Session session = sessions.get(Source.NETEASE);
        session.tracks = List.copyOf(tracks);
        session.draft = Objects.requireNonNullElse(keyword, "");
    }
    public Source source() { return selected; }
    public boolean isQq() { return selected == Source.QQ; }
    public String draft() { return sessions.get(selected).draft; }
    public int scroll() { return sessions.get(selected).scroll; }
    public List<Track> neteaseTracks() { return sessions.get(Source.NETEASE).tracks; }

    public void remember(String draft, int scroll) {
        Session session = sessions.get(selected);
        session.draft = Objects.requireNonNull(draft);
        session.scroll = Math.max(0, scroll);
    }
    public void switchTo(Source source, String draft, int scroll) {
        if (closed) return;
        remember(draft, scroll);
        selected = Objects.requireNonNull(source);
        changed.run();
    }
    public void scrollQq(int delta) {
        if (closed || !isQq()) return;
        Session session = sessions.get(selected);
        session.scroll = Math.max(0, Math.min(session.scroll + delta,
                Math.max(0, session.songs.size() - visibleRows)));
        changed.run();
        if(delta>0 && session.scroll+visibleRows>=session.songs.size())loadMore();
    }
    public View view() {
        Session session = sessions.get(selected);
        return new View(selected, session.songs, session.scroll, session.loading, session.error, session.status);
    }

    public boolean submit(String keyword) {
        if (closed || keyword == null || keyword.isBlank()) return false;
        Source source = selected;
        Session session = sessions.get(source);
        if (session.loading) return false;
        session.query = keyword.strip();
        session.draft = keyword;
        session.scroll = 0;
        session.songs = List.of();
        session.nextOffset = 0;
        session.hasMore = true;
        ++session.generation;
        return request(source, false);
    }

    public boolean loadMore() {
        if (closed || !isQq()) return false;
        Session session = sessions.get(Source.QQ);
        if (session.loading || !session.hasMore || session.query.isEmpty()) return false;
        // 限制单个搜索会话的内存；更换关键词或关闭 Screen 即释放。
        if (session.songs.size() >= 2000) {
            session.hasMore=false;session.status="已加载 2000 首，请缩小搜索范围";changed.run();return false;
        }
        return request(Source.QQ, true);
    }

    private boolean request(Source source, boolean append) {
        Session session=sessions.get(source);
        ACTIVE.add(this);
        session.loading=true;session.error=false;
        session.status=append?"正在加载更多…":"正在搜索「"+session.query+"」…";
        long generation=session.generation;
        String query=session.query;
        int offset=session.nextOffset;
        changed.run();
        try {
            session.pending=workers.get(source).submit(()->{
                try {
                    OnlineMusicProvider provider=router.apply(source.id());
                    QQProvider.SearchPage page;
                    List<Track> originals=List.of();
                    if(source==Source.QQ && provider instanceof QQProvider qq)page=qq.searchPage(query,offset,30);
                    else {
                        var batch=provider.searchSongs(query,offset,30);
                        if(source==Source.NETEASE)originals=NeteaseSearchAdapter.originalTracks(batch);
                        var songs=List.copyOf(batch);
                        page=new QQProvider.SearchPage(songs,offset+30,source==Source.QQ&&songs.size()==30);
                    }
                    List<Track> originalTracks=originals;
                    if(page.songs().stream().anyMatch(song->!source.id().equals(song.source())))throw new IOException("Provider returned a different source");
                    main.accept(()->complete(source,generation,append,page,originalTracks,null));
                } catch(Exception exception) {
                    if(exception instanceof InterruptedException)Thread.currentThread().interrupt();
                    log.accept(exception);
                    main.accept(()->complete(source,generation,append,null,List.of(),exception));
                }
            });
        } catch(RejectedExecutionException exception) {
            log.accept(exception);complete(source,generation,append,null,List.of(),exception);
        }
        return true;
    }

    private void complete(Source source,long generation,boolean append,QQProvider.SearchPage page,
                          List<Track> tracks,Exception failure) {
        Session session=sessions.get(source);
        if(closed||generation!=session.generation)return;
        session.pending=null;session.loading=false;session.error=failure!=null;
        if(failure==null){
            var unique=new java.util.LinkedHashMap<String,UnifiedSong>();
            if(append)for(var song:session.songs)unique.put(song.platformId(),song);
            for(var song:page.songs()){
                if(source==Source.QQ&&unique.size()>=2000)break;
                unique.putIfAbsent(song.platformId(),song);
            }
            session.songs=List.copyOf(unique.values());
            session.tracks=tracks;
            session.hasMore=page.hasMore()&&page.nextOffset()>session.nextOffset;
            // 按上游原始条目推进游标，不能按去重或过滤后的结果数量推进。
            session.nextOffset=page.nextOffset();
            session.status=source==Source.QQ&&session.songs.size()>=2000?"已加载 2000 首，请缩小搜索范围":session.songs.isEmpty()?"没有找到相关歌曲":
                    "已加载 "+session.songs.size()+" 首 · "+(session.hasMore?"向下滚动加载更多":"已到底")+"（点行播放）";
        }else{
            session.status=failure instanceof HttpTimeoutException?"搜索超时，请稍后重试":
                    failure instanceof InterruptedException?"搜索已取消":
                    append?"加载更多失败，向下滚动重试":"搜索失败，请检查网络后重试";
        }
        changed.run();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        ACTIVE.remove(this);
        for (Session session : sessions.values()) {
            ++session.generation;
            if (session.pending != null) session.pending.cancel(true);
            session.pending = null;
            session.loading = false;
            session.songs = List.of();
            session.tracks = List.of();
        }
        // 不在渲染线程等待网络任务退出。
        workers.values().forEach(ExecutorService::shutdownNow);
    }

    static void closeAll() { ACTIVE.forEach(QQSearchController::close); }
}
