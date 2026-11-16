package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KugouRecommendApiTest {
    @Test void parsesOnlyTheHomepageNewReleaseSectionAndDeduplicatesTracks() throws Exception {
        String first = track("A", "首发歌曲");
        String page = """
                <html><body>
                <div dataobj='[{&quot;hash&quot;:&quot;%s&quot;,&quot;songname&quot;:&quot;搜索缓存歌曲&quot;}]'></div>
                <!-- 新歌首发新 -->
                <div dataobj='[%s,%s]'></div>
                <!-- other section -->
                <div dataobj='[%s]'></div>
                </body></html>
                """.formatted("B".repeat(32), first, first, track("C", "其他区歌曲"));

        List<KugouTrack> songs = new KugouRecommendApi(() -> page).recommend();

        assertEquals(List.of("A".repeat(32)), songs.stream().map(song -> song.song().platformId()).toList());
        assertEquals("首发歌曲", songs.getFirst().song().title());
    }

    @Test void missingOrEmptyRecommendationSectionFailsInsteadOfReturningSearchData() {
        assertThrows(IOException.class, () -> KugouRecommendApi.parse("<html><div>暂时没有推荐</div></html>"));
        assertThrows(IOException.class, () -> KugouRecommendApi.parse("<!-- 新歌首发新 --><div dataobj='[]'></div>"));
    }

    @Test void parsesTheOfficialHomepageFieldNames() throws Exception {
        String row = "{&#34;Hash&#34;:&#34;" + "E".repeat(32)
                + "&#34;,&#34;FileName&#34;:&#34;官方歌手 - 官方歌曲&#34;,&#34;timeLen&#34;:194000,"
                + "&#34;albumId&#34;:213249707,&#34;mixsongid&#34;:980679320,&#34;album_sizable_cover&#34;:"
                + "&#34;http://imge.kugou.com/stdmusic/{size}/cover.jpg&#34;,&#34;privilege&#34;:8}";
        var song = KugouRecommendApi.parse("<!-- 新歌首发新 --><span dataobj='[" + row + "]'></span>").getFirst();
        assertEquals("官方歌曲", song.song().title());
        assertEquals("官方歌手", song.song().artist());
        assertEquals("213249707", song.albumId());
        assertEquals(194, song.durationSeconds());
        assertTrue(song.song().vip());
    }

    @Test void eachFetchReadsTheHomepageAgain() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        try (var api = new KugouRecommendApi(() -> {
            requests.incrementAndGet();
            return "<!-- 新歌首发新 --><div dataobj='[" + track("D", "推荐") + "]'></div>";
        })) {
            api.recommend();
            api.recommend();
        }
        assertEquals(2, requests.get());
    }

    private static String track(String hash, String title) {
        return "{\"hash\":\"" + hash.repeat(32) + "\",\"songname\":\"" + title + "\",\"singername\":\"歌手\",\"album_id\":\"9\"}";
    }
}
