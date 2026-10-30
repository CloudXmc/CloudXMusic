package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.OnlineMusicProvider;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** QQ 公网匿名搜索；对应 DangoMusic 的无 sign 搜索路径，只能在后台线程调用。 */
public final class QQProvider implements OnlineMusicProvider {
    private static final int MAX_LIMIT = 100;
    private static final int UPSTREAM_PAGE_SIZE = 40;
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final Pattern MID = Pattern.compile("[A-Za-z0-9]{1,64}");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
    private static final String SEARCH_ENDPOINT = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private final URI searchEndpoint;
    private final Duration requestTimeout;
    private final QQPlaybackResolver playback;
    // 只缓存非敏感元数据；有界，不保存临时直链或账号信息。
    private final java.util.Map<String,QQTrackMetadata.Info> details=java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,QQTrackMetadata.Info> entry){return size()>128;}
    });
    int durationSeconds(String mid){QQTrackMetadata.Info info=details.get(mid);return info==null?0:info.durationSeconds();}
    QQTrackMetadata.Info details(UnifiedSong song)throws IOException,InterruptedException{
        QQTrackMetadata.Info info=details.get(song.platformId());if(info==null){info=QQTrackMetadata.loadPublic(song.platformId());details.put(song.platformId(),info);}return info;
    }

    public QQProvider() {
        this(Duration.ofSeconds(15));
    }
    QQProvider(QQPlaybackResolver playback) {
        this(URI.create(SEARCH_ENDPOINT), Duration.ofSeconds(15), Objects.requireNonNull(playback));
    }

    QQProvider(Duration requestTimeout) {
        this(URI.create(SEARCH_ENDPOINT), requestTimeout);
    }

    /** 仅包内测试可注入回环服务；正式构造器始终使用 QQ 公网 HTTPS 地址。 */
    QQProvider(URI searchEndpoint, Duration requestTimeout) {
        this(searchEndpoint, requestTimeout, null);
    }
    private QQProvider(URI searchEndpoint, Duration requestTimeout, QQPlaybackResolver playback) {
        this.playback = playback;
        this.searchEndpoint = Objects.requireNonNull(searchEndpoint, "searchEndpoint");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        if (requestTimeout.toMillis() < 1) throw new IllegalArgumentException("requestTimeout must be positive");
    }

    @Override
    public String sourceId() { return "qq"; }

    @Override
    public List<UnifiedSong> searchSongs(String keyword, int offset, int limit)
            throws IOException, InterruptedException {
        return searchPage(keyword, offset, limit).songs();
    }

    public record SearchPage(List<UnifiedSong> songs, int nextOffset, boolean hasMore) {}
    public SearchPage searchPage(String keyword, int offset, int limit) throws IOException, InterruptedException {
        if (keyword == null || keyword.isBlank() || offset < 0 || offset > Integer.MAX_VALUE-MAX_LIMIT || limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("keyword must not be blank; offset >= 0; limit must be 1..100");
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("QQ 搜索已中断");
        long page = (long) offset / UPSTREAM_PAGE_SIZE + 1;
        int skip = offset % UPSTREAM_PAGE_SIZE;
        int maxRequests = (skip + limit + UPSTREAM_PAGE_SIZE - 1) / UPSTREAM_PAGE_SIZE;
        int remaining = limit;
        List<UnifiedSong> result = new ArrayList<>();
        boolean hasMore = false;
        int consumed = 0;
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            try {
                // offset 按原始搜索结果计数；无效 MID 不改变分页位置，也不额外追页补齐。
                for (int request = 0; request < maxRequests && remaining > 0; request++, page++, skip = 0) {
                    URI uri = buildSearchUri(keyword, page);
                    Upstream response = fetchPage(client, uri, page);
                    JsonArray songs = response.songs();
                    if (songs.size() > UPSTREAM_PAGE_SIZE) throw new IOException("QQ 公网接口返回条目数超过请求页大小");
                    if (skip >= songs.size()) {hasMore=false;break;}
                    int count = Math.min(remaining, songs.size() - skip);
                    for (int index = skip; index < skip + count; index++) {
                        UnifiedSong song = mapSong(object(songs.get(index), "歌曲条目"));
                        if (song != null) result.add(song);
                    }
                    consumed += count;
                    hasMore = response.total() >= 0 ? (long) offset + consumed < response.total()
                            : skip + count < songs.size() || songs.size() == UPSTREAM_PAGE_SIZE;
                    remaining -= count;
                    if (songs.size() < UPSTREAM_PAGE_SIZE) break;
                }
            } finally {
                // 本次调用独占客户端，取消、失败和正常结束都释放其网络资源。
                client.shutdownNow();
            }
        }
        return new SearchPage(List.copyOf(result), offset + consumed, hasMore && consumed > 0);
    }

    @Override
    public String getPlaybackUrl(UnifiedSong song) throws IOException, InterruptedException {
        return getPlaybackUrl(song,QQQuality.SQ);
    }
    public String getPlaybackUrl(UnifiedSong song,QQQuality quality)throws IOException,InterruptedException{
        var link=playbackLink(song,quality);return link==null?null:link.url();
    }
    QQPlaybackResolver.Link playbackLink(UnifiedSong song,QQQuality quality)throws IOException,InterruptedException{
        if (playback == null) throw new IOException("QQ 账号与取链服务尚未装配，请通过 OmniRouter 获取已注册的 Provider");
        return playback.resolveLink(song,quality,info->details.put(song.platformId(),info));
    }

    @Override
    public Lyric getLyrics(UnifiedSong song)throws IOException,InterruptedException {
        return new QQLyricsApi().load(song);
    }
    QQTimedLyrics timedLyrics(UnifiedSong song)throws IOException,InterruptedException{return new QQLyricsApi().loadDetailed(song);}

    private URI buildSearchUri(String keyword, long page) {
        // DangoMusic 的此搜索入口不计算 sign；不要混入酷狗签名或登录态 g_tk。
        String query = "format=json&p=" + page + "&n=" + UPSTREAM_PAGE_SIZE
                + "&w=" + URLEncoder.encode(keyword.strip(), StandardCharsets.UTF_8);
        return URI.create(searchEndpoint + "?" + query);
    }

    private record Upstream(JsonArray songs, long total) {}
    private Upstream fetchPage(HttpClient client, URI uri, long expectedPage) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(requestTimeout)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://y.qq.com/")
                .GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request,
                info -> new LimitedBodySubscriber());
        HttpResponse<byte[]> response;
        try {
            // 额外限定完整响应体的等待时间，服务端发送响应头后停滞也必须能退出。
            response = pending.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            throw new HttpTimeoutException("QQ 搜索请求超时");
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof IOException io) throw io;
            throw new IOException("QQ 搜索网络请求失败", exception.getCause());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } finally {
            pending.cancel(true);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("QQ API HTTP 状态码：" + response.statusCode());
        }
        String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(response.body())).toString();
        try {
            JsonObject root = object(JsonParser.parseString(json), "搜索响应");
            JsonObject upstream = root.has("response") ? object(root.get("response"), "response") : root;
            JsonElement code = upstream.get("code");
            if (code == null || !code.isJsonPrimitive() || code.getAsJsonPrimitive().isBoolean()) {
                throw new IOException("QQ 搜索响应缺少有效 code");
            }
            int status;
            try {
                status = code.getAsBigDecimal().intValueExact();
            } catch (NumberFormatException | ArithmeticException exception) {
                throw new IOException("QQ 搜索响应 code 不是整数", exception);
            }
            if (status != 0) throw new IOException("QQ 搜索业务错误，code=" + status);
            JsonObject data = object(upstream.get("data"), "data");
            JsonObject songBlock = object(data.get("song"), "data.song");
            JsonElement returnedPage = songBlock.get("curpage");
            try {
                if (returnedPage == null || !returnedPage.isJsonPrimitive()
                        || returnedPage.getAsJsonPrimitive().isBoolean()
                        || returnedPage.getAsBigDecimal().longValueExact() != expectedPage) {
                    throw new IOException("QQ 搜索返回页码与请求不一致");
                }
            } catch (NumberFormatException | ArithmeticException exception) {
                throw new IOException("QQ 搜索返回页码不是有效整数", exception);
            }
            JsonElement list = songBlock.get("list");
            if (list == null || !list.isJsonArray()) throw new IOException("QQ 搜索响应 data.song.list 必须是数组");
            long total=-1;
            if(songBlock.has("totalnum")){
                try{total=songBlock.get("totalnum").getAsBigDecimal().longValueExact();if(total<0)throw new ArithmeticException();}
                catch(RuntimeException e){throw new IOException("QQ 搜索总数无效",e);}
            }
            return new Upstream(list.getAsJsonArray(),total);
        } catch (JsonParseException exception) {
            throw new IOException("QQ 搜索返回无效 JSON", exception);
        }
    }

    static UnifiedSong mapSong(JsonObject song) throws IOException {
        String mid = first(text(song, "songmid"), text(song, "mid"));
        if (!MID.matcher(mid).matches()) return null;
        String title = first(text(song, "songname"), text(song, "title"), text(song, "name"));
        List<String> artists = new ArrayList<>();
        JsonElement singers = song.get("singer");
        if (singers != null && !singers.isJsonNull()) {
            if (!singers.isJsonArray()) throw new IOException("QQ 歌曲 singer 必须是数组");
            for (JsonElement singer : singers.getAsJsonArray()) {
                String name = cleanText(text(object(singer, "歌手条目"), "name"));
                if (!name.isEmpty()) artists.add(name);
            }
        }
        String albumName = text(song, "albumname");
        JsonElement album = song.get("album");
        if (album != null && !album.isJsonNull()) {
            albumName = first(albumName, text(object(album, "专辑"), "name"));
        }
        return new UnifiedSong("qq", mid, cleanText(title), String.join(" / ", artists), cleanText(albumName),QQVip.parse(song));
    }

    private static JsonObject object(JsonElement value, String label) throws IOException {
        if (value == null || !value.isJsonObject()) throw new IOException("QQ " + label + " 必须是对象");
        return value.getAsJsonObject();
    }

    private static String text(JsonObject object, String key) throws IOException {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("QQ 歌曲字段 " + key + " 必须是字符串");
        }
        return value.getAsString().strip();
    }

    private static String first(String... values) {
        for (String value : values) if (!value.isEmpty()) return value;
        return "";
    }

    private static String cleanText(String value) {
        return HTML_TAG.matcher(value).replaceAll("")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").strip();
    }

    /** 流式限制收到的 JSON 字节数，避免先把异常大响应完整载入内存。 */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() { return body; }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - output.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new IOException("QQ 搜索响应超过 4 MiB"));
                    return;
                }
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                output.writeBytes(bytes);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable error) { body.completeExceptionally(error); }

        @Override
        public void onComplete() { body.complete(output.toByteArray()); }
    }
}
