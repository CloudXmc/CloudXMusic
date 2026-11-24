package dev.lzasn.cloudxmusic.client.kugou;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Owns the discovery-page session; search results are never used as recommendations. */
public final class KugouRecommendController implements AutoCloseable {
    public record View(List<KugouTrack> songs, boolean loading, boolean error, String status) {
        public View { songs = List.copyOf(songs); }
    }

    private static final View EMPTY = new View(List.of(), false, false, "");
    private final KugouRecommendApi api;
    private final Consumer<Runnable> main;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "CloudXMusic-Kugou-Recommend");
                thread.setDaemon(true);
                return thread;
            });
    private volatile View view = EMPTY;
    private View observed = EMPTY;
    private long generation;
    private Future<?> pending;
    private boolean active;
    private boolean closed;

    public KugouRecommendController(KugouRecommendApi api, Consumer<Runnable> main) {
        this.api = api;
        this.main = main;
    }

    public View view() { return view; }

    public boolean update(boolean requestedActive) {
        if (!closed && active != requestedActive) {
            active = requestedActive;
            cancel();
            if (!active) view = EMPTY;
            else load();
        }
        boolean changed = !observed.equals(view);
        observed = view;
        return changed;
    }

    public void refresh() {
        if (!closed && active) {
            cancel();
            load();
        }
    }

    private void load() {
        long token = generation;
        view = new View(List.of(), true, false, "正在获取每日推荐…");
        try {
            pending = worker.submit(() -> {
                View result;
                try {
                    result = new View(api.recommend(), false, false, "");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception error) {
                    result = new View(List.of(), false, true, "暂无每日推荐 · 点击刷新重试");
                }
                View completed = result;
                main.accept(() -> {
                    if (!closed && active && token == generation) view = completed;
                });
            });
        } catch (RejectedExecutionException error) {
            view = new View(List.of(), false, true, "暂无每日推荐 · 点击刷新重试");
        }
    }

    private void cancel() {
        generation++;
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        worker.getQueue().clear();
        worker.purge();
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        active = false;
        cancel();
        worker.shutdownNow();
        api.close();
        view = EMPTY;
    }
}
