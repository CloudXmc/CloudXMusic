package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.OmniRouter;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.*;

/** 手动取链测试会话：快捷键触发、结果写日志；绝不调用任何播放器或队列。 */
public final class QQPlaybackProbeController implements AutoCloseable {
    public record View(boolean loading, boolean error, String status) {}
    @FunctionalInterface interface Resolver { String resolve(UnifiedSong song) throws Exception; }
    private static final Set<QQPlaybackProbeController> ACTIVE = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), task -> { Thread t = new Thread(task,"CloudXMusic-QQ-LinkTest"); t.setDaemon(true); return t; });
    private final Consumer<Runnable> main;
    private final LongSupplier accountRevision;
    private final BiConsumer<UnifiedSong,String> success;
    private final Resolver resolver;
    private volatile View view = new View(false,false,"");
    private View painted;
    private Future<?> pending;
    private long generation;
    private boolean closed;
    QQPlaybackProbeController(Consumer<Runnable> main, LongSupplier revision, BiConsumer<UnifiedSong,String> success) {
        this(main,revision,success,song -> OmniRouter.getProvider("qq").getPlaybackUrl(song));
    }
    QQPlaybackProbeController(Consumer<Runnable> main, LongSupplier revision, BiConsumer<UnifiedSong,String> success, Resolver resolver) {
        this.main=main; this.accountRevision=revision; this.success=success; this.resolver=resolver; ACTIVE.add(this);
    }
    public View view() { return view; }
    public boolean changed() { View next=view; boolean changed=painted!=next; painted=next; return changed; }
    public synchronized boolean start(UnifiedSong song) {
        if (closed || view.loading()) return false;
        if (song==null || !song.source().equals("qq")) { view=new View(false,true,"请先搜索 QQ 歌曲，再按 Ctrl+Shift+L 测试首条可见结果"); return false; }
        long token=++generation, revision=accountRevision.getAsLong();
        view=new View(true,false,"正在后台测试取链："+song.title()+"（F000 → M800 → M500，不播放）");
        worker.purge();
        try { pending=worker.submit(() -> run(song,token,revision)); }
        catch (RejectedExecutionException e) { view=new View(false,true,"QQ 取链测试任务繁忙，请稍后重试"); return false; }
        return true;
    }
    private void run(UnifiedSong song,long token,long revision) {
        try {
            String url=resolver.resolve(song);
            main.accept(() -> {
                synchronized (this) {
                    if (!current(token,revision)) return;
                    if (url==null) { view=new View(false,true,"F000、M800 和 M500 均无可用资源；QQ 登录状态未修改"); return; }
                    String path=URI.create(url).getPath();
                    String format=path.toLowerCase(java.util.Locale.ROOT).endsWith(".flac")?QQQuality.SQ.barLabel():path.substring(path.lastIndexOf('/')+1).startsWith("M500")?QQQuality.STANDARD.barLabel():QQQuality.HQ.barLabel();
                    success.accept(song,url); // 仅由玩家手动测试触发，临时完整直链按要求写日志。
                    view=new View(false,false,"取链成功："+format+"；真实直链已写入 latest.log（未播放）");
                }
            });
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (Exception e) {
            main.accept(() -> { synchronized (this) {
                if (current(token,revision)) view=new View(false,true,"QQ 取链失败："+(e.getMessage()==null?"网络或响应异常":e.getMessage()));
            }});
        }
    }
    private boolean current(long token,long revision) {
        if (closed || generation!=token) return false;
        if (accountRevision.getAsLong()!=revision) { view=new View(false,true,"QQ 账号已切换或退出，旧取链结果已丢弃"); return false; }
        return true;
    }
    public synchronized void cancel() {
        generation++; if (pending!=null) { pending.cancel(true); pending=null; }
        worker.getQueue().clear(); view=new View(false,false,"");
    }
    @Override public synchronized void close() { if (!closed) { cancel(); closed=true; worker.shutdownNow(); ACTIVE.remove(this); } }
    static void closeAll() { for (QQPlaybackProbeController probe:ACTIVE) probe.close(); }
}
