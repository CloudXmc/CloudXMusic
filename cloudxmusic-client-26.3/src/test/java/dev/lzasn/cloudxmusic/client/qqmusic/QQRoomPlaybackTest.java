package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.room.QQRoomPlaybackBridge;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QQRoomPlaybackTest {
    @Test void roomPermissionFailuresClearLoadingAndEchoesCannotRetry()throws Exception{
        for(Exception denied:List.of(new QQPlaybackAccessException(QQPlaybackAccessException.Reason.LOGIN_REQUIRED),
                new QQPlaybackApi.BusinessFailure(22006),new QQAuthHttp.HttpFailure(403))){
            RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger attempts=new AtomicInteger();AtomicLong clock=new AtomicLong();
            try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->{attempts.incrementAndGet();throw denied;},()->1)){
                var bridge=new QQRoomPlaybackBridge(c,clock::get);bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,1);
                pump(main,()->c.view().error());
                assertFalse(c.view().loading());assertTrue(c.roomUnavailable());
                assertEquals(denied instanceof QQPlaybackAccessException?QQPlaybackController.RoomStatus.LOGIN_REQUIRED:QQPlaybackController.RoomStatus.NO_PERMISSION,c.roomStatus());
                for(int i=0;i<100;i++){clock.addAndGet(TimeUnit.SECONDS.toNanos(1));bridge.follow(A,true,i,PlaybackMode.REPEAT_LIST,1);c.tick();}
                assertEquals(1,attempts.get());assertTrue(a.played.isEmpty());
            }
        }
    }
    @Test void loadingRemainsNetworkStateUntilActualOutputAndDeviceErrorsStayDistinct()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->{entered.countDown();release.await(2,TimeUnit.SECONDS);return new QQPlaybackController.Resolved("https://a.qq.com/A.mp3",100);},()->1)){
            c.roomMode(true);c.playRoomTrack(A,0);assertTrue(entered.await(1,TimeUnit.SECONDS));
            assertEquals(QQPlaybackController.RoomStatus.GETTING_AUDIO,c.roomStatus());
            release.countDown();pump(main,()->c.view().playing()&&!c.view().loading());
            assertEquals(QQPlaybackController.RoomStatus.PLAYING,c.roomStatus());
            a.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.ERROR,0,100,.8f,"声卡失败");
            assertEquals(QQPlaybackController.RoomStatus.ERROR,c.roomStatus());assertFalse(c.roomUnavailable());
        }finally{release.countDown();}
    }
    @Test void audioStartCallbackIsOncePerSessionAndRejectsStaleSameMidAndPausedSessions()throws Exception{
        class StartedAudio extends RoomAudio{
            List<Runnable> starts=new ArrayList<>();
            @Override public CompletableFuture<Void> playFrom(URI u,int duration,double offset,boolean paused,BooleanSupplier allowed,Runnable started,Runnable finished){
                starts.add(started);return super.playFrom(u,duration,offset,paused,allowed,finished);
            }
        }
        var a=new StartedAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger started=new AtomicInteger(),ended=new AtomicInteger();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            c.roomMode(true);c.playRoomTrack(A,0,started::incrementAndGet,ended::incrementAndGet);pump(main,()->!c.view().loading());
            assertEquals(0,started.get());a.starts.getFirst().run();a.starts.getFirst().run();while(!main.isEmpty())main.remove().run();assertEquals(1,started.get());
            c.playRoomTrack(A,0,started::incrementAndGet,ended::incrementAndGet);pump(main,()->a.starts.size()==2&&!c.view().loading());
            a.starts.getFirst().run();while(!main.isEmpty())main.remove().run();assertEquals(1,started.get());
            c.roomPause();a.starts.getLast().run();while(!main.isEmpty())main.remove().run();assertEquals(1,started.get());assertEquals(0,ended.get());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"flac,REPEAT_ONE","flac,REPEAT_LIST","flac,SHUFFLE","mp3,REPEAT_ONE","mp3,REPEAT_LIST","mp3,SHUFFLE"})
    void realRoomDecoderEmitsOneCompletionAndWaitsForAuthoritativeRestart(String format,PlaybackMode mode)throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger opened=new AtomicInteger(),completed=new AtomicInteger(),started=new AtomicInteger(),running=new AtomicInteger(),max=new AtomicInteger();
        var audio=new QQAudioPlayer(f->new QQAudioPlayerTest.Device(running,max).line(),(u,s)->{
            opened.incrementAndGet();return getClass().getResourceAsStream("/qq-audio/"+(format.equals("flac")?"tone16.flac":"tone.mp3"));
        });
        try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/A."+format,1),()->1)){
            c.roomMode(true);c.roomRepeatMode(mode);c.playRoomTrack(A,0,started::incrementAndGet,completed::incrementAndGet);
            pump(main,()->completed.get()==1);for(int i=0;i<30;i++)c.tick();assertEquals(1,opened.get());assertEquals(1,started.get());assertEquals(1,completed.get());
            c.playRoomTrack(A,0,started::incrementAndGet,completed::incrementAndGet);pump(main,()->completed.get()==2);
            assertEquals(2,opened.get());assertEquals(2,started.get());assertEquals(mode,c.mode());assertEquals(1,max.get());
        }
    }
    @Test void singletonLoopRestartsImmediatelyOnNewServerTimelineAfterEof()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong clock=new AtomicLong();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            var bridge=new QQRoomPlaybackBridge(c,clock::get);
            bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,0);pump(main,()->!c.view().loading());
            a.finish(0);while(!main.isEmpty())main.remove().run();clock.set(TimeUnit.MILLISECONDS.toNanos(800));
            bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,1);
            pump(main,()->a.played.size()==2&&!c.view().loading());
            assertEquals(List.of("/A.mp3","/A.mp3"),a.played);
        }
    }
    @Test void explicitServerRestartCanRecoverAnEarlierAudioError()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong clock=new AtomicLong();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            var bridge=new QQRoomPlaybackBridge(c,clock::get);
            bridge.follow(A,true,0,PlaybackMode.REPEAT_ONE,0);pump(main,()->!c.view().loading());
            a.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.ERROR,99,100,.8f,"old failure");
            clock.set(TimeUnit.SECONDS.toNanos(4));bridge.follow(A,true,0,PlaybackMode.REPEAT_ONE,1);
            pump(main,()->a.played.size()==2&&!c.view().loading());assertFalse(c.view().error());
        }
    }
    static class RoomAudio extends QQPlaybackCompletionTest.CallbackAudio {
        double offset;
        int stops;
        @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier allowed){
            offset=position;var future=super.play(uri,duration,allowed);
            v=new View(paused?Status.PAUSED:Status.PLAYING,position,duration,.8f,"");return future;
        }
        @Override public CompletableFuture<Void> playFrom(URI uri,int duration,double position,boolean paused,BooleanSupplier allowed,Runnable finished){
            callbacks.add(finished);return playFrom(uri,duration,position,paused,allowed);
        }
        @Override public CompletableFuture<Void> stop(){stops++;return super.stop();}
    }
    @Test void roomTrackPreservesFullQueueAndStartsAtServerOffsetExactlyOnce()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            c.enqueue(A);c.enqueue(B);c.roomMode(true);c.playRoomTrack(B,42.5);
            pump(main,()->!c.view().loading());
            assertEquals(List.of(A,B),c.queue());assertEquals(B,c.view().song());
            assertEquals(42.5,a.offset);assertEquals(List.of("/B.mp3"),a.played);
        }
    }
    @Test void roomEofNeverAdvancesOrRepeatsInAnyPersonalMode()throws Exception{
        for(var mode:PlaybackMode.values()){
            RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
            try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
                c.mode(mode);c.enqueue(A);c.enqueue(B);c.roomMode(true);c.playRoomTrack(A,0);
                pump(main,()->!c.view().loading());a.finish(0);
                while(!main.isEmpty())main.remove().run();for(int i=0;i<20;i++)c.tick();
                assertEquals(List.of("/A.mp3"),a.played,mode.name());assertEquals(A,c.view().song());
            }
        }
    }
    @Test void unavailableRoomSongWaitsForServerInsteadOfSkipping()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger attempts=new AtomicInteger();
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->{attempts.incrementAndGet();return null;},()->1)){
            c.enqueue(A);c.enqueue(B);c.roomMode(true);c.playRoomTrack(A,0);pump(main,()->c.view().error());
            c.tick();assertEquals(1,attempts.get());assertEquals(A,c.view().song());assertTrue(a.played.isEmpty());
        }
    }
    @Test void identicalServerEchoesDuringLoadingAndPlaybackDoNotRestartAudio()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong clock=new AtomicLong();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            var bridge=new QQRoomPlaybackBridge(c,clock::get);
            for(int i=0;i<100;i++)bridge.follow(A,true,12,PlaybackMode.REPEAT_LIST,0);
            pump(main,()->!c.view().loading());int stops=a.stops;
            for(int i=0;i<100;i++){clock.addAndGet(50_000_000L);bridge.follow(A,true,12,PlaybackMode.REPEAT_LIST,0);}
            assertEquals(List.of("/A.mp3"),a.played);assertEquals(stops,a.stops);assertTrue(c.view().playing());
        }
    }
    @Test void serverPauseCancelsPendingResolveAndLateResultCannotStart()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);
        try(var c=new QQPlaybackController(a,()->CompletableFuture.completedFuture(null),main::add,s->{
            entered.countDown();for(;;){try{release.await();break;}catch(InterruptedException ignored){}}
            returned.countDown();return new QQPlaybackController.Resolved("https://a.qq.com/A.mp3",100);
        },()->1)){
            var bridge=new QQRoomPlaybackBridge(c);
            bridge.follow(A,true,10,PlaybackMode.REPEAT_LIST,0);assertTrue(entered.await(1,TimeUnit.SECONDS));
            bridge.follow(A,false,10,PlaybackMode.REPEAT_LIST,0);release.countDown();assertTrue(returned.await(1,TimeUnit.SECONDS));
            QQCatalogControllerTest.awaitCallback(main);while(!main.isEmpty())main.remove().run();
            assertTrue(a.played.isEmpty());assertFalse(c.view().loading());assertFalse(c.view().playing());
        }finally{release.countDown();}
    }
    @Test void driftCorrectionIsThrottledAndDoesNotChangeTheRoomSong()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong clock=new AtomicLong();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            c.enqueue(A);c.enqueue(B);var bridge=new QQRoomPlaybackBridge(c,clock::get);
            bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,0);pump(main,()->!c.view().loading());
            clock.set(TimeUnit.SECONDS.toNanos(11));bridge.follow(A,true,20,PlaybackMode.REPEAT_LIST,0);
            pump(main,()->a.played.size()==2&&!c.view().loading());assertEquals(20,a.offset);
            for(int i=0;i<50;i++){clock.addAndGet(50_000_000L);bridge.follow(A,true,40,PlaybackMode.REPEAT_LIST,0);}
            assertEquals(2,a.played.size());assertEquals(A,c.view().song());assertEquals(List.of(A,B),c.queue());
        }
    }
    @Test void serverSeekCanRestartSameMidAfterEofButPeriodicEchoCannot()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong clock=new AtomicLong();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            var bridge=new QQRoomPlaybackBridge(c,clock::get);bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,0);
            pump(main,()->!c.view().loading());a.finish(0);while(!main.isEmpty())main.remove().run();
            clock.set(TimeUnit.SECONDS.toNanos(120));bridge.follow(A,true,100,PlaybackMode.REPEAT_LIST,0);
            assertEquals(1,a.played.size());bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,1);
            pump(main,()->a.played.size()==2&&!c.view().loading());assertEquals(0,a.offset);
        }
    }
    @Test void roomModeUsesServerRepeatAndRejectsPersonalTransport()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            c.mode(PlaybackMode.REPEAT_ONE);c.enqueue(A);c.enqueue(B);var bridge=new QQRoomPlaybackBridge(c);
            bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,0);pump(main,()->!c.view().loading());
            c.cycleMode();c.next();c.previous();c.toggle();c.play(List.of(B),0);
            assertEquals(PlaybackMode.REPEAT_LIST,c.mode());assertEquals(List.of(A,B),c.queue());assertEquals(1,a.played.size());
            bridge.follow(A,true,0,PlaybackMode.SHUFFLE,0);assertEquals(PlaybackMode.SHUFFLE,c.mode());
            c.roomMode(false);assertEquals(PlaybackMode.REPEAT_ONE,c.mode());
        }
    }
    @Test void removingCurrentRoomSongWaitsForServerAndKeepsAudioRunning()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            c.enqueue(A);c.enqueue(B);c.roomMode(true);c.playRoomTrack(A,30);pump(main,()->!c.view().loading());
            int stops=a.stops;c.remove(0);assertEquals(List.of(B),c.queue());assertTrue(c.view().playing());assertEquals(stops,a.stops);
            c.clearQueue();assertTrue(c.queue().isEmpty());assertTrue(c.view().playing());
        }
    }
    @Test void repeatedServerSeeksAreCoalescedUntilCooldownExpires()throws Exception{
        RoomAudio a=new RoomAudio();Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong clock=new AtomicLong();
        try(var c=QQPlaybackCompletionTest.controller(a,main,new AtomicLong(1))){
            var bridge=new QQRoomPlaybackBridge(c,clock::get);bridge.follow(A,true,0,PlaybackMode.REPEAT_LIST,0);
            pump(main,()->!c.view().loading());clock.set(TimeUnit.SECONDS.toNanos(4));bridge.follow(A,true,40,PlaybackMode.REPEAT_LIST,1);
            pump(main,()->a.played.size()==2&&!c.view().loading());
            for(int i=0;i<20;i++){clock.addAndGet(50_000_000);bridge.follow(A,true,50,PlaybackMode.REPEAT_LIST,i+2);}
            assertEquals(2,a.played.size());clock.set(TimeUnit.SECONDS.toNanos(8));bridge.follow(A,true,70,PlaybackMode.REPEAT_LIST,22);
            pump(main,()->a.played.size()==3&&!c.view().loading());assertEquals(70,a.offset);
        }
    }
    @Test void realFlacAndMp3EofInRoomNeverReopenTheStream()throws Exception{
        for(String format:List.of("flac","mp3")){
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger running=new AtomicInteger(),max=new AtomicInteger(),opened=new AtomicInteger();
            var audio=new QQAudioPlayer(f->new QQAudioPlayerTest.Device(running,max).line(),(u,s)->{
                opened.incrementAndGet();return getClass().getResourceAsStream("/qq-audio/"+(format.equals("flac")?"tone16.flac":"tone.mp3"));
            });
            try(var c=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,s->
                    new QQPlaybackController.Resolved("https://a.qq.com/"+s.platformId()+"."+format,1),()->1)){
                c.mode(PlaybackMode.REPEAT_ONE);c.enqueue(A);c.enqueue(B);c.roomMode(true);c.playRoomTrack(A,0);
                pump(main,()->c.finished()&&!c.view().loading());while(!main.isEmpty())main.remove().run();
                for(int i=0;i<40;i++)c.tick();assertEquals(1,opened.get(),format);assertEquals(List.of(A,B),c.queue());
            }
        }
    }
}
