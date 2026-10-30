package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.*;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/** 单次取链调用的 QQ 认证上下文，不向 CDN 传递 Cookie 或 authst。 */
final class QQPlaybackApi {
    @FunctionalInterface interface Transport {
        JsonObject post(JsonObject request, CookieManager cookies, Duration timeout) throws IOException, InterruptedException;
    }
    record Reply(long code, JsonObject data) {}
    static final class BusinessFailure extends IOException {
        private final long code;
        BusinessFailure(long code) { super("QQ 音乐服务返回业务错误，code=" + code); this.code=code; }
        long code(){return code;}
        // 只归类已确认的歌曲/档位限制；未知错误不能伪装成无版权。
        boolean songUnavailable(){return code==22006;}
    }
    private final QQCredentials credentials;
    private final Transport transport;
    private final BooleanSupplier active;
    private final long deadline;
    private final CookieManager cookies = QQAuthHttp.emptyCookies();
    QQPlaybackApi(QQCredentials credentials, Transport transport, BooleanSupplier active, Duration budget) {
        this.credentials = credentials; this.transport = transport; this.active = active;
        deadline = System.nanoTime() + budget.toNanos();
        for (String[] pair : new String[][]{{"qqmusic_uin", credentials.userId()}, {"qqmusic_key", credentials.musicKey()},
                {"login_type", credentials.loginType() == 1 ? "2" : "1"}}) {
            HttpCookie cookie = new HttpCookie(pair[0], pair[1]);
            cookie.setDomain(".qq.com"); cookie.setPath("/"); cookie.setSecure(true); cookie.setVersion(0);
            cookies.getCookieStore().add(URI.create("https://u.y.qq.com/"), cookie);
        }
    }
    Duration remaining() throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("QQ 取链已取消");
        if (!active.getAsBoolean()) throw new IOException("QQ 账号已切换或退出，请重新测试取链");
        long left = deadline - System.nanoTime();
        if (left <= 0) throw new HttpTimeoutException("QQ 取链总时限已到");
        return Duration.ofNanos(Math.min(left, Duration.ofSeconds(15).toNanos()));
    }
    Reply call(String module, String method, JsonObject params, boolean desktop) throws IOException, InterruptedException {
        Duration timeout = remaining();
        JsonObject request = QQAuthHttp.request(module, method, params);
        JsonObject comm = request.getAsJsonObject("comm");
        comm.addProperty("ct", desktop ? 19 : 24); comm.addProperty("cv", desktop ? 2201 : 0);
        comm.addProperty("uin", credentials.userId()); comm.addProperty("authst", credentials.musicKey());
        comm.addProperty("g_tk", QQSessionVerifier.hash33(credentials.musicKey())); comm.addProperty("tmeLoginType", credentials.loginType());
        JsonObject root = transport.post(request, cookies, timeout);
        remaining();
        long code = QQAuthHttp.integer(root, "code");
        if (code != 0) return rejected(code);
        JsonObject block = QQAuthHttp.object(root, "req_0");
        code = QQAuthHttp.integer(block, "code");
        if (code != 0) return rejected(code);
        JsonObject data = QQAuthHttp.object(block, "data");
        if (data.has("code")) {
            code = QQAuthHttp.integer(data, "code"); if (code != 0) return rejected(code);
        }
        return new Reply(0, data);
    }
    private static Reply rejected(long code) throws IOException {
        if (QQSessionVerifier.authExpired(code) || code == 20279 || code == 104604) throw new QQSessionVerifier.Rejected(code);
        return new Reply(code, null);
    }
    static JsonObject requireData(Reply reply) throws IOException {
        if (reply.code() != 0) throw new BusinessFailure(reply.code());
        return reply.data();
    }
}
