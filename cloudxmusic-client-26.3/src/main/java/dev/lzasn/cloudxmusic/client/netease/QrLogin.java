package dev.lzasn.cloudxmusic.client.netease;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.util.Async;

/**
 * 扫码登录的完整流程。
 *
 * <p>和以前不同的地方：<b>不再只是把链接丢给浏览器</b>，而是把链接编码成二维码交给
 * {@link dev.lzasn.cloudxmusic.client.gui.QrLoginScreen} 在游戏里画出来，手机直接扫。</p>
 *
 * <p><b>key 自动续</b>：unikey 大约两分钟过期（返回 800），过期就立刻申请新的，最多续 3 次，
 * 用户不用重开界面。</p>
 *
 * <p>轮询跑在独立线程，不占网络线程池，免得一次登录把取播放地址之类的请求全堵住。</p>
 */
public final class QrLogin {
    private static final int POLL_INTERVAL_MS = 1500;
    private static final int MAX_REFRESH = 3;
    private static final int MAX_POLLS_PER_KEY = 55;

    /** 回调都在主线程触发。 */
    public interface Listener {
        void onQrCode(boolean[][] modules);

        void onStatus(String text);

        void onSuccess(String nickname);

        void onFailure(String text);
    }

    private static volatile boolean cancelled;
    private static volatile String lastUrl = "";
    private static Thread poller;
    private static volatile long generation;

    private QrLogin() {}

    public static synchronized void start(Listener listener) {
        cancel();
        cancelled = false;
        long token = generation;
        Thread thread = new Thread(() -> run(listener, token), "CloudXmusic-QrLogin");
        thread.setDaemon(true);
        poller = thread;
        thread.start();
    }

    /** 最近一次生成的登录链接（界面用来显示兜底文本）。 */
    public static String lastUrl() {
        return lastUrl;
    }

    public static synchronized void cancel() {
        cancelled = true;
        generation++;
        if (poller != null) poller.interrupt();
        poller = null;
    }

    private static boolean active(long token) { return !cancelled && generation == token; }

    /** 网络任务取消后，已经排入主线程的旧回调也必须失效。 */
    private static void deliver(long token, Runnable callback) {
        Async.main(() -> { if (active(token)) callback.run(); });
    }

    private static void run(Listener listener, long token) {
        try {
            for (int refresh = 0; refresh <= MAX_REFRESH && active(token); refresh++) {
                String key = NeteaseApi.qrKey();
                if (!active(token)) return;
                String url = NeteaseApi.qrLoginUrl(key);
                lastUrl = url;
                boolean[][] modules = QrCodes.encode(url);
                CloudXMusicClient.LOGGER.info("扫码登录：key={} 二维码模块={}", key, modules == null ? "null" : modules.length);

                deliver(token, () -> {
                    // 兜底：链接也放进剪贴板，扫不了还能自己粘到浏览器
                    net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                    if (minecraft != null) minecraft.keyboardHandler.setClipboard(url);
                    if (modules == null) {
                        listener.onFailure("二维码生成失败，链接：" + url);
                        return;
                    }
                    listener.onQrCode(modules);
                    listener.onStatus("打开网易云 App → 扫一扫");
                });
                if (modules == null) return;

                boolean scanned = false;
                for (int i = 0; i < MAX_POLLS_PER_KEY && active(token); i++) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    NeteaseApi.QrStatus status = NeteaseApi.qrCheck(key);
                    if (!active(token)) return;
                    CloudXMusicClient.LOGGER.debug("扫码状态：code={} msg={}", status.code(), status.message());
                    if (status.expired()) break;
                    if (status.code() == 802 && !scanned) {
                        scanned = true;
                        deliver(token, () -> listener.onStatus("已扫码，请在手机上点确认"));
                    }
                    if (status.success()) {
                        // 803 的登录 Cookie 可能只在 JSON body 里，先显式导入再落盘。
                        NeteaseHttp.importCookieString(status.cookie());
                        if (!NeteaseHttp.hasSession()) {
                            CloudXMusicClient.LOGGER.warn("扫码已确认，但响应中没有可用的 MUSIC_U（cookie 字段 {} 字符）",
                                    status.cookie() == null ? 0 : status.cookie().length());
                            deliver(token, () -> listener.onFailure("扫码已确认，但没有收到登录 Cookie，请重新扫码"));
                            return;
                        }
                        String nickname = status.nickname();
                        Async.main(() -> {
                            // 先确认登录成功并刷新界面；资料请求不能阻塞成功反馈。
                            NeteaseSession.INSTANCE.markLoggedIn(status.nickname());
                            if (active(token)) listener.onSuccess(nickname == null || nickname.isBlank() ? "已登录" : nickname);
                            CloudXMusicClient.STATE.refreshLibrary();
                            NeteaseSession.INSTANCE.refreshAccountAsync();
                        });
                        return;
                    }
                }
                if (!active(token)) return;
                deliver(token, () -> listener.onStatus("二维码过期，正在换新的…"));
            }
            deliver(token, () -> listener.onFailure("没等到确认，请重新扫码"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            deliver(token, () -> listener.onFailure("扫码登录失败：" + e.getMessage()));
        } finally {
            synchronized (QrLogin.class) {
                if (poller == Thread.currentThread()) poller = null;
            }
        }
    }
}
