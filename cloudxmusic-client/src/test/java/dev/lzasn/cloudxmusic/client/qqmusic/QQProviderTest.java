package dev.lzasn.cloudxmusic.client.qqmusic;

import com.sun.net.httpserver.HttpServer;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class QQProviderTest {
    @Test void paidSearchFieldsBecomeVipMetadata()throws Exception{
        replies=uri->new Reply(200,wrapped("[{\"mid\":\"Pay1\",\"pay\":{\"play\":1}},{\"mid\":\"Pay2\",\"vip\":true},{\"mid\":\"Pay3\",\"pay\":{\"pay_play\":\"1\"}},{\"mid\":\"Free\",\"pay\":{\"play\":0,\"download\":1}}]"));
        var songs=provider().searchSongs("test",0,20);
        assertTrue(songs.get(0).vip());assertTrue(songs.get(1).vip());assertTrue(songs.get(2).vip());assertFalse(songs.get(3).vip());
    }
    @TempDir Path directory;
    private HttpServer server;
    private URI endpoint;
    private final List<URI> requests = new CopyOnWriteArrayList<>();
    private volatile Function<URI, Reply> replies;
    private volatile String cookie;
    private volatile String referer;

    @BeforeEach
    void startServer() throws Exception {
        replies = uri -> new Reply(200, wrapped("[]"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                requests.add(exchange.getRequestURI());
                cookie = exchange.getRequestHeaders().getFirst("Cookie");
                referer = exchange.getRequestHeaders().getFirst("Referer");
                Reply reply = replies.apply(exchange.getRequestURI());
                byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(reply.status(), bytes.length);
                if (reply.bodyDelayMillis() > 0) Thread.sleep(reply.bodyDelayMillis());
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 超时、取消和体积限制测试会主动断开客户端连接。
            } finally {
                exchange.close();
            }
        });
        server.start();
        endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/soso/fcgi-bin/client_search_cp");
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void identifiesSourceAndValidatesMissingSongBeforeLyricsRequest() {
        QQProvider provider = provider();
        assertEquals("qq", provider.sourceId());
        assertThrows(IOException.class, () -> provider.getPlaybackUrl(null), "未装配的独立搜索 Provider 必须明确报告缺少账号服务");
        assertThrows(IllegalArgumentException.class, () -> provider.getLyrics(null));
        assertTrue(requests.isEmpty());
    }

    @Test
    void mapsLegacyFieldsAndCleansSearchHighlights() throws Exception {
        replies = uri -> new Reply(200, wrapped("""
                [{"songmid":"Legacy123","songname":"<em>晴天</em> &amp; 风",
                  "singer":[{"name":"歌手甲"},{"name":"歌手乙 &quot;B&quot;"}],
                  "albumname":"专辑 &#39;一&#39;"}]
                """));
        UnifiedSong song = provider().searchSongs("晴天", 0, 20).getFirst();
        assertEquals("qq", song.source());
        assertEquals("Legacy123", song.platformId());
        assertEquals("晴天 & 风", song.title());
        assertEquals("歌手甲 / 歌手乙 \"B\"", song.artist());
        assertEquals("专辑 '一'", song.album());
    }

    @Test
    void mapsModernFieldsAndUsesDangoFallbackOrder() throws Exception {
        replies = uri -> new Reply(200, wrapped("""
                [{"songmid":"","mid":"Modern123","songname":"","title":"标题优先","name":"名称",
                  "singer":[{"name":"甲"},{"name":""},{"name":"乙"}],"album":{"name":"新专辑"}},
                 {"mid":"NameOnly","name":"名称回退"}]
                """));
        List<UnifiedSong> songs = provider().searchSongs("test", 0, 20);
        assertEquals("Modern123", songs.getFirst().platformId());
        assertEquals("标题优先", songs.getFirst().title());
        assertEquals("甲 / 乙", songs.getFirst().artist());
        assertEquals("新专辑", songs.getFirst().album());
        assertEquals("名称回退", songs.get(1).title());
        assertEquals("", songs.get(1).artist());
        assertEquals("", songs.get(1).album());
    }

    @Test
    void acceptsRawUpstreamResponse() throws Exception {
        replies = uri -> new Reply(200, "{\"code\":0,\"data\":{\"song\":{\"curpage\":1,\"list\":[{\"mid\":\"Raw123\"}]}}}");
        assertEquals("Raw123", provider().searchSongs("test", 0, 20).getFirst().platformId());
    }

    @Test
    void encodesNativeQueryWithoutCookiesOrSignature() throws Exception {
        provider().searchSongs(" 周杰伦 & + / ? ", 0, 20);
        assertEquals("/soso/fcgi-bin/client_search_cp", requests.getFirst().getPath());
        assertEquals("周杰伦 & + / ?", query(requests.getFirst(), "w"));
        assertEquals("1", query(requests.getFirst(), "p"));
        assertEquals("40", query(requests.getFirst(), "n"));
        assertNull(cookie);
        assertEquals("https://y.qq.com/", referer);
        assertEquals(4, requests.getFirst().getRawQuery().split("&").length);
        assertEquals("json", query(requests.getFirst(), "format"));
    }

    @Test
    void handlesUnalignedOffsetsAcrossTwoPages() throws Exception {
        replies = uri -> {
            int page = Integer.parseInt(query(uri, "p"));
            String list = java.util.stream.IntStream.range((page - 1) * 40, page * 40)
                    .mapToObj(i -> "{\"mid\":\"S" + i + "\"}").collect(java.util.stream.Collectors.joining(",", "[", "]"));
            return new Reply(200, wrapped(list).replace("\"curpage\":1", "\"curpage\":" + page));
        };
        List<UnifiedSong> songs = provider().searchSongs("test", 39, 3);
        assertEquals(List.of("S39", "S40", "S41"), songs.stream().map(UnifiedSong::platformId).toList());
        assertEquals(List.of("1", "2"), requests.stream().map(uri -> query(uri, "p")).toList());
    }

    @Test
    void skipsInvalidMidsWithoutMovingPaginationBoundaries() throws Exception {
        replies = uri -> new Reply(200, wrapped("[{\"mid\":\"bad-mid!\"},{\"mid\":\"Good123\"},{\"songid\":123}]"));
        List<UnifiedSong> songs = provider().searchSongs("test", 0, 3);
        assertEquals(List.of("Good123"), songs.stream().map(UnifiedSong::platformId).toList());
        assertEquals(1, requests.size());
    }

    @Test
    void returnsEmptyOnlyForValidEmptyOrExhaustedPages() throws Exception {
        assertTrue(provider().searchSongs("test", 0, 20).isEmpty());
        replies = uri -> new Reply(200, wrapped("[{\"mid\":\"One\"}]"));
        assertTrue(provider().searchSongs("test", 2, 20).isEmpty());
        assertEquals(2, requests.size());
    }

    @Test
    void rejectsInvalidArgumentsBeforeNetworkAccess() {
        QQProvider provider = provider();
        assertThrows(IllegalArgumentException.class, () -> provider.searchSongs(" ", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> provider.searchSongs(null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> provider.searchSongs("x", -1, 20));
        assertThrows(IllegalArgumentException.class, () -> provider.searchSongs("x", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> provider.searchSongs("x", 0, 101));
        assertTrue(requests.isEmpty());
    }

    @Test
    void reportsHttpAndBusinessErrors() {
        for (Reply reply : List.of(new Reply(503, "unavailable"),
                new Reply(302, "redirect"), new Reply(200, "{\"response\":{\"code\":1000}}"))) {
            replies = uri -> reply;
            assertThrows(IOException.class, () -> provider().searchSongs("test", 0, 20));
        }
    }

    @Test
    void reportsMalformedJsonAndMissingOrWrongShapedFields() {
        for (String body : List.of("not json", "null", "[]", "{}", "{\"response\":null}",
                "{\"response\":{\"code\":false}}", "{\"response\":{\"code\":0}}",
                wrapped("{}"), wrapped("[null]"), wrapped("[{\"mid\":\"A\",\"singer\":{}}]"),
                wrapped("[{\"mid\":\"A\",\"album\":[]}]"))) {
            replies = uri -> new Reply(200, body);
            assertThrows(IOException.class, () -> provider().searchSongs("test", 0, 20));
        }
    }

    @Test
    void limitsResponseSize() {
        replies = uri -> new Reply(200, " ".repeat(4 * 1024 * 1024 + 1));
        assertThrows(IOException.class, () -> provider().searchSongs("test", 0, 20));
    }

    @Test
    void boundsWholeResponseTimeIncludingBody() {
        replies = uri -> new Reply(200, wrapped("[]"), 500);
        assertThrows(HttpTimeoutException.class,
                () -> new QQProvider(endpoint, Duration.ofMillis(150)).searchSongs("test", 0, 20));
    }

    @Test
    void preservesInterruptionAndDoesNotSendRequest() {
        try {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class,
                    () -> provider().searchSongs("test", 0, 20));
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(requests.isEmpty());
        } finally {
            Thread.interrupted();
        }
    }

    @Test void paginationCursorCountsRawItemsEvenWithInvalidAndDuplicateMids()throws Exception{
        replies=uri->{
            int page=Integer.parseInt(query(uri,"p"));
            String list=java.util.stream.IntStream.range(0,page==1?40:25)
                .mapToObj(n->"{\"mid\":\""+(n%5==0?"!invalid":"Same"+(page*40+n))+"\"}")
                .collect(java.util.stream.Collectors.joining(",","[","]"));
            return new Reply(200,wrapped(list).replace("\"curpage\":1","\"curpage\":"+page));
        };
        var provider=provider();var first=provider.searchPage("raw",0,30);
        assertEquals(24,first.songs().size());assertEquals(30,first.nextOffset());assertTrue(first.hasMore());
        var second=provider.searchPage("raw",first.nextOffset(),30);
        assertEquals(60,second.nextOffset());assertTrue(second.hasMore());
        var last=provider.searchPage("raw",second.nextOffset(),30);
        assertEquals(65,last.nextOffset());assertFalse(last.hasMore());
    }
    @Test void upstreamTotalEndsExactlyOnFullPage()throws Exception{
        String list=java.util.stream.IntStream.range(0,40).mapToObj(n->"{\"mid\":\"A"+n+"\"}")
            .collect(java.util.stream.Collectors.joining(",","[","]"));
        replies=uri->new Reply(200,wrapped(list).replace("\"curpage\":1","\"curpage\":1,\"totalnum\":40"));
        var page=provider().searchPage("total",0,40);assertEquals(40,page.nextOffset());assertFalse(page.hasMore());
    }

    private QQProvider provider() { return new QQProvider(endpoint, Duration.ofSeconds(3)); }

    @Test void rejectsMismatchedMissingAndNonIntegerPageNumbers() {
        for (String page : List.of("2", "null", "false", "1.5", "\"bad\"")) {
            replies = uri -> new Reply(200, wrapped("[]").replace("\"curpage\":1", "\"curpage\":" + page));
            assertThrows(IOException.class, () -> provider().searchSongs("x", 0, 20));
        }
    }

    @Test void rejectsOversizedPageInsteadOfSilentlyShiftingOffsets() {
        String list = java.util.stream.IntStream.range(0, 41).mapToObj(i -> "{\"mid\":\"S" + i + "\"}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        replies = uri -> new Reply(200, wrapped(list));
        assertThrows(IOException.class, () -> provider().searchSongs("x", 0, 30));
    }

    private static String wrapped(String list) {
        return "{\"response\":{\"code\":0,\"data\":{\"song\":{\"curpage\":1,\"list\":" + list + "}}}}";
    }

    private static String query(URI uri, String name) {
        for (String part : uri.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair[0].equals(name)) return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
        }
        throw new AssertionError("Missing query parameter: " + name);
    }

    private record Reply(int status, String body, long bodyDelayMillis) {
        Reply(int status, String body) { this(status, body, 0); }
    }
}
