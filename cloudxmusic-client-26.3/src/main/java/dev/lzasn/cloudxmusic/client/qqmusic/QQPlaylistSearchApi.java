package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.*;

/** 独立的匿名歌单搜索；使用实测可用的桌面 search_type=3，页码按上游推进。 */
public final class QQPlaylistSearchApi {
    public record Item(Playlist playlist,String creator,long plays) {}
    public record Page(List<Item> items,int nextPage,boolean hasMore){public Page{items=List.copyOf(items);}}
    private final Call call;
    public QQPlaylistSearchApi(){this((module,method,p)->{
        var request=QQAuthHttp.request(module,method,p);request.getAsJsonObject("comm").addProperty("cv",1859);
        var root=QQAuthHttp.post(request,QQAuthHttp.emptyCookies(),Duration.ofSeconds(15));
        if(QQAuthHttp.integer(root,"code")!=0)throw new IOException("QQ 歌单搜索服务拒绝请求");
        var block=QQAuthHttp.object(root,"req_0");if(QQAuthHttp.integer(block,"code")!=0)throw new IOException("QQ 歌单搜索业务失败");
        return QQAuthHttp.object(block,"data");
    });}
    QQPlaylistSearchApi(Call call){this.call=call;}
    public Page search(String query,int page)throws IOException,InterruptedException{
        if(query==null||query.isBlank()||query.length()>256||page<1||page>100)throw new IllegalArgumentException("QQ 歌单搜索参数无效");
        var data=call.call("music.search.SearchCgiService","DoSearchForQQMusicDesktop",params("query",query.strip(),"search_type",3,"num_per_page",12,"page_num",page,"highlight",0));
        var meta=object(data,"meta");if(number(meta,"curpage",-1)!=page)throw new IOException("QQ 歌单搜索返回了错误页码");
        var body=object(data,"body");var rows=array(object(body,"songlist"),"list");if(rows.size()>100)throw new IOException("QQ 歌单搜索响应过大");
        Map<String,Item> unique=new LinkedHashMap<>();
        for(var element:rows){var row=object(element,"搜索歌单");String id=text(row,"dissid","tid");
            if(!id.matches("[0-9]{1,18}")||id.matches("0+"))continue;
            String title=clean(text(row,"dissname","title"));if(title.isBlank())continue;
            String creator=row.has("creator")&&row.get("creator").isJsonObject()?clean(text(row.getAsJsonObject("creator"),"name","nickname")):clean(text(row,"nickname"));
            var playlist=new Playlist(id,0,"",title,(int)Math.clamp(number(row,"song_count",0),0,Integer.MAX_VALUE),QQAccountProfile.safeAvatar(text(row,"imgurl","picurl")));
            unique.putIfAbsent(playlist.key(),new Item(playlist,creator,Math.max(0,number(row,"listennum",0))));
        }
        long next=number(meta,"nextpage",-1);boolean more=next>page&&next<=100&&!rows.isEmpty();
        return new Page(List.copyOf(unique.values()),more?(int)next:page+1,more);
    }
}
