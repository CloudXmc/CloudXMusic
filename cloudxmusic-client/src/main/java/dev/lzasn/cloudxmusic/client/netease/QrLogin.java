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

    private QrLogin() {}

    public static synchronized void start(Listener listener) {
        cancel();
        cancelled = false;
        Thread thread = new Thread(() -> run(listener), "CloudXMusic-QrLogin");
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
        poller = null;
    }

    private static void run(Listener listener) {
        try {
            for (int refresh = 0; refresh <= MAX_REFRESH && !cancelled; refresh++) {
                String key = NeteaseApi.qrKey();
                String url = NeteaseApi.qrLoginUrl(key);
                lastUrl = url;
                boolean[][] modules = QrCodes.encode(url);
                CloudXMusicClient.LOGGER.info("扫码登录：key={} 二维码模块={}", key, modules == null ? "null" : modules.length);

                Async.main(() -> {
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
                for (int i = 0; i < MAX_POLLS_PER_KEY && !cancelled; i++) {
                    Thread.sleep(POLL_INTERVAL_MS);
                    NeteaseApi.QrStatus status = NeteaseApi.qrCheck(key);
                    CloudXMusicClient.LOGGER.debug("扫码状态：code={} msg={}", status.code(), status.message());
                    if (status.expired()) break;
                    if (status.code() == 802 && !scanned) {
                        scanned = true;
                        Async.main(() -> listener.onStatus("已扫码，请在手机上点确认"));
                    }
                    if (status.success()) {
                        // 803 的登录 Cookie 可能只在 JSON body 里，先显式导入再落盘。
                        NeteaseHttp.importCookieString(status.cookie());
                        if (!NeteaseHttp.hasSession()) {
                            CloudXMusicClient.LOGGER.warn("扫码已确认，但响应中没有可用的 MUSIC_U（cookie 字段 {} 字符）",
                                    status.cookie() == null ? 0 : status.cookie().length());
                            Async.main(() -> listener.onFailure("扫码已确认，但没有收到登录 Cookie，请重新扫码"));
                            return;
                        }
                        // 先把登录态落盘再问账号信息，免得 account() 因网络抖动把整个登录判死。
                        NeteaseSession.INSTANCE.markLoggedIn(status.nickname());
                        String nick = status.nickname();
                        try {
                            NeteaseApi.LoginResult account = NeteaseApi.account();
                            if (account.ok()) {
                                NeteaseSession.INSTANCE.applyLogin(account);
                                nick = account.nickname();
                            }
                        } catch (Exception e) {
                            CloudXMusicClient.LOGGER.warn("扫码成功但读账号信息失败：{}", e.getMessage());
                        }
                        String finalNick = nick;
                        Async.main(() -> {
                            listener.onSuccess(finalNick == null || finalNick.isBlank() ? "已登录" : finalNick);
                            CloudXMusicClient.STATE.refreshLibrary();
                        });
                        return;
                    }
                }
                if (cancelled) return;
                Async.main(() -> listener.onStatus("二维码过期，正在换新的…"));
            }
            Async.main(() -> listener.onFailure("没等到确认，重新执行 /cxm login qr"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Async.main(() -> listener.onFailure("扫码登录失败：" + e.getMessage()));
        }
    }
}
