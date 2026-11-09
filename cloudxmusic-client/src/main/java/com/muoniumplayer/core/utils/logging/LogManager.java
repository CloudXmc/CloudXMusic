package com.muoniumplayer.core.utils.logging;

/** CloudXMusic adapter retaining the upstream call shape. */
public final class LogManager {
    private LogManager() {
    }

    public static Logger getLogger(String name) {
        return new Logger(name);
    }
}
