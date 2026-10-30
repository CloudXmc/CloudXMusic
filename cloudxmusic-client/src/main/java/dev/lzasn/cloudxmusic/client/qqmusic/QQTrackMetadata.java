package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.io.IOException;

/** songmid 是歌曲标识，media_mid 是文件标识；两者不能互相猜测替代。 */
final class QQTrackMetadata {
    record Info(String mediaMid,int durationSeconds,String albumMid,String coverUrl,boolean vip) {
        Info(String mediaMid,int durationSeconds,String albumMid,String coverUrl){this(mediaMid,durationSeconds,albumMid,coverUrl,false);}
    }
    private QQTrackMetadata() {}
    static String load(String songMid, QQPlaybackApi api) throws IOException, InterruptedException {
        return loadInfo(songMid,api).mediaMid();
    }
    static Info loadInfo(String songMid, QQPlaybackApi api) throws IOException, InterruptedException {
        JsonObject params = new JsonObject(); params.addProperty("song_mid", songMid);
        return parseInfo(songMid, QQPlaybackApi.requireData(api.call("music.pf_song_detail_svr", "get_song_detail_yqq", params, false)));
    }
    static Info loadPublic(String songMid)throws IOException,InterruptedException{
        if(!validMid(songMid))throw new IllegalArgumentException("QQ 歌曲 MID 无效");
        JsonObject params=new JsonObject();params.addProperty("song_mid",songMid);
        JsonObject root=QQAuthHttp.post(QQAuthHttp.request("music.pf_song_detail_svr","get_song_detail_yqq",params),QQAuthHttp.emptyCookies());
        if(QQAuthHttp.integer(root,"code")!=0)throw new IOException("QQ 歌曲详情请求失败");
        JsonObject block=QQAuthHttp.object(root,"req_0");if(QQAuthHttp.integer(block,"code")!=0)throw new IOException("QQ 歌曲详情接口拒绝请求");
        return parseInfo(songMid,QQAuthHttp.object(block,"data"));
    }
    static boolean validMid(String mid) { return mid != null && mid.matches("[A-Za-z0-9]{1,64}"); }
    static String parse(String songMid, JsonObject data) throws IOException {
        return parseInfo(songMid,data).mediaMid();
    }
    static Info parseInfo(String songMid, JsonObject data) throws IOException {
        JsonObject track = QQAuthHttp.object(data, "track_info");
        String returnedMid = QQAuthHttp.text(track, "mid");
        if (!returnedMid.isEmpty() && !returnedMid.equals(songMid)) throw new IOException("QQ 歌曲详情与请求的 songmid 不一致");
        String mediaMid = QQAuthHttp.string(QQAuthHttp.object(track, "file"), "media_mid");
        if (!validMid(mediaMid)) throw new IOException("QQ 歌曲详情缺少有效 media_mid，未生成猜测地址");
        long duration=track.has("interval")?QQAuthHttp.integer(track,"interval"):0;
        if(duration<0||duration>86400)throw new IOException("QQ 歌曲时长无效");
        String albumMid="";
        if(track.has("album")&&!track.get("album").isJsonNull())albumMid=QQAuthHttp.text(QQAuthHttp.object(track,"album"),"mid");
        if(!albumMid.isEmpty()&&!validMid(albumMid))throw new IOException("QQ 专辑 MID 无效");
        return new Info(mediaMid,(int)duration,albumMid,albumMid.isEmpty()?"":"https://y.gtimg.cn/music/photo_new/T002R300x300M000"+albumMid+".jpg",QQVip.parse(track));
    }
}
