package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class KugouCookieLoginTest {
    @TempDir Path dir;
    @Test void rawAndKuGooCookiesRoundTripIndependently()throws Exception{
        var store=new KugouAccountStore(dir.resolve("kugou/account.json"));String device=store.account().deviceId();
        var account=KugouCookieLogin.parse("token=t=ok+%; userid=123; kg_dfid=abc",device);
        assertTrue(account.cookieLogin());assertEquals("t=ok+%",account.token());assertEquals("abc",KugouApi.cookie(account.cookieManager(),"kg_dfid"));
        assertTrue(store.commit(0,account));var restored=new KugouAccountStore(dir.resolve("kugou/account.json")).account();assertTrue(restored.cookieLogin());assertEquals(account.token(),restored.token());
        var web=KugouCookieLogin.parse("KuGoo=KugooID=123&t=web%2Btoken&NickName=test",device);assertEquals("web+token",web.token());assertEquals("123",web.userId());
    }
    @Test void incompleteConflictAndHeaderInjectionAreRejected(){
        for(var raw:new String[]{"userid=123", "userid=0; token=secret", "userid=123; token=secret\nCookie: x", "userid=123; token=secret; token=other"})assertThrows(IllegalArgumentException.class,()->KugouCookieLogin.parse(raw,"a".repeat(32)));
    }
}
