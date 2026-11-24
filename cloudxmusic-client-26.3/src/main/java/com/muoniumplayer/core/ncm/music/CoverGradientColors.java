package com.muoniumplayer.core.ncm.music;

/**
 * CloudXMusic adapter. Album-cover palette extraction is optional in the
 * imported renderer; returning no snapshot keeps the configured text color path.
 */
public final class CoverGradientColors {
    private CoverGradientColors() {
    }

    public static int[] snapshot() {
        return null;
    }

    public static int version() {
        return 0;
    }
}
