package dev.lzasn.cloudxmusic.client.omni;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class MusicSourceCoordinatorTest {
    @Test void commitsOnlyAfterBothOutputsAreQuietAndNeverAutoplays(){
        var net=new CompletableFuture<Void>();var qq=new CompletableFuture<Void>();List<MusicSource> committed=new ArrayList<>();
        var c=new MusicSourceCoordinator(MusicSource.NETEASE,s->s==MusicSource.QQ?qq:net,Runnable::run,committed::add);
        c.select(MusicSource.QQ);assertTrue(c.view().switching());assertEquals(MusicSource.NETEASE,c.source());
        assertFalse(c.allowsPlayback(MusicSource.NETEASE));net.complete(null);assertTrue(committed.isEmpty());
        qq.complete(null);assertEquals(List.of(MusicSource.QQ),committed);assertTrue(c.allowsPlayback(MusicSource.QQ));
    }
    @Test void latestChoiceWinsAcrossDelayedHandoffs(){
        Queue<Runnable> main=new ArrayDeque<>();var wait=new CompletableFuture<Void>();AtomicInteger pauses=new AtomicInteger();
        var c=new MusicSourceCoordinator(MusicSource.NETEASE,s->{pauses.incrementAndGet();return wait;},main::add,s->{});
        c.select(MusicSource.QQ);c.select(MusicSource.NETEASE);wait.complete(null);while(!main.isEmpty())main.remove().run();
        assertEquals(MusicSource.NETEASE,c.source());assertFalse(c.allowsPlayback(MusicSource.QQ));assertEquals(4,pauses.get());
    }
    @Test void failedDeviceHandoffNeverGrantsEitherSource(){
        var c=new MusicSourceCoordinator(MusicSource.NETEASE,s->CompletableFuture.failedFuture(new IllegalStateException()),Runnable::run,s->fail());
        c.select(MusicSource.QQ);assertTrue(c.view().error());assertEquals(MusicSource.NETEASE,c.source());
        assertFalse(c.allowsPlayback(MusicSource.NETEASE));assertFalse(c.allowsPlayback(MusicSource.QQ));
    }
    @Test void shutdownInvalidatesPendingCommit(){
        var wait=new CompletableFuture<Void>();var c=new MusicSourceCoordinator(MusicSource.NETEASE,s->wait,Runnable::run,s->fail());
        c.select(MusicSource.QQ);c.close();wait.complete(null);assertEquals(MusicSource.NETEASE,c.source());assertFalse(c.allowsPlayback(MusicSource.NETEASE));
    }
}
