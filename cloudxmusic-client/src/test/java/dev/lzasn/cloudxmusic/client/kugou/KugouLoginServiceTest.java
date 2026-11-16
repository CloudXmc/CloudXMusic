package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.*;
import javax.crypto.spec.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouLoginServiceTest {
    @Test void renewalBodyTokenOverridesUnchangedOldCookieAndUpdatesSavedCookie()throws Exception{
        var old=new KugouAccount("123","old-token","a".repeat(32),"用户",List.of(new KugouAccount.Cookie("KuGoo","t=old-token&KugooID=123",".kugou.com","/",true,0)));
        try(var service=new KugouLoginService(old.deviceId(),new KugouApi((u,p,h,b,c)->{
            if(u.endsWith("/login_by_token_get"))return json("{\"status\":1,\"data\":{\"userid\":123,\"token\":\"body-token\"}}");
            assertEquals("body-token",p.get("token"));return json("{\"status\":1,\"data\":{\"userid\":123,\"nickname\":\"用户\"}}");
        }))){var next=service.renew(old);assertEquals("body-token",next.token());assertTrue(next.cookies().stream().anyMatch(c->c.name().equals("KuGoo")&&c.value().contains("t=body-token")));}
    }
    @Test void unchangedOldCookieCannotMasqueradeAsRenewalResponse()throws Exception{
        var old=new KugouAccount("123","old-token","a".repeat(32),"用户",List.of(new KugouAccount.Cookie("KuGoo","t=old-token&KugooID=123",".kugou.com","/",true,0)));
        try(var service=new KugouLoginService(old.deviceId(),new KugouApi((u,p,h,b,c)->json("{\"status\":1,\"data\":{\"nickname\":\"用户\"}}")))){assertThrows(java.io.IOException.class,()->service.renew(old));}
    }
    @Test void qrTokenIsExchangedAtCurrentWebVersionAndCookiesStayPrivate()throws Exception {
        CookieHandler original=CookieHandler.getDefault();List<String> endpoints=new ArrayList<>();
        try(var service=new KugouLoginService("a".repeat(32),new KugouApi((endpoint,params,headers,body,cookies)->{
            endpoints.add(endpoint);assertNotSame(original,cookies);assertEquals(32,params.get("signature").length());
            if(endpoint.endsWith("/qrcode"))return json("{\"status\":1,\"data\":{\"qrcode\":\"qr-key\",\"qrcode_img\":\"data:image/png;base64,iVBORw0KGgo=\"}}");
            if(endpoint.endsWith("/get_userinfo_qrcode"))return json("{\"status\":1,\"data\":{\"status\":4,\"userinfo\":{\"userid\":123,\"token\":\"one-time-token\",\"nickname\":\"音乐\"}}}");
            assertTrue(endpoint.endsWith("/login_by_token_get"));assertEquals("20000",params.get("clientver"));assertEquals("",body);
            assertFalse(params.toString().contains("one-time-token"));
            HttpCookie cookie=new HttpCookie("KuGoo","t=web-token&KugooID=123");cookie.setDomain(".kugou.com");cookie.setPath("/");cookies.getCookieStore().add(URI.create("https://loginservice.kugou.com/"),cookie);
            return json("{\"status\":1,\"data\":{\"nickname\":\"音乐\"}}");
        }))){
            var qr=service.create();assertEquals("qr-key",qr.key());var result=service.poll(qr.key());
            assertEquals(4,result.status());assertEquals("web-token",result.account().token());assertEquals("123",result.account().userId());
            assertEquals("音乐",result.account().nickname());assertEquals(1,result.account().cookies().size());assertSame(original,CookieHandler.getDefault());assertEquals(3,endpoints.size());
        }
    }
    @Test void waitingAndScannedNeverPersistQrToken()throws Exception {
        for(int state:List.of(1,2,3))try(var service=new KugouLoginService("b".repeat(32),new KugouApi((e,p,h,b,c)->json("{\"status\":1,\"data\":{\"status\":"+state+"}}")))){var poll=service.poll("key");assertEquals(state,poll.status());assertNull(poll.account());}
    }
    @Test void loginEncryptionUsesExactAesKeyIvAndJsonPayload()throws Exception {
        String key="0123456789ABCDEF",token="测试-token";var encrypted=KugouSigning.token(token,key,123456789L);
        String digest=KugouSigning.md5(key);Cipher aes=Cipher.getInstance("AES/CBC/PKCS5Padding");
        aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(digest.getBytes(StandardCharsets.US_ASCII),"AES"),new IvParameterSpec(digest.substring(16).getBytes(StandardCharsets.US_ASCII)));
        assertEquals(token,JsonParser.parseString(new String(aes.doFinal(HexFormat.of().parseHex(encrypted.get("params"))),StandardCharsets.UTF_8)).getAsJsonObject().get("token").getAsString());
        assertEquals(0,encrypted.get("pk").length()%4);assertThrows(IllegalArgumentException.class,()->KugouSigning.token("t","bad",1));
    }
    static JsonObject json(String text){return JsonParser.parseString(text).getAsJsonObject();}
}
