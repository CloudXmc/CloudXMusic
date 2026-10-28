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
                        int coverRevision, float seekPreview, float volume, String avatarUrl,
                        boolean darkMode, float panelScale, float islandScale, float islandX, float islandY) {
    public enum Page { NOW, DISCOVER, SEARCH, FAVORITES, QUEUE, SETTINGS, ACCOUNT }
    public record LyricRow(String text, String translation, boolean active) {}
    public float position() { return seekPreview >= 0 ? seekPreview : positionTenths / 10f; }
    public float progress() { return Math.max(0, Math.min(1, position() / Math.max(1, current.durationSeconds()))); }

    /**
     * 抹掉随播放时钟变化的字段（进度、拖动预览、音量）与歌词窗口。
     * 静态画布只按这个快照比较，播放中不再整幅重绘；这些量分别由
     * {@code MusicPainter.paintDynamic}、{@code paintLyrics}、{@code paintCover} 画在独立纹理上。
     */
    public MusicView staticView() {
        return new MusicView(page, current, tracks, queue, List.of(), 0, playing, loggedIn, nickname,
                search, focused, cursor, status, quality, lyricMode, islandEnabled, playbackMode,
                favorite, version, hover, coverRevision, -1f, 0f, avatarUrl, darkMode, panelScale, islandScale,
                islandX, islandY);
    }
}
