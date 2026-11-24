package com.muoniumplayer.worldlyrics;

import com.muoniumplayer.core.management.FontManager;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.settings.HudConfig;
import com.muoniumplayer.render.skija.SkijaNatives;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.MusicSource;
import dev.lzasn.cloudxmusic.client.omni.PlaybackSnapshot;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import dev.lzasn.cloudxmusic.client.worldlyrics.WorldLyricConfig;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ImageInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuoWorldLyricsPortTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    void bundledSkijaAndFontsAreUsable() {
        assertTrue(SkijaNatives.ensureLoaded(), SkijaNatives::failure);
        assertTrue(FontManager.pf25bold.lineBox() > 0);

        try (Bitmap bitmap = new Bitmap()) {
            assertTrue(bitmap.allocPixels(ImageInfo.makeN32Premul(96, 96)));
            Canvas canvas = new Canvas(bitmap);
            try {
                canvas.clear(0);
                FontManager.pf25bold.drawGlyphsOn(
                        canvas, "云朵 Music", 4, 40, 0xFFFFFFFF, 255, 0f, null);
            } finally {
                canvas.close();
            }
            byte[] pixels = bitmap.readPixels();
            boolean hasInk = false;
            for (int i = 3; i < pixels.length; i += 4) {
                if ((pixels[i] & 0xFF) != 0) {
                    hasInk = true;
                    break;
                }
            }
            assertTrue(hasInk);
        }
    }

    @Test
    void settingsBridgeMapsAllWorldLyricOptions() {
        WorldLyricConfig config = new WorldLyricConfig(
                true, 14f, 1.5f, 45f, true, false, 2,
                true, .6f, 1, true, true, false, true, true, false);
        config.applyToHud();

        assertTrue(HudConfig.worldLyricsEnabled);
        assertEquals(14f, HudConfig.worldLyricsDistance);
        assertEquals(1.5f, HudConfig.worldLyricsScale);
        assertEquals(45f, HudConfig.worldLyricsSpreadDeg);
        assertTrue(HudConfig.worldLyricsStayOnScreen);
        assertFalse(HudConfig.worldLyricsKeepDistance);
        assertEquals(2, HudConfig.worldLyricsTranslation);
        assertEquals(.6f, HudConfig.worldLyricsGlowStrength);
        assertEquals(1, HudConfig.worldLyricsGlowMode);
        assertTrue(HudConfig.worldLyricsWordPop);
        assertTrue(HudConfig.worldLyricsCadenza);
        assertFalse(HudConfig.worldLyricsHuiguang);
        assertTrue(HudConfig.worldLyricsHudDraw);
        assertTrue(HudConfig.worldLyricsTransEffects);
        assertFalse(HudConfig.worldLyricsSplitLines);
    }

    @Test
    void worldLyricsRemainAvailableWhenIslandLyricsAreOff() {
        SongView song = new SongView(
                MusicSource.NETEASE, "123", "Title", "Artist", "Album",
                180, "", false);
        PlaybackSnapshot snapshot = new PlaybackSnapshot(
                MusicSource.NETEASE, song, List.of(song),
                List.of(new Lyric.Line(1000, "第一句", "First line")),
                true, false, 1.5, 180, .5f, "极高", "极高",
                LyricMode.OFF, PlaybackMode.REPEAT_LIST, false, "", false, List.of());
        WorldLyricConfig config = new WorldLyricConfig(
                true, 10.5f, 1f, 30f, false, true, 1,
                true, .35f, 0, false, false, false, false, false, true);

        CloudMusic.update(snapshot, config);

        assertFalse(CloudMusic.lyrics.isEmpty());
        assertTrue(CloudMusic.lyricsMatchCurrentTrack());
    }
}
