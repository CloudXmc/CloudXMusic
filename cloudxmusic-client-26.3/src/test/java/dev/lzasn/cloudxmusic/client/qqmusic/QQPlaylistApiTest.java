package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.Section;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QQPlaylistApiTest {
    private static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    @Test void ownedExcludesLikedAndKeepsCoverAndCount() throws Exception {
        var api=new QQPlaylistApi((m,n,p)->{
            assertEquals("GetPlaylistByUin",n);assertEquals("123",p.get("uin").getAsString());
            return json("{\"v_playlist\":[{\"dirid\":201,\"tid\":7},{\"tid\":8,\"dirId\":1,\"title\":\"我的&amp;歌单\",\"song_cnt\":30,\"cover\":{\"default_url\":\"https://y.gtimg.cn/cover.jpg\"}}]}");
        },"123",()->"encrypted");
        var page=api.playlists(Section.OWNED,0);assertEquals(1,page.items().size());
        assertEquals("我的&歌单",page.items().getFirst().title());assertEquals(30,page.items().getFirst().count());
        assertEquals("https://y.gtimg.cn/cover.jpg",page.items().getFirst().cover());assertFalse(page.hasMore());
    }
    @Test void collectedCursorUsesRawRowsAndEncryptedOwner() throws Exception {
        var api=new QQPlaylistApi((m,n,p)->{
            assertEquals("CgiGetPlaylistFavInfo",n);assertEquals("encrypted",p.get("uin").getAsString());assertEquals(100,p.get("offset").getAsInt());
            return json("{\"v_list\":[{\"id\":8,\"name\":\"收藏\"},{\"id\":0},{\"id\":8}],\"hasmore\":1}");
        },"123",()->"encrypted");
        var page=api.playlists(Section.COLLECTED,100);assertEquals(103,page.nextCursor());assertEquals(1,page.items().size());assertTrue(page.hasMore());
    }
    @Test void missingArrayAndForeignAccountReferenceFail() throws Exception {
        var broken=new QQPlaylistApi((m,n,p)->new JsonObject(),"123",()->"encrypted");
        assertThrows(java.io.IOException.class,()->broken.playlists(Section.OWNED,0));
        var first=new QQPlaylistApi((m,n,p)->json("{\"v_playlist\":[{\"tid\":8,\"title\":\"歌单\"}]}"),"123",()->"encrypted");
        var ref=first.playlists(Section.OWNED,0).items().getFirst();
        var other=new QQPlaylistApi((m,n,p)->{fail("不能请求另一账号的歌单");return null;},"456",()->"other");
        assertThrows(java.io.IOException.class,()->other.tracks(ref,0));
    }
    @Test void songPagesRetainVipAndAdvancePastDuplicateRecords() throws Exception {
        var api=new QQPlaylistApi((m,n,p)->n.equals("GetPlaylistByUin")?json("{\"v_playlist\":[{\"tid\":8,\"title\":\"歌单\"}]}"):
                json("{\"songlist\":[{\"mid\":\"ABC\",\"title\":\"歌曲\",\"pay\":{\"pay_play\":1}},{\"mid\":\"ABC\",\"title\":\"歌曲\"}],\"total_song_num\":3,\"hasmore\":1}"),"123",()->"enc");
        var page=api.tracks(api.playlists(Section.OWNED,0).items().getFirst(),0);
        assertEquals(2,page.nextCursor());assertEquals(1,page.items().size());assertTrue(page.items().getFirst().display().vip());assertEquals(3,page.total());
    }
}
