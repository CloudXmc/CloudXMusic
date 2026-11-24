package dev.lzasn.cloudxmusic.client.omni;

import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import java.util.List;

/** 网易云只读展示适配；不接管其账号、队列或播放流程。 */
public final class NeteasePlaybackAdapter {
    private final MusicState state;
    public NeteasePlaybackAdapter(MusicState state){this.state=state;}
    public PlaybackSnapshot snapshot(){
        var session=NeteaseSession.INSTANCE;
        return new PlaybackSnapshot(MusicSource.NETEASE,SongView.from(state.current()),state.queue().stream().map(SongView::from).toList(),
                List.of(state.lyric().lines()),state.playing(),false,state.position(),state.current().durationSeconds(),state.volume(),
                session.quality().label,session.quality().label,session.lyricMode(),state.playbackMode(),state.isFavorite(state.current()),state.statusLine(),false);
    }
}
