package dev.lzasn.cloudxmusic.client.audio;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class PcmPlaybackClockTest {
    @Test void neteasePublicPositionUsesHardwareClockInsteadOfDecoderCounter()throws Exception{
        var player=new Mp3Player();var frames=new AtomicLong();var running=new AtomicBoolean(true);
        Class<?> type=Class.forName(Mp3Player.class.getName()+"$Engine");
        var constructor=type.getDeclaredConstructor(long.class);constructor.setAccessible(true);Object engine=constructor.newInstance(1L);
        var clockField=type.getDeclaredField("clock");clockField.setAccessible(true);
        var clock=(PcmPlaybackClock)clockField.get(engine);clock.open(line(frames,running,48000),480000);clock.submitted(48000);
        var field=Mp3Player.class.getDeclaredField("engine");field.setAccessible(true);field.set(player,engine);
        var decoded=Mp3Player.class.getDeclaredField("positionMs");decoded.setAccessible(true);decoded.setLong(player,11999);
        frames.set(12000);assertEquals(10250,player.positionMs());
        running.set(false);Thread.sleep(25);assertEquals(10250,player.positionMs());
        running.set(true);frames.set(24000);assertEquals(10500,player.positionMs());
    }
    static SourceDataLine line(AtomicLong frames,AtomicBoolean running,int rate){return (SourceDataLine)Proxy.newProxyInstance(PcmPlaybackClockTest.class.getClassLoader(),new Class[]{SourceDataLine.class},(p,m,a)->switch(m.getName()){
        case "getLongFramePosition"->frames.get();case "getFormat"->new AudioFormat(rate,16,2,true,false);case "isRunning","isActive","isOpen"->running.get();default->throw new UnsupportedOperationException(m.getName());
    });}
    @Test void subtractsDecoderLeadExactlyOnceAndNeverUsesWallTime()throws Exception{
        var frames=new AtomicLong();var running=new AtomicBoolean(true);var clock=new PcmPlaybackClock();clock.open(line(frames,running,48000),0);clock.submitted(48000);
        frames.set(28800);assertEquals(.6,clock.snapshot().seconds(),1e-9);assertEquals(19200,clock.snapshot().queuedFrames());
        Thread.sleep(20);assertEquals(.6,clock.snapshot().seconds(),1e-9,"网络断流/硬件帧未前进时不能按墙钟虚走");
        frames.set(48000);assertEquals(1,clock.snapshot().seconds(),1e-9);
    }
    @Test void freezeReopenAndSeekHaveIndependentEpochsAndDoNotCountFadeTail(){
        var frames=new AtomicLong(500);var running=new AtomicBoolean(true);var clock=new PcmPlaybackClock();clock.open(line(frames,running,44100),441000);clock.submitted(44100);
        frames.set(44600);assertEquals(11,clock.snapshot().seconds(),1e-9);frames.addAndGet(441);assertEquals(11,clock.snapshot().seconds(),1e-9,"人工淡出尾音不属于歌曲时间");
        long epoch=clock.snapshot().epoch();clock.freeze();frames.addAndGet(44100);assertEquals(11,clock.snapshot().seconds(),1e-9);assertFalse(clock.snapshot().running());
        frames.set(0);clock.open(line(frames,running,44100),220500);clock.submitted(22050);frames.set(22050);
        assertEquals(5.5,clock.snapshot().seconds(),1e-9);assertTrue(clock.snapshot().epoch()>epoch);
    }
    @Test void longTrackUsesIntegerFramesWithoutPerMp3FrameRounding(){
        var frames=new AtomicLong();var clock=new PcmPlaybackClock();clock.open(line(frames,new AtomicBoolean(true),44100),0);
        for(int i=0;i<100000;i++)clock.submitted(1152);
        frames.set(115200000);assertEquals(115200000/44100.0,clock.snapshot().seconds(),1e-9);
    }
}
