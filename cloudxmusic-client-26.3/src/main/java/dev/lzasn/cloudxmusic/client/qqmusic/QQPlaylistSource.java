package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 当前 QQ 凭证快照到歌单页面的适配，不持久化账号或修改音频引擎。 */
public final class QQPlaylistSource implements PlaylistSource {
    private final QQPlaylistApi api;
    private final QQPlaybackController playback;
    private final BooleanSupplier active;
    private record Stamp(long revision,dev.lzasn.cloudxmusic.client.omni.UnifiedSong song,boolean playing,boolean loading,QQQuality quality){}
    QQPlaylistSource(QQCredentials credentials,QQPlaybackController playback,BooleanSupplier active){
        var service=new QQCatalogService(credentials,active);
        api=new QQPlaylistApi(service::call,credentials.userId(),service::profileId);this.playback=playback;this.active=active;
    }
    public Page<Entry> playlists(Section section,int cursor)throws Exception{return api.playlists(section,cursor);}
    public Page<Track> liked(int cursor)throws Exception{return api.liked(cursor);}
    public Page<Track> tracks(Entry entry,int cursor)throws Exception{return api.tracks(entry,cursor);}
    public Object playbackStamp(){var view=playback.view();return new Stamp(playback.queueRevision(),view.song(),view.playing(),view.loading(),playback.preferredQuality());}
    public void play(List<Track> tracks,int selected){if(active.getAsBoolean())playback.play(tracks.stream().map(Track::song).toList(),selected);}
}
