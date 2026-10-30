package dev.lzasn.cloudxmusic.client.omni;

import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.state.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackSnapshotTest {
    @Test void defensiveCollectionsAndTimelineDoNotDependOnNeteaseState(){
        var lines=new ArrayList<>(List.of(new Lyric.Line(1000,"QQ lyric","translation")));var queue=new ArrayList<SongView>();
        SongView song=SongView.from(new UnifiedSong("qq","Mid","QQ title","artist","album"),123,"cover");queue.add(song);
        var s=new PlaybackSnapshot(MusicSource.QQ,song,queue,lines,true,false,1,123,.5f,"SQ","SQ",LyricMode.BILINGUAL,PlaybackMode.REPEAT_LIST,false,"",false);
        lines.clear();queue.clear();assertEquals(1,s.queue().size());assertNull(s.lineAt(999));assertEquals("QQ lyric",s.lineAt(1000).text());assertTrue(s.hasTrack());
        assertThrows(UnsupportedOperationException.class,()->s.queue().clear());
    }
}
