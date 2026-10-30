package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** 登录专用 HTTPS 传输，每次调用使用独立 QQ Cookie 容器，绝不复用网易云客户端。 */
final class QQAuthHttp {
    static final class HttpFailure extends IOException {
        private final int statusCode;
        HttpFailure(int statusCode){super("QQ 登录 HTTP 状态码："+statusCode);this.statusCode=statusCode;}
        int statusCode(){return statusCode;}
    }
    private static final URI ENDPOINT = URI.create("https://u.y.qq.com/cgi-bin/musicu.fcg");
    @FunctionalInterface interface Transport {
        JsonObject post(JsonObject request, CookieManager cookies) throws IOException, InterruptedException;
    }
    static JsonObject post(JsonObject body, CookieManager cookies) throws IOException, InterruptedException {
        return post(ENDPOINT, Duration.ofSeconds(15), body, cookies);
    }
    static JsonObject post(JsonObject body, CookieManager cookies, Duration timeout) throws IOException, InterruptedException {
        return post(ENDPOINT, timeout, body, cookies);
    }
    static JsonObject post(URI endpoint, Duration timeout, JsonObject body, CookieManager cookies)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).cookieHandler(cookies).build();
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                    .header("Content-Type", "application/json; charset=utf-8").header("Accept", "application/json")
                    .header("Accept-Encoding", "identity").header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://y.qq.com/").header("Origin", "https://y.qq.com")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new LimitedBody());
            try {
                HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (response.statusCode() != 200) throw new HttpFailure(response.statusCode());
                return parse(response.body());
            } catch (TimeoutException e) { throw new HttpTimeoutException("QQ 登录请求超时"); }
            catch (ExecutionException e) { throw new IOException("QQ 登录网络请求失败"); }
            finally { pending.cancel(true); }
        } finally { client.shutdownNow(); }
    }
    static JsonObject parse(byte[] bytes) throws IOException {
        if (bytes.length > 2 * 1024 * 1024) throw new IOException("QQ JSON 响应超过 2 MiB");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            boolean quoted = false, escaped = false; int depth = 0;
            // Gson 构建树前限制嵌套，避免异常响应耗尽工作线程栈。
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (quoted) {
                    if (escaped) escaped = false;
                    else if (ch == '\\') escaped = true;
                    else if (ch == '"') quoted = false;
                } else if (ch == '"') quoted = true;
                else if (ch == '{' || ch == '[') { if (++depth > 64) throw new IOException("QQ JSON 嵌套超过 64 层"); }
                else if (ch == '}' || ch == ']') depth--;
            }
            try (JsonReader reader = new JsonReader(new StringReader(text))) {
                reader.setLenient(false);
                JsonElement value = Streams.parse(reader);
                if (reader.peek() != JsonToken.END_DOCUMENT || !value.isJsonObject()) throw new IOException("QQ 响应必须是单个 JSON 对象");
                return value.getAsJsonObject();
            }
        } catch (JsonParseException | IllegalStateException e) { throw new IOException("QQ 响应 JSON 格式无效"); }
    }
    static JsonObject object(JsonObject parent, String key) throws IOException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw new IOException("QQ 响应缺少对象：" + key);
        return value.getAsJsonObject();
    }
    static String string(JsonObject parent, String key) throws IOException {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IOException("QQ 响应缺少字符串：" + key);
        return value.getAsString();
    }
    static String text(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        return value != null && value.isJsonPrimitive() && !value.getAsJsonPrimitive().isBoolean() ? value.getAsString() : "";
    }
    static long integer(JsonObject parent, String key) throws IOException {
        JsonElement value = parent.get(key);
        try {
            if (value == null || !value.isJsonPrimitive() || value.getAsJsonPrimitive().isBoolean()) throw new NumberFormatException();
            return value.getAsBigDecimal().longValueExact();
        } catch (NumberFormatException | ArithmeticException e) { throw new IOException("QQ 响应缺少有效整数：" + key); }
    }
    static JsonObject request(String module, String method, JsonObject params) {
        JsonObject comm = new JsonObject(); comm.addProperty("ct", 19); comm.addProperty("cv", 2201);
        JsonObject block = new JsonObject(); block.addProperty("module", module); block.addProperty("method", method); block.add("param", params);
        JsonObject root = new JsonObject(); root.add("comm", comm); root.add("req_0", block); return root;
    }
    static CookieManager emptyCookies() { return new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER); }
    static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > 2 * 1024 * 1024 - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("QQ 登录响应超过 2 MiB")); return;
                }
                byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable e) { result.completeExceptionally(e); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
