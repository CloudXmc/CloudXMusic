package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonObject;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.util.*;
import static dev.lzasn.cloudxmusic.client.kugou.KugouJson.*;

/** 搜索目录元数据；标准 hash 始终作为身份，album_id 不混入其他音源的数据。 */
public record KugouTrack(UnifiedSong song,String albumId,String albumAudioId,int durationSeconds,String coverUrl) {
    public KugouTrack {
        Objects.requireNonNull(song);Objects.requireNonNull(albumId);Objects.requireNonNull(albumAudioId);Objects.requireNonNull(coverUrl);
        if(!song.source().equals("kugou")||!song.platformId().matches("[A-Fa-f0-9]{32}")||durationSeconds<0)throw new IllegalArgumentException("酷狗歌曲身份无效");
    }
    public dev.lzasn.cloudxmusic.client.omni.SongView view(){return new dev.lzasn.cloudxmusic.client.omni.SongView(dev.lzasn.cloudxmusic.client.omni.MusicSource.KUGOU,song.platformId(),song.title(),song.artist(),song.album(),durationSeconds,coverUrl,song.vip());}
    public static KugouTrack parse(JsonObject item){
        JsonObject audio=object(item,"audio_info"),album=object(item,"album_info");
        if(album.size()==0)album=object(item,"albumInfo");
        JsonObject base=object(item,"base");
        String hash=text(item,"hash","Hash","filehash","FileHash","fileHash","audio_hash","audioHash");
        if(hash.isBlank())hash=text(audio,"hash_128","hash");
        if(!hash.matches("[A-Fa-f0-9]{32}"))throw new IllegalArgumentException("酷狗歌曲缺少 hash");
        String title=text(item,"songname","SongName","songName","audio_name"),artist=text(item,"singername","SingerName","singer_name","author_name","AuthorName");
        String filename=text(item,"filename","FileName","name");int separator=filename.indexOf(" - ");
        if(title.isBlank())title=separator<0?filename:filename.substring(separator+3);
        if(artist.isBlank()&&separator>=0)artist=filename.substring(0,separator);
        String cover=text(item,"imgurl","album_sizable_cover","sizable_cover");
        JsonObject trans=object(item,"trans_param");if(cover.isBlank())cover=text(trans,"union_cover");
        long duration=number(item,"duration","Duration");if(duration==0)duration=number(item,"timeLen")/1000;
        if(duration==0)duration=number(item,"timelen","timelength")/1000;
        boolean vip=number(item,"privilege","Privilege")>0||number(item,"pay_type","payType")>0||number(trans,"musicpack_advance","pay_block_tpl")>0;
        String albumName=text(item,"album_name","AlbumName","albumName");if(albumName.isBlank())albumName=text(album,"album_name","AlbumName","albumName","name");
        String albumId=text(item,"album_id","AlbumID","albumId");if(albumId.isBlank())albumId=text(album,"album_id","AlbumID","albumId");
        String albumAudioId=text(item,"album_audio_id","albumAudioId","mixsongid");
        if(albumAudioId.isBlank())albumAudioId=text(audio,"album_audio_id","albumAudioId","mixsongid");
        if(albumAudioId.isBlank())albumAudioId=text(base,"album_audio_id","albumAudioId","mixsongid");
        return new KugouTrack(new UnifiedSong("kugou",hash.toUpperCase(Locale.ROOT),title,artist,albumName,vip),
                albumId,albumAudioId,(int)Math.clamp(duration,0,86400),cover.replace("{size}","300"));
    }
}
