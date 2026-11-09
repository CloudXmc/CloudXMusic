package com.muoniumplayer.core.utils.logging;

import org.slf4j.LoggerFactory;

/** Small SLF4J-backed facade used by the imported world-lyrics bridge. */
public final class Logger {
    private final org.slf4j.Logger delegate;

    public Logger(String name) {
        this.delegate = LoggerFactory.getLogger(name);
    }

    public void debug(String message, Object... args) {
        delegate.debug(message, args);
    }

    public void info(String message, Object... args) {
        delegate.info(message, args);
    }

    public void warn(String message, Object... args) {
        delegate.warn(message, args);
    }

    public void error(String message, Object... args) {
        delegate.error(message, args);
    }
}
