package dev.lzasn.cloudxmusic.client.gui.playlist;

import dev.lzasn.cloudxmusic.client.omni.SongView;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.util.List;

/** UI 的只读目录契约；凭证、签名和平台原生标识只由各自适配器处理。 */
public interface PlaylistSource {
    enum Section { LIKED, OWNED, COLLECTED }
    record Entry(String key,String title,String cover,int count) {}
    record Track(UnifiedSong song,SongView display,String albumId,String albumAudioId,Long order) {}
    record Page<T>(List<T> items,int nextCursor,boolean hasMore,int total) {
        public Page { items=List.copyOf(items); }
    }
    Page<Entry> playlists(Section section,int cursor) throws Exception;
    Page<Track> liked(int cursor) throws Exception;
    Page<Track> tracks(Entry entry,int cursor) throws Exception;
    /** 只在客户端线程读取，用来防止后台收集覆盖后来发起的播放。 */
    Object playbackStamp();
    void play(List<Track> tracks,int selected);
}
