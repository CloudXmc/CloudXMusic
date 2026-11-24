package com.muoniumplayer.core.settings;

/**
 * CloudXMusic adapter for the subset of MuoniumPlayer's HUD settings consumed by
 * the world-lyrics implementation.
 */
public final class HudConfig {
    public static boolean worldLyricsEnabled = false;
    public static float worldLyricsDistance = 10.5f;
    public static boolean worldLyricsFacePlayer = true;
    public static boolean worldLyricsKeepAway = true;
    public static float worldLyricsSpreadDeg = 30.0f;
    public static float worldLyricsScale = 1.0f;
    public static boolean worldLyricsModernStyle = true;
    public static boolean worldLyricsSplitLines = true;
    public static boolean worldLyricsGlowEnabled = true;
    public static float worldLyricsGlowStrength = 0.35f;
    public static float worldLyricsGlowSpread = 1.3f;
    public static int worldLyricsGlowColorRgb = 0xFFFFFF;
    public static int worldLyricsGlowColorMode = 1;
    public static int worldLyricsColorRgb = 0xFFFFFF;
    public static boolean worldLyricsColorCustom = false;
    public static boolean worldLyricsWordPop = false;
    public static boolean worldLyricsTextFlash = false;
    public static boolean worldLyricsCadenza = false;
    public static boolean worldLyricsHuiguang = false;
    public static String worldLyricsKeywordColors = "";
    public static int worldLyricsQualityTier = 0;
    public static boolean worldLyricsStayOnScreen = false;
    public static boolean worldLyricsKeepDistance = true;
    public static boolean worldLyricsAboveGround = false;
    public static int worldLyricsTranslation = 1;
    public static boolean worldLyricsTransEffects = false;
    public static int worldLyricsGlowTier = 2;
    public static int worldLyricsGlowMode = 0;
    public static int worldLyricsCoverColorMode = 0;
    public static boolean worldLyricsCoverGradient = false;
    public static int worldLyricsCoverPaletteRgb0 = 0xF2A7C3;
    public static int worldLyricsCoverPaletteRgb1 = 0xA7C8F2;
    public static int worldLyricsCoverPaletteRgb2 = 0xC3F2A7;
    public static int worldLyricsCoverPaletteRgb3 = 0xF2E3A7;
    public static boolean worldLyricsHudDraw = false;

    private HudConfig() {
    }
}
