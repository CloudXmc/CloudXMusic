package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 云歌单歌曲元数据随完整队列提交，避免大歌单越过搜索缓存容量后丢失专辑身份。 */
public final class KugouPlaylistSource implements PlaylistSource {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("CloudXMusic/Kugou");
    private final KugouPlaylistApi api;
    private final KugouProvider provider;
    private final KugouPlaybackController playback;
    private final BooleanSupplier active;
    private record Stamp(List<dev.lzasn.cloudxmusic.client.omni.UnifiedSong> queue,dev.lzasn.cloudxmusic.client.omni.UnifiedSong song,boolean playing,boolean loading,KugouQuality quality){}
    KugouPlaylistSource(KugouPlaylistApi api,KugouProvider provider,KugouPlaybackController playback,BooleanSupplier active){this.api=api;this.provider=provider;this.playback=playback;this.active=active;}
    public Page<Entry> playlists(Section section,int cursor)throws Exception{return api.playlists(section,cursor);}
    public Page<Track> liked(int cursor)throws Exception{return api.liked(cursor);}
    public Page<Track> tracks(Entry entry,int cursor)throws Exception{return api.tracks(entry,cursor);}
    public Object playbackStamp(){var view=playback.view();return new Stamp(playback.queue(),view.song(),view.playing(),view.loading(),playback.preferredQuality());}
    public void play(List<Track> tracks,int selected){
        if(!active.getAsBoolean())return;
        if(selected>=0&&selected<tracks.size()){
            var picked=tracks.get(selected);
            LOG.info("[酷狗收藏] 点击播放，hash = {}, album_id = {}, album_audio_id = {}",picked.song().platformId(),picked.albumId(),picked.albumAudioId());
        }else{
            LOG.info("[酷狗收藏] 播放全部，歌曲数 = {}",tracks.size());
        }
        provider.rememberPlaylist(tracks.stream().map(t->new KugouTrack(t.song(),t.albumId(),t.albumAudioId(),t.display().durationSeconds(),t.display().coverUrl())).toList());
        playback.play(tracks.stream().map(Track::song).toList(),selected);
    }
}
