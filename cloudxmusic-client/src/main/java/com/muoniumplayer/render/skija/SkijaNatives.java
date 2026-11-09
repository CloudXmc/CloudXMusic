package com.muoniumplayer.render.skija;

/** CloudXMusic adapter for loading the Skija native library bundled in the mod jar. */
public final class SkijaNatives {
    private static Boolean loaded;
    private static String failure;

    private SkijaNatives() {
    }

    public static synchronized boolean ensureLoaded() {
        if (loaded != null) {
            return loaded;
        }
        try {
            io.github.humbleui.skija.impl.Library.load();
            loaded = Boolean.TRUE;
            failure = null;
        } catch (Throwable t) {
            loaded = Boolean.FALSE;
            failure = t.toString();
            System.err.println("[CloudXMusic/WorldLyrics] Skija native load failed: " + failure);
        }
        return loaded;
    }

    public static String failure() {
        return failure == null ? "not loaded" : failure;
    }
}
