package dev.lzasn.cloudxmusic.client.qqmusic;

import java.util.HashSet;

/** 手动公网只读探针，不读取本地凭证，也不参与离线单元测试。 */
public final class QQChartsPublicProbe {
    public static void main(String[] args)throws Exception{
        var api=new QQCatalogService(null,()->true).api();
        var charts=api.charts();
        if(charts.items().isEmpty())throw new IllegalStateException("公网榜单为空");
        var chart=charts.items().stream().filter(c->c.id().equals("26")).findFirst().orElse(charts.items().getFirst());
        var first=api.tracks(chart,0);
        if(first.items().isEmpty()||!first.hasMore())throw new IllegalStateException("榜单第一页或分页标识无效");
        var second=api.tracks(chart,first.nextOffset());
        var mids=new HashSet<String>();first.items().forEach(t->mids.add(t.song().platformId()));int before=mids.size();
        second.items().forEach(t->mids.add(t.song().platformId()));
        if(mids.size()<=before)throw new IllegalStateException("榜单第二页没有新 MID");
        System.out.printf("charts=%d chart=%s period=%s page1=%d page2=%d newMids=%d officialCover=%s%n",
                charts.items().size(),chart.id(),chart.period(),first.items().size(),second.items().size(),mids.size()-before,!chart.cover().isBlank());
    }
}
