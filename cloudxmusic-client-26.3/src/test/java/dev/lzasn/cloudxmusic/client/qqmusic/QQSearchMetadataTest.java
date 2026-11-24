package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class QQSearchMetadataTest {
    @Test void searchEnrichesAllRowsIndependentlyAndClearsOldQuery()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();Set<String> covers=new HashSet<>();
        List<UnifiedSong> songs=java.util.stream.IntStream.range(0,30).mapToObj(QQQueueMetadataTest::song).toList();
        Thread owner=Thread.currentThread();AtomicInteger calls=new AtomicInteger();
        try(var metadata=new QQSearchMetadata(new QQQueueMetadata(song->{
            assertNotSame(owner,Thread.currentThread());calls.incrementAndGet();
            return new QQTrackMetadata.Info("Media",240,"Album","https://y.gtimg.cn/"+song.platformId()+".jpg",true);
        },main::add,()->1,url->{covers.add(url);return true;}))){
            var view=new QQSearchController.View(QQSearchController.Source.QQ,songs,20,false,false,"");
            QQPlaybackControllerTest.pump(main,()->{metadata.update(view,true);return songs.stream().allMatch(s->metadata.song(s).durationSeconds()>0);});
            assertEquals(30,calls.get());assertEquals(30,covers.size());
            assertTrue(metadata.song(songs.getFirst()).vip());assertFalse(songs.getFirst().vip(),"搜索 DTO 不可被回调修改");
            metadata.update(new QQSearchController.View(QQSearchController.Source.QQ,List.of(),0,false,false,""),true);
            assertEquals("",metadata.song(songs.getFirst()).coverUrl());
        }
    }
}
