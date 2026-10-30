package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import java.io.IOException;
import java.util.*;

/** QQ 只读目录接口。所有方法同步且只供后台任务调用；页偏移按原始记录数推进。 */
public final class QQCatalogApi {
    @FunctionalInterface interface Call {JsonObject call(String module,String method,JsonObject params)throws IOException,InterruptedException;}
    @FunctionalInterface interface ProfileId {String load()throws IOException,InterruptedException;}
    public record Playlist(String id,int dir,String owner,String title,int count,String cover) {
        public String key(){return dir==201?"liked:"+owner:"playlist:"+id;}
    }
    public record Track(UnifiedSong song,SongView display,long songId) {}
    public record Page<T>(List<T> items,int nextOffset,boolean hasMore) {public Page{items=List.copyOf(items);}}
    private final Call call;
    private final String owner;
    private final ProfileId profile;
    QQCatalogApi(Call call,String owner,ProfileId profile){this.call=call;this.owner=owner;this.profile=profile;}
    public Page<Playlist> recommend(int offset)throws IOException,InterruptedException{
        checkOffset(offset);JsonObject data=call.call("music.playlist.PlaylistSquare","GetRecommendFeed",params("From",offset,"Size",20));
        JsonArray rows=array(data,"List");Map<String,Playlist> playlists=new LinkedHashMap<>();
        for(var row:rows){JsonObject basic=object(object(row,"歌单条目"),"Playlist");basic=object(basic,"basic");Playlist p=playlist(basic,false);if(p!=null)playlists.putIfAbsent(p.key(),p);}
        return new Page<>(List.copyOf(playlists.values()),offset+rows.size(),rows.size()>0&&flag(data,"HasMore",rows.size()==20));
    }
    public Playlist liked()throws IOException,InterruptedException{
        requireAccount();JsonObject data=call.call("music.musicasset.PlaylistBaseRead","GetPlaylistByUin",params("uin",owner));
        for(var row:array(data,"v_playlist")){Playlist p=playlist(object(row,"私人歌单"),true);if(p!=null&&p.dir()==201)return p;}
        // QQ 的私人“我喜欢”可能不返回公开 tid，目录 201 仍然是有效资源。
        return new Playlist("0",201,owner,"我喜欢",0,"");
    }
    public Page<Track> tracks(Playlist playlist,int offset)throws IOException,InterruptedException{
        checkOffset(offset);if(playlist.dir()==201&&(!owner.equals(playlist.owner())||owner.isBlank()))throw new IOException("QQ 私人歌单账号已变化");
        if(!playlist.id().matches("[0-9]{1,19}"))throw new IOException("QQ 歌单 ID 无效");
        JsonObject p=params("disstid",Long.parseLong(playlist.id()),"dirid",playlist.dir(),"song_begin",offset,"song_num",100,"userinfo",true,"tag",true,"orderlist",true);
        if(playlist.dir()==201&&playlist.id().equals("0"))p.addProperty("enc_host_uin",profile.load());
        JsonObject data=call.call("music.srfDissInfo.DissInfo","CgiGetDiss",p);JsonArray rows=array(data,"songlist");
        long total=number(data,"total_song_num",0);boolean more=rows.size()>0&&(total<=0||offset+rows.size()<total)&&flag(data,"hasmore",rows.size()==100);
        return new Page<>(tracks(rows),offset+rows.size(),more);
    }
    private Playlist playlist(JsonObject data,boolean own)throws IOException{
        String id=text(data,"tid","id");if(id.isBlank())id="0";
        int dir=(int)number(data,data.has("dirId")?"dirId":"dirid",0);boolean liked=own&&dir==201;
        if(!id.matches("[0-9]{1,19}")||id.equals("0")&&!liked)return null;
        String title=text(data,"title","dirName","name");if(title.isBlank())title=liked?"我喜欢":"QQ 音乐歌单";
        String cover=text(data,"picurl","picUrl","logo","bigpicUrl");
        if(cover.isBlank()&&data.has("cover")&&data.get("cover").isJsonObject())cover=text(data.getAsJsonObject("cover"),"default_url","medium_url");
        return new Playlist(id,liked?201:0,own?owner:"",clean(title),(int)Math.min(Integer.MAX_VALUE,number(data,data.has("song_cnt")?"song_cnt":data.has("songNum")?"songNum":"songnum",0)),QQAccountProfile.safeAvatar(cover));
    }
    static List<Track> tracks(JsonArray rows)throws IOException{
        Map<String,Track> tracks=new LinkedHashMap<>();for(var row:rows){JsonObject data=object(row,"歌曲条目");
            for(String key:new String[]{"track_info","songInfo","songInfoDetail","track"})if(data.has(key)&&data.get(key).isJsonObject()){data=data.getAsJsonObject(key);break;}
            UnifiedSong song=QQProvider.mapSong(data);if(song==null)continue;
            String album=text(data,"albummid");if(data.has("album")&&data.get("album").isJsonObject())album=text(data.getAsJsonObject("album"),"mid");
            String cover=QQTrackMetadata.validMid(album)?"https://y.gtimg.cn/music/photo_new/T002R300x300M000"+album+".jpg":"";
            int duration=(int)Math.clamp(number(data,"interval",0),0,86400);
            tracks.putIfAbsent(song.platformId(),new Track(song,SongView.from(song,duration,cover),number(data,data.has("id")?"id":"songid",0)));
        }return List.copyOf(tracks.values());
    }
    static JsonObject params(Object... pairs){JsonObject o=new JsonObject();for(int i=0;i<pairs.length;i+=2){String key=(String)pairs[i];Object v=pairs[i+1];if(v instanceof Number n)o.addProperty(key,n);else if(v instanceof Boolean b)o.addProperty(key,b);else o.addProperty(key,v.toString());}return o;}
    static JsonArray array(JsonObject o,String key)throws IOException{JsonElement v=o.get(key);if(v==null||!v.isJsonArray())throw new IOException("QQ 目录响应缺少数组 "+key);return v.getAsJsonArray();}
    static JsonObject object(JsonElement v,String label)throws IOException{if(v==null||!v.isJsonObject())throw new IOException("QQ 目录响应缺少对象 "+label);return v.getAsJsonObject();}
    static JsonObject object(JsonObject o,String key)throws IOException{return object(o.get(key),key);}
    static String text(JsonObject o,String... keys){for(String key:keys){JsonElement v=o.get(key);if(v!=null&&v.isJsonPrimitive()&&!v.getAsJsonPrimitive().isBoolean()){String value=v.getAsString().strip();if(!value.isBlank())return value;}}return "";}
    static long number(JsonObject o,String key,long fallback)throws IOException{if(!o.has(key)||o.get(key).isJsonNull())return fallback;try{return o.get(key).getAsBigDecimal().longValueExact();}catch(RuntimeException e){throw new IOException("QQ 目录数值无效："+key,e);}}
    static boolean flag(JsonObject o,String key,boolean fallback)throws IOException{if(!o.has(key))return fallback;var v=o.get(key);if(v.isJsonPrimitive()&&v.getAsJsonPrimitive().isBoolean())return v.getAsBoolean();return number(o,key,0)!=0;}
    static String clean(String value){return value.replaceAll("<[^>]*>","").replace("&amp;","&").replace("&quot;","\"");}
    private void requireAccount()throws IOException{if(owner.isBlank())throw new IOException("请先登录 QQ 音乐后读取个性推荐和收藏");}
    private static void checkOffset(int offset){if(offset<0||offset>20000)throw new IllegalArgumentException("QQ 目录分页超出范围");}
}
