package dev.lzasn.cloudxmusic.client.state;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class PlaybackModeTest {
    private final List<Track> queue = List.of(
            new Track("one", "artist", 60, "1", "", ""),
            new Track("two", "artist", 60, "2", "", ""),
            new Track("three", "artist", 60, "3", "", ""));

    @Test void repeatOneKeepsTheCurrentQueueEntry() {
        assertEquals(1, PlaybackMode.REPEAT_ONE.nextIndex(queue, queue.get(1)));
    }

    @Test void repeatListWrapsAtTheEnd() {
        assertEquals(0, PlaybackMode.REPEAT_LIST.nextIndex(queue, queue.get(2)));
    }

    @Test void shuffleSkipsTheCurrentEntry() {
        int next = PlaybackMode.SHUFFLE.nextIndex(queue, queue.get(1), new Random(0));
        assertTrue(next >= 0 && next < queue.size());
        assertNotEquals(1, next);
    }
}
