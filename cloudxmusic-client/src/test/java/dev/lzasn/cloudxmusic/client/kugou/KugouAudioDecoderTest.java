package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouAudioDecoderTest {
    @Test void closingFlacInputClearsBufferedBytesEvenIfUnderlyingCloseFails()throws Exception{
        InputStream source=new ByteArrayInputStream(new byte[]{1,2,3,4}){
            @Override public void close()throws IOException{throw new IOException("test close failure");}
        };
        var input=new KugouAudioDecoder.StreamInput(source);assertEquals(1,input.readUint(8));
        assertThrows(IOException.class,input::close);
        assertThrows(IllegalStateException.class,input::readByte,"关闭后必须拒绝读取，不能继续输出旧 FLAC 缓冲");
        var next=new KugouAudioDecoder.StreamInput(new ByteArrayInputStream(new byte[]{9,10}));
        try(next){assertEquals(9,next.readUint(8));}
    }
    @Test void knownFlacSampleCountFinishesWithoutWaitingForTransportEof()throws Exception{
        try(InputStream bytes=getClass().getResourceAsStream("/qq-audio/tone16.flac")){
            InputStream lingering=new FilterInputStream(bytes){
                @Override public int read(byte[] b,int off,int len)throws IOException{int n=super.read(b,off,len);if(n<0)throw new IOException("transport waits after the final audio sample");return n;}
            };
            KugouAudioDecoder.decode(lingering,true,SINK);
        }
    }
    static final KugouAudioDecoder.Sink SINK = new KugouAudioDecoder.Sink() {
        public void open(int rate,int channels,long frames) {}
        public void write(short[] samples,int frames,int channels) {}
    };
    @Test void rejectsHtmlAndTruncatedFlac() {
        assertThrows(IOException.class,()->KugouAudioDecoder.decode(new ByteArrayInputStream("<html>error</html>".getBytes()), true, SINK));
        assertThrows(IOException.class,()->KugouAudioDecoder.decode(new ByteArrayInputStream(new byte[]{'f','L','a','C'}), true, SINK));
    }
    @Test void rejectsEmptyMp3InsteadOfReportingSuccess() {
        assertThrows(IOException.class,()->KugouAudioDecoder.decode(new ByteArrayInputStream(new byte[0]), false, SINK));
    }
    @Test void decodesRealSixteenBitFlac()throws Exception{assertDecoded("tone16.flac",true,44100,2,11025);}
    @Test void decodesRealTwentyFourBitFortyEightKhzFlac()throws Exception{assertDecoded("tone24.flac",true,48000,2,12000);}
    @Test void decodesRealMp3UsingExistingJlayer()throws Exception{assertDecoded("tone.mp3",false,44100,2,-1);}
    @Test void formalPlayerHasNoTwentySecondTestLimit()throws Exception{assertDecoded("long-silence.flac",true,48000,2,1017600);}
    static void assertDecoded(String file,boolean flac,int rate,int channels,long expected)throws Exception{
        long[] count={0};try(InputStream in=KugouAudioDecoderTest.class.getResourceAsStream("/qq-audio/"+file)){
            assertNotNull(in);KugouAudioDecoder.decode(in,flac,new KugouAudioDecoder.Sink(){
                public void open(int r,int c,long frames){assertEquals(rate,r);assertEquals(channels,c);if(flac)assertEquals(expected,frames);}
                public void write(short[] samples,int frames,int c){count[0]+=frames;assertEquals(channels,c);}
            });
        }
        if(expected>=0)assertEquals(expected,count[0]);else assertTrue(count[0]>=11025);
    }
}
