package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import java.util.List;

/** 只包含绘制所需的不可变快照，不把游戏实例传入画布。 */
public record MusicView(Page page, SongView current, List<SongView> tracks, List<SongView> queue,
                        List<LyricRow> lyrics, int positionTenths, boolean playing,
                        boolean loggedIn, String nickname, String search, boolean focused,
                        boolean cursor, String status, String quality, String lyricMode,
                        boolean islandEnabled, PlaybackMode playbackMode, boolean favorite,
                        String version, String hover,
                        int coverRevision, float seekPreview, float volume, String avatarUrl,
                        boolean darkMode, float panelScale, float islandScale, float islandX, float islandY,
                        String openKey,
                        /** 列表当前首个可见行下标与总条数，只用于绘制右侧滚动条。 */
                        int scroll, int total, RoomView room) {
    public enum Page { NOW, DISCOVER, SEARCH, FAVORITES, QUEUE, ROOM, SETTINGS, ACCOUNT }
    public record LyricRow(String text, String translation, boolean active) {}
    public record RoomMember(String name, String detail, boolean owner, boolean canPlay) {
        public RoomMember {
            name = name == null || name.isBlank() ? "成员" : name;
            detail = detail == null ? "" : detail;
        }
    }
    public record RoomTrack(String title, String artist, String duration) {
        public RoomTrack {
            title = title == null || title.isBlank() ? "等待添加歌曲" : title;
            artist = artist == null ? "" : artist;
            duration = duration == null ? "" : duration;
        }
    }
    public record RoomSummary(String roomCode, String playerId, String source, int members, String status) {
        public RoomSummary {
            roomCode = roomCode == null || roomCode.isBlank() ? "------" : roomCode;
            playerId = playerId == null || playerId.isBlank() ? "未知玩家" : playerId;
            source = source == null || source.isBlank() ? "网易云音乐" : source;
            members = Math.max(0, members);
            status = status == null || status.isBlank() ? "等待加入" : status;
        }
    }

    /** 房间页只读取不可变展示快照；网络会话接入前由空快照显示未加入状态。 */
    public record RoomView(boolean joined, String roomCode, String source, String roomStatus,
                           String nowTitle, String nowArtist, int positionTenths, int durationSeconds,
                           boolean playing, List<RoomMember> members, List<RoomTrack> queue,
                           List<RoomSummary> publicRooms, int queueScroll) {
        public RoomView {
            roomCode = roomCode == null || roomCode.isBlank() ? "------" : roomCode;
            source = source == null || source.isBlank() ? "网易云音乐" : source;
            roomStatus = roomStatus == null ? "" : roomStatus;
            nowTitle = nowTitle == null ? "" : nowTitle;
            nowArtist = nowArtist == null ? "" : nowArtist;
            members = members == null ? List.of() : List.copyOf(members);
            queue = queue == null ? List.of() : List.copyOf(queue);
            publicRooms = publicRooms == null ? List.of() : List.copyOf(publicRooms);
            queueScroll = Math.max(0, queueScroll);
        }
        public static RoomView empty() {
            return new RoomView(false, "------", "网易云音乐", "", "", "", 0, 0, false,
                    List.of(), List.of(), List.of(), 0);
        }
        public RoomView withQueueScroll(int value) {
            return new RoomView(joined, roomCode, source, roomStatus, nowTitle, nowArtist,
                    positionTenths, durationSeconds, playing, members, queue, publicRooms, value);
        }
    }

    /** 兼容原有调用：没有房间会话时固定显示未加入页面。 */
    public MusicView(Page page, SongView current, List<SongView> tracks, List<SongView> queue,
                     List<LyricRow> lyrics, int positionTenths, boolean playing,
                     boolean loggedIn, String nickname, String search, boolean focused,
                     boolean cursor, String status, String quality, String lyricMode,
                     boolean islandEnabled, PlaybackMode playbackMode, boolean favorite,
                     String version, String hover, int coverRevision, float seekPreview,
                     float volume, String avatarUrl, boolean darkMode, float panelScale,
                     float islandScale, float islandX, float islandY, String openKey,
                     int scroll, int total) {
        this(page, current, tracks, queue, lyrics, positionTenths, playing, loggedIn, nickname,
                search, focused, cursor, status, quality, lyricMode, islandEnabled, playbackMode,
                favorite, version, hover, coverRevision, seekPreview, volume, avatarUrl, darkMode,
                panelScale, islandScale, islandX, islandY, openKey, scroll, total, RoomView.empty());
    }
    /** 不带滚动信息的 SongView 版本，QQ 测试与旧调用沿用（滚动条按无滚动处理）。 */
    public MusicView(Page page, SongView current, List<SongView> tracks, List<SongView> queue,
                     List<LyricRow> lyrics, int positionTenths, boolean playing,
                     boolean loggedIn, String nickname, String search, boolean focused,
                     boolean cursor, String status, String quality, String lyricMode,
                     boolean islandEnabled, PlaybackMode playbackMode, boolean favorite,
                     String version, String hover,
                     int coverRevision, float seekPreview, float volume, String avatarUrl,
                     boolean darkMode, float panelScale, float islandScale, float islandX, float islandY,
                     String openKey) {
        this(page, current, tracks, queue, lyrics, positionTenths, playing, loggedIn, nickname,
                search, focused, cursor, status, quality, lyricMode, islandEnabled, playbackMode,
                favorite, version, hover, coverRevision, seekPreview, volume, avatarUrl, darkMode,
                panelScale, islandScale, islandX, islandY, openKey, 0, 0);
    }

    /** 保留原绘制测试/网易云调用的构造入口，转换只发生在展示边界。 */
    public MusicView(Page page,Track current,List<Track> tracks,List<Track> queue,List<LyricRow> lyrics,int positionTenths,boolean playing,
                     boolean loggedIn,String nickname,String search,boolean focused,boolean cursor,String status,String quality,String lyricMode,
                     boolean islandEnabled,PlaybackMode playbackMode,boolean favorite,String version,String hover,int coverRevision,float seekPreview,
                     float volume,String avatarUrl,boolean darkMode,float panelScale,float islandScale,float islandX,float islandY,String openKey){
        this(page,current,tracks,queue,lyrics,positionTenths,playing,loggedIn,nickname,search,focused,cursor,status,quality,lyricMode,
                islandEnabled,playbackMode,favorite,version,hover,coverRevision,seekPreview,volume,avatarUrl,darkMode,panelScale,
                islandScale,islandX,islandY,openKey,0,0);
    }
    /** 带滚动信息的一版，离线预览用它验证滚动条。 */
    public MusicView(Page page,Track current,List<Track> tracks,List<Track> queue,List<LyricRow> lyrics,int positionTenths,boolean playing,
                     boolean loggedIn,String nickname,String search,boolean focused,boolean cursor,String status,String quality,String lyricMode,
                     boolean islandEnabled,PlaybackMode playbackMode,boolean favorite,String version,String hover,int coverRevision,float seekPreview,
                     float volume,String avatarUrl,boolean darkMode,float panelScale,float islandScale,float islandX,float islandY,String openKey,
                     int scroll,int total){
        this(page,SongView.from(current),tracks.stream().map(SongView::from).toList(),queue.stream().map(SongView::from).toList(),lyrics,
                positionTenths,playing,loggedIn,nickname,search,focused,cursor,status,quality,lyricMode,islandEnabled,playbackMode,favorite,version,
                hover,coverRevision,seekPreview,volume,avatarUrl,darkMode,panelScale,islandScale,islandX,islandY,openKey,scroll,total);
    }
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
                islandX, islandY, openKey, scroll, total, room);
    }
}
