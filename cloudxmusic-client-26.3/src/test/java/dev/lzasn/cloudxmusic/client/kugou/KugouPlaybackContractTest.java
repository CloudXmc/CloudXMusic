package dev.lzasn.cloudxmusic.client.kugou;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KugouPlaybackContractTest {
    private static final KugouAccount ACCOUNT = new KugouAccount("123456", "web-token", "a".repeat(32), "", List.of());

    @Test void webSongInfoUsesProtocolParametersAndAppRouteOnlyAcceptsMp3Tiers() throws Exception {
        var requests = new ArrayList<Map<String, String>>();
        var endpoints = new ArrayList<String>();
        var api = new KugouApi((endpoint, params, headers, body, cookies) -> {
            endpoints.add(endpoint);
            requests.add(new LinkedHashMap<>(params));
            if (endpoint.endsWith("/play/songinfo")) {
                assertEquals(KugouApi.WEB_USER_AGENT, headers.get("User-Agent"));
            } else if (endpoint.endsWith("/v5/url")) {
                assertEquals(KugouApi.PLAYBACK_USER_AGENT, headers.get("User-Agent"));
            }
            return KugouLoginServiceTest.json("{\"status\":1}");
        });

        api.webSongInfo(KugouPlaybackResolverTest.track(), ACCOUNT, true);
        assertEquals("https://wwwapi.kugou.com/play/songinfo", endpoints.getFirst());
        var web = requests.getFirst();
        assertEquals(KugouPlaybackResolverTest.HASH, web.get("hash"), "KugouWeb.songInfo preserves the canonical hash in both query and signature");
        assertEquals("123", web.get("album_id"));
        assertEquals("4", web.get("platid"));
        assertEquals("123456", web.get("userid"));
        assertEquals("web-token", web.get("token"));
        assertFalse(web.containsKey("album_audio_id"), "Web songinfo does not send album_audio_id");
        assertFalse(web.containsKey("quality"), "Web songinfo has no requested-quality parameter");
        var webSigned = new LinkedHashMap<>(web);
        String webSignature = webSigned.remove("signature");
        assertEquals(KugouSigning.signature(webSigned, true).toUpperCase(Locale.ROOT), webSignature);

        for (KugouQuality quality : List.of(KugouQuality.HQ, KugouQuality.STANDARD)) {
            api.quality(KugouPlaybackResolverTest.track(), ACCOUNT, quality, true);
            assertEquals(quality.apiValue(), requests.getLast().get("quality"));
        }
        assertThrows(IllegalArgumentException.class, () -> api.quality(KugouPlaybackResolverTest.track(), ACCOUNT, KugouQuality.HIGH, true));
        assertThrows(IllegalArgumentException.class, () -> api.quality(KugouPlaybackResolverTest.track(), ACCOUNT, KugouQuality.FLAC, true));
    }

    @Test void appGatewayUsesAndroidClientHeaderWhileWebPlaybackUsesBrowserHeader() throws Exception {
        var api = new KugouApi((endpoint, params, headers, body, cookies) -> {
            if (endpoint.endsWith("/v3/get_my_info"))
                assertEquals(KugouApi.GATEWAY_USER_AGENT, headers.get("User-Agent"));
            return KugouLoginServiceTest.json("{\"status\":1}");
        });

        api.app("https://gateway.kugou.com/v3/get_my_info", "usercenter.kugou.com", Map.of(), null, ACCOUNT);
    }

    @Test void verifiesFlacByContentWhenCdnUrlHasNoExtension() throws Exception {
        byte[] audio;
        try (var input = getClass().getResourceAsStream("/qq-audio/tone16.flac")) {
            assertNotNull(input);
            audio = input.readAllBytes();
        }
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().add("Content-Range", "bytes 0-41/" + audio.length);
            exchange.getResponseHeaders().add("Content-Type", "audio/flac");
            exchange.sendResponseHeaders(206, 42);
            exchange.getResponseBody().write(audio, 0, 42);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/stream?id=opaque");
            assertEquals(uri, KugouAudioUrlVerifier.verify(uri, true, Duration.ofSeconds(2), value -> "127.0.0.1".equals(value.getHost())));
            assertEquals(KugouQuality.FLAC, KugouAudioUrlVerifier.probe(uri, KugouQuality.HIGH, 0, Duration.ofSeconds(2),
                    value -> "127.0.0.1".equals(value.getHost())).quality());
        } finally {
            server.stop(0);
        }
    }

    @Test void detects320KbpsMp3FromAudioFramesWhenUrlHasNoExtension() throws Exception {
        byte[] audio;
        try (var input = getClass().getResourceAsStream("/qq-audio/tone.mp3")) {
            assertNotNull(input);
            audio = input.readAllBytes();
        }
        int end = Math.min(8192, audio.length);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stream", exchange -> {
            exchange.getResponseHeaders().add("Content-Range", "bytes 0-" + (end - 1) + "/" + audio.length);
            exchange.getResponseHeaders().add("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(206, end);
            exchange.getResponseBody().write(audio, 0, end);
            exchange.close();
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/stream?id=opaque");
            var result = KugouAudioUrlVerifier.probe(uri, KugouQuality.HQ, 0, Duration.ofSeconds(2),
                    value -> "127.0.0.1".equals(value.getHost()));
            assertNotNull(result);
            assertEquals(KugouQuality.HQ, result.quality());
        } finally {
            server.stop(0);
        }
    }

    @Test void nonPermissionApiErrorTriesOtherRoutesAndRetainsCodeIfAllFail() {
        var attempts = new ArrayList<KugouQuality>();
        var resolver = new KugouPlaybackResolver((song, account, quality, metadata) -> {
            attempts.add(quality);
            return KugouLoginServiceTest.json("{\"status\":2,\"error_code\":20018}");
        }, (uri, quality, bitrate) -> null);

        IOException failure = assertThrows(IOException.class, () -> resolver.resolve(
                KugouPlaybackResolverTest.track(), ACCOUNT, KugouQuality.HIGH));
        assertTrue(failure.getMessage().contains("20018"), failure.getMessage());
        assertTrue(attempts.contains(KugouQuality.HQ));assertTrue(attempts.contains(KugouQuality.STANDARD));assertTrue(attempts.size()<=4);
    }

    @Test void networkTimeoutHasBoundedRetriesAndRemainsANetworkError() {
        var attempts = new AtomicInteger();
        var resolver = new KugouPlaybackResolver((song, account, quality, metadata) -> {
            attempts.incrementAndGet();
            throw new java.net.SocketTimeoutException("timeout");
        }, (uri, quality, bitrate) -> null);

        assertThrows(java.net.SocketTimeoutException.class, () -> resolver.resolve(KugouPlaybackResolverTest.track(), ACCOUNT, KugouQuality.HIGH));
        assertTrue(attempts.get()>=2&&attempts.get()<=4);
    }
}
