package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQAccountManagerTest.await;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QQLoginServiceTest {
    @TempDir Path directory;
    static JsonObject qrReply() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_RGB), "png", bytes);
        JsonObject data = new JsonObject(); data.addProperty("qrcodeID", "qr-id"); data.addProperty("qrcode", Base64.getEncoder().encodeToString(bytes.toByteArray()));
        return reply(0, data.toString());
    }
    static final class FakeChannel implements QQLoginService.Channel {
        final BlockingQueue<QQLoginService.Event> events = new LinkedBlockingQueue<>();
        volatile boolean connected, closed;
        public void connect() { connected = true; }
        public QQLoginService.Event next(long deadline) throws InterruptedException {
            while (System.nanoTime() < deadline && !closed) { var event = events.poll(100, TimeUnit.MILLISECONDS); if (event != null) return event; }
            return null;
        }
        public void close() { closed = true; }
        void send(String type) { events.offer(new QQLoginService.Event(type, new byte[0])); }
        void cookies() { events.offer(new QQLoginService.Event("cookies", payload("key").toString().getBytes(StandardCharsets.UTF_8))); }
    }
    QQAccountManager accounts() { return new QQAccountManager(new QQCredentialStore(directory.resolve("account.json")), new QQSessionVerifier()); }
    QQSessionVerifier goodVerifier() { return new QQSessionVerifier((r,c) -> reply(0, "{\"musicid\":123456,\"nick\":\"昵称\"}")); }

    @Test void subscribesBeforeShowingQrAndOnlySavesVerifiedSession() throws Exception {
        FakeChannel channel = new FakeChannel();
        try (var accounts = accounts(); var login = new QQLoginService(accounts, Runnable::run, (r,c) -> {
            assertEquals(23, r.getAsJsonObject("comm").get("ct").getAsInt());
            JsonObject p = r.getAsJsonObject("req_0").getAsJsonObject("param");
            assertEquals(19, p.get("ct").getAsInt()); assertEquals(2201, p.get("cv").getAsInt()); assertEquals("qqmusic", p.get("tmeAppID").getAsString());
            return qrReply();
        }, id -> channel, goodVerifier())) {
            login.start(); await(() -> login.view().state() == QQLoginService.State.WAITING);
            assertTrue(channel.connected); assertNotNull(login.view().qr()); assertFalse(Files.exists(directory.resolve("account.json")));
            channel.send("scanned"); await(() -> login.view().state() == QQLoginService.State.SCANNED);
            channel.cookies(); await(() -> login.view().state() == QQLoginService.State.SUCCESS);
            await(() -> accounts.view().status().contains("凭证已保存")); assertTrue(channel.closed);
            assertEquals("key", new QQCredentialStore(directory.resolve("account.json")).load().orElseThrow().musicKey());
        }
    }
    @Test void cancellationDiscardsAlreadyQueuedMainThreadSuccess() throws Exception {
        Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>(); FakeChannel channel = new FakeChannel(); channel.cookies();
        try (var accounts = accounts(); var login = new QQLoginService(accounts, callbacks::add, (r,c) -> qrReply(), id -> channel, goodVerifier())) {
            login.start(); await(() -> callbacks.size() >= 3); login.cancel(); callbacks.forEach(Runnable::run);
            assertEquals(QQLoginService.State.IDLE, login.view().state()); assertFalse(accounts.view().loggedIn());
            assertFalse(Files.exists(directory.resolve("account.json"))); assertTrue(channel.closed);
        }
    }
    @Test void refreshDiscardsOldSessionCallbacks() throws Exception {
        Queue<Runnable> callbacks = new ConcurrentLinkedQueue<>(); List<FakeChannel> channels = new CopyOnWriteArrayList<>();
        try (var accounts = accounts(); var login = new QQLoginService(accounts, callbacks::add, (r,c) -> qrReply(), id -> {
            FakeChannel channel = new FakeChannel(); channels.add(channel); return channel;
        }, goodVerifier())) {
            login.start(); await(() -> callbacks.size() == 1); Runnable oldWaiting = callbacks.remove();
            login.start(); oldWaiting.run(); assertEquals(QQLoginService.State.LOADING, login.view().state());
            await(() -> channels.size() == 2); assertTrue(channels.getFirst().closed);
            await(() -> !callbacks.isEmpty()); callbacks.remove().run(); assertEquals(QQLoginService.State.WAITING, login.view().state());
        }
    }
    @Test void expiredCanceledAndFailureNeverAuthenticate() throws Exception {
        for (String event : List.of("timeout", "canceled", "loginFailed")) {
            FakeChannel channel = new FakeChannel(); channel.send(event);
            try (var accounts = accounts(); var login = new QQLoginService(accounts, Runnable::run, (r,c) -> qrReply(), id -> channel, goodVerifier())) {
                login.start(); await(() -> switch (login.view().state()) { case EXPIRED,CANCELED,ERROR -> true; default -> false; });
                assertFalse(accounts.view().loggedIn()); assertNull(login.view().qr()); assertFalse(Files.exists(directory.resolve("account.json")));
            }
        }
    }
    @Test void unverifiedCookiesNeverSaveAndExistingAccountSurvives() throws Exception {
        FakeChannel channel = new FakeChannel(); channel.cookies();
        try (var accounts = accounts(); var login = new QQLoginService(accounts, Runnable::run, (r,c) -> qrReply(), id -> channel,
                new QQSessionVerifier((r,c) -> reply(20279, "{}")))) {
            accounts.acceptVerified(new QQCredentials("123456", "original-key", 2, "原账号", 0));
            await(() -> accounts.view().status().contains("凭证已保存"));
            login.start(); await(() -> login.view().state() == QQLoginService.State.ERROR);
            assertTrue(accounts.view().loggedIn()); assertEquals("原账号", accounts.view().nickname());
            assertEquals("original-key", new QQCredentialStore(directory.resolve("account.json")).load().orElseThrow().musicKey());
        }
    }
    @Test void connectionFailureDoesNotExposeQr() throws Exception {
        try (var accounts = accounts(); var login = new QQLoginService(accounts, Runnable::run, (r,c) -> qrReply(), id -> new QQLoginService.Channel() {
            public void connect() throws IOException { throw new IOException("offline"); }
            public QQLoginService.Event next(long d) { fail("must not poll"); return null; }
            public void close() {}
        }, goodVerifier())) {
            login.start(); await(() -> login.view().state() == QQLoginService.State.ERROR); assertNull(login.view().qr());
        }
    }
    @Test void invalidQrNeverConnects() throws Exception {
        try (var accounts = accounts(); var login = new QQLoginService(accounts, Runnable::run,
                (r,c) -> reply(0, "{\"qrcodeID\":\"../evil\",\"qrcode\":\"xxx\"}"), id -> fail("must not connect"), goodVerifier())) {
            login.start(); await(() -> login.view().state() == QQLoginService.State.ERROR);
        }
    }
    @Test void qrDecoderRejectsInvalidHugeAndWrongSizeData() throws Exception {
        for (String text : List.of("!", "AA==", "a".repeat(1400001))) assertThrows(IOException.class, () -> QQLoginService.decodeQr(text));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2048,32,BufferedImage.TYPE_INT_RGB), "png", bytes);
        assertThrows(IOException.class, () -> QQLoginService.decodeQr(Base64.getEncoder().encodeToString(bytes.toByteArray())));
        String qr = qrReply().getAsJsonObject("req_0").getAsJsonObject("data").get("qrcode").getAsString();
        assertEquals(64, QQLoginService.decodeQr("data:image/png;base64," + qr).getWidth());
    }
    @Test void closeIsIdempotentAndStopsActiveChannel() throws Exception {
        FakeChannel channel = new FakeChannel();
        try (var accounts = accounts()) {
            var login = new QQLoginService(accounts, Runnable::run, (r,c) -> qrReply(), id -> channel, goodVerifier());
            login.start(); await(() -> channel.connected); login.close(); login.close(); login.start();
            assertTrue(channel.closed); assertNull(login.view().qr());
        }
    }
}
