package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout;
import dev.lzasn.cloudxmusic.client.gui.design.MusicPainter;
import dev.lzasn.cloudxmusic.client.gui.design.MusicView;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import dev.lzasn.cloudxmusic.client.state.Track;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.pump;

class QQPlaylistSearchTest {
    static JsonObject response(){return JsonParser.parseString("{\"body\":{\"songlist\":{\"list\":[{\"dissid\":\"123\",\"dissname\":\"<em>歌曲</em>&amp;合集\",\"imgurl\":\"https://qpic.y.qq.com/cover\",\"song_count\":20,\"listennum\":123456,\"creator\":{\"name\":\"作者\"}},{\"dissid\":\"123\",\"dissname\":\"重复\"},{\"dissid\":\"bad-mid\"}]}},\"meta\":{\"curpage\":1,\"nextpage\":2,\"sum\":40}}").getAsJsonObject();}
    @Test void desktopSearchMapsVerifiedShapeAndConsumesRawPage()throws Exception{
        var api=new QQPlaylistSearchApi((m,n,p)->{
            assertEquals("music.search.SearchCgiService",m);assertEquals("DoSearchForQQMusicDesktop",n);
            assertEquals(3,p.get("search_type").getAsInt());assertEquals(1,p.get("page_num").getAsInt());return response();});
        var page=api.search("词",1);assertEquals(1,page.items().size());assertEquals(2,page.nextPage());assertTrue(page.hasMore());
        var item=page.items().getFirst();assertEquals("123",item.playlist().id());assertEquals("歌曲&合集",item.playlist().title());assertEquals("作者",item.creator());assertEquals(123456,item.plays());
    }
    @Test void malformedMissingAndWrongPagesAreErrors(){
        for(String text:List.of("{}","{\"body\":{\"songlist\":{}},\"meta\":{\"curpage\":1}}","{\"body\":{\"songlist\":{\"list\":[]}},\"meta\":{\"curpage\":2}}")){
            assertThrows(java.io.IOException.class,()->new QQPlaylistSearchApi((m,n,p)->JsonParser.parseString(text).getAsJsonObject()).search("词",1));
        }
    }
    @Test void accountAndPageChangesRevokeQueuedCallbacksAndRequestIsDebounced()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong account=new AtomicLong(1);AtomicInteger changes=new AtomicInteger();
        try(var c=new QQPlaylistSearchController((q,p)->new QQPlaylistSearchApi((m,n,v)->response()).search(q,p),main::add,changes::incrementAndGet,account::get)){
            c.update(true);c.playlists(true);assertTrue(c.submit("词"));assertFalse(c.submit("重复"));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(main.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(5);assertFalse(main.isEmpty());
            account.incrementAndGet();c.update(true);main.forEach(Runnable::run);main.clear();assertTrue(c.view().items().isEmpty());assertFalse(c.view().loading());
            c.submit("新词");pump(main,()->!c.view().loading());assertEquals(1,c.view().items().size());
            c.loadMore();c.update(false);main.forEach(Runnable::run);assertFalse(c.view().loading());
        }
    }
    @Test void searchDetailsSurviveFramesAndBackDoesNotLoadCharts()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();
        try(var c=new QQCatalogController(active->new QQCatalogControllerTest().data((m,n,p)->{assertEquals("CgiGetDiss",n);return JsonParser.parseString("{\"songlist\":["+QQCatalogApiTest.track("A")+"],\"hasmore\":0}").getAsJsonObject();}),main::add,()->1,()->true,null)){
            c.update(QQCatalogController.Area.SEARCH);c.open(new QQCatalogApi.Playlist("123",0,"","歌单",1,""),false);c.update(QQCatalogController.Area.SEARCH);
            pump(main,()->!c.view().loading());assertTrue(c.searchDetail());assertEquals(1,c.view().tracks().size());
            c.back();assertFalse(c.searchDetail());assertFalse(c.view().loading());
        }
    }

    @Test void playlistPainterKeepsGridHitsAndCoverVisible() throws Exception {
        var song = new QQCatalogApi.Playlist("123", 0, "", "测试歌单", 20, "cover");
        var view = new QQPlaylistSearchController.View(List.of(new QQPlaylistSearchApi.Item(song, "作者", 1234)), 0, false, false, "已加载");
        var music = musicView();
        var base = MusicPainter.paint(music, new MusicLayout(MusicView.Page.SEARCH, 0, 0), 1, key -> null);
        var cover = new BufferedImage(40, 40, BufferedImage.TYPE_INT_RGB);
        var brush = cover.createGraphics();
        brush.setColor(new Color(0x33AA77));
        brush.fillRect(0, 0, 40, 40);
        brush.dispose();
        var output = Path.of("../outputs/delivery/2.5.0-alpha.2/previews");
        Files.createDirectories(output);
        try {
            for (boolean dark : new boolean[]{false, true}) {
                FrostCanvas.setDark(dark);
                base = MusicPainter.paint(music, new MusicLayout(MusicView.Page.SEARCH, 0, 0), 1, key -> null);
                var painted = QQPlaylistSearchPainter.decorate(base, 1, music, true, view, key -> cover);
                assertEquals(0, QQPlaylistSearchPainter.itemAt(292 + 20, 208 + 20, 0, 1));
                assertEquals(-1, QQPlaylistSearchPainter.itemAt(292 + 180, 208 + 20, 0, 1));
                assertNotEquals(base.getRGB(MusicLayout.PAD + 292 + 20, MusicLayout.PAD + 208 + 20),
                        painted.getRGB(MusicLayout.PAD + 292 + 20, MusicLayout.PAD + 208 + 20));
                assertEquals(1, QQPlaylistSearchPainter.itemAt(292 + 20, 208 + 20, 0, 1) + 1);
                ImageIO.write(painted, "png", output.resolve(dark ? "playlist-search-dark.png" : "playlist-search-light.png").toFile());
            }
        } finally {
            FrostCanvas.setDark(false);
        }
    }

    private static MusicView musicView() {
        return new MusicView(MusicView.Page.SEARCH, new Track("歌曲", "歌手", 200, "123", "专辑", ""),
                List.of(), List.of(), List.of(), 0, true, false, "", "歌单", true, true, "", "标准", "原文", true,
                PlaybackMode.values()[0], false, "test", "", 0, -1, .5f, "", FrostCanvas.dark,
                1f, 1f, .5f, .5f, "H");
    }
}
