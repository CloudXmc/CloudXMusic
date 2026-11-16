package dev.lzasn.cloudxmusic.client.omni;

import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.state.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackRouterTest {
    @Test void thirdSourcePublishesItsOwnSongQueueAndWordTimeline() {
        var selected=new AtomicReference<>(MusicSource.NETEASE);
        var old=new PlaybackSnapshot(MusicSource.NETEASE,SongView.empty(MusicSource.NETEASE),List.of(),List.of(),false,false,0,0,1,"","",LyricMode.ORIGINAL,PlaybackMode.REPEAT_LIST,false,"",false);
        var song=new SongView(MusicSource.KUGOU,"hash","酷狗歌曲","歌手","",12,"cover",true);
        var kg=new PlaybackSnapshot(MusicSource.KUGOU,song,List.of(song),List.of(new Lyric.Line(1000,"歌词","译文")),true,false,1.5,12,.4f,"标准音质","Hi-Res音质",LyricMode.BILINGUAL,PlaybackMode.REPEAT_ONE,true,"",false);
        var router=new PlaybackRouter(selected::get,()->old,()->kg);
        assertSame(old,router.snapshot());selected.set(MusicSource.KUGOU);
        assertSame(kg,router.snapshot());assertEquals("歌词",router.snapshot().lineAt(1500).text());
        assertTrue(router.snapshot().song().vip());assertTrue(router.snapshot().favorite());
        selected.set(MusicSource.QQ);assertSame(old,router.snapshot());
    }
}
