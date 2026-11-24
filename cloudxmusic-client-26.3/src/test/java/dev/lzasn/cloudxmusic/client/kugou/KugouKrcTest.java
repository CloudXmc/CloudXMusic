package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class KugouKrcTest {
    @Test void decodesXorZlibAndKeepsAbsoluteUnicodeWordTimes() throws Exception {
        var lyrics=KugouKrc.decode(encode("[offset:50]\n[1000,500]<0,200,0>你好<200,300,0>🌙"));
        assertEquals(1050,lyrics.timed().getFirst().startMs());
        assertEquals("你好🌙",lyrics.timed().getFirst().text());
        assertEquals(1250,lyrics.timed().getFirst().words().get(1).startMs());
        assertEquals("你好🌙",lyrics.lyric().lineAt(1400).text());
    }
    @Test void translationsStayWithRawRowsBeforeBlankLinesAreFiltered() throws Exception {
        String language=Base64.getEncoder().encodeToString("{\"content\":[{\"type\":1,\"lyricContent\":[[\"空\"],[\"翻译\"]]}]}".getBytes(StandardCharsets.UTF_8));
        var lyrics=KugouKrc.parse("[language:"+language+"]\n[0,100]<0,100,0> \n[200,100]<0,100,0>hello");
        assertEquals(1,lyrics.timed().size());assertEquals("翻译",lyrics.lyric().lineAt(200).translated());
    }
    @Test void rejectsBadHeadersCorruptionInflationBombAndOverflow() throws Exception {
        assertThrows(IOException.class,()->KugouKrc.decode("a3JjMQ=="));
        assertThrows(IOException.class,()->KugouKrc.decode(encode("x".repeat(2*1024*1024+1))));
        assertTrue(KugouKrc.parse("[999999999999999999999,2]<0,2,0>x\n[100,500]<0,20,0>ok").timed().size()==1);
    }
    static String encode(String text) throws Exception {
        ByteArrayOutputStream compressed=new ByteArrayOutputStream();
        try(var deflater=new DeflaterOutputStream(compressed)){deflater.write(text.getBytes(StandardCharsets.UTF_8));}
        int[] mask={0x40,0x47,0x61,0x77,0x5e,0x32,0x74,0x47,0x51,0x36,0x31,0x2d,0xce,0xd2,0x6e,0x69};
        byte[] result=new byte[compressed.size()+4];System.arraycopy(new byte[]{'k','r','c','1'},0,result,0,4);
        for(int i=0;i<compressed.size();i++)result[i+4]=(byte)(compressed.toByteArray()[i]^mask[i%16]);
        return Base64.getEncoder().encodeToString(result);
    }
}
