package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KugouTrackMetadataTest {
    @Test void parsesHashAndAlbumIdentityFromCloudFavoriteNestedMetadata() {
        String hash="abcdef0123456789abcdef0123456789";
        var row=JsonParser.parseString("""
                {"filename":"Papa Tin - Una Mattina.mp3","timelen":177000,
                 "audio_info":{"hash_128":"%s"},
                 "album_info":{"album_id":42,"album_name":"Piano Covers"},
                 "base":{"album_audio_id":73},"privilege":1}
                """.formatted(hash)).getAsJsonObject();

        var track=KugouTrack.parse(row);

        assertEquals(hash.toUpperCase(java.util.Locale.ROOT),track.song().platformId());
        assertEquals("Una Mattina.mp3",track.song().title());
        assertEquals("Papa Tin",track.song().artist());
        assertEquals("42",track.albumId());
        assertEquals("73",track.albumAudioId());
        assertEquals(177,track.durationSeconds());
    }
}
