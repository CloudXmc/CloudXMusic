package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sound.sampled.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static dev.lzasn.cloudxmusic.client.qqmusic.QQAudioPlayerTest.waitFor;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QQAudioDrainTest {
    @ParameterizedTest @ValueSource(strings={"tone16.flac","tone.mp3"})
    void naturalEofWaitsForAllPcmBeforePublishingFinished(String file)throws Exception{
        BufferedDevice device=new BufferedDevice();AtomicInteger finished=new AtomicInteger();CountDownLatch callback=new CountDownLatch(1);
        try(var player=player(device,file)){
            player.playFrom(uri(file),99,0,false,()->true,()->{finished.incrementAndGet();callback.countDown();}).get(1,TimeUnit.SECONDS);
            assertTrue(device.draining.await(1,TimeUnit.SECONDS),"自然 EOF 必须进入声卡 drain");
            assertFalse(callback.await(250,TimeUnit.MILLISECONDS),"不能用 180ms 超时截掉尚未播出的尾音");
            assertEquals(QQPlaybackAudio.Status.PLAYING,player.view().status());assertTrue(device.open);assertTrue(device.running);
            long frames=device.written.get();device.played.set(frames/2);
            assertEquals((frames/2)/device.rate(),player.view().position(),1e-9);
            assertTrue(device.drainThread.startsWith("CloudXMusic-QQ-Decode"),"不能在游戏线程或音频控制线程排空");
            device.release.countDown();assertTrue(callback.await(1,TimeUnit.SECONDS));
            assertEquals(1,finished.get());assertEquals(QQPlaybackAudio.Status.FINISHED,player.view().status());
            assertEquals(frames/device.rate(),player.view().position(),1e-9);
            assertEquals(frames/device.rate(),player.view().duration(),1e-9,"实际 PCM 帧数应覆盖错误的接口时长");
            assertEquals(frames,device.written.get(),"自然结束不得追加代替原曲尾音的合成衰减帧");
            assertEquals(0,device.discarded.get());assertFalse(device.open);
        }finally{device.release.countDown();}
    }

    @Test void stopDuringDrainClosesOutputPromptlyWithoutNaturalCompletion()throws Exception{
        BufferedDevice device=new BufferedDevice();AtomicInteger finished=new AtomicInteger();
        try(var player=player(device,"tone16.flac")){
            player.playFrom(uri("tone16.flac"),1,0,false,()->true,finished::incrementAndGet).get(1,TimeUnit.SECONDS);
            assertTrue(device.draining.await(1,TimeUnit.SECONDS));
            player.stop().get(1,TimeUnit.SECONDS);assertTrue(device.drainExited.await(1,TimeUnit.SECONDS));
            assertFalse(device.open);assertEquals(QQPlaybackAudio.Status.STOPPED,player.view().status());assertEquals(0,finished.get());
        }finally{device.release.countDown();}
    }

    @Test void replacingSongDuringDrainDoesNotWaitForOrCompleteOldSong()throws Exception{
        BufferedDevice old=new BufferedDevice(),replacement=new BufferedDevice();AtomicInteger opens=new AtomicInteger(),oldFinished=new AtomicInteger();
        try(var player=new QQAudioPlayer(f->opens.getAndIncrement()==0?old.line():replacement.line(),(u,s)->getClass().getResourceAsStream("/qq-audio/tone16.flac"))){
            player.playFrom(uri("old.flac"),1,0,false,()->true,oldFinished::incrementAndGet).get(1,TimeUnit.SECONDS);
            assertTrue(old.draining.await(1,TimeUnit.SECONDS));
            player.play(uri("new.flac"),1,()->true).get(1,TimeUnit.SECONDS);
            assertTrue(replacement.draining.await(1,TimeUnit.SECONDS));assertTrue(old.drainExited.await(1,TimeUnit.SECONDS));
            assertFalse(old.open);assertTrue(replacement.open);assertEquals(0,oldFinished.get());
            player.stop().get(1,TimeUnit.SECONDS);
        }finally{old.release.countDown();replacement.release.countDown();}
    }

    @Test void pausingAtEofPreservesQueuedTailAndResumeDrainsIt()throws Exception{
        BufferedDevice device=new BufferedDevice();AtomicInteger finished=new AtomicInteger();CountDownLatch callback=new CountDownLatch(1);
        try(var player=player(device,"tone16.flac")){
            player.playFrom(uri("tone16.flac"),1,0,false,()->true,()->{finished.incrementAndGet();callback.countDown();}).get(1,TimeUnit.SECONDS);
            assertTrue(device.draining.await(1,TimeUnit.SECONDS));player.pause().get(1,TimeUnit.SECONDS);
            assertEquals(QQPlaybackAudio.Status.PAUSED,player.view().status());assertTrue(device.open);assertFalse(device.running);
            assertEquals(0,device.discarded.get());device.release.countDown();assertTrue(device.drainExited.await(1,TimeUnit.SECONDS));
            assertEquals(0,finished.get());assertEquals(QQPlaybackAudio.Status.PAUSED,player.view().status());
            player.resume(()->true).get(1,TimeUnit.SECONDS);assertTrue(callback.await(1,TimeUnit.SECONDS));
            assertEquals(1,finished.get());assertEquals(0,device.discarded.get());assertEquals(1,device.opens.get());
            assertEquals(device.written.get()/device.rate(),player.view().position(),1e-9);
        }finally{device.release.countDown();}
    }

    @Test void failedDrainReportsDeviceErrorInsteadOfAdvancing()throws Exception{
        BufferedDevice device=new BufferedDevice();device.failDrain=true;AtomicInteger finished=new AtomicInteger();
        try(var player=player(device,"tone16.flac")){
            player.playFrom(uri("tone16.flac"),1,0,false,()->true,finished::incrementAndGet).get(1,TimeUnit.SECONDS);
            waitFor(()->player.view().status()==QQPlaybackAudio.Status.ERROR);
            assertEquals(0,finished.get());assertFalse(player.unavailable());assertTrue(player.view().error().contains("声卡"));assertFalse(device.open);
        }finally{device.release.countDown();}
    }

    @Test void roomCompletionAndPollingWaitForHardwareDrainAndFireOnce()throws Exception{
        BufferedDevice device=new BufferedDevice();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger finished=new AtomicInteger();
        var audio=player(device,"tone16.flac");
        try(var controller=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,
                song->new QQPlaybackController.Resolved("https://a.qq.com/tone16.flac",1),()->1)){
            controller.roomMode(true);controller.playRoomTrack(A,0,()->{},finished::incrementAndGet);
            pump(main,()->device.draining.getCount()==0&&!controller.view().loading());
            for(int n=0;n<10;n++)controller.tick();assertEquals(0,finished.get());assertEquals(A,controller.view().song());
            device.release.countDown();pump(main,()->finished.get()==1);
            for(int n=0;n<10;n++)controller.tick();assertEquals(1,finished.get());assertTrue(controller.finished());
        }finally{device.release.countDown();}
    }

    @Test void apiDurationDoesNotStopPcmBeforeDecoderEof()throws Exception{
        BufferedDevice device=new BufferedDevice();device.blockAfterFrames=96_000;
        AtomicInteger finished=new AtomicInteger();
        try(var player=player(device,"long-silence.flac")){
            player.playFrom(uri("long-silence.flac"),1,0,false,()->true,finished::incrementAndGet).get(1,TimeUnit.SECONDS);
            assertTrue(device.writing.await(1,TimeUnit.SECONDS));device.played.set(device.written.get());
            assertTrue(player.view().position()>1);assertEquals(QQPlaybackAudio.Status.PLAYING,player.view().status());
            assertEquals(0,finished.get());assertEquals(1,device.draining.getCount());
            device.releaseWrite.countDown();assertTrue(device.draining.await(1,TimeUnit.SECONDS));device.release.countDown();
            waitFor(()->finished.get()==1);assertEquals(21.2,player.view().duration(),1e-9);assertEquals(21.2,player.view().position(),1e-9);
        }finally{device.releaseWrite.countDown();device.release.countDown();}
    }

    private QQAudioPlayer player(BufferedDevice device,String file){
        return new QQAudioPlayer(f->device.line(),(u,s)->getClass().getResourceAsStream("/qq-audio/"+file));
    }
    private static URI uri(String file){return URI.create("https://a.qq.com/"+file);}

    /** 分开模拟已写入与已消费 PCM；write 成功并不表示扬声器已经播完。 */
    static final class BufferedDevice {
        final AtomicLong written=new AtomicLong(),played=new AtomicLong(),discarded=new AtomicLong();
        final AtomicInteger opens=new AtomicInteger();
        final CountDownLatch draining=new CountDownLatch(1),release=new CountDownLatch(1),drainExited=new CountDownLatch(1);
        final CountDownLatch writing=new CountDownLatch(1),releaseWrite=new CountDownLatch(1);
        volatile boolean open,running,failDrain;volatile long blockAfterFrames=Long.MAX_VALUE;
        volatile AudioFormat format;volatile String drainThread="";
        double rate(){return format.getSampleRate();}
        SourceDataLine line(){return (SourceDataLine)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SourceDataLine.class},(p,m,a)->switch(m.getName()){
            case "open"->{format=(AudioFormat)a[0];opens.incrementAndGet();open=true;yield null;}
            case "start"->{running=true;yield null;}
            case "stop"->{running=false;yield null;}
            case "close"->{open=false;running=false;release.countDown();releaseWrite.countDown();yield null;}
            case "flush"->{discarded.addAndGet(written.get()-played.get());played.set(written.get());yield null;}
            case "drain"->{
                drainThread=Thread.currentThread().getName();draining.countDown();
                try{
                    if(failDrain)throw new IllegalStateException("test drain failure");
                    if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test drain timeout");
                    if(open&&running)played.set(written.get());
                }finally{drainExited.countDown();}
                yield null;
            }
            case "write"->{
                int bytes=(Integer)a[2];written.addAndGet(bytes/format.getFrameSize());
                if(written.get()>blockAfterFrames){writing.countDown();assertTrue(releaseWrite.await(5,TimeUnit.SECONDS));}
                yield bytes;
            }
            case "getFormat"->format;case "getLongFramePosition"->played.get();
            case "getFramePosition"->(int)played.get();case "getBufferSize","available"->8192;
            case "isOpen"->open;case "isRunning","isActive"->running;case "isControlSupported"->false;
            case "getControls"->new Control[0];default->null;
        });}
    }
}
