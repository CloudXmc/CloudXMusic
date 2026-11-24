package com.muoniumplayer.core.ncm.music;

import com.muoniumplayer.core.screens.ncm.LyricLine;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.PlaybackSnapshot;
import dev.lzasn.cloudxmusic.client.omni.TimedLyrics;
import dev.lzasn.cloudxmusic.client.worldlyrics.WorldLyricConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CloudXMusic playback/lyric adapter for the imported MuoniumPlayer renderer.
 */
public final class CloudMusic {
    public static volatile List<LyricLine> lyrics = List.of();
    public static volatile String lyricsTrackKey;
    public static volatile long lyricsRevision;
    public static volatile boolean playing;
    public static volatile long positionMs;

    private static String currentTrackKey;
    private static String lyricSignature = "";

    private CloudMusic() {
    }

    public static final class PlaybackSnapshot {
        public final boolean playing;
        public final long positionMs;

        PlaybackSnapshot(boolean playing, long positionMs) {
            this.playing = playing;
            this.positionMs = positionMs;
        }
    }

    public static PlaybackSnapshot getSnapshot() {
        return new PlaybackSnapshot(playing, positionMs);
    }

    public static boolean lyricsMatchCurrentTrack() {
        String key = lyricsTrackKey;
        return key != null && key.equals(currentTrackKey) && !lyrics.isEmpty();
    }

    public static String getSecondaryLyrics(LyricLine line) {
        return line == null || line.translationText == null ? "" : line.translationText;
    }

    /**
     * Called from the client tick. Converts the unified player snapshot into the
     * immutable model consumed by the renderer.
     */
    public static synchronized void update(dev.lzasn.cloudxmusic.client.omni.PlaybackSnapshot snapshot,
                                           WorldLyricConfig config) {
        playing = snapshot != null && snapshot.playing();
        positionMs = snapshot == null ? 0L : Math.max(0L, Math.round(snapshot.position() * 1000.0));
        config.applyToHud();

        if (snapshot == null || !snapshot.hasTrack()) {
            setTrack(null, List.of(), "");
            return;
        }

        String source = snapshot.source() == null ? "unknown" : snapshot.source().name().toLowerCase();
        String key = source + ':' + snapshot.song().platformId();
        if (snapshot.lyrics().isEmpty()) {
            setTrack(key, List.of(), "");
            return;
        }

        Map<String, TimedLyrics.Line> timedByStart = new HashMap<>();
        for (TimedLyrics.Line timed : snapshot.timedLyrics()) {
            timedByStart.putIfAbsent(timed.startMs() + "\u0000" + timed.text(), timed);
        }

        List<Lyric.Line> sourceLines = snapshot.lyrics();
        List<LyricLine> converted = new ArrayList<>(sourceLines.size());
        StringBuilder signature = new StringBuilder(key).append('|');
        long songDurationMs = Math.max(0L, Math.round(snapshot.duration() * 1000.0));
        for (int i = 0; i < sourceLines.size(); i++) {
            Lyric.Line sourceLine = sourceLines.get(i);
            long start = sourceLine.timeMs();
            long end = i + 1 < sourceLines.size() ? sourceLines.get(i + 1).timeMs() : songDurationMs;
            long duration = Math.max(0L, end - start);
            LyricLine line = new LyricLine(start, sourceLine.text());
            line.duration = duration;
            line.translationText = sourceLine.hasTranslation() ? sourceLine.translated() : null;
            line.isBreakLine = sourceLine.text().isBlank();

            TimedLyrics.Line timed = timedByStart.get(start + "\u0000" + sourceLine.text());
            if (timed == null) {
                timed = nearestTimedLine(snapshot.timedLyrics(), sourceLine);
            }
            if (timed != null) {
                for (TimedLyrics.Word word : timed.words()) {
                    line.words.add(new LyricLine.Word(word.text(), word.startMs(), word.durationMs()));
                }
            }
            converted.add(line);
            signature.append(start).append(':').append(sourceLine.text())
                    .append(':').append(sourceLine.translated())
                    .append(':').append(line.words.size()).append(';');
        }
        String nextSignature = signature.toString();
        setTrack(key, List.copyOf(converted), nextSignature);
    }

    /** QQ sources occasionally differ by a few milliseconds between LRC and QRC headers. */
    private static TimedLyrics.Line nearestTimedLine(List<TimedLyrics.Line> candidates, Lyric.Line line) {
        TimedLyrics.Line best = null;
        long bestDelta = Long.MAX_VALUE;
        for (TimedLyrics.Line timed : candidates) {
            if (!timed.text().equals(line.text())) {
                continue;
            }
            long delta = Math.abs((long) timed.startMs() - line.timeMs());
            if (delta <= 250L && delta < bestDelta) {
                best = timed;
                bestDelta = delta;
            }
        }
        return best;
    }

    private static void setTrack(String key, List<LyricLine> lines, String signature) {
        currentTrackKey = key;
        lyricsTrackKey = key;
        lyrics = lines;
        if (!lyricSignature.equals(signature)) {
            lyricSignature = signature;
            lyricsRevision++;
        }
    }
}
