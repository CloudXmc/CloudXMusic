package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class QQModeCrashTest {
    @Test void screenMustNotPassNullToImmutableLyricsIndexOf()throws Exception{
        // 原实机堆栈的直接触发条件：首句在 10s，当前进度为 0。
        List<Lyric.Line> lines=List.of(new Lyric.Line(10000,"第一句",""));
        assertThrows(NullPointerException.class,()->lines.indexOf(null));
        for(var mode:PlaybackMode.values()){
            var p=new PlaybackSnapshot(MusicSource.QQ,SongView.empty(MusicSource.QQ),List.of(),lines,true,false,0,100,.8f,"SQ FLAC","SQ",LyricMode.BILINGUAL,mode,false,"",false);
            assertEquals(-1,p.lyricIndexAt(0));assertNull(p.lineAt(0));
            assertEquals(0,p.lyricIndexAt(10000));assertEquals("第一句",p.lineAt(10000).text());
        }
    }
    @Test void cyclingQqModesWithEmptyQueueNeverAccessesOtherSourceOrStartsAudio(){
        var audio=new QQPlaybackControllerTest.Audio();
        try(var c=new QQPlaybackController(audio,()->{fail("切播放模式不能暂停网易云");return CompletableFuture.completedFuture(null);},Runnable::run,
                song->{fail("切播放模式不能请求音频");return null;},()->1)){
            for(int i=0;i<300;i++){
                c.cycleMode();
                assertNotNull(c.mode());assertTrue(c.queue().isEmpty());assertFalse(c.hasTrack());
            }
            assertTrue(audio.played.isEmpty());
        }
    }
}
