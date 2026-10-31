package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApi.*;

/** 排行榜只读适配；榜单期次是资源身份的一部分，翻页不能换期。 */
final class QQChartsApi {
    private final Call call;
    QQChartsApi(Call call){this.call=call;}
    Page<Playlist> all()throws IOException,InterruptedException{
        JsonObject data=call.call("music.musicToplist.Toplist","GetAll",new JsonObject());
        JsonArray groups=array(data,"group");Map<String,Playlist> result=new LinkedHashMap<>();
        if(groups.size()>100)throw new IOException("QQ 排行榜分组过多");
        for(var group:groups){
            JsonArray rows=array(object(group,"排行榜分组"),"toplist");
            if(rows.size()>500)throw new IOException("QQ 排行榜条目过多");
            for(var row:rows){
                JsonObject o=object(row,"排行榜");long id=number(o,"topId",0);String period=text(o,"period");
                if(id<=0||id>Integer.MAX_VALUE||!validPeriod(period))continue;
                String cover=text(o,"frontPicUrl","headPicUrl");
                if(cover.startsWith("http://"))cover="https://"+cover.substring(7);
                Playlist chart=new Playlist(Long.toString(id),0,"",clean(text(o,"title")),
                        (int)Math.clamp(number(o,"totalNum",0),0,20000),QQAccountProfile.safeAvatar(cover),Kind.CHART,period);
                result.putIfAbsent(chart.key(),chart);
                if(result.size()>500)throw new IOException("QQ 排行榜数量过多");
            }
        }
        return new Page<>(List.copyOf(result.values()),result.size(),false);
    }
    Page<Track> tracks(Playlist chart,int offset)throws IOException,InterruptedException{
        if(chart.kind()!=Kind.CHART||!chart.id().matches("[0-9]{1,9}")||!validPeriod(chart.period())||offset<0||offset>20000)
            throw new IOException("QQ 排行榜标识或分页无效");
        JsonObject data=call.call("music.musicToplist.Toplist","GetDetail",params("topId",Integer.parseInt(chart.id()),"offset",offset,"num",100,"period",chart.period()));
        JsonObject meta=object(data,"data");
        if(!chart.period().equals(text(meta,"period"))||number(meta,"topId",Long.parseLong(chart.id()))!=Long.parseLong(chart.id()))
            throw new IOException("QQ 排行榜期次已变化，请返回后刷新榜单");
        JsonArray rows=array(data,"songInfoList");if(rows.size()>1000)throw new IOException("QQ 排行榜单页歌曲过多");
        int next=offset+rows.size();long total=number(meta,"totalNum",chart.count());
        return new Page<>(QQCatalogApi.tracks(rows),next,rows.size()>0&&(total>0?next<total:rows.size()==100));
    }
    private static boolean validPeriod(String value){return value.matches("[0-9_-]{1,32}");}
}
