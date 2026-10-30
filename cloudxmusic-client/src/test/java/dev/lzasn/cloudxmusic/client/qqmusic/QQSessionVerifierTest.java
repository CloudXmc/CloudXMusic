package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QQSessionVerifierTest {
    static JsonObject json(String text) { return JsonParser.parseString(text).getAsJsonObject(); }
    static JsonObject reply(long code, String data) {
        return json("{\"code\":0,\"req_0\":{\"code\":" + code + ",\"data\":" + data + "}}");
    }
    static JsonObject payload(String key) {
        JsonObject values = new JsonObject();
        values.add("qqmusic_uin", json("{\"value\":\"123456\"}"));
        JsonObject token = new JsonObject(); token.addProperty("value", key); values.add("qqmusic_key", token);
        JsonObject payload = new JsonObject(); payload.add("cookies", values); return payload;
    }
    @Test void directlyUsableKeyIsNeverExchanged() throws Exception {
        List<JsonObject> calls = new ArrayList<>();
        QQSessionVerifier verifier = new QQSessionVerifier((request, cookies) -> {
            calls.add(request);
            assertEquals(3, cookies.getCookieStore().getCookies().size());
            assertEquals("GetLoginUserInfo", request.getAsJsonObject("req_0").get("method").getAsString());
            assertEquals(19, request.getAsJsonObject("comm").get("ct").getAsInt());
            return reply(0, "{\"musicid\":123456,\"info\":{\"base\":{\"nick\":\"测试昵称\"}}}");
        });
        QQCredentials result = verifier.complete(payload("usable-key"), "qr-id");
        assertEquals(1, calls.size()); assertEquals("测试昵称", result.nickname()); assertEquals("usable-key", result.musicKey());
    }
    @Test void ticketExchangeUsesFreshJarAndIsVerifiedBeforeSuccess() throws Exception {
        List<String> methods = new ArrayList<>();
        QQSessionVerifier verifier = new QQSessionVerifier((request, cookies) -> {
            String method = request.getAsJsonObject("req_0").get("method").getAsString(); methods.add(method);
            if (methods.size() == 1) return reply(1000, "{}");
            if (method.equals("Login")) {
                assertTrue(cookies.getCookieStore().getCookies().isEmpty());
                assertFalse(request.getAsJsonObject("comm").has("authst"));
                assertEquals(6, request.getAsJsonObject("comm").get("tmeLoginType").getAsInt());
                JsonObject p = request.getAsJsonObject("req_0").getAsJsonObject("param");
                assertEquals("ticket", p.get("token").getAsString()); assertEquals("qr-id", p.get("qrCodeID").getAsString());
                java.net.HttpCookie extra = new java.net.HttpCookie("exchange_session", "opaque");
                extra.setDomain(".qq.com"); extra.setPath("/"); extra.setSecure(true);
                cookies.getCookieStore().add(java.net.URI.create("https://u.y.qq.com/"), extra);
                return reply(0, "{\"musicid\":123456,\"musickey\":\"new-key\",\"loginType\":2}");
            }
            if (methods.size() == 3) assertTrue(cookies.getCookieStore().getCookies().stream()
                    .anyMatch(cookie -> cookie.getName().equals("exchange_session") && cookie.getValue().equals("opaque")));
            assertEquals("new-key", request.getAsJsonObject("comm").get("authst").getAsString());
            return reply(0, "{\"str_musicid\":\"123456\",\"nickname\":\"昵称\"}");
        });
        assertEquals("new-key", verifier.complete(payload("ticket"), "qr-id").musicKey());
        assertEquals(List.of("GetLoginUserInfo", "Login", "GetLoginUserInfo"), methods);
    }
    @Test void exchangedKeyGetsOneReadOnlyPropagationRetry() throws Exception {
        int[] count = {0};
        QQSessionVerifier verifier = new QQSessionVerifier((r, c) -> switch (++count[0]) {
            case 1, 3 -> reply(104401, "{}");
            case 2 -> reply(0, "{\"musicid\":123456,\"musickey\":\"new\"}");
            default -> reply(0, "{\"musicid\":123456}");
        });
        assertEquals("new", verifier.complete(payload("ticket"), "qr-id").musicKey()); assertEquals(4, count[0]);
    }
    @Test void rejectedExchangeRechecksOriginalWithoutSecondMutation() throws Exception {
        int[] count = {0};
        QQSessionVerifier verifier = new QQSessionVerifier((r, c) -> ++count[0] < 3 ? reply(1000, "{}") : reply(0, "{\"musicid\":123456}"));
        assertEquals("ticket", verifier.complete(payload("ticket"), "qr-id").musicKey()); assertEquals(3, count[0]);
    }
    @Test void deviceLimitDoesNotTriggerExchange() {
        int[] count = {0}; QQSessionVerifier verifier = new QQSessionVerifier((r, c) -> { count[0]++; return reply(20279, "{}"); });
        QQSessionVerifier.Rejected error = assertThrows(QQSessionVerifier.Rejected.class, () -> verifier.complete(payload("ticket"), "qr-id"));
        assertTrue(error.getMessage().contains("设备")); assertEquals(1, count[0]);
    }
    @Test void emptyProfileCannotAuthenticate() {
        assertThrows(IOException.class, () -> new QQSessionVerifier((r, c) -> reply(0, "{}"))
                .complete(payload("key"), "qr-id"));
    }
    @Test void identityMismatchCannotAuthenticate() {
        assertThrows(IOException.class, () -> new QQSessionVerifier((r, c) -> reply(0, "{\"musicid\":456}"))
                .complete(payload("key"), "qr-id"));
    }
    @Test void exchangedIdentityMismatchCannotAuthenticate() {
        int[] count = {0}; assertThrows(IOException.class, () -> new QQSessionVerifier((r, c) -> ++count[0] == 1
                ? reply(1000, "{}") : reply(0, "{\"musicid\":456,\"musickey\":\"key\"}"))
                .complete(payload("ticket"), "qr-id"));
    }
    @Test void malformedCodesAndNetworkFailuresAreNotSuccess() {
        for (String text : List.of("{}", "{\"code\":false}", "{\"code\":0.5}", "{\"code\":0,\"req_0\":[]}"))
            assertThrows(IOException.class, () -> new QQSessionVerifier((r, c) -> json(text)).complete(payload("key"), "qr-id"));
        assertThrows(IOException.class, () -> new QQSessionVerifier((r, c) -> { throw new IOException("offline"); }).complete(payload("key"), "qr-id"));
    }
    @Test void invalidTicketIsRejectedBeforeNetwork() {
        assertThrows(IOException.class, () -> new QQSessionVerifier((r, c) -> fail("network must not be called"))
                .complete(payload("injected;cookie=1"), "qr-id"));
    }
    @Test void wechatTypeAndGtkMatchReference() throws Exception {
        assertEquals(5381, QQSessionVerifier.hash33("")); assertEquals(193485963, QQSessionVerifier.hash33("abc"));
        QQCredentials value = new QQSessionVerifier((r, cookies) -> {
            assertEquals(1, r.getAsJsonObject("comm").get("tmeLoginType").getAsInt());
            assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(c -> c.getName().equals("login_type") && c.getValue().equals("2")));
            return reply(0, "{\"musicid\":123456}");
        }).complete(payload("W_X-key"), "qr-id");
        assertEquals(1, value.loginType());
    }
}
