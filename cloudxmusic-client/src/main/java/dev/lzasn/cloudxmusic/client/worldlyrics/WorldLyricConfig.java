package dev.lzasn.cloudxmusic.client.worldlyrics;

import com.muoniumplayer.core.settings.HudConfig;
import dev.lzasn.cloudxmusic.client.state.MusicState;

/** Immutable bridge from CloudXMusic settings to the imported renderer. */
public record WorldLyricConfig(
        boolean enabled,
        float distance,
        float scale,
        float spreadDeg,
        boolean stayOnScreen,
        boolean keepDistance,
        int translation,
        boolean glowEnabled,
        float glowStrength,
        int glowMode,
        boolean wordPop,
        boolean cadenza,
        boolean huiguang,
        boolean hudDraw,
        boolean translationEffects,
        boolean splitLines
) {
    public WorldLyricConfig {
        distance = clamp(distance, 3f, 48f);
        scale = clamp(scale, .4f, 6f);
        spreadDeg = clamp(spreadDeg, 0f, 60f);
        translation = Math.max(0, Math.min(2, translation));
        glowStrength = clamp(glowStrength, 0f, 1f);
        glowMode = Math.max(0, Math.min(1, glowMode));
    }

    public static WorldLyricConfig from(MusicState state) {
        return new WorldLyricConfig(
                state.worldLyrics(),
                state.worldLyricDistance(),
                state.worldLyricScale(),
                state.worldLyricSpreadDeg(),
                state.worldLyricStayOnScreen(),
                state.worldLyricKeepDistance(),
                state.worldLyricTranslation(),
                state.worldLyricGlow(),
                state.worldLyricGlowStrength(),
                state.worldLyricGlowMode(),
                state.worldLyricWordPop(),
                state.worldLyricCadenza(),
                state.worldLyricHuiguang(),
                state.worldLyricHudDraw(),
                state.worldLyricTransEffects(),
                state.worldLyricSplitLines());
    }

    public void applyToHud() {
        HudConfig.worldLyricsEnabled = enabled;
        HudConfig.worldLyricsDistance = distance;
        HudConfig.worldLyricsScale = scale;
        HudConfig.worldLyricsSpreadDeg = spreadDeg;
        HudConfig.worldLyricsStayOnScreen = stayOnScreen;
        HudConfig.worldLyricsKeepDistance = keepDistance;
        HudConfig.worldLyricsTranslation = translation;
        HudConfig.worldLyricsGlowEnabled = glowEnabled;
        HudConfig.worldLyricsGlowStrength = glowStrength;
        HudConfig.worldLyricsGlowMode = glowMode;
        HudConfig.worldLyricsWordPop = wordPop;
        HudConfig.worldLyricsCadenza = cadenza;
        HudConfig.worldLyricsHuiguang = huiguang;
        HudConfig.worldLyricsHudDraw = hudDraw;
        HudConfig.worldLyricsTransEffects = translationEffects;
        HudConfig.worldLyricsSplitLines = splitLines;

        HudConfig.worldLyricsModernStyle = true;
        HudConfig.worldLyricsFacePlayer = true;
        HudConfig.worldLyricsKeepAway = true;
        HudConfig.worldLyricsGlowSpread = 1.3f;
        HudConfig.worldLyricsGlowTier = 2;
        HudConfig.worldLyricsGlowColorMode = 1;
        HudConfig.worldLyricsGlowColorRgb = 0xFFFFFF;
        HudConfig.worldLyricsTextFlash = false;
        HudConfig.worldLyricsAboveGround = false;
        HudConfig.worldLyricsCoverColorMode = 0;
        HudConfig.worldLyricsCoverGradient = false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
