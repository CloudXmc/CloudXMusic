package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;

class QQAudioPlayerTest {
    @Test void returningToTitleClosesDecodedAudioAndReenteringWorldDoesNotResumeIt()throws Exception{
        AtomicInteger running=new AtomicInteger(),maximum=new AtomicInteger();Device device=new Device(running,maximum);
        AtomicReference<CompletableFuture<Void>> stopped=new AtomicReference<>();
        try(var player=new QQAudioPlayer(f->device.line(),(uri,session)->fixture());
            var lifecycle=new dev.lzasn.cloudxmusic.client.omni.PlaybackLifecycle(()->stopped.set(player.stop()))){
            lifecycle.update(true,false);player.play(URI.create("https://a.qq.com/test.flac"),22,lifecycle::allowsPlayback).get(1,TimeUnit.SECONDS);
            waitFor(()->device.running&&device.frames>1000);lifecycle.update(false,true);
            stopped.get().get(1,TimeUnit.SECONDS);assertFalse(device.open);assertFalse(device.running);assertEquals(0,running.get());
            assertEquals(QQPlaybackAudio.Status.STOPPED,player.view().status());
            lifecycle.joined();lifecycle.update(true,false);assertEquals(QQPlaybackAudio.Status.STOPPED,player.view().status());
        }
    }
    @Test void exactSampleOffsetIsIncludedInClockAndPausedStartDoesNotOpenOutput()throws Exception{
        AtomicInteger running=new AtomicInteger(),max=new AtomicInteger(),opens=new AtomicInteger();Device d=new Device(running,max);
        try(var p=new QQAudioPlayer(f->{opens.incrementAndGet();return d.line();},(u,s)->fixture())){
            p.playFrom(URI.create("https://a.qq.com/a.flac"),22,5.25,true,()->true).get(1,TimeUnit.SECONDS);
            Thread.sleep(30);assertEquals(0,opens.get());assertEquals(5.25,p.view().position(),.0001);
            p.resume(()->true).get(1,TimeUnit.SECONDS);waitFor(()->d.running&&d.frames>1000);assertTrue(p.view().position()>=5.25);assertEquals(1,max.get());
            p.stop().get(1,TimeUnit.SECONDS);
        }
    }
    static class Device {
        volatile boolean open,running,failClose,failStop;volatile long frames;AudioFormat format;
        final AtomicInteger runningCount,maximum;
        Device(AtomicInteger count,AtomicInteger max){runningCount=count;maximum=max;}
        SourceDataLine line(){return (SourceDataLine)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SourceDataLine.class},(p,m,a)->{
            switch(m.getName()){
                case "open" -> {format=(AudioFormat)a[0];frames=0;open=true;return null;}
                case "start" -> {if(!running){running=true;maximum.accumulateAndGet(runningCount.incrementAndGet(),Math::max);}return null;}
                case "stop" -> {if(failStop)throw new IllegalStateException("test device stop failure");if(running){running=false;runningCount.decrementAndGet();}return null;}
                case "close" -> {if(failClose)throw new IllegalStateException("test device close failure");open=false;return null;}
                case "flush","drain" -> {return null;}
                case "isOpen" -> {return open;}case "isRunning","isActive" -> {return running;}
                case "write" -> {int len=(Integer)a[2];if(!open)throw new IllegalStateException("closed");LockSupport.parkNanos(1_000_000);frames+=len/format.getFrameSize();return len;}
                case "getLongFramePosition" -> {return frames;}case "getFramePosition" -> {return (int)frames;}
                case "getFormat" -> {return format;}case "available","getBufferSize" -> {return 8192;}
                case "isControlSupported" -> {return false;}case "getControls" -> {return new Control[0];}
                default -> {if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;if(m.getReturnType()==long.class)return 0L;if(m.getReturnType()==float.class)return 0f;return null;}
            }
        });}
    }
    static void waitFor(java.util.function.BooleanSupplier done)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!done.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(3);assertTrue(done.getAsBoolean());}
    static InputStream fixture(){return QQAudioPlayerTest.class.getResourceAsStream("/qq-audio/long-silence.flac");}
    @Test void pauseAcknowledgesStoppedLineAndResumeKeepsProgress()throws Exception{
        AtomicInteger running=new AtomicInteger(),max=new AtomicInteger();Device d=new Device(running,max);
        try(var p=new QQAudioPlayer(f->d.line(),(u,s)->fixture())){
            p.play(URI.create("https://a.qq.com/F000.flac"),22,()->true).get(1,TimeUnit.SECONDS);waitFor(()->p.view().status()==QQPlaybackAudio.Status.PLAYING&&d.frames>1000);
            p.pause().get(1,TimeUnit.SECONDS);assertFalse(d.running);assertFalse(d.open);double position=p.view().position();Thread.sleep(70);assertEquals(position,p.view().position(),.05);
            p.resume(()->true).get(1,TimeUnit.SECONDS);waitFor(()->p.view().position()>position+.01);p.stop().get(1,TimeUnit.SECONDS);assertFalse(d.open);assertEquals(1,max.get());
        }
    }
    @Test void rapidReplacementClosesOldDeviceBeforeNewStarts()throws Exception{
        AtomicInteger running=new AtomicInteger(),max=new AtomicInteger();List<Device> devices=new CopyOnWriteArrayList<>();
        try(var p=new QQAudioPlayer(f->{Device d=new Device(running,max);devices.add(d);return d.line();},(u,s)->fixture())){
            p.play(URI.create("https://a.qq.com/first.flac"),22,()->true).get(1,TimeUnit.SECONDS);waitFor(()->devices.size()==1&&devices.get(0).running);
            p.play(URI.create("https://a.qq.com/second.flac"),22,()->true).get(1,TimeUnit.SECONDS);waitFor(()->devices.size()==2&&devices.get(1).running);
            assertFalse(devices.get(0).open);assertEquals(1,max.get());p.stop().get(1,TimeUnit.SECONDS);
        }
    }
    @Test void missingOutputPermissionNeverOpensDevice()throws Exception{
        AtomicInteger opens=new AtomicInteger();AtomicBoolean permit=new AtomicBoolean(false);
        try(var p=new QQAudioPlayer(f->{opens.incrementAndGet();throw new LineUnavailableException();},(u,s)->fixture())){
            p.play(URI.create("https://a.qq.com/a.flac"),22,permit::get).get(1,TimeUnit.SECONDS);Thread.sleep(60);assertEquals(0,opens.get());p.stop().get(1,TimeUnit.SECONDS);
        }
    }
    @Test void failedCloseCannotStartReplacement()throws Exception{
        AtomicInteger running=new AtomicInteger(),max=new AtomicInteger(),opens=new AtomicInteger();Device d=new Device(running,max);
        try(var p=new QQAudioPlayer(f->{opens.incrementAndGet();return d.line();},(u,s)->fixture())){
            p.play(URI.create("https://a.qq.com/a.flac"),22,()->true).get(1,TimeUnit.SECONDS);waitFor(()->d.running);d.failClose=true;
            assertThrows(ExecutionException.class,()->p.play(URI.create("https://a.qq.com/b.flac"),22,()->true).get(1,TimeUnit.SECONDS));assertEquals(1,opens.get());d.failClose=false;p.stop().get(1,TimeUnit.SECONDS);
        }
    }
    @Test void badAudioReportsErrorAndClosesDevice()throws Exception{
        AtomicInteger opens=new AtomicInteger();try(var p=new QQAudioPlayer(f->{opens.incrementAndGet();throw new LineUnavailableException();},(u,s)->new ByteArrayInputStream("<html>expired</html>".getBytes()))){
            p.play(URI.create("https://a.qq.com/a.flac"),0,()->true).get(1,TimeUnit.SECONDS);waitFor(()->p.view().status()==QQPlaybackAudio.Status.ERROR);assertEquals(0,opens.get());assertTrue(p.view().error().contains("解码"));
        }
    }
    @Test void unsupportedOutputIsReportedAsSoundDeviceError()throws Exception{
        try(var p=new QQAudioPlayer(f->{throw new LineUnavailableException("test device unavailable");},(u,s)->fixture())){
            p.play(URI.create("https://a.qq.com/a.flac"),22,()->true).get(1,TimeUnit.SECONDS);
            waitFor(()->p.view().status()==QQPlaybackAudio.Status.ERROR);assertTrue(p.view().error().contains("声卡"));
        }
    }
    @Test void cancelledSessionWithFailedStopCannotPretendItIsSilent()throws Exception{
        AtomicInteger running=new AtomicInteger(),max=new AtomicInteger();Device d=new Device(running,max);
        try(var p=new QQAudioPlayer(f->d.line(),(u,s)->fixture())){
            p.play(URI.create("https://a.qq.com/a.flac"),22,()->true).get(1,TimeUnit.SECONDS);waitFor(()->d.running);d.failStop=true;
            assertThrows(ExecutionException.class,()->p.stop().get(1,TimeUnit.SECONDS));
            assertThrows(ExecutionException.class,()->p.pause().get(1,TimeUnit.SECONDS));d.failStop=false;p.stop().get(1,TimeUnit.SECONDS);
        }
    }
    @Test void naturalEofIsNotChangedBackToPlayingByClockOrPause()throws Exception{
        AtomicInteger running=new AtomicInteger(),max=new AtomicInteger();Device d=new Device(running,max);
        try(var p=new QQAudioPlayer(f->d.line(),(u,s)->QQAudioPlayerTest.class.getResourceAsStream("/qq-audio/tone16.flac"))){
            p.play(URI.create("https://a.qq.com/a.flac"),1,()->true).get(1,TimeUnit.SECONDS);waitFor(()->p.view().status()==QQPlaybackAudio.Status.FINISHED);
            Thread.sleep(100);assertEquals(QQPlaybackAudio.Status.FINISHED,p.view().status());p.pause().get(1,TimeUnit.SECONDS);assertEquals(QQPlaybackAudio.Status.FINISHED,p.view().status());
        }
    }
}
