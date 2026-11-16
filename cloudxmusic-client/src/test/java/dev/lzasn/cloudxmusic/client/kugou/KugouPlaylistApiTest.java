package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.Section;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouPlaylistApiTest {
    static final KugouAccount ACCOUNT=new KugouAccount("123","test-token","a".repeat(32),"测试",List.of());
    static JsonObject json(String s){return JsonParser.parseString(s).getAsJsonObject();}
    static String rows="{\"info\":[{\"listid\":1,\"type\":0,\"is_def\":2},{\"listid\":2,\"type\":0,\"name\":\"自建\",\"count\":120,\"pic\":\"https://imge.kugou.com/{size}/cover.jpg\"},{\"listid\":3,\"type\":1,\"name\":\"收藏\"},{\"listid\":4,\"type\":0,\"is_del\":1}],\"list_count\":3,\"collect_count\":1}";
    @Test void categoryFilteringKeepsRawPaginationAndUsesCurrentToken() throws Exception {
        var api=new KugouPlaylistApi((path,params,body,account)->{
            assertEquals("/v7/get_all_list",path);assertEquals("test-token",body.get("token").getAsString());assertEquals("123",body.get("userid").getAsString());assertSame(ACCOUNT,account);return json(rows);
        },ACCOUNT,()->true);
        var own=api.playlists(Section.OWNED,0);assertEquals(1,own.items().size());assertEquals("自建",own.items().getFirst().title());
        assertEquals(120,own.items().getFirst().count());assertTrue(own.items().getFirst().cover().contains("/300/"));assertFalse(own.hasMore());
        assertEquals("收藏",api.playlists(Section.COLLECTED,0).items().getFirst().title());
    }
    @Test void trackMetadataAndSortSurviveCloudParsing() throws Exception {
        var api=new KugouPlaylistApi((path,p,b,a)->path.endsWith("get_all_list")?json(rows):json("{\"info\":[{\"hash\":\""+"b".repeat(32)+"\",\"name\":\"歌手 - 歌曲\",\"album_id\":42,\"mixsongid\":73,\"sort\":9,\"privilege\":1,\"timelen\":180000}],\"count\":1}"),ACCOUNT,()->true);
        var page=api.tracks(api.playlists(Section.OWNED,0).items().getFirst(),0);var track=page.items().getFirst();
        assertEquals("42",track.albumId());assertEquals("73",track.albumAudioId());assertEquals(9L,track.order());assertEquals(180,track.display().durationSeconds());assertTrue(track.display().vip());assertFalse(page.hasMore());
    }
    @Test void malformedOrCancelledResponseIsNotAnEmptyLibrary() {
        var broken=new KugouPlaylistApi((p,v,b,a)->new JsonObject(),ACCOUNT,()->true);
        assertThrows(java.io.IOException.class,()->broken.playlists(Section.OWNED,0));
        var cancelled=new KugouPlaylistApi((p,v,b,a)->{fail("取消后不能发请求");return null;},ACCOUNT,()->false);
        assertThrows(InterruptedException.class,()->cancelled.playlists(Section.OWNED,0));
    }
    @Test void sharedLocalIdCannotReadAnotherAccount() throws Exception {
        var api=new KugouPlaylistApi((p,v,b,a)->json(rows),ACCOUNT,()->true);var ref=api.playlists(Section.OWNED,0).items().getFirst();
        var other=new KugouPlaylistApi((p,v,b,a)->{fail("不能读旧账号");return null;},new KugouAccount("456","token","a".repeat(32),"",List.of()),()->true);
        assertThrows(java.io.IOException.class,()->other.tracks(ref,0));
    }
    @Test void serverCanReturnLessThanRequestedWithoutTruncatingPlaylist() throws Exception {
        var api=new KugouPlaylistApi((path,p,b,a)->path.endsWith("get_all_list")?json(rows):json("{\"info\":[{\"hash\":\""+(b.get("page").getAsInt()==1?"b":"c").repeat(32)+"\",\"name\":\"歌手 - 歌曲\"}],\"count\":2}"),ACCOUNT,()->true);
        var entry=api.playlists(Section.OWNED,0).items().getFirst();var first=api.tracks(entry,0);
        assertTrue(first.hasMore(),"服务端一页只给一首，总数仍是两首");var second=api.tracks(entry,first.nextCursor());assertFalse(second.hasMore());
    }
}
