package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** 无账号的 CDN 小包检查：只保留前 16 字节，绝不解码或输出到声卡。 */
final class QQAudioUrlVerifier {
    private QQAudioUrlVerifier() {}
    static boolean allowed(URI uri) {
        String host = uri.getHost(); if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == 80 || uri.getPort() == 443)
                && (host.equals("qq.com") || host.endsWith(".qq.com") || host.equals("qqmusic.com") || host.endsWith(".qqmusic.com"));
    }
    static URI verify(URI uri, boolean flac, Duration timeout) throws IOException, InterruptedException {
        return verify(uri, flac, timeout, QQAudioUrlVerifier::allowed);
    }
    /** 仅测试允许注入回环地址；正式调用固定使用 QQ CDN 域名检查。 */
    static URI verify(URI uri, boolean flac, Duration timeout, java.util.function.Predicate<URI> allowed) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofNanos(Math.min(timeout.toNanos(), Duration.ofSeconds(5).toNanos())))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            for (int redirects = 0; redirects <= 3; redirects++) {
                if (!allowed.test(uri) || !extension(uri, flac)) throw new IOException("QQ 音频地址的域名或格式不受支持");
                long left = deadline - System.nanoTime(); if (left <= 0) throw new HttpTimeoutException("QQ 音频地址检查超时");
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(left))
                        .header("Range", "bytes=0-15").header("Accept-Encoding", "identity")
                        .header("Referer", "https://y.qq.com/").header("User-Agent", "Mozilla/5.0").GET().build();
                CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new PrefixBody());
                HttpResponse<byte[]> response;
                try { response = pending.get(left, TimeUnit.NANOSECONDS); }
                catch (TimeoutException e) { throw new HttpTimeoutException("QQ 音频地址检查超时"); }
                catch (ExecutionException e) { throw new IOException("QQ 音频 CDN 请求失败"); }
                finally { pending.cancel(true); }
                int status = response.statusCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("QQ CDN 重定向缺少目标"));
                    try { uri = uri.resolve(location); } catch (IllegalArgumentException e) { throw new IOException("QQ CDN 重定向目标无效"); }
                    continue;
                }
                if (status != 200 && status != 206) return null;
                if (status == 206 && !response.headers().firstValue("Content-Range").orElse("").matches("(?i)bytes 0-[0-9]+/(?:[0-9]+|\\*)"))
                    throw new IOException("QQ CDN 返回的 Range 起点无效");
                if (!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity")) return null;
                return audioHeader(response.body(), flac) ? uri : null;
            }
            throw new IOException("QQ 音频地址重定向次数超限");
        } finally { client.shutdownNow(); }
    }
    static boolean extension(URI uri, boolean flac) {
        return uri.getPath() != null && uri.getPath().toLowerCase(Locale.ROOT).endsWith(flac ? ".flac" : ".mp3");
    }
    static boolean audioHeader(byte[] bytes, boolean flac) {
        if (flac) return bytes.length >= 4 && bytes[0] == 'f' && bytes[1] == 'L' && bytes[2] == 'a' && bytes[3] == 'C';
        if (bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') return true;
        if (bytes.length < 4) return false;
        int b1 = bytes[1] & 255, b2 = bytes[2] & 255;
        return (bytes[0] & 255) == 255 && (b1 & 224) == 224 && (b1 & 24) != 8 && (b1 & 6) != 0
                && (b2 & 240) != 0 && (b2 & 240) != 240 && (b2 & 12) != 12;
    }
    private static final class PrefixBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(16);
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                byte[] part = new byte[Math.min(16 - bytes.size(), buffer.remaining())]; buffer.get(part); bytes.writeBytes(part);
                if (bytes.size() == 16) { body.complete(bytes.toByteArray()); subscription.cancel(); return; }
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
