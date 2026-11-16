package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.net.CookieManager;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouApiPlaybackTest {
    @Test void appPlaybackCarriesAccountCredentialsAndDfidHeaderWithMatchingSignature() throws Exception {
        var cookie=new KugouAccount.Cookie("dfid","real-dfid",".kugou.com","/",true,0);
        var account=new KugouAccount("123456","account-token","a".repeat(32),"会员",List.of(cookie));
        var api=new KugouApi((endpoint,params,headers,body,cookies)->{
            assertTrue(endpoint.endsWith("/v5/url"));
            assertEquals("account-token",params.get("token"));
            assertEquals("123456",params.get("userid"));
            assertEquals("real-dfid",params.get("dfid"));
            assertEquals("real-dfid",headers.get("dfid"));
            assertEquals("trackercdn.kugou.com",headers.get("x-router"));
            assertTrue(Set.of("320","128").contains(params.get("quality")));
            var signed=new LinkedHashMap<>(params);String signature=signed.remove("signature");
            assertEquals(KugouSigning.signature(signed,false),signature);
            assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(value->value.getName().equals("dfid")&&value.getValue().equals("real-dfid")));
            return KugouLoginServiceTest.json("{\"status\":2,\"error_code\":20018}");
        });

        for(KugouQuality quality:List.of(KugouQuality.HQ,KugouQuality.STANDARD)){
            JsonObject response=api.quality(KugouPlaybackResolverTest.track(),account,quality,true);
            assertEquals(20018,KugouApi.code(response));
        }
    }
}
