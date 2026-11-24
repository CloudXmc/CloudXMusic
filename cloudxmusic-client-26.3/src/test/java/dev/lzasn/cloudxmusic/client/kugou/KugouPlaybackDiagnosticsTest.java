package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouPlaybackDiagnosticsTest {
    @Test void logsActualEndpointParametersAndPayloadWithoutQueryHeaderOrCookieSecrets(){
        var params=Map.of("hash","ABCDEF","quality","320","token","request-token","userid","987654321","signature","request-signature","mid","private-mid");
        var headers=Map.of("User-Agent",List.of("Mozilla/5.0"),"Referer",List.of("https://www.kugou.com/"),
                "dfid",List.of("private-dfid"),"Cookie",List.of("kg_dfid=cookie-device; KuGoo=token%3Dcookie-token"));
        String text=KugouPlaybackDiagnostics.request("https://gateway.kugou.com/v5/url",params,headers,null);
        var json=JsonParser.parseString(text).getAsJsonObject();
        assertEquals("GET",json.get("method").getAsString());assertTrue(json.get("payload").isJsonNull());
        assertTrue(json.get("url").getAsString().contains("https://gateway.kugou.com/v5/url?"));
        assertTrue(text.contains("quality=320"));assertTrue(text.contains("kg_dfid"));
        assertEquals("https://www.kugou.com/",json.getAsJsonObject("headers").get("Referer").getAsString());
        for(String secret:List.of("request-token","987654321","request-signature","private-mid","private-dfid","cookie-device","cookie-token"))assertFalse(text.contains(secret),text);
        String raw="{\"status\":2,\"error_msg\":\"denied request-token 987654321 private-dfid cookie-device\"}";
        String response=KugouPlaybackDiagnostics.response(raw.getBytes(StandardCharsets.UTF_8),params,headers);
        for(String secret:List.of("request-token","987654321","private-dfid","cookie-device"))assertFalse(response.contains(secret),response);
        assertTrue(response.contains("denied"));
    }

    @Test void nonJsonResponseNeverDumpsHtmlOrCredentials(){
        String result=KugouPlaybackDiagnostics.response("<html>token=private</html>".getBytes(StandardCharsets.UTF_8),Map.of(),Map.of());
        assertTrue(result.contains("non_json_response"));assertFalse(result.contains("private"));
    }

    @Test void cookieFlagsDoNotEraseStatusClientIdsAndPermissionEvidence(){
        var headers=Map.of("Cookie",List.of("t=1; a_id=1014; kg_dfid=private-device"));
        var raw="{\"status\":1,\"appid\":1014,\"need_vip\":1,\"auth_through\":[],\"error_code\":20018,\"debug\":\"private-device\"}";
        var json=JsonParser.parseString(KugouPlaybackDiagnostics.response(raw.getBytes(StandardCharsets.UTF_8),Map.of(),headers)).getAsJsonObject();
        assertEquals(1,json.get("status").getAsInt());assertEquals(1014,json.get("appid").getAsInt());
        assertEquals(1,json.get("need_vip").getAsInt());assertFalse(json.toString().contains("private-device"));
    }
    @Test void retainsNestedServerEvidenceAndErrorMessagesInsteadOfDroppingUnknownFields(){
        var response=KugouLoginServiceTest.json("""
                {"status":2,"error_code":20018,"error_msg":"账号权限校验失败", "data":{
                  "supported_quality":["128","320","flac"],"diagnostic":{"quality":4,"available":false},
                  "need_vip_type":"vip","fileSize":28000000}}
                """);
        var safe=JsonParser.parseString(KugouPlaybackDiagnostics.snapshot(response)).getAsJsonObject();
        assertEquals("账号权限校验失败",safe.get("error_msg").getAsString());
        assertEquals("flac",safe.getAsJsonObject("data").getAsJsonArray("supported_quality").get(2).getAsString());
        assertEquals(4,safe.getAsJsonObject("data").getAsJsonObject("diagnostic").get("quality").getAsInt());
    }

    @Test void recursivelyMasksCredentialsEvenWhenEchoedInErrorTextAndArrays(){
        var response=KugouLoginServiceTest.json("""
                {"token":"private-token","userid":987654321,"data":{"nested":[
                 {"refresh_token":"private-refresh","dfid":"private-device","signature":"private-signature"}],
                 "message":"denied private-token 987654321 private-refresh private-device private-signature",
                 "extra_link":"https://fs.kugou.com/song?key=private-cdn"}}
                """);
        String safe=KugouPlaybackDiagnostics.snapshot(response);
        for(String secret:new String[]{"private-token","987654321","private-refresh","private-device","private-signature","private-cdn"})
            assertFalse(safe.contains(secret),safe);
        assertTrue(safe.contains("nested"));assertTrue(safe.contains("denied"));
        assertDoesNotThrow(()->JsonParser.parseString(safe));
    }

    @Test void boundsLargeResponseAndKeepsValidJson(){
        var root=new com.google.gson.JsonObject();
        for(int i=0;i<1000;i++)root.addProperty("server_field_"+i,"x".repeat(2000));
        String safe=KugouPlaybackDiagnostics.snapshot(root);
        assertTrue(safe.length()<=16384,"response log must be bounded");
        assertTrue(safe.contains("truncated"));assertDoesNotThrow(()->JsonParser.parseString(safe));
    }
    @Test void diagnosticJsonRetainsServerCodesAndFormatsWithoutCredentialsOrSignedUrls(){
        var response=KugouLoginServiceTest.json("{\"status\":1,\"token\":\"my-secret\",\"userid\":123456,\"data\":{\"error_code\":20018,\"timelength\":240000,\"bitrate\":96,\"extName\":\"ogg\",\"play_url\":\"https://webfs.kugou.com/a.ogg?signature=secret-key\",\"message\":\"my-secret\"}}");
        String text=KugouPlaybackDiagnostics.snapshot(response);
        assertTrue(text.contains("20018"));assertTrue(text.contains("240000"));assertTrue(text.contains("ogg"));
        assertFalse(text.contains("my-secret"));assertFalse(text.contains("secret-key"));assertFalse(text.contains("123456"));assertFalse(text.contains("https://"));
        assertTrue(text.length()<=4096);
    }
    @Test void apiHttpStatusAndMalformedJsonStayDiagnosable()throws Exception{
        assertTrue(assertThrows(IOException.class,()->KugouHttp.parseResponse(403,new byte[0])).getMessage().contains("403"));
        assertTrue(assertThrows(IOException.class,()->KugouHttp.parseResponse(200,"<html>private-url</html>".getBytes())).getMessage().contains("JSON"));
        assertEquals(1,KugouHttp.parseResponse(200,"{\"status\":1}".getBytes()).get("status").getAsInt());
    }
}
