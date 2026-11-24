package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class QQQrcTest {
    @Test void qrcRequestEnablesCryptAndTranslationAndPlainLrcRemainsFallback()throws Exception{
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var api=new QQLyricsApi((body,cookies)->{
            var p=body.getAsJsonObject("req").getAsJsonObject("param");int n=calls.getAndIncrement();
            assertEquals(n==0?1:0,p.get("qrc").getAsInt());assertEquals(n==0?1:0,p.get("crypt").getAsInt());assertEquals(1,p.get("trans").getAsInt());assertTrue(cookies.getCookieStore().getCookies().isEmpty());
            return QQCatalogApiTest.json(n==0?"{\"code\":0,\"req\":{\"code\":0,\"data\":{\"lyric\":\"broken\"}}}":"{\"code\":0,\"req\":{\"code\":0,\"data\":{\"lyric\":\"[00:01.000]hello\",\"trans\":\"[00:01.000]你好\"}}}");
        });
        var result=api.loadDetailed(new dev.lzasn.cloudxmusic.client.omni.UnifiedSong("qq","PublicMid","","",""));
        assertTrue(result.timed().isEmpty());assertEquals("你好",result.lyric().lineAt(1000).translated());assertEquals(2,calls.get());
        var data=QQCatalogApiTest.json("{\"lyric\":\"[1000,800]你(1000,400)好(1400,400)\",\"trans\":\"[1000,800]hello(1000,800)\"}");
        assertEquals("hello",QQLyricsApi.parseDetailed(data).lyric().lineAt(1000).translated());
    }
    @Test void decryptsRealPublicQrcSampleAndBuildsTimedLines()throws Exception{
        String hex=new String(getClass().getResourceAsStream("/qq-qrc-public.hex").readAllBytes(),StandardCharsets.US_ASCII);
        String xml=QQQrcCodec.plaintext(hex);assertTrue(xml.contains("LyricContent"));
        var lines=QQQrcParser.parse(xml);assertTrue(lines.size()>10);
        assertTrue(lines.stream().anyMatch(line->line.words().size()>3));
    }
    @Test void parsesAbsoluteWordTimingWithoutBreakingSupplementaryCharacters()throws Exception{
        var lines=QQQrcParser.parse("<LyricInfo LyricContent=\"[1000,800]你(1000,400)😀好(1400,400)&#10;[2400,600]다(2400,600)\"/>");
        assertEquals(2,lines.size());assertEquals("你😀好",lines.getFirst().text());
        assertEquals("😀好",lines.getFirst().words().get(1).text());
        assertEquals(1400,lines.getFirst().words().get(1).startMs());
    }
    @Test void supportsPlainZlibAndRejectsInvalidOrOversizedPayload()throws Exception{
        String content="[1000,800]你(1000,400)好(1400,400)";
        var bytes=new ByteArrayOutputStream();try(var zip=new DeflaterOutputStream(bytes)){zip.write(content.getBytes(StandardCharsets.UTF_8));}
        assertEquals(content,QQQrcCodec.plaintext(HexFormat.of().formatHex(bytes.toByteArray())));
        assertThrows(IOException.class,()->QQQrcCodec.plaintext("xyz"));
        assertThrows(IOException.class,()->QQQrcCodec.plaintext("0".repeat(2*1024*1024+2)));
    }
    @Test void clampsInvalidTimingsAndRetainsUntimedTail()throws Exception{
        var line=QQQrcParser.parse("[1000,800]好(1000,400)呀").getFirst();
        assertEquals("好呀",line.text());assertEquals(2,line.words().size());
        assertEquals(1400,line.words().get(1).startMs());
        assertTrue(QQQrcParser.parse("[999999999999,2]x(0,1)").isEmpty());
        assertTrue(QQQrcParser.parse("[00:01.00]普通 LRC").isEmpty());
    }
}
