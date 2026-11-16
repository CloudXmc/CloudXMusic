package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.*;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.lzasn.cloudxmusic.client.kugou.KugouJson.*;

/** H5 云歌单目录，listid 只在当前账号命名空间内有效；所有调用均在后台执行。 */
public final class KugouPlaylistApi {
    @FunctionalInterface interface Call {JsonObject call(String path,Map<String,String> params,JsonObject body,KugouAccount account)throws Exception;}
    private final Call call;
    private final KugouAccount account;
    private final BooleanSupplier active;
    private Entry liked;
    private record Progress(int next,int received,Set<String> pages){}
    private final Map<String,Progress> progress=new LinkedHashMap<>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Progress> entry){return size()>16;}
    };
    KugouPlaylistApi(Call call,KugouAccount account,BooleanSupplier active){this.call=call;this.account=account;this.active=active;}
    private JsonObject auth(){var body=new JsonObject();body.addProperty("userid",new BigInteger(account.userId()));body.addProperty("token",account.token());return body;}
    private void check()throws Exception{if(!active.getAsBoolean()||Thread.currentThread().isInterrupted())throw new InterruptedException();if(!account.loggedIn())throw new IOException("请先登录账号");}
    private JsonObject directory(int cursor)throws Exception{
        check();if(cursor<0||cursor>=400)throw new IOException("酷狗歌单分页超出范围");
        var body=auth();body.addProperty("total_ver",0);body.addProperty("type",2);body.addProperty("page",cursor+1);body.addProperty("pagesize",50);
        var data=call.call("/v7/get_all_list",Map.of("plat","1"),body,account);check();requireArray(data);return data;
    }
    public Page<Entry> playlists(Section section,int cursor)throws Exception{
        if(section==Section.LIKED)throw new IllegalArgumentException("我喜欢使用歌曲接口");var data=directory(cursor);var rows=array(data,"info");var entries=new LinkedHashMap<String,Entry>();
        for(var item:rows){if(!item.isJsonObject())continue;var row=item.getAsJsonObject();if(number(row,"is_del")==1)continue;Entry e=entry(row);if(e==null)continue;
            if(number(row,"is_def")==2){liked=e;continue;}
            // 缺少类型不能默认为自建，避免把未知目录错分到用户歌单。
            if(!row.has("type"))continue;long type=number(row,"type");if(type==0&&section==Section.OWNED||type!=0&&section==Section.COLLECTED)entries.putIfAbsent(e.key(),e);
        }
        int total=(int)Math.clamp(number(data,"list_count")+number(data,"collect_count"),0,Integer.MAX_VALUE);
        boolean more=hasMore("directory:"+section,cursor,rows,total);
        return new Page<>(List.copyOf(entries.values()),cursor+1,more,total);
    }
    private Entry entry(JsonObject row){long id=number(row,"listid");if(id<=0)return null;String title=text(row,"name").replaceAll("<[^>]*>","");return new Entry("kugou:"+account.userId()+":"+id,title.isBlank()?"酷狗音乐歌单":title,image(text(row,"pic","imgurl")),(int)Math.clamp(number(row,"count"),0,Integer.MAX_VALUE));}
    public Page<Track> liked(int cursor)throws Exception{
        if(liked==null){var seen=new HashSet<String>();int received=0;for(int page=0;page<400;page++){var data=directory(page);var rows=array(data,"info");if(!rows.isEmpty()&&!seen.add(KugouSigning.md5(rows.toString())))throw new IOException("酷狗歌单分页重复");
                for(var item:rows)if(item.isJsonObject()){var row=item.getAsJsonObject();if(number(row,"is_def")==2&&number(row,"is_del")!=1)liked=entry(row);}
                if(liked!=null)break;received+=rows.size();long total=number(data,"list_count")+number(data,"collect_count");if(rows.isEmpty()||total>0&&received>=total)break;}
            if(liked==null)throw new IOException("酷狗未返回我喜欢歌单");}
        return tracks(liked,cursor);
    }
    public Page<Track> tracks(Entry entry,int cursor)throws Exception{
        check();String prefix="kugou:"+account.userId()+":";if(!entry.key().startsWith(prefix)||!entry.key().substring(prefix.length()).matches("[1-9][0-9]{0,18}"))throw new IOException("酷狗歌单账号已变化");
        if(cursor<0||cursor>=400)throw new IOException("酷狗歌曲分页超出范围");
        var body=auth();body.addProperty("listid",Long.parseLong(entry.key().substring(prefix.length())));body.addProperty("area_code",1);body.addProperty("show_relate_goods",0);body.addProperty("allplatform",1);body.addProperty("show_cover",1);body.addProperty("type",0);body.addProperty("page",cursor+1);body.addProperty("pagesize",50);
        var data=call.call("/v4/get_list_all_file",Map.of("plat","1"),body,account);check();requireArray(data);var rows=array(data,"info");var tracks=new LinkedHashMap<String,Track>();
        for(var item:rows)if(item.isJsonObject()){var row=item.getAsJsonObject();try{
            var t=KugouTrack.parse(row);String mix=t.albumAudioId().isBlank()?text(row,"mixsongid"):t.albumAudioId();Long sort=null;
            if(row.has("sort")&&!row.get("sort").isJsonNull())try{sort=row.get("sort").getAsBigDecimal().longValueExact();}catch(RuntimeException ignored){}
            tracks.putIfAbsent(t.song().platformId(),new Track(t.song(),t.view(),t.albumId(),mix,sort));
        }catch(IllegalArgumentException ignored){/* 无身份的条目不能构造播放请求，页码仍按原始记录推进。 */}}
        int total=(int)Math.clamp(number(data,"count","total"),0,Integer.MAX_VALUE);boolean more=hasMore("tracks:"+entry.key(),cursor,rows,total);
        return new Page<>(List.copyOf(tracks.values()),cursor+1,more,total);
    }
    private boolean hasMore(String key,int cursor,JsonArray rows,int total)throws IOException{
        Progress previous=cursor==0?null:progress.get(key);
        if(cursor>0&&(previous==null||previous.next()!=cursor))throw new IOException("酷狗分页游标已失效，请刷新重试");
        int received=(previous==null?0:previous.received())+rows.size();Set<String> pages=previous==null?new HashSet<>():previous.pages();
        if(!rows.isEmpty()&&!pages.add(KugouSigning.md5(rows.toString())))throw new IOException("酷狗重复返回同一页");
        if(received>20000)throw new IOException("酷狗歌单超出读取上限");
        boolean more=total>0?received<total:!rows.isEmpty();
        if(more&&rows.isEmpty())throw new IOException("酷狗分页提前结束");
        progress.put(key,new Progress(cursor+1,received,pages));return more;
    }
    private static void requireArray(JsonObject data)throws IOException{if(data==null||!data.has("info")||!data.get("info").isJsonArray())throw new IOException("酷狗歌单返回不完整");}
    private static String image(String raw){String value=raw.replace("{size}","300");if(value.startsWith("//"))value="https:"+value;if(value.startsWith("http://"))value="https:"+value.substring(5);try{URI uri=URI.create(value);return "https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getRawUserInfo()==null?value:"";}catch(IllegalArgumentException e){return "";}}
}
