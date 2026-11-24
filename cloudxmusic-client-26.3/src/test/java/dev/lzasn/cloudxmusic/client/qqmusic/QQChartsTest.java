package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApiTest.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.pump;

class QQChartsTest {
    static String charts(){return "{\"group\":[{\"toplist\":[{\"topId\":26,\"title\":\"热歌榜\",\"totalNum\":300,\"period\":\"2026-10-30\",\"frontPicUrl\":\"http://y.gtimg.cn/chart.jpg\"}]}]}";}
    @Test void chartIdentityPeriodAndCoverAreDistinctFromPlaylists()throws Exception{
        var api=new QQChartsApi((m,n,p)->{assertEquals("GetAll",n);return json(charts());});
        var chart=api.all().items().getFirst();assertEquals("chart:26:2026-10-30",chart.key());assertEquals("2026-10-30",chart.period());
        assertEquals("https://y.gtimg.cn/chart.jpg",chart.cover());assertEquals(QQCatalogApi.Kind.CHART,chart.kind());
    }
    @Test void paginationPinsPeriodUsesRawOffsetAndRejectsChangedPeriod()throws Exception{
        var chart=new QQChartsApi((m,n,p)->json(charts())).all().items().getFirst();
        var api=new QQChartsApi((m,n,p)->{
            assertEquals("GetDetail",n);assertEquals(26,p.get("topId").getAsInt());assertEquals("2026-10-30",p.get("period").getAsString());
            assertEquals(100,p.get("offset").getAsInt());
            return json("{\"data\":{\"topId\":26,\"period\":\"2026-10-30\",\"totalNum\":300},\"songInfoList\":["+track("A")+","+track("A")+","+track("B")+"]}");
        });
        var page=api.tracks(chart,100);assertEquals(103,page.nextOffset());assertEquals(2,page.items().size());assertTrue(page.hasMore());
        assertThrows(IOException.class,()->new QQChartsApi((m,n,p)->json("{\"data\":{\"period\":\"2026-10-31\"},\"songInfoList\":[]}")).tracks(chart,0));
    }
    @Test void discoveryUsesChartsAndAppendsDeduplicatedDetailPages()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();List<String> calls=new ArrayList<>();
        var catalog=new QQCatalogApi((m,n,p)->{
            calls.add(n);if(n.equals("GetAll"))return json(charts());
            int offset=p.get("offset").getAsInt();
            return json("{\"data\":{\"topId\":26,\"period\":\"2026-10-30\",\"totalNum\":4},\"songInfoList\":["+track(offset==0?"A":"B")+","+track(offset==0?"B":"C")+"]}");
        },"",()->"");
        try(var controller=new QQCatalogController(active->new QQCatalogController.DataSource(){public QQCatalogApi api(){return catalog;}public QQCatalogApi.Playlist daily(){return null;}},main::add,()->1,()->true,null)){
            controller.update(QQCatalogController.Area.DISCOVER);pump(main,()->!controller.view().loading());
            assertEquals(1,controller.view().playlists().size());controller.open(controller.view().playlists().getFirst(),false);
            pump(main,()->!controller.view().loading());controller.loadMore();pump(main,()->!controller.view().loading());
            assertEquals(List.of("A","B","C"),controller.view().tracks().stream().map(t->t.song().platformId()).toList());assertFalse(controller.view().hasMore());
            assertEquals(List.of("GetAll","GetDetail","GetDetail"),calls);
        }
    }
}
