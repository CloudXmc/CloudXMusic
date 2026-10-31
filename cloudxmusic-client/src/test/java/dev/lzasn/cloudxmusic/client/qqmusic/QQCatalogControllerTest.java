package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.pump;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQCatalogApiTest.*;

class QQCatalogControllerTest {
    @Test void discoveryTopBannerHasOneDailyDestinationIncludingItsRightHalf()throws Exception{
        for(int x:new int[]{320,800,1020}){
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();
            try(var controller=new QQCatalogController(active->data((m,n,p)->n.equals("GetAll")?
                    json("{\"group\":[]}"):json("{\"songlist\":["+track("Daily")+"],\"hasmore\":0}")),main::add,()->1,()->true,null)){
                controller.update(QQCatalogController.Area.DISCOVER);pump(main,()->!controller.view().loading());
                assertTrue(QQCatalogPainter.click(controller,x,180));assertEquals(QQCatalogController.Mode.DAILY,controller.view().mode());
                pump(main,()->!controller.view().loading());assertEquals("Daily",controller.view().tracks().getFirst().song().platformId());
            }
        }
    }
    @Test void dailyCardAndPlayButtonUseTheNormalQueue()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();var audio=new QQPlaybackControllerTest.Audio();
        try(var player=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/"+s.platformId()+".mp3",180),()->1);
            var controller=new QQCatalogController(active->data((m,n,p)->n.equals("GetAll")?json("{\"group\":[]}"):
                    json("{\"songlist\":["+track("Daily")+","+track("Next")+"],\"hasmore\":0}")),main::add,()->1,()->true,player)){
            controller.update(QQCatalogController.Area.DISCOVER);pump(main,()->!controller.view().loading());
            var card=QQCatalogLayout.dailyCard();assertTrue(card.x()+card.w()<=1050);
            QQCatalogPainter.click(controller,card.x()+30,card.y()+35);assertEquals(QQCatalogController.Mode.DAILY,controller.view().mode());
            pump(main,()->!controller.view().loading());assertTrue(audio.played.isEmpty());
            controller.home();pump(main,()->!controller.view().loading());var play=QQCatalogLayout.dailyPlay();
            QQCatalogPainter.click(controller,play.cx(),play.cy());pump(main,()->audio.played.size()==1&&!player.view().loading());
            assertEquals("Daily",player.view().song().platformId());assertEquals(2,player.queue().size());
            audio.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.FINISHED,180,180,.8f,"");player.tick();
            pump(main,()->audio.played.size()==2&&!player.view().loading());assertEquals("Next",player.view().song().platformId());
            assertEquals(2,player.queue().size());assertFalse(player.view().error());
        }
    }
    @Test void recommendedPlaylistPlayUsesNormalQueueAndKeepsBrowsingIndependent()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();var audio=new QQPlaybackControllerTest.Audio();
        try(var player=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/"+s.platformId()+".mp3",180),()->1);
            var controller=new QQCatalogController(active->data((m,n,p)->n.equals("GetAll")?
                    json(QQChartsTest.charts()):
                    json("{\"data\":{\"period\":\"2026-10-30\",\"totalNum\":2},\"songInfoList\":["+track("A")+","+track("B")+"]}")),main::add,()->1,()->true,player)){
            controller.update(QQCatalogController.Area.DISCOVER);pump(main,()->!controller.view().loading());
            var tile=QQCatalogLayout.tile(0);QQCatalogPainter.click(controller,tile.x()+150,tile.y()+150);
            pump(main,()->audio.played.size()==1&&!player.view().loading());assertEquals(QQCatalogController.Mode.PLAYLIST,controller.view().mode());
            assertEquals(2,player.queue().size());assertEquals("A",player.view().song().platformId());
            QQCatalogPainter.click(controller,QQCatalogPainter.BACK.cx(),QQCatalogPainter.BACK.cy());pump(main,()->!controller.view().loading());
            assertEquals(QQCatalogController.Mode.HOME,controller.view().mode());assertEquals(1,audio.played.size());
            player.next();pump(main,()->audio.played.size()==2&&!player.view().loading());assertEquals("B",player.view().song().platformId());
            audio.v=new QQPlaybackAudio.View(QQPlaybackAudio.Status.FINISHED,180,180,.8f,"");player.tick();
            pump(main,()->audio.played.size()==3&&!player.view().loading());assertEquals("A",player.view().song().platformId());assertEquals(2,player.queue().size());
        }
    }
    @Test void likedCallbackInvalidatesTheCachedPageWithoutAnyMouseInteraction()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var controller=new QQCatalogController(active->data((m,n,p)->{
            if(n.equals("GetPlaylistByUin"))return json("{\"v_playlist\":[]}");
            requested.countDown();release.await();return json("{\"songlist\":["+track("Liked")+"],\"hasmore\":0}");
        }),main::add,()->1,()->true,null)){
            assertTrue(controller.update(QQCatalogController.Area.FAVORITES));assertTrue(requested.await(1,TimeUnit.SECONDS));
            assertTrue(controller.view().loading());assertFalse(controller.update(QQCatalogController.Area.FAVORITES));
            release.countDown();pump(main,()->!controller.view().loading());assertEquals(1,controller.view().tracks().size());
            assertTrue(controller.update(QQCatalogController.Area.FAVORITES),"异步回调后的首帧必须通知静态纹理重绘");
            assertFalse(controller.update(QQCatalogController.Area.FAVORITES),"无新数据时不能每帧重绘");
        }finally{release.countDown();}
    }
    @Test void squareGridCanScrollToTheLastPlaylistRowAndHitItsTitle()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var controller=new QQCatalogController(active->data((m,n,p)->{
            if(!n.equals("GetAll"))return json("{\"data\":{\"period\":\"2026-10-30\",\"totalNum\":0},\"songInfoList\":[]}");
            return chartList(12);        }),main::add,()->1,()->true,null)){
            controller.update(QQCatalogController.Area.DISCOVER);pump(main,()->!controller.view().loading());
            controller.scroll(20);assertEquals(8,controller.view().scroll(),"四列正方形卡片只能同时展示一行，末行不能被滚动上限遮住");
            assertTrue(QQCatalogPainter.click(controller,320,530));assertEquals("歌单9",controller.view().title());
            pump(main,()->!controller.view().loading());
        }
    }
    @Test void latePlayCardCannotRestartAfterExplicitStop()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();var audio=new QQPlaybackControllerTest.Audio();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try(var player=new QQPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,s->new QQPlaybackController.Resolved("https://a.qq.com/A.mp3",180),()->1);
            var controller=new QQCatalogController(active->data((m,n,p)->{
                if(n.equals("GetAll"))return json("{\"group\":[]}");entered.countDown();release.await();return json("{\"songlist\":["+track("A")+"],\"hasmore\":0}");
            }),main::add,()->1,()->true,player)){
            controller.update(QQCatalogController.Area.DISCOVER);pump(main,()->!controller.view().loading());controller.daily(true);
            assertTrue(entered.await(1,TimeUnit.SECONDS));player.stop();release.countDown();pump(main,()->!controller.view().loading());
            assertEquals(1,controller.view().tracks().size());assertTrue(audio.played.isEmpty());assertFalse(player.hasTrack());
        }finally{release.countDown();}
    }
    static void awaitCallback(Queue<Runnable> main)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(main.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(2);
        assertFalse(main.isEmpty(),"后台回调未在期限内到达");
    }
    QQCatalogController.DataSource data(QQCatalogApi.Call call){return new QQCatalogController.DataSource(){
        public QQCatalogApi api(){return new QQCatalogApi(call,"12345",()->"encrypted");}
        public QQCatalogApi.Playlist daily(){return new QQCatalogApi.Playlist("123",0,"","每日30首",30,"");}
    };}
    @Test void pageAccountSourceAndCloseInvalidateLateReplies()throws Exception{
        for(String change:List.of("page","account","source","close")){
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong account=new AtomicLong(1);AtomicBoolean qq=new AtomicBoolean(true);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
            try(var controller=new QQCatalogController(active->data((m,n,p)->{entered.countDown();for(;;){try{release.await();break;}catch(InterruptedException ignored){}}return json("{\"group\":[]}");}),main::add,account::get,qq::get,null)){
                controller.update(QQCatalogController.Area.DISCOVER);assertTrue(entered.await(1,TimeUnit.SECONDS));
                switch(change){case "account"->account.incrementAndGet();case "source"->qq.set(false);case "close"->controller.close();default->controller.update(QQCatalogController.Area.NONE);}
                release.countDown();QQCatalogControllerTest.awaitCallback(main);while(!main.isEmpty())main.remove().run();
                assertTrue(controller.view().playlists().isEmpty());
                assertFalse(controller.view().status().contains("官方排行榜 ·"));
            }finally{release.countDown();}
        }
    }
    @Test void trackPagesAppendDeduplicateAndRejectRepeatedPages()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger();List<Integer> offsets=new CopyOnWriteArrayList<>();
        try(var controller=new QQCatalogController(active->data((m,n,p)->{
            assertNotEquals(Thread.currentThread().getName(),"Test worker","网络必须在专用后台线程执行");
            int page=calls.getAndIncrement();offsets.add(p.get("song_begin").getAsInt());
            return json("{\"songlist\":["+track(page==0?"A":"B")+","+track("B")+"],\"hasmore\":1,\"total_song_num\":20}");
        }),main::add,()->1,()->true,null)){
            controller.update(QQCatalogController.Area.NONE);controller.update(QQCatalogController.Area.DISCOVER);
            // 取消首页后直接打开专门的歌曲页，旧首页结果不能覆盖。
            controller.open(new QQCatalogApi.Playlist("123",0,"","测试",20,""),false);
            pump(main,()->!controller.view().loading());assertEquals(2,controller.view().tracks().size());
            controller.loadMore();pump(main,()->!controller.view().loading());
            assertEquals(2,controller.view().tracks().size());assertTrue(controller.view().error());assertFalse(controller.view().hasMore());
            assertEquals(List.of(0,2),offsets);
        }
    }
    static com.google.gson.JsonObject chartList(int count){
        var rows=new com.google.gson.JsonArray();for(int i=0;i<count;i++)rows.add(json("{\"topId\":"+(i+1)+",\"period\":\"2026-10-30\",\"title\":\"歌单"+(i+1)+"\"}"));
        var group=new com.google.gson.JsonObject();group.add("toplist",rows);var groups=new com.google.gson.JsonArray();groups.add(group);var root=new com.google.gson.JsonObject();root.add("group",groups);return root;
    }
    @Test void horizontalScrollDoesNotRefetchAndRefreshReloadsCharts()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger();
        try(var controller=new QQCatalogController(active->data((m,n,p)->{assertEquals("GetAll",n);calls.incrementAndGet();return chartList(12);}),main::add,()->1,()->true,null)){
            controller.update(QQCatalogController.Area.DISCOVER);pump(main,()->!controller.view().loading());
            controller.scroll(50);assertEquals(8,controller.view().scroll());assertEquals(1,calls.get());
            controller.refresh();pump(main,()->!controller.view().loading());assertEquals(2,calls.get());
        }
    }
}