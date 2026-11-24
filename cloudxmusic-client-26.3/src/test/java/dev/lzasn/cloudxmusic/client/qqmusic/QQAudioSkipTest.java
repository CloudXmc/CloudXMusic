package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class QQAudioSkipTest {
    @Test void skipsExactInterleavedFramesIncludingPartialBlock()throws Exception{
        var frames=new AtomicInteger();var first=new AtomicInteger(-1);var opened=new AtomicBoolean();
        QQAudioDecoder.Sink sink=new QQAudioDecoder.Sink(){
            public void open(int rate,int channels,long total){opened.set(true);assertEquals(10,rate);}
            public void write(short[] samples,int n,int channels){if(first.get()<0)first.set(samples[0]);frames.addAndGet(n);}
        };
        var skip=new QQSampleSkip(sink,.5,()->false);skip.open(10,2,10);
        skip.write(new short[]{0,0,1,1,2,2},3,2);assertFalse(opened.get());
        skip.write(new short[]{3,3,4,4,5,5,6,6},4,2);assertTrue(opened.get());assertEquals(5,first.get());assertEquals(2,frames.get());
    }
    @Test void cancelledSkipNeverOutputs()throws Exception{
        var skip=new QQSampleSkip(QQAudioDecoderTest.SINK,100,()->true);
        assertThrows(InterruptedException.class,()->skip.open(44100,2,100));
    }
}
