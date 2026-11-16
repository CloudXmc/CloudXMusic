package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.io.IOException;
import java.math.BigInteger;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.lzasn.cloudxmusic.client.kugou.KugouJson.*;

/** 云端“我喜欢”的完整快照；取消收藏必须使用同账号同歌单的真实 fileid。后台专用。 */
final class KugouFavoritesService {
    record Entry(KugouTrack track,long fileId){}
    record Snapshot(long listId,List<Entry> entries){
        Snapshot{entries=List.copyOf(entries);}
        boolean contains(String hash){return entries.stream().anyMatch(e->e.track().song().platformId().equalsIgnoreCase(hash));}
    }
    interface Remote {
        Snapshot load(KugouAccount account,BooleanSupplier active)throws Exception;
        Snapshot set(KugouAccount account,KugouTrack track,boolean liked,BooleanSupplier active)throws Exception;
    }
    private final KugouApi api;
    KugouFavoritesService(KugouApi api){this.api=api;}
    Remote remote(){return new Remote(){
        public Snapshot load(KugouAccount a,BooleanSupplier active)throws Exception{return KugouFavoritesService.this.load(a,active);}
        public Snapshot set(KugouAccount a,KugouTrack t,boolean liked,BooleanSupplier active)throws Exception{return KugouFavoritesService.this.set(a,t,liked,active);}
    };}
    private static void check(KugouAccount account,BooleanSupplier active)throws Exception{
        if(!active.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException("酷狗收藏请求已取消");
        if(!account.loggedIn())throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.LOGIN_REQUIRED);
    }
    private static JsonObject auth(KugouAccount account){var body=new JsonObject();body.addProperty("userid",new BigInteger(account.userId()));body.addProperty("token",account.token());return body;}
    private JsonObject call(String path,Map<String,String> values,JsonObject body,KugouAccount account,BooleanSupplier active)throws Exception{
        check(account,active);var root=KugouApi.checked(api.cloud(path,values,body,account));check(account,active);
        var data=object(root,"data");if(KugouApi.code(data)!=0||data.has("status")&&number(data,"status")==0)throw new IOException("酷狗收藏操作未成功");return data;
    }
    private long likedList(KugouAccount account,BooleanSupplier active)throws Exception{
        Set<Long> seen=new HashSet<>();
        for(int page=1;page<=100;page++){
            var body=auth(account);body.addProperty("total_ver",0);body.addProperty("type",2);body.addProperty("page",page);body.addProperty("pagesize",100);
            var data=call("/v7/get_all_list",Map.of("plat","1"),body,account,active);
            if(!data.has("info")||!data.get("info").isJsonArray())throw new IOException("酷狗收藏歌单列表不完整");
            var rows=array(data,"info");int added=0;
            for(var item:rows)if(item.isJsonObject()){var row=item.getAsJsonObject();long id=number(row,"listid");if(number(row,"is_del")==1)continue;if(id>0&&seen.add(id))added++;if(number(row,"is_def")==2&&id>0)return id;}
            if(rows.size()<100||added==0)break;
        }
        throw new IOException("酷狗账号未返回我喜欢歌单");
    }
    Snapshot load(KugouAccount account,BooleanSupplier active)throws Exception{return load(account,likedList(account,active),active);}
    private Snapshot load(KugouAccount account,long listId,BooleanSupplier active)throws Exception{
        var entries=new LinkedHashMap<String,Entry>();Set<String> pages=new HashSet<>();int received=0;
        for(int page=1;page<=100;page++){
            var body=auth(account);body.addProperty("listid",listId);body.addProperty("area_code",1);body.addProperty("allplatform",1);body.addProperty("show_cover",1);body.addProperty("show_relate_goods",0);body.addProperty("type",0);body.addProperty("page",page);body.addProperty("pagesize",100);
            var data=call("/v4/get_list_all_file",Map.of("plat","1"),body,account,active);
            if(!data.has("info")||!data.get("info").isJsonArray())throw new IOException("酷狗收藏歌曲列表不完整");var rows=array(data,"info");
            if(!rows.isEmpty()&&!pages.add(KugouSigning.md5(rows.toString())))throw new IOException("酷狗收藏分页重复");
            for(var item:rows)if(item.isJsonObject()){
                var row=item.getAsJsonObject();try{var track=KugouTrack.parse(row);entries.put(track.song().platformId(),new Entry(track,number(row,"fileid")));}catch(IllegalArgumentException ignored){}
            }
            received+=rows.size();long total=number(data,"count","total");
            if(total>0&&received>=total||rows.size()<100&&total<=received)return new Snapshot(listId,List.copyOf(entries.values()));
            if(rows.isEmpty())throw new IOException("酷狗收藏分页提前结束");
        }
        throw new IOException("酷狗收藏超出读取上限");
    }
    Snapshot set(KugouAccount account,KugouTrack track,boolean liked,BooleanSupplier active)throws Exception{
        var before=load(account,active);String hash=track.song().platformId();if(before.contains(hash)==liked)return before;
        var body=auth(account);body.addProperty("listid",before.listId());body.addProperty("list_ver",0);body.addProperty("type",0);var rows=new JsonArray();var row=new JsonObject();
        if(liked){
            row.addProperty("number",1);row.addProperty("name",track.song().artist()+" - "+track.song().title());row.addProperty("hash",hash);row.addProperty("album_id",track.albumId().isBlank()?"0":track.albumId());row.addProperty("mixsongid",track.albumAudioId().isBlank()?"0":track.albumAudioId());
            for(String key:List.of("size","sort","bitrate"))row.addProperty(key,0);row.addProperty("timelen",track.durationSeconds()*1000L);rows.add(row);body.add("data",rows);body.addProperty("slow_upload",1);body.addProperty("scene","false;null");
            call("/v6/add_song",Map.of("last_time",Long.toString(System.currentTimeMillis()/1000),"last_area","gztx"),body,account,active);
        }else{
            long fileId=before.entries().stream().filter(e->e.track().song().platformId().equalsIgnoreCase(hash)).mapToLong(Entry::fileId).findFirst().orElse(0);
            if(fileId<=0)throw new IOException("酷狗收藏缺少条目 ID，请刷新后重试");row.addProperty("fileid",fileId);rows.add(row);body.add("data",rows);
            call("/v4/delete_songs",Map.of(),body,account,active);
        }
        var confirmed=load(account,before.listId(),active);if(confirmed.contains(hash)!=liked)throw new IOException("酷狗尚未确认收藏状态，请刷新后重试");return confirmed;
    }
}
