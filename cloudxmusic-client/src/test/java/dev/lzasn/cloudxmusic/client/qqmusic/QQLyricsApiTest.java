package dev.lzasn.cloudxmusic.client.qqmusic;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.Base64;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.json;
import static org.junit.jupiter.api.Assertions.*;

class QQLyricsApiTest {
    @Test void modernApiExplicitlyRequestsTranslationWithoutAccountCookies()throws Exception{
        var api=new QQLyricsApi((body,cookies)->{
            assertTrue(cookies.getCookieStore().getCookies().isEmpty());
            var param=body.getAsJsonObject("req").getAsJsonObject("param");
            assertEquals(1,param.get("trans").getAsInt());assertEquals(0,param.get("crypt").getAsInt());
            assertEquals(0,param.get("qrc").getAsInt());assertEquals("PublicMid",param.get("songMid").getAsString());
            var response=json("{\"code\":0,\"req\":{\"code\":0,\"data\":{}}}");
            var data=response.getAsJsonObject("req").getAsJsonObject("data");
            data.addProperty("lyric",Base64.getEncoder().encodeToString("[00:01.00]hello".getBytes(StandardCharsets.UTF_8)));
            data.addProperty("trans",Base64.getEncoder().encodeToString("[00:01.00]你好".getBytes(StandardCharsets.UTF_8)));
            return response;
        });
        var result=api.load(new dev.lzasn.cloudxmusic.client.omni.UnifiedSong("qq","PublicMid","","",""));
        assertEquals("你好",result.lineAt(1000).translated());
    }
    @Test void translationCannotJumpAcrossUnrelatedLinesOrReuseOneTranslation()throws Exception{
        var root=json("{\"retcode\":0}");root.addProperty("lyric","[00:01.000]one\n[00:01.050]two\n[00:05.000]three");
        root.addProperty("trans","[00:01.020]一\n[00:03.000]不相关");
        var lines=QQLyricsApi.parse(root).lines();assertEquals("一",lines[0].translated());
        assertFalse(lines[1].hasTranslation());assertFalse(lines[2].hasTranslation());
        assertThrows(IOException.class,()->QQLyricsApi.parseModern(json("{\"code\":0,\"req\":{\"code\":24001}}")));
    }
    @Test void alignsTranslationWithSmallTimestampDriftWithoutChangingOriginal() throws Exception {
        var root=json("{\"retcode\":0}");root.addProperty("lyric","[00:01.000]hello\n[00:02.000]world");
        root.addProperty("trans","[00:01.020]你好\n[00:02.010]世界");
        var lyric=QQLyricsApi.parse(root);assertEquals(1000,lyric.lines()[0].timeMs());
        assertEquals("你好",lyric.lines()[0].translated());assertEquals("世界",lyric.lines()[1].translated());
    }
    @Test void optionalTranslationAliasAndBase64TranslationKeepOriginalTimeline()throws Exception{
        var root=json("{\"retcode\":0,\"lyric\":\"[00:01.00]hello\\n[00:02.00]world\",\"trans\":null}");
        String translation="[00:01.00]你好\n[00:02.00]世界";
        root.addProperty("translyric",Base64.getEncoder().encodeToString(translation.getBytes(StandardCharsets.UTF_8)));
        var lyric=QQLyricsApi.parse(root);assertEquals("hello",lyric.lineAt(1000).text());assertEquals("你好",lyric.lineAt(1000).translated());
        assertEquals("",QQTranslationControl.translation(lyric.lineAt(1000),dev.lzasn.cloudxmusic.client.state.LyricMode.ORIGINAL));
        assertEquals("你好",QQTranslationControl.translation(lyric.lineAt(1000),dev.lzasn.cloudxmusic.client.state.LyricMode.BILINGUAL));
        root.remove("translyric");assertFalse(QQLyricsApi.parse(root).lineAt(1000).hasTranslation());
    }
    @Test void plainAndBase64LyricsMergeTranslations()throws Exception{
        String lrc="[00:01.00]甲\n[00:02.50]乙",trans="[00:01.00]translation";
        var plain=json("{\"retcode\":0}");plain.addProperty("lyric",lrc);plain.addProperty("trans",trans);
        var a=QQLyricsApi.parse(plain);assertEquals("甲",a.lineAt(1000).text());assertEquals("translation",a.lineAt(1000).translated());assertEquals(2500,a.lines()[1].timeMs());
        plain.addProperty("lyric",Base64.getEncoder().encodeToString(lrc.getBytes(StandardCharsets.UTF_8)));
        assertArrayEquals(a.lines(),QQLyricsApi.parse(plain).lines());
    }
    @Test void legitimateNoLyricsIsEmptyButFailuresAreExplicit()throws Exception{
        assertTrue(QQLyricsApi.parse(json("{\"retcode\":0,\"lyric\":\"\"}")).isEmpty());
        for(String body:new String[]{"{}","{\"retcode\":-1}","{\"retcode\":0}","{\"retcode\":0,\"lyric\":[]}","{\"retcode\":0,\"lyric\":\"%%%\"}"})
            assertThrows(IOException.class,()->QQLyricsApi.parse(json(body)),body);
    }
    @Test void numericHtmlEntitiesPreserveTimelineAndUnicode()throws Exception{
        var root=json("{\"retcode\":0,\"lyric\":\"&#91;00&#58;01.00&#93;&#x4F60;&#22909;&#10;[00:02.00]next\"}");
        assertEquals("你好",QQLyricsApi.parse(root).lineAt(1000).text());assertEquals(2,QQLyricsApi.parse(root).lines().length);
    }
    @Test void sendsQqHeadersWithoutCookiesAndRejectsHttpErrors()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.atomic.AtomicInteger response=new java.util.concurrent.atomic.AtomicInteger(200);
        server.createContext("/lyrics",x->{
            assertEquals("https://y.qq.com/",x.getRequestHeaders().getFirst("Referer"));assertNull(x.getRequestHeaders().getFirst("Cookie"));
            byte[] bytes="{\"retcode\":0,\"lyric\":\"[00:01.00]test\"}".getBytes(StandardCharsets.UTF_8);
            x.sendResponseHeaders(response.get(),bytes.length);try(var out=x.getResponseBody()){out.write(bytes);}
        });server.start();
        try{URI endpoint=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/lyrics");assertEquals("test",new QQLyricsApi().load(endpoint).lineAt(1000).text());response.set(403);assertThrows(IOException.class,()->new QQLyricsApi().load(endpoint));}
        finally{server.stop(0);}
    }
}
