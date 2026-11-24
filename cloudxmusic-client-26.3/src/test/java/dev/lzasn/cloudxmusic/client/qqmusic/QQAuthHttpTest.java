package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class QQAuthHttpTest {
    @Test void preservesHttpStatusCodesWithoutExposingResponseBodies()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        for(int code:new int[]{401,403,429,503})server.createContext("/"+code,e->{
            byte[] bytes="untrusted account=secret".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(code,bytes.length);
            try(var out=e.getResponseBody()){out.write(bytes);}
        });
        server.start();
        try{for(int code:new int[]{401,403,429,503}){
            var failure=assertThrows(QQAuthHttp.HttpFailure.class,()->QQAuthHttp.post(
                    URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/"+code),Duration.ofSeconds(3),new JsonObject(),QQAuthHttp.emptyCookies()));
            assertEquals(code,failure.statusCode());assertFalse(failure.getMessage().contains("secret"));
        }}finally{server.stop(0);}
    }
    @Test void excessiveJsonNestingIsRejectedBeforeObjectConstruction() {
        String value = "{\"nested\":".repeat(80) + "0" + "}".repeat(80);
        assertThrows(IOException.class, () -> QQAuthHttp.parse(value.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void rejectsMalformedJsonUtf8AndTrailingTokens() {
        for (String value : new String[]{"[]", "{", "{} {}", "{code:0}", "{\"code\":NaN}", "{\"x\":1,}"})
            assertThrows(IOException.class, () -> QQAuthHttp.parse(value.getBytes(StandardCharsets.UTF_8)), value);
        assertThrows(IOException.class, () -> QQAuthHttp.parse(new byte[]{(byte)0xff}));
    }
    @Test void networkStatusRedirectSizeAndBodyTimeoutAreExplicit() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
        server.createContext("/ok", e -> {
            byte[] bytes = "{\"code\":0}".getBytes(StandardCharsets.UTF_8); e.sendResponseHeaders(200, bytes.length);
            try (var out = e.getResponseBody()) { out.write(bytes); }
        });
        server.createContext("/redirect", e -> { e.getResponseHeaders().add("Location", "/ok"); e.sendResponseHeaders(302,-1); e.close(); });
        server.createContext("/fail", e -> { e.sendResponseHeaders(503,-1); e.close(); });
        server.createContext("/large", e -> {
            e.sendResponseHeaders(200,0); try (var out = e.getResponseBody()) { out.write(new byte[2 * 1024 * 1024 + 1]); }
            catch (IOException ignored) {}
        });
        server.createContext("/slow", e -> {
            e.sendResponseHeaders(200,0); e.getResponseBody().flush();
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            e.close();
        }); server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals(0, QQAuthHttp.post(base.resolve("/ok"), Duration.ofSeconds(3), new JsonObject(), QQAuthHttp.emptyCookies()).get("code").getAsInt());
            for (String path : new String[]{"/redirect", "/fail", "/large"})
                assertThrows(IOException.class, () -> QQAuthHttp.post(base.resolve(path), Duration.ofSeconds(3), new JsonObject(), QQAuthHttp.emptyCookies()));
            assertThrows(java.net.http.HttpTimeoutException.class, () -> QQAuthHttp.post(base.resolve("/slow"), Duration.ofMillis(200), new JsonObject(), QQAuthHttp.emptyCookies()));
        } finally { server.stop(0); executor.shutdownNow(); }
    }
}
