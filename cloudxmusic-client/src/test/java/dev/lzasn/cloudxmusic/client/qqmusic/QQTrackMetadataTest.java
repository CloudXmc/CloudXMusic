package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.json;
import static org.junit.jupiter.api.Assertions.*;

class QQTrackMetadataTest {
    @Test void albumMidCoverAndDurationAreReadFromDetails()throws Exception{
        var info=QQTrackMetadata.parseInfo("Song123",json("{\"track_info\":{\"mid\":\"Song123\",\"interval\":234,\"album\":{\"mid\":\"Album789\"},\"file\":{\"media_mid\":\"Media456\"}}}"));
        assertEquals("Album789",info.albumMid());assertEquals(234,info.durationSeconds());assertEquals("https://y.gtimg.cn/music/photo_new/T002R300x300M000Album789.jpg",info.coverUrl());
    }
    @Test void readsActualMediaMidRatherThanSongMid() throws Exception {
        assertEquals("Media456", QQTrackMetadata.parse("Song123", json("{\"track_info\":{\"mid\":\"Song123\",\"file\":{\"media_mid\":\"Media456\"}}}")));
    }
    @Test void missingMediaMidOrMismatchedSongCannotGenerateFilename() {
        for (String value : new String[]{"{}", "{\"track_info\":{\"file\":{}}}",
                "{\"track_info\":{\"mid\":\"Other\",\"file\":{\"media_mid\":\"Media456\"}}}",
                "{\"track_info\":{\"file\":{\"media_mid\":\"../bad\"}}}"})
            assertThrows(IOException.class, () -> QQTrackMetadata.parse("Song123", json(value)));
    }
}
