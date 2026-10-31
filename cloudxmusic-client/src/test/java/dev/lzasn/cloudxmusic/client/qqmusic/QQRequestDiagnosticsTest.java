package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QQRequestDiagnosticsTest {
    @Test void outerBusinessCodeCannotBeReplacedByNestedRetCode() throws Exception {
        var response = JsonParser.parseString("{\"retCode\":0,\"result\":{\"songId\":123}}").getAsJsonObject();
        assertTrue(QQRequestDiagnostics.json(response).contains("retCode"));
        assertThrows(QQPlaybackApi.BusinessFailure.class, () -> QQPlaybackApi.requireData(new QQPlaybackApi.Reply(80105, response)));
    }
    @Test void webIdentityHasCookieAliasesAndMatchingCsrf() throws Exception {
        var credentials=new QQCredentials("123456","private-key",2,"",0);
        var api=new QQPlaybackApi(credentials,(request,cookies,timeout)->{
            var comm=request.getAsJsonObject("comm");
            assertEquals(4747474,comm.get("cv").getAsInt());
            assertEquals("yqq.json",comm.get("platform").getAsString());
            assertEquals(QQSessionVerifier.hash33(credentials.musicKey()),comm.get("g_tk").getAsInt());
            assertEquals(comm.get("g_tk"),comm.get("g_tk_new_20200303"));
            var names=new HashMap<String,String>();cookies.getCookieStore().getCookies().forEach(c->names.put(c.getName(),c.getValue()));
            assertEquals(names.get("qqmusic_key"),names.get("qm_keyst"));
            assertEquals(names.get("qqmusic_uin"),names.get("uin"));
            return QQSessionVerifierTest.reply(0,"{\"retCode\":0}");
        },()->true,Duration.ofSeconds(5));
        assertEquals(0,api.call("music.pf_song_detail_svr","get_song_detail_yqq",new JsonObject(),false).code());
    }
    @Test void logsRetainCodesAndFileIdentityButNeverCredentialsOrSignedQueries(){
        var json=JsonParser.parseString("{\"code\":0,\"retCode\":1001,\"authst\":\"secret\",\"uin\":123456,\"purl\":\"AI00media.flac?vkey=secret&guid=abc\",\"nested\":{\"qm_keyst\":\"secret\"}}");
        String safe=QQRequestDiagnostics.json(json);
        assertTrue(safe.contains("1001"));assertTrue(safe.contains("AI00media.flac"));
        assertFalse(safe.contains("secret"));assertFalse(safe.contains("123456"));assertFalse(safe.contains("guid=abc"));
        assertTrue(json.toString().contains("secret"),"诊断不能修改原响应");
    }

    @Test void csrfPrefersPSkeyWhenWebCookieIsAvailable() {
        var cookies = QQAuthHttp.emptyCookies();
        var cookie = new java.net.HttpCookie("p_skey", "web-session-key");
        cookie.setDomain(".qq.com");
        cookies.getCookieStore().add(java.net.URI.create("https://u.y.qq.com/"), cookie);
        assertEquals(QQSessionVerifier.hash33("web-session-key"),
                QQPlaybackApi.csrfToken(cookies, "music-key-fallback"));
    }
}
