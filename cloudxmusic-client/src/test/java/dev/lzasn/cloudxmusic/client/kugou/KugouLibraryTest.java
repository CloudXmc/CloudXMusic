package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouLibraryTest {
    @TempDir Path home;
    private KugouTrack track() {return new KugouTrack(new UnifiedSong("kugou","a".repeat(32),"歌曲","歌手","专辑",true),"123","456",180,"https://imge.kugou.com/cover.jpg");}
    @Test void favoritesRestoreAlbumMetadataVipAndPreferencesWithoutTouchingAccounts() throws Exception {
        Files.createDirectories(home.resolve("qqmusic"));
        Files.writeString(home.resolve("qqmusic/account.json"),"untouched");
        try(var library=KugouLibrary.open(home)) {
            library.toggle(track()); library.preferences(KugouQuality.FLAC,.35f,LyricMode.BILINGUAL);
        }
        try(var restored=KugouLibrary.open(home)) {
            assertEquals("123",restored.favorites().getFirst().albumId());
            assertTrue(restored.favorites().getFirst().song().vip());
            assertEquals(KugouQuality.FLAC,restored.quality());
            assertEquals(.35f,restored.volume()); assertEquals(LyricMode.BILINGUAL,restored.lyricMode());
            assertTrue(restored.contains(track().song()));
        }
        assertEquals("untouched",Files.readString(home.resolve("qqmusic/account.json")));
        assertFalse(Files.exists(home.resolve("kugou/account.json")));
    }
    @Test void favoritesUseHashIdentityAndRejectForeignSources() throws Exception {
        try(var library=KugouLibrary.open(home)) {
            library.toggle(track());
            library.toggle(new KugouTrack(new UnifiedSong("kugou","a".repeat(32),"新标题","歌手","专辑"),"123","",180,""));
            assertTrue(library.favorites().isEmpty());
            assertThrows(IllegalArgumentException.class,()->library.toggle(new KugouTrack(new UnifiedSong("qq","Mid","标题","歌手","专辑"),"","",1,"")));
        }
    }
    @Test void malformedSettingsKeepDefaultsAndNeverDeleteOriginalFile() throws Exception {
        Files.createDirectories(home.resolve("kugou"));
        var file=home.resolve("kugou/library.json");Files.writeString(file,"broken json");
        try(var library=KugouLibrary.open(home)) {assertTrue(library.favorites().isEmpty());assertFalse(library.status().isBlank());}
        assertEquals("broken json",Files.readString(file));
    }
}
