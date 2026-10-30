package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QQCatalogApiTest {
    @Test void dailyGzipIsDecodedEvenWhenServerIgnoresAcceptIdentityAndCannotInflateWithoutBound()throws Exception{
        String html="<li class='playlist__item'><a data-rid='222'>今日私享</a></li>";
        var output=new java.io.ByteArrayOutputStream();try(var gzip=new java.util.zip.GZIPOutputStream(output)){gzip.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        assertEquals(html,QQCatalogService.decodeBody(output.toByteArray(),"text/html; charset=UTF-8","gzip"));
        assertEquals(html,QQCatalogService.decodeBody(output.toByteArray(),"text/html",""));
        output.reset();try(var gzip=new java.util.zip.GZIPOutputStream(output)){gzip.write(new byte[2*1024*1024+1]);}
        byte[] oversized=output.toByteArray();assertThrows(java.io.IOException.class,()->QQCatalogService.decodeBody(oversized,"text/html","gzip"));
    }
    @Test void dailyHtmlHonorsLegacyGb2312CharsetWithoutCorruptingChinese()throws Exception{
        String html="<meta charset='gb2312'><li class='playlist__item'><a data-rid='222'>今日私享</a></li>";
        byte[] bytes=html.getBytes(java.nio.charset.Charset.forName("GB18030"));
        assertEquals("222",QQDailyDiscovery.parse(QQCatalogService.decodePage(bytes,"text/html")).id());
        assertEquals(html,QQCatalogService.decodePage(bytes,"text/html; charset=GBK"));
    }
    static JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    static String track(String mid){return "{\"mid\":\""+mid+"\",\"title\":\"歌\",\"interval\":180,\"album\":{\"mid\":\"Album\",\"name\":\"专辑\"},\"singer\":[{\"name\":\"歌手\"}],\"pay\":{\"pay_play\":1}}";}
    @Test void recommendUsesDangoSchemaAndRawOffset()throws Exception{
        var api=new QQCatalogApi((module,method,param)->{
            assertEquals("music.playlist.PlaylistSquare",module);assertEquals(20,param.get("From").getAsInt());
            return json("{\"List\":[{\"Playlist\":{\"basic\":{\"tid\":123,\"title\":\"推荐\",\"song_cnt\":30,\"cover\":{\"default_url\":\"https://y.gtimg.cn/cover.jpg\"}}}}],\"HasMore\":1}");
        },"12345",()->"encrypted");
        var page=api.recommend(20);assertEquals("123",page.items().getFirst().id());assertEquals(21,page.nextOffset());assertTrue(page.hasMore());
    }
    @Test void privateLikedDirectoryWorksWithoutPublicTid()throws Exception{
        var api=new QQCatalogApi((module,method,param)->{
            if(method.equals("GetPlaylistByUin"))return json("{\"v_playlist\":[{\"dirid\":201,\"dirName\":\"我喜欢\"}]}");
            assertEquals(201,param.get("dirid").getAsInt());assertEquals("encrypted",param.get("enc_host_uin").getAsString());
            assertTrue(param.get("disstid").getAsJsonPrimitive().isNumber(),"QQ CgiGetDiss 拒绝字符串 disstid");
            assertEquals(100,param.get("song_begin").getAsInt());
            return json("{\"songlist\":["+track("A")+","+track("A")+","+track("B")+"],\"hasmore\":1,\"total_song_num\":200}");
        },"12345",()->"encrypted");
        var page=api.tracks(api.liked(),100);assertEquals(103,page.nextOffset());assertEquals(2,page.items().size());assertTrue(page.hasMore());
        assertTrue(page.items().getFirst().display().vip());assertTrue(page.items().getFirst().display().coverUrl().contains("Album"));
    }
    @Test void dailySelectsOnlyActualDailyResourceAndNeverFirstOrdinaryPlaylist()throws Exception{
        String html="<div class='mod_for_u'><li class='playlist__item'><a class='playlist__link' data-rid='111'>普通推荐</a></li>"+
                "<li class='playlist__item'><a class='playlist__link' data-rid='222'><img src='https://y.gtimg.cn/day.jpg'></a><h3 class='playlist__name'>今日私享</h3></li></div>";
        assertEquals("222",QQDailyDiscovery.parse(html).id());
        assertThrows(java.io.IOException.class,()->QQDailyDiscovery.parse(html.replace("今日私享","热门歌曲")));
    }
    @Test void malformedSuccessIsAnErrorRatherThanEmptyData() {
        var api=new QQCatalogApi((m,n,p)->new JsonObject(),"12345",()->"encrypted");
        assertThrows(java.io.IOException.class,()->api.recommend(0));
        assertThrows(java.io.IOException.class,()->api.tracks(new QQCatalogApi.Playlist("123",0,"","测试",0,""),0));
    }
}
