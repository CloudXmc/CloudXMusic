package com.muoniumplayer.render.skija;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.DirectContext;

/**
 * CloudXMusic adapter.
 *
 * <p>The imported world-lyrics renderer bakes glyph atlases into standalone Skija
 * bitmaps and uploads them through {@code LyricGlyphAtlasBridge}; it does not use
 * MuoniumPlayer's full-screen Skija GUI host. This small host keeps the shared
 * Skija helpers source-compatible without enabling an unrelated offscreen GUI
 * compositor.</p>
 */
public final class SkijaHost {
    private SkijaHost() {
    }

    public static boolean isAvailable() {
        return true;
    }

    public static boolean inFrame() {
        return false;
    }

    public static Canvas canvas() {
        return null;
    }

    public static DirectContext context() {
        return null;
    }

    public static double guiScale() {
        return 1.0;
    }
}
