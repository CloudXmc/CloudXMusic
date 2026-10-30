package dev.lzasn.cloudxmusic.client.omni;

import dev.lzasn.cloudxmusic.client.state.Track;

/** 纯展示 DTO；不把 QQ 曲目交给网易云 Track/队列。 */
public record SongView(MusicSource source,String platformId,String title,String artist,String album,int durationSeconds,String coverUrl,boolean vip) {
    public SongView(MusicSource source,String platformId,String title,String artist,String album,int durationSeconds,String coverUrl){this(source,platformId,title,artist,album,durationSeconds,coverUrl,false);}
    public SongView {durationSeconds=Math.max(0,durationSeconds);coverUrl=coverUrl==null?"":coverUrl;}
    public static SongView from(Track track){return new SongView(MusicSource.NETEASE,track.id()==null?"":track.id(),track.title(),track.artist(),track.album(),track.durationSeconds(),track.coverUrl(),track.vip());}
    public static SongView from(UnifiedSong song,int duration,String cover){return from(song,duration,cover,false);}
    public static SongView from(UnifiedSong song,int duration,String cover,boolean vip){return new SongView(MusicSource.QQ,song.platformId(),song.title(),song.artist(),song.album(),duration,cover,song.vip()||vip);}
    public static SongView empty(MusicSource source){return new SongView(source,"","未在播放","去搜索挑选一首歌曲","",0,"");}
    public String durationText(){return Track.format(durationSeconds);}
    public boolean netease(){return source==MusicSource.NETEASE&&!platformId.isBlank();}
}
