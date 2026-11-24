package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.netease.NeteaseApi;
import dev.lzasn.cloudxmusic.client.omni.OnlineMusicProvider;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.Track;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/** 只适配已有搜索 API；登录、Cookie、播放和队列仍由原模块负责。 */
public final class NeteaseSearchAdapter implements OnlineMusicProvider {
    @FunctionalInterface
    interface SearchApi { List<Track> search(String keyword, int limit) throws Exception; }
    private final SearchApi api;

    public NeteaseSearchAdapter() { this(NeteaseApi::searchSongs); }
    NeteaseSearchAdapter(SearchApi api) { this.api = Objects.requireNonNull(api); }

    @Override public String sourceId() { return "netease"; }

    @Override public List<UnifiedSong> searchSongs(String keyword, int offset, int limit) throws Exception {
        // 原公开 API 只支持首页；不伪造它没有的 offset 能力。
        if (keyword == null || keyword.isBlank() || offset != 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("keyword required; offset must be 0; limit must be 1..100");
        }
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("搜索已中断");
        return new TrackSongs(api.search(keyword.strip(), limit));
    }

    /** 原对象与本批结果一起传递，不使用全局 MID 缓存，避免并发搜索串批。 */
    static List<Track> originalTracks(List<UnifiedSong> songs) {
        if (!(songs instanceof TrackSongs batch)) {
            throw new IllegalArgumentException("Only native NetEase result batches contain playable tracks");
        }
        return batch.tracks;
    }

    @Override public String getPlaybackUrl(UnifiedSong song) {
        throw new UnsupportedOperationException("播放由原网易云播放控制负责");
    }
    @Override public Lyric getLyrics(UnifiedSong song) {
        throw new UnsupportedOperationException("歌词由原网易云播放控制负责");
    }

    private static final class TrackSongs extends AbstractList<UnifiedSong> implements RandomAccess {
        private final List<Track> tracks;
        private final List<UnifiedSong> songs;
        TrackSongs(List<Track> tracks) {
            this.tracks = List.copyOf(tracks);
            this.songs = this.tracks.stream().map(track -> new UnifiedSong("netease",
                    track.id(), track.title(), track.artist(), track.album())).toList();
        }
        @Override public UnifiedSong get(int index) { return songs.get(index); }
        @Override public int size() { return songs.size(); }
    }
}
