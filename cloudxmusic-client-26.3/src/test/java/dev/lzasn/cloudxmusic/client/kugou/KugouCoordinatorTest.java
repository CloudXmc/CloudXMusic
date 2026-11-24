package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouCoordinatorTest {
    @Test void thirdSourceHandoffWaitsForAllRegisteredOutputs() {
        var kugou=new CompletableFuture<Void>();List<MusicSource> pauses=new ArrayList<>();
        var coordinator=new MusicSourceCoordinator(MusicSource.QQ,s->{pauses.add(s);return CompletableFuture.completedFuture(null);},Runnable::run,s->{});
        coordinator.register(MusicSource.KUGOU,()->kugou);
        coordinator.select(MusicSource.KUGOU);assertTrue(coordinator.view().switching());assertEquals(List.of(MusicSource.NETEASE,MusicSource.QQ),pauses);
        assertFalse(coordinator.allowsPlayback(MusicSource.KUGOU));kugou.complete(null);assertTrue(coordinator.allowsPlayback(MusicSource.KUGOU));
        assertFalse(coordinator.allowsPlayback(MusicSource.QQ));assertFalse(coordinator.allowsPlayback(MusicSource.NETEASE));
        assertFalse(KugouCapabilities.supportsRooms());
    }
    @Test void omittedThirdSourceRegistrationCannotActivateAnUnmanagedOutput() {
        var coordinator=new MusicSourceCoordinator(MusicSource.NETEASE,s->CompletableFuture.completedFuture(null),Runnable::run,s->fail());
        assertThrows(IllegalStateException.class,()->coordinator.select(MusicSource.KUGOU));
        assertEquals(MusicSource.NETEASE,coordinator.source());
    }
}
