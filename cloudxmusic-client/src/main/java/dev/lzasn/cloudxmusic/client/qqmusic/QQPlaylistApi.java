package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.*;
import java.io.IOException;
import java.util.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.params;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.array;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.object;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.text;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.number;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.flag;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.clean;
import dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.Call;
import dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.ProfileId;

/** 账号歌单只读接口；偏移量按原始条目推进，过滤系统目录不会漏掉下一页。 */
public final class QQPlaylistApi {
    private final Call call;
    private final String owner;
    private final ProfileId profile;
    QQPlaylistApi(Call call,String owner,ProfileId profile){this.call=call;this.owner=owner;this.profile=profile;}
    public Page<Entry> playlists(Section section,int cursor)throws Exception{
        requireAccount();checkCursor(cursor);
        if(section==Section.LIKED)throw new IllegalArgumentException("我喜欢使用歌曲接口");
        JsonObject data;
        if(section==Section.OWNED){if(cursor!=0)throw new IOException("QQ 自建目录没有下一页");data=call.call("music.musicasset.PlaylistBaseRead","GetPlaylistByUin",params("uin",owner));}
        else data=call.call("music.musicasset.PlaylistFavRead","CgiGetPlaylistFavInfo",params("uin",profile.load(),"offset",cursor,"size",100));
        JsonArray rows=array(data,section==Section.OWNED?"v_playlist":"v_list");var entries=new LinkedHashMap<String,Entry>();
        for(var item:rows){var row=object(item,"歌单");if(number(row,row.has("dirId")?"dirId":"dirid",0)==201)continue;
            Entry entry=entry(row);if(entry!=null)entries.putIfAbsent(entry.key(),entry);}
        boolean more=section==Section.COLLECTED&&flag(data,"hasmore",rows.size()==100);
        if(more&&rows.isEmpty())throw new IOException("QQ 歌单分页为空但仍有下一页");
        return new Page<>(List.copyOf(entries.values()),cursor+rows.size(),more,(int)Math.clamp(number(data,"total",0),0,20000));
    }
    private Entry entry(JsonObject row)throws IOException{
        String id=text(row,"tid","id");if(!id.matches("[1-9][0-9]{0,18}"))return null;
        try{Long.parseLong(id);}catch(NumberFormatException e){return null;}
        String title=clean(text(row,"title","dirName","name"));if(title.isBlank())title="QQ 音乐歌单";
        String cover=text(row,"picurl","picUrl","logo","bigpicUrl");if(cover.isBlank())cover=text(row.has("cover")&&row.get("cover").isJsonObject()?row.getAsJsonObject("cover"):new JsonObject(),"default_url","medium_url");
        int count=(int)Math.clamp(number(row,row.has("song_cnt")?"song_cnt":row.has("songNum")?"songNum":"songnum",0),0,Integer.MAX_VALUE);
        return new Entry("qq:"+owner+":"+id,title,QQAccountProfile.safeAvatar(cover),count);
    }
    public Page<Track> tracks(Entry entry,int cursor)throws Exception{
        requireAccount();checkCursor(cursor);String prefix="qq:"+owner+":";
        if(!entry.key().startsWith(prefix)||!entry.key().substring(prefix.length()).matches("[1-9][0-9]{0,18}"))throw new IOException("QQ 歌单账号已变化");
        String id=entry.key().substring(prefix.length());
        // 共享原有解析器，同时读取总数用于加载进度，不改变原目录的分页约定。
        JsonObject data=call.call("music.srfDissInfo.DissInfo","CgiGetDiss",params("disstid",Long.parseLong(id),"dirid",0,"song_begin",cursor,"song_num",100,"userinfo",true,"tag",true,"orderlist",true));
        return trackPage(data,cursor);
    }
    public Page<Track> liked(int cursor)throws Exception{
        requireAccount();checkCursor(cursor);var api=new QQCatalogApi(call,owner,profile);var ref=api.liked();
        var p=params("disstid",Long.parseLong(ref.id()),"dirid",201,"song_begin",cursor,"song_num",100,"userinfo",true,"tag",true,"orderlist",true);
        if(ref.id().equals("0"))p.addProperty("enc_host_uin",profile.load());
        return trackPage(call.call("music.srfDissInfo.DissInfo","CgiGetDiss",p),cursor);
    }
    private static Page<Track> trackPage(JsonObject data,int cursor)throws IOException{
        var rows=array(data,"songlist");int total=(int)Math.clamp(number(data,"total_song_num",0),0,Integer.MAX_VALUE);int next=cursor+rows.size();
        boolean more=total>0?next<total:flag(data,"hasmore",rows.size()==100);
        if(more&&rows.isEmpty())throw new IOException("QQ 歌单歌曲分页提前结束");
        var songs=QQCatalogApi.tracks(rows).stream().map(t->new Track(t.song(),t.display(),"","",null)).toList();
        return new Page<>(songs,next,more,total);
    }
    private void requireAccount()throws IOException{if(owner.isBlank())throw new IOException("请先登录账号");}
    private static void checkCursor(int cursor){if(cursor<0||cursor>20000)throw new IllegalArgumentException("歌单分页超出范围");}
}
