package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the public "new song releases" section from Kugou's homepage. */
public final class KugouRecommendApi implements AutoCloseable {
    private static final URI HOMEPAGE = URI.create("https://www.kugou.com/");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final Pattern SECTION = Pattern.compile("<!--\\s*新歌首发新\\s*-->([\\s\\S]*?)(?=<!--|$)");
    private static final Pattern DATA_OBJECT = Pattern.compile("\\bdataobj\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HTML_ENTITY = Pattern.compile("&(?:#([0-9]{1,7}|[xX][0-9a-fA-F]{1,6})|quot|apos|lt|gt|amp|nbsp);", Pattern.CASE_INSENSITIVE);

    @FunctionalInterface
    interface Homepage {
        String read() throws Exception;
    }

    private final Homepage homepage;
    private final HttpClient http;

    public KugouRecommendApi() {
        this(null);
    }

    KugouRecommendApi(Homepage homepage) {
        this.homepage = homepage;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public List<KugouTrack> recommend() throws Exception {
        return parse(homepage == null ? fetchHomepage() : homepage.read());
    }

    static List<KugouTrack> parse(String html) throws IOException {
        if (html == null || html.length() > MAX_RESPONSE_BYTES)
            throw new IOException("酷狗每日推荐响应无效或过大");
        Matcher sectionMatcher = SECTION.matcher(html);
        if (!sectionMatcher.find()) throw new IOException("暂无每日推荐");

        Map<String, KugouTrack> songs = new LinkedHashMap<>();
        Matcher objects = DATA_OBJECT.matcher(sectionMatcher.group(1));
        while (objects.find()) {
            String json = decodeHtml(objects.group(2));
            try {
                JsonElement value = JsonParser.parseString(json);
                if (!value.isJsonArray()) continue;
                for (JsonElement item : value.getAsJsonArray()) {
                    if (!item.isJsonObject()) continue;
                    try {
                        KugouTrack track = KugouTrack.parse(item.getAsJsonObject());
                        songs.putIfAbsent(track.song().platformId(), track);
                    } catch (IllegalArgumentException ignored) {
                        // Homepage modules contain unrelated records as well as songs.
                    }
                }
            } catch (RuntimeException ignored) {
                // Skip malformed attributes while still requiring at least one valid song.
            }
        }
        if (songs.isEmpty()) throw new IOException("暂无每日推荐");
        return List.copyOf(songs.values());
    }

    private String fetchHomepage() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(HOMEPAGE).timeout(TIMEOUT)
                .header("User-Agent", KugouHttp.USER_AGENT)
                .header("Referer", HOMEPAGE.toString())
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Encoding", "identity")
                .GET().build();
        CompletableFuture<HttpResponse<byte[]>> response = http.sendAsync(request, info -> new LimitedBody(MAX_RESPONSE_BYTES));
        try {
            HttpResponse<byte[]> page = response.get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (page.statusCode() != 200) throw new IOException("酷狗每日推荐请求失败");
            try {
                return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(page.body())).toString();
            } catch (CharacterCodingException error) {
                throw new IOException("酷狗每日推荐字符编码无效", error);
            }
        } catch (TimeoutException error) {
            response.cancel(true);
            throw new IOException("酷狗每日推荐请求超时", error);
        } catch (InterruptedException error) {
            response.cancel(true);
            throw error;
        } catch (ExecutionException error) {
            response.cancel(true);
            if (error.getCause() instanceof IOException ioError) throw ioError;
            throw new IOException("酷狗每日推荐网络请求失败", error.getCause());
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        private LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("酷狗每日推荐响应过大"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }

    private static String decodeHtml(String value) {
        Matcher matcher = HTML_ENTITY.matcher(value);
        StringBuilder result = new StringBuilder(value.length());
        int cursor = 0;
        while (matcher.find()) {
            result.append(value, cursor, matcher.start());
            String entity = matcher.group();
            String number = matcher.group(1);
            if (number != null) {
                try {
                    int radix = number.startsWith("x") || number.startsWith("X") ? 16 : 10;
                    String digits = radix == 16 ? number.substring(1) : number;
                    int codePoint = Integer.parseInt(digits, radix);
                    if (Character.isValidCodePoint(codePoint) && (codePoint < Character.MIN_SURROGATE || codePoint > Character.MAX_SURROGATE))
                        result.appendCodePoint(codePoint);
                    else result.append(entity);
                } catch (IllegalArgumentException ignored) {
                    result.append(entity);
                }
            } else {
                result.append(switch (entity.toLowerCase(java.util.Locale.ROOT)) {
                    case "&quot;" -> "\"";
                    case "&apos;" -> "'";
                    case "&lt;" -> "<";
                    case "&gt;" -> ">";
                    case "&amp;" -> "&";
                    case "&nbsp;" -> "\u00a0";
                    default -> entity;
                });
            }
            cursor = matcher.end();
        }
        return result.append(value, cursor, value.length()).toString();
    }

    @Override
    public void close() {
        http.shutdownNow();
    }
}
