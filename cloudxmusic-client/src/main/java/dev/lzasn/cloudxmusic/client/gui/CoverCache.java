package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.netease.NeteaseHttp;
import dev.lzasn.cloudxmusic.client.util.Async;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/** 有界封面缓存；集合只在客户端线程访问，后台只传递已经解码的图像。 */
public final class CoverCache {
    private static final int LIMIT = 64, MAX_BYTES = 2 * 1024 * 1024;
    private static final Map<String, BufferedImage> READY = new LinkedHashMap<>(LIMIT, .75f, true);
    private static final Map<String, Long> FAILED = new LinkedHashMap<>();
    private static final Map<String, Future<?>> LOADING = new HashMap<>();
    private static final ThreadPoolExecutor DOWNLOADS = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), r -> { Thread t = new Thread(r, "CloudXmusic-Cover"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.AbortPolicy());
    private static int generation, revision;
    private CoverCache() {}
    public static int revision() { return revision; }

    public static BufferedImage image(String url) {
        return image(url, true);
    }

    /** 头像等非专辑图不加 param 缩略参数，否则网易云 CDN 会返回 404。 */
    public static BufferedImage avatar(String url) {
        return image(url, false);
    }

    private static BufferedImage image(String url, boolean thumb) {
        if (url == null || url.isBlank() || !FirstRunNotice.accepted()) return null;
        BufferedImage ready = READY.get(url);
        if (ready != null) return ready;
        Long failed = FAILED.get(url);
        if (failed != null && System.currentTimeMillis() - failed < 60_000) return null;
        if (LOADING.containsKey(url) || DOWNLOADS.isShutdown()) return null;
        int token = generation;
        try {
            Future<?> future = DOWNLOADS.submit(() -> {
                BufferedImage image = null;
                try { image = download(url, thumb); } catch (Exception ignored) { /* 短暂退避，保持占位封面。 */ }
                BufferedImage result = image;
                Async.main(() -> {
                    if (token != generation) return;
                    LOADING.remove(url);
                    if (result == null) {
                        if (FAILED.size() >= LIMIT) FAILED.remove(FAILED.keySet().iterator().next());
                        FAILED.put(url, System.currentTimeMillis());
                    } else {
                        if (READY.size() >= LIMIT) READY.remove(READY.keySet().iterator().next());
                        READY.put(url, result); FAILED.remove(url); revision++;
                    }
                });
            });
            LOADING.put(url, future);
        } catch (RejectedExecutionException ignored) { /* 下载队列已满，下一帧可重试。 */ }
        return null;
    }

    private static BufferedImage download(String url, boolean thumb) throws Exception {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) return null;
        String target = thumb && !url.contains("param=")
                ? url + (url.contains("?") ? "&" : "?") + "param=384y384" : url;
        HttpURLConnection connection = (HttpURLConnection) URI.create(target).toURL().openConnection();
        connection.setConnectTimeout(8000); connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", NeteaseHttp.USER_AGENT);
        connection.setRequestProperty("Referer", "https://music.163.com/");
        try (InputStream in = connection.getInputStream()) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES || Thread.currentThread().isInterrupted()) return null;
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } finally { connection.disconnect(); }
    }

    public static void clear() {
        generation++; LOADING.values().forEach(f -> f.cancel(true)); LOADING.clear(); DOWNLOADS.purge();
        READY.clear(); FAILED.clear(); revision++;
    }
    public static void close() { clear(); DOWNLOADS.shutdownNow(); }
}
