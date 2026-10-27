package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import java.util.List;

/** 只包含绘制所需的不可变快照，不把游戏实例传入画布。 */
public record MusicView(Page page, Track current, List<Track> tracks, List<Track> queue,
                        List<LyricRow> lyrics, int positionTenths, boolean playing,
                        boolean loggedIn, String nickname, String search, boolean focused,
                        boolean cursor, String status, String quality, String lyricMode,
                        boolean islandEnabled, PlaybackMode playbackMode, boolean favorite,
                        String version, String hover,
                        int coverRevision, float seekPreview, float volume) {
    public enum Page { NOW, DISCOVER, SEARCH, FAVORITES, QUEUE, SETTINGS }
    public record LyricRow(String text, String translation, boolean active) {}
    public float position() { return seekPreview >= 0 ? seekPreview : positionTenths / 10f; }
    public float progress() { return Math.max(0, Math.min(1, position() / Math.max(1, current.durationSeconds()))); }
}
