package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouProviderTest {
    @TempDir Path dir;
    @Test void paginatedSearchRetainsHashAlbumCoverAndVip()throws Exception {
        List<Integer> pages=new ArrayList<>();
        try(var provider=new KugouProvider(new KugouAccountStore(dir.resolve("kugou/account.json")),new KugouApi((endpoint,params,headers,body,cookies)->{
            assertTrue(endpoint.endsWith("/api/v3/search/song"));int page=Integer.parseInt(params.get("page"));pages.add(page);
            String hash=Integer.toString(page).repeat(32);
            return KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"info\":[{\"hash\":\""+hash+"\",\"album_id\":\"12\",\"songname\":\"歌\",\"singername\":\"人\",\"duration\":200,\"privilege\":8,\"imgurl\":\"https://imge.kugou.com/{size}/a.jpg\"},{\"hash\":\""+"A".repeat(31)+page+"\",\"filename\":\"人 - 歌2\"}]}}");
        }))){
            var songs=provider.searchTracks("歌",1,2);assertEquals(List.of(1,2),pages);assertEquals(2,songs.size());
            var metadata=songs.get(1);assertEquals("12",metadata.albumId());assertTrue(metadata.song().vip());assertTrue(metadata.coverUrl().contains("300"));assertEquals(200,metadata.durationSeconds());
            assertEquals("人",songs.getFirst().song().artist());assertEquals("歌2",songs.getFirst().song().title());
        }
    }
    @Test void krcDownloadProducesSameTimedAndLineLyrics()throws Exception {
        String encoded=KugouKrcTest.encode("[1000,1000]<0,500,0>你<500,500,0>好");
        try(var provider=new KugouProvider(new KugouAccountStore(dir.resolve("account.json")),new KugouApi((e,p,h,b,c)->{
            if(e.endsWith("/search"))return KugouLoginServiceTest.json("{\"candidates\":[{\"id\":\"1\",\"accesskey\":\"key\"}]}");
            assertEquals("krc",p.get("fmt"));return KugouLoginServiceTest.json("{\"content\":\""+encoded+"\"}");
        }))){var track=KugouPlaybackResolverTest.track();provider.remember(track);var lyrics=provider.lyrics(track.song());assertEquals(2,lyrics.timed().getFirst().words().size());assertEquals("你好",lyrics.lyric().lineAt(1000).text());}
    }
    @Test void invalidKrcFallsBackToLrcWithoutInventingWordTimes()throws Exception {
        try(var provider=new KugouProvider(new KugouAccountStore(dir.resolve("account.json")),new KugouApi((e,p,h,b,c)->{
            if(e.endsWith("/search"))return KugouLoginServiceTest.json("{\"candidates\":[{\"id\":\"1\",\"accesskey\":\"key\"}]}");
            String content=p.get("fmt").equals("krc")?"bad":Base64.getEncoder().encodeToString("[00:01.000]歌词".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return KugouLoginServiceTest.json("{\"content\":\""+content+"\"}");
        }))){var lyrics=provider.lyrics(KugouPlaybackResolverTest.track().song());assertTrue(lyrics.timed().isEmpty());assertEquals("歌词",lyrics.lyric().lineAt(1000).text());}
    }
}
