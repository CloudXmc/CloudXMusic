package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QQPcmTest {
    @Test void convertsSixteenTwentyFourAndThirtyTwoBitWithoutOverflow() {
        assertEquals(32767, QQPcm.to16(8388607, 24));
        assertEquals(-32768, QQPcm.to16(-8388608, 24));
        assertEquals(32767, QQPcm.to16(Integer.MAX_VALUE, 32));
        assertEquals(-32768, QQPcm.to16(Integer.MIN_VALUE, 32));
        assertEquals(-1234, QQPcm.to16(-1234, 16));
        assertEquals(32512, QQPcm.to16(127, 8));
    }
    @Test void gainStartsAtSilenceAndMovesSmoothlyToVolume() {
        QQPcm.Gain gain = new QQPcm.Gain();
        short[] samples = new short[960]; java.util.Arrays.fill(samples, (short)20000);
        byte[] pcm = gain.encode(samples, 0, 480, 2, .5f, 48000);
        assertTrue(Math.abs(value(pcm, 0)) < 30);
        assertEquals(10000, value(pcm, 958), 30);
        for (int i=2;i<960;i+=2) assertTrue(Math.abs(value(pcm,i)-value(pcm,i-2)) <= 30);
    }
    @Test void tailStartsAtLastSampleAndEndsAtZero() {
        byte[] tail = QQPcm.tail(new short[]{12000,-12000}, 2, 480);
        assertEquals(12000,value(tail,0)); assertEquals(-12000,value(tail,1));
        assertEquals(0,value(tail,958)); assertEquals(0,value(tail,959));
    }
    static int value(byte[] b,int index) { return (short)((b[index*2]&255)|(b[index*2+1]<<8)); }
}
