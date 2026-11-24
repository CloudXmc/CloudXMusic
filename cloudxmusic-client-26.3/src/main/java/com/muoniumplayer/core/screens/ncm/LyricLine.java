package com.muoniumplayer.core.screens.ncm;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CloudXMusic adapter for the lyric model expected by the imported renderer.
 *
 * <p>Only fields used by the three-dimensional lyric path are retained; the
 * original class also carries unrelated 2D-widget animation state.</p>
 */
public class LyricLine {
    public long timestamp;
    public String lyric;
    public String translationText;
    public String romanizationText;
    public long duration;
    public boolean isBreakLine;
    public final List<Word> words = new CopyOnWriteArrayList<>();

    public LyricLine(long timestamp, String lyric) {
        this.timestamp = timestamp;
        this.lyric = lyric == null ? "" : lyric;
    }

    public boolean hasTimedWords() {
        for (Word word : words) {
            if (word != null && word.duration > 0L) {
                return true;
            }
        }
        return false;
    }

    public void markDirty() {
    }

    public static final class Word {
        public final String word;
        public final long timestamp;
        public final long duration;

        public Word(String word, long timestamp, long duration) {
            this.word = word == null ? "" : word;
            this.timestamp = timestamp;
            this.duration = duration;
        }
    }
}
