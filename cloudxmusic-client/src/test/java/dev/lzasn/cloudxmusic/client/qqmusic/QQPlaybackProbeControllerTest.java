package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQAccountManagerTest.await;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackResolverTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QQPlaybackProbeControllerTest {
    @Test void requestsRunOffCallerThreadAndOnlyExplicitSuccessLogsUrl() throws Exception {
        Thread caller=Thread.currentThread(); AtomicReference<String> logged=new AtomicReference<>();
        try(var probe=new QQPlaybackProbeController(Runnable::run,()->1,(s,u)->logged.set(u),s->{
            assertNotEquals(caller,Thread.currentThread());return HOST+"F000Media456.flac?vkey=opaque";
        })){
            assertTrue(probe.start(SONG));await(()->!probe.view().loading());
            assertFalse(probe.view().error());assertTrue(probe.view().status().contains("SQ FLAC"));assertNotNull(logged.get());
        }
    }
    @Test void canceledQueuedCompletionCannotLogOrUpdate() throws Exception {
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger logged=new AtomicInteger();
        try(var probe=new QQPlaybackProbeController(main::add,()->1,(s,u)->logged.incrementAndGet(),s->HOST+"F000Media456.flac")){
            probe.start(SONG);await(()->!main.isEmpty());probe.cancel();main.remove().run();
            assertEquals(0,logged.get());assertTrue(probe.view().status().isEmpty());
        }
    }
    @Test void logoutAndSwitchInvalidateReturnedUrls() throws Exception {
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong revision=new AtomicLong(1);AtomicInteger logged=new AtomicInteger();
        try(var probe=new QQPlaybackProbeController(main::add,revision::get,(s,u)->logged.incrementAndGet(),s->HOST+"F000Media456.flac")){
            probe.start(SONG);await(()->!main.isEmpty());revision.incrementAndGet();main.remove().run();
            assertEquals(0,logged.get());assertTrue(probe.view().error());assertFalse(probe.view().loading());
        }
    }
    @Test void busyKeyRepeatsDoNotSpawnDuplicateRequests() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        try(var probe=new QQPlaybackProbeController(Runnable::run,()->1,(s,u)->{},s->{calls.incrementAndGet();entered.countDown();release.await();return HOST+"M800Media456.mp3";})){
            assertTrue(probe.start(SONG));assertTrue(entered.await(3,TimeUnit.SECONDS));assertFalse(probe.start(SONG));
            release.countDown();await(()->!probe.view().loading());assertEquals(1,calls.get());assertTrue(probe.view().status().contains("HQ 320k"));
        }finally{release.countDown();}
    }
    @Test void failureAndNoResourceAreVisibleAndNeverLog() throws Exception {
        for(boolean failure:new boolean[]{false,true}){
            try(var probe=new QQPlaybackProbeController(Runnable::run,()->1,(s,u)->fail("must not log"),s->{if(failure)throw new java.io.IOException("离线");return null;})){
                probe.start(SONG);await(()->!probe.view().loading());assertTrue(probe.view().error());
            }
        }
    }
    @Test void emptyListAndCloseDoNotRequestAnything() {
        var probe=new QQPlaybackProbeController(Runnable::run,()->1,(s,u)->fail("no log"),s->fail("no request"));
        assertFalse(probe.start(null));assertTrue(probe.view().error());probe.close();probe.close();assertFalse(probe.start(SONG));
    }
}
