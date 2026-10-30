package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QQPlaybackCompletionTest {
    @Test void typedUnavailableSongSkipsWithinTheNormalQueueAndKeepsQualityPreference()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();Audio audio=new Audio();
        try(var player=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,s->{
            if(s==A)throw new QQPlaybackApi.BusinessFailure(22006);
            return new QQPlaybackController.Resolved("https://a.qq.com/B.mp3",180,QQQuality.HQ);
        },()->1)){
            player.play(List.of(A,B),0);pump(main,()->audio.played.size()==1&&!player.view().loading());
            assertEquals(B,player.view().song());assertEquals(List.of(A,B),player.queue());assertFalse(player.view().error());
            assertEquals(QQQuality.SQ,player.preferredQuality());assertEquals(QQQuality.HQ,player.view().actualQuality());
        }
    }
    @Test void titleStopsBothSourcesAndRejectsPendingResolveAndHandoff()throws Exception{
        for(String stage:List.of("resolve","handoff")){
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();Audio audio=new Audio();AtomicInteger neteaseStops=new AtomicInteger(),neteaseStarts=new AtomicInteger();
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);CompletableFuture<Void> handoff=new CompletableFuture<>();
            try(var player=new QQPlaybackController(audio,()->stage.equals("handoff")?handoff:CompletableFuture.completedFuture(null),main::add,s->{
                if(stage.equals("resolve")){entered.countDown();for(;;){try{release.await();break;}catch(InterruptedException ignored){}}}
                return new QQPlaybackController.Resolved("https://a.qq.com/A.mp3",180);
            },()->1);
                var lifecycle=new dev.lzasn.cloudxmusic.client.omni.PlaybackLifecycle(()->{player.stop();neteaseStops.incrementAndGet();})){
                player.sourceGuards(lifecycle::allowsPlayback,lifecycle::allowsPlayback);lifecycle.update(true,false);player.play(List.of(A,B),0);
                if(stage.equals("resolve"))assertTrue(entered.await(1,TimeUnit.SECONDS));
                lifecycle.update(true,true);release.countDown();handoff.complete(null);QQCatalogControllerTest.awaitCallback(main);
                while(!main.isEmpty())main.remove().run();player.tick();player.toggle();player.beforeNetease(neteaseStarts::incrementAndGet);
                while(!main.isEmpty())main.remove().run();assertTrue(audio.played.isEmpty());assertEquals(0,neteaseStarts.get());assertEquals(1,neteaseStops.get());
                assertFalse(player.view().playing());assertFalse(player.view().loading());
                lifecycle.update(true,false);assertFalse(player.view().playing());
            }finally{release.countDown();}
        }
    }
    @ParameterizedTest
    @CsvSource({"flac,REPEAT_ONE", "flac,REPEAT_LIST", "flac,SHUFFLE", "mp3,REPEAT_ONE", "mp3,REPEAT_LIST", "mp3,SHUFFLE"})
    void realDecoderEofAdvancesEveryModeWithoutScreenOrPolling(String format,PlaybackMode mode)throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger running=new AtomicInteger(),max=new AtomicInteger();
        List<String> opened=new CopyOnWriteArrayList<>();List<QQQuality> requested=new CopyOnWriteArrayList<>();
        var audio=new QQAudioPlayer(f->new QQAudioPlayerTest.Device(running,max).line(),(u,s)->{
            opened.add(u.getPath());return getClass().getResourceAsStream("/qq-audio/"+(format.equals("flac")?"tone16.flac":"tone.mp3"));
        });
        try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,(song,q)->{
            requested.add(q);return new QQPlaybackController.Resolved("https://a.qq.com/"+song.platformId()+"."+format,1,q);
        },()->1)){
            c.mode(mode);QQQuality preferred=format.equals("flac")?QQQuality.SQ:QQQuality.STANDARD;c.quality(preferred);
            c.play(List.of(A,B),mode==PlaybackMode.REPEAT_LIST?1:0);
            // 仅派发主线程回调，刻意不调用 c.tick()；证明结束事件不依赖界面或轮询。
            pump(main,()->opened.size()>=2);
            String expected=mode==PlaybackMode.REPEAT_LIST||mode==PlaybackMode.REPEAT_ONE?"/A.":"/B.";
            assertEquals(expected+format,opened.get(1));assertEquals(1,max.get());
            assertTrue(requested.stream().allMatch(q->q==preferred));
        }
    }
    static class CallbackAudio extends Audio {
        final List<Runnable> callbacks=new ArrayList<>();
        @Override public CompletableFuture<Void> playFrom(URI u,int duration,double position,boolean paused,BooleanSupplier p,Runnable finished){callbacks.add(finished);return super.playFrom(u,duration,position,paused,p);}
        void finish(int n){v=new View(Status.FINISHED,100,100,.8f,"");callbacks.get(n).run();}
    }
    static QQPlaybackController controller(CallbackAudio a,Queue<Runnable> main,AtomicLong revision){
        return new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,(s,q)->new QQPlaybackController.Resolved("https://a.qq.com/"+s.platformId()+".mp3",100,q),revision::get);
    }
    @Test void duplicateCallbackAndPollingConsumeTheSameCompletionOnlyOnce()throws Exception{
        var a=new CallbackAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var c=controller(a,main,new AtomicLong(1))){
            c.play(List.of(A,B),0);pump(main,()->!c.view().loading());a.finish(0);a.callbacks.getFirst().run();c.tick();
            pump(main,()->a.played.size()==2&&!c.view().loading());c.tick();assertEquals(2,a.played.size());assertEquals(B,c.view().song());
        }
    }
    @Test void completionAfterPauseAndResumeStillBelongsToSameAudioSession()throws Exception{
        var a=new CallbackAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var c=controller(a,main,new AtomicLong(1))){
            c.play(List.of(A,B),0);pump(main,()->!c.view().loading());c.toggle();pump(main,()->c.view().paused());c.toggle();pump(main,()->c.view().playing());
            a.finish(0);pump(main,()->a.played.size()==2&&!c.view().loading());assertEquals(B,c.view().song());
        }
    }
    @Test void staleFinishCannotSkipReplacementOrRestartSuspendedOrLoggedOutSource()throws Exception{
        for(String action:List.of("replace","pause","source","account","stop","error")){
            var a=new CallbackAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong revision=new AtomicLong(1);
            try(var c=controller(a,main,revision)){
                c.play(List.of(A,B),0);pump(main,()->!c.view().loading());Runnable old=a.callbacks.getFirst();int expected=1;
                switch(action){
                    case "replace"->{c.next();pump(main,()->a.played.size()==2&&!c.view().loading());expected=2;}
                    case "pause"->c.toggle();case "source"->c.suspend();case "account"->revision.incrementAndGet();case "stop"->c.stop();
                    case "error"->a.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.ERROR,0,100,.8f,"bad stream");
                }
                old.run();Runnable r;while((r=main.poll())!=null)r.run();c.tick();assertEquals(expected,a.played.size(),action);
            }
        }
    }
    @Test void singletonShuffleReplaysOnlyEntry()throws Exception{
        var a=new CallbackAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var c=controller(a,main,new AtomicLong(1))){
            c.mode(PlaybackMode.SHUFFLE);c.play(List.of(A),0);pump(main,()->!c.view().loading());a.finish(0);
            pump(main,()->a.played.size()==2&&!c.view().loading());assertEquals(List.of("/A.mp3","/A.mp3"),a.played);
        }
    }
    @Test void errorsCancellationAndInputCloseFailureNeverEmitNaturalCompletion()throws Exception{
        for(String variant:List.of("bad","cancel","close")){
            AtomicInteger completed=new AtomicInteger(),running=new AtomicInteger(),max=new AtomicInteger();
            try(var p=new QQAudioPlayer(f->new QQAudioPlayerTest.Device(running,max).line(),(u,s)->{
                if(variant.equals("bad"))return new java.io.ByteArrayInputStream("<html>error</html>".getBytes());
                if(variant.equals("cancel"))return QQAudioPlayerTest.fixture();
                return new java.io.FilterInputStream(getClass().getResourceAsStream("/qq-audio/tone16.flac")){
                    @Override public void close()throws java.io.IOException{super.close();throw new java.io.IOException("close failure");}
                };
            })){
                p.playFrom(URI.create("https://a.qq.com/a.flac"),1,0,false,()->true,completed::incrementAndGet).get(1,TimeUnit.SECONDS);
                if(variant.equals("cancel"))p.stop().get(1,TimeUnit.SECONDS);
                else QQAudioPlayerTest.waitFor(()->p.view().status()==QQPlaybackAudio.Status.ERROR);
                assertEquals(0,completed.get(),variant);
            }
        }
    }
}
