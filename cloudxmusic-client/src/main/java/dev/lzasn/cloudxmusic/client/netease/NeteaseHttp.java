package dev.lzasn.cloudxmusic.client.netease;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 网易云 HTTP 层：JDK HttpClient + CookieManager。
 *
 * <p>用 {@code CookieManager} + {@link PersistentCookies} 管理整站 Cookie：
 * 扫码的 unikey 和轮询必须落在同一个会话里，只手动拼 MUSIC_U 会让轮询跑到另一个会话、
 * 永远收不到 803；Cookie 落盘顺带解决了重启掉登录。</p>
 */
public final class NeteaseHttp {
    public static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    /** 全流程共用一套 Cookie，扫码的 unikey 和轮询才会是同一个会话。 */
    private static final PersistentCookies STORE = new PersistentCookies();
    private static final CookieManager MANAGER = new CookieManager(STORE, CookiePolicy.ACCEPT_ALL);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .cookieHandler(MANAGER)
            .build();

    private NeteaseHttp() {}

    /** 有没有登录态。 */
    public static boolean hasSession() {
        return STORE.hasSession();
    }

    /** 退出登录：清空所有 Cookie。 */
    public static void clearCookies() {
        STORE.removeAll();
    }

    /**
     * 导入接口 JSON 里的 {@code cookie} 字段。
     *
     * <p>网易云的登录/扫码接口经常把 {@code MUSIC_U=...; __csrf=...} 放在响应 JSON 里，
     * 而不是标准 {@code Set-Cookie} 响应头；只靠 CookieManager 会漏掉登录态。</p>
     *
     * @return 是否至少导入了一条有效 Cookie
     */
    public static boolean importCookieString(String raw) {
        if (raw == null || raw.isBlank()) return false;
        int imported = 0;
        for (String pair : raw.split(";")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) continue;
            String name = pair.substring(0, equals).trim();
            String value = pair.substring(equals + 1).trim();
            if (name.isEmpty() || value.isEmpty()) continue;
            try {
                HttpCookie cookie = new HttpCookie(name, value);
                // 这些 Cookie 只属于网易云主域；不要发到 music.126.net 的 CDN。
                cookie.setDomain(".music.163.com");
                cookie.setPath("/");
                STORE.add(URI.create("https://music.163.com/"), cookie);
                imported++;
            } catch (IllegalArgumentException e) {
                CloudXMusicClient.LOGGER.warn("跳过无效网易云 Cookie：{}", name);
            }
        }
        if (imported > 0) {
            CloudXMusicClient.LOGGER.info("已导入网易云 Cookie：{} 条（含 MUSIC_U：{}）", imported, hasSession());
        }
        return imported > 0;
    }

    /** 给不走 HttpClient 的请求（音频 CDN、探测）取一份 Cookie 头。 */
    public static String cookieHeader(String url) {
        try {
            StringBuilder sb = new StringBuilder();
            for (java.net.HttpCookie cookie : STORE.get(URI.create(url))) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(cookie.getName()).append('=').append(cookie.getValue());
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static Response get(String url) throws Exception {
        HttpRequest.Builder builder = base(url).GET();
        return send(builder.build());
    }

    public static Response postForm(String url, String form) throws Exception {
        HttpRequest.Builder builder = base(url)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8));
        return send(builder.build());
    }

    private static HttpRequest.Builder base(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://music.163.com/")
                .header("Accept", "*/*");
    }

    private static Response send(HttpRequest request) throws Exception {
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    public record Response(int status, String body) {}
}
