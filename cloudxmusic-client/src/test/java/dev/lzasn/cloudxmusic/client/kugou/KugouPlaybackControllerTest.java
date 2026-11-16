package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouPlaybackControllerTest {
    static UnifiedSong song(String suffix){return new UnifiedSong("kugou","a".repeat(31)+suffix,"歌","人","");}
    static final class Audio implements KugouPlaybackAudio {
        View state=new View(Status.STOPPED,0,120,.8f,"");int plays;double start;boolean startPaused;BooleanSupplier permitted;Runnable started=()->{},finished=()->{};
        public CompletableFuture<Void> play(URI u,int d,BooleanSupplier p){return playFrom(u,d,0,false,p,()->{},()->{});}
        public CompletableFuture<Void> playFrom(URI u,int d,double s,boolean paused,BooleanSupplier p,Runnable started,Runnable finished){plays++;start=s;startPaused=paused;permitted=p;this.started=started;this.finished=finished;state=new View(paused?Status.PAUSED:Status.LOADING,s,d,.8f,"");return CompletableFuture.completedFuture(null);}
        void audible(){state=new View(Status.PLAYING,start,120,.8f,"");started.run();}
        public CompletableFuture<Void> pause(){state=new View(Status.PAUSED,state.position(),state.duration(),.8f,"");return CompletableFuture.completedFuture(null);}
        public CompletableFuture<Void> resume(BooleanSupplier p){permitted=p;state=new View(Status.PLAYING,state.position(),state.duration(),.8f,"");return CompletableFuture.completedFuture(null);}
        public CompletableFuture<Void> stop(){state=new View(Status.STOPPED,0,120,.8f,"");return CompletableFuture.completedFuture(null);}
        public void volume(float value){}public View view(){return state;}public void close(){}
    }
    static void pump(Queue<Runnable> main,BooleanSupplier done)throws Exception {long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!done.getAsBoolean()&&System.nanoTime()<end){Runnable r;while((r=main.poll())!=null)r.run();Thread.sleep(5);}assertTrue(done.getAsBoolean());}
    @Test void waitsForBothOtherSourcesAndRejectsStaleResolution()throws Exception {
        var main=new ConcurrentLinkedQueue<Runnable>();Audio audio=new Audio();var barrier=new CompletableFuture<Void>();AtomicLong account=new AtomicLong(1);
        try(var controller=new KugouPlaybackController(audio,()->barrier,main::add,(s,q)->new KugouPlaybackResolver.Link("https://fs.kugou.com/a.mp3",120,KugouQuality.STANDARD),account::get)){
            controller.play(List.of(song("1")),0);Thread.sleep(50);assertEquals(0,audio.plays);
            barrier.complete(null);pump(main,()->audio.plays==1);audio.audible();pump(main,()->controller.view().playing());
            assertEquals(KugouQuality.STANDARD,controller.view().actualQuality());controller.suspend().get(1,TimeUnit.SECONDS);assertFalse(audio.permitted.getAsBoolean());
            controller.toggle();pump(main,()->controller.view().playing());account.incrementAndGet();controller.tick();assertFalse(controller.hasTrack());assertFalse(audio.permitted.getAsBoolean());
        }
    }
    @Test void seekingKeepsTargetUntilActualPcmStartsAndLegacyQualityDoesNotRestartAudio()throws Exception {
        var main=new ConcurrentLinkedQueue<Runnable>();Audio audio=new Audio();
        try(var controller=new KugouPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,(s,q)->new KugouPlaybackResolver.Link("https://fs.kugou.com/a.mp3",120,KugouQuality.STANDARD),()->1)){
            controller.play(List.of(song("1")),0);pump(main,()->audio.plays==1);audio.audible();pump(main,()->controller.view().playing());
            controller.beginSeek(45);assertEquals(45,controller.view().position());controller.seek(45);pump(main,()->audio.plays==2);
            assertTrue(controller.seeking());assertEquals(45,controller.view().position());assertEquals(45,audio.start);
            audio.audible();pump(main,()->!controller.seeking());controller.quality(KugouQuality.HQ);
            assertEquals(2,audio.plays);assertEquals(45,audio.start);assertTrue(controller.view().playing());
            assertEquals(KugouQuality.HIGH,controller.preferredQuality(),"旧档位不应覆盖自动取链策略");
        }
    }
    @Test void sourceRevocationDuringNetworkResolveNeverOpensCard()throws Exception {
        var main=new ConcurrentLinkedQueue<Runnable>();Audio audio=new Audio();AtomicBoolean allowed=new AtomicBoolean(true);CountDownLatch resolving=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var controller=new KugouPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,(s,q)->{resolving.countDown();release.await();return new KugouPlaybackResolver.Link("https://fs.kugou.com/a.mp3",120,KugouQuality.STANDARD);},()->1)){
            controller.sourceGuard(allowed::get);controller.play(List.of(song("1")),0);assertTrue(resolving.await(1,TimeUnit.SECONDS));allowed.set(false);release.countDown();controller.suspend();
            Thread.sleep(30);Runnable r;while((r=main.poll())!=null)r.run();assertEquals(0,audio.plays);
        }finally{release.countDown();}
    }
}
