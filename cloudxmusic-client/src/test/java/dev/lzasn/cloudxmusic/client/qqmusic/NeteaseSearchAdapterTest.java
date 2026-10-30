package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.Track;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NeteaseSearchAdapterTest {
    @Test void retainsOriginalTracksWithoutChangingMetadata() throws Exception {
        Track track = new Track("歌名", "歌手", 123, "123456", "专辑", "cover");
        NeteaseSearchAdapter adapter = new NeteaseSearchAdapter((keyword, limit) -> List.of(track));
        List<UnifiedSong> songs = adapter.searchSongs("歌名", 0, 30);
        assertEquals("netease", adapter.sourceId());
        assertEquals("123456", songs.getFirst().platformId());
        assertEquals("专辑", songs.getFirst().album());
        assertSame(track, NeteaseSearchAdapter.originalTracks(songs).getFirst());
        assertThrows(UnsupportedOperationException.class, () -> songs.clear());
        assertThrows(UnsupportedOperationException.class, () -> NeteaseSearchAdapter.originalTracks(songs).clear());
    }

    @Test void propagatesOriginalSearchFailure() {
        NeteaseSearchAdapter adapter = new NeteaseSearchAdapter((keyword, limit) -> { throw new IOException("failed"); });
        assertThrows(IOException.class, () -> adapter.searchSongs("x", 0, 30));
    }

    @Test void cannotConvertQqMetadataToNeteaseTracks() {
        assertThrows(IllegalArgumentException.class, () -> NeteaseSearchAdapter.originalTracks(
                List.of(new UnifiedSong("qq", "Mid1", "title", "artist", "album"))));
    }

    @Test void rejectsUnsupportedPaginationBeforeCallingNetease() {
        NeteaseSearchAdapter adapter = new NeteaseSearchAdapter((keyword, limit) -> { fail("must not send"); return List.of(); });
        assertThrows(IllegalArgumentException.class, () -> adapter.searchSongs("x", 1, 30));
        assertThrows(IllegalArgumentException.class, () -> adapter.searchSongs(" ", 0, 30));
        assertThrows(IllegalArgumentException.class, () -> adapter.searchSongs("x", 0, 101));
    }
}
