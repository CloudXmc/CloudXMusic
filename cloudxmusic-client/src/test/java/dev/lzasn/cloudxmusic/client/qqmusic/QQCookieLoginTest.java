package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.*;

class QQCookieLoginTest {
    @TempDir Path dir;
    @Test void cookieImportNormalizesUinPreservesEqualsAndNeverEnablesRefresh()throws Exception{
        var account=QQCookieLogin.parse("Cookie: foo=x; uin=o00123456; qm_keyst=a=b+c%2F; login_type=1");
        assertEquals("123456",account.userId());assertEquals("a=b+c%2F",account.musicKey());assertTrue(account.cookieLogin());assertFalse(account.renewal().available());
        var store=new QQCredentialStore(dir.resolve("qqmusic/account.json"));store.save(account);var restored=store.load().orElseThrow();assertTrue(restored.cookieLogin());assertEquals(account.musicKey(),restored.musicKey());assertFalse(restored.toString().contains("a=b"));
    }
    @Test void malformedAndConflictingCookiesAreRejectedWithoutEchoingSecrets(){
        for(var text:new String[]{"uin=123456", "uin=0; qm_keyst=secret", "uin=123456; qm_keyst=secret\r\nx: y", "uin=123456; qm_keyst=secret; qm_keyst=different"}){
            var error=assertThrows(IllegalArgumentException.class,()->QQCookieLogin.parse(text));assertFalse(error.getMessage().contains("secret"));
        }
    }
    @Test void cookieVerifyPreservesLoginMethodAndReadOnlyRequest()throws Exception{
        var verifier=new QQSessionVerifier((r,c)->{assertEquals("GetLoginUserInfo",r.getAsJsonObject("req_0").get("method").getAsString());return reply(0,"{\"musicid\":123456,\"nick\":\"真实昵称\"}");});
        var verified=verifier.verify(QQCookieLogin.parse("uin=123456; qm_keyst=long-key"));assertTrue(verified.cookieLogin());assertEquals("真实昵称",verified.nickname());
    }
}
