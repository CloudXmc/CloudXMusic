package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class QQQueueMetadataTest {
    static UnifiedSong song(int n){return new UnifiedSong("qq","Song"+n,"歌曲"+n,"歌手","专辑");}
    static QQTrackMetadata.Info info(int n){return new QQTrackMetadata.Info("Media"+n,180+n,"Album"+n,"https://y.gtimg.cn/"+n+".jpg");}
    @Test void allQueuedTracksGetMetadataAndCoversWithoutBecomingCurrent()throws Exception{
        List<UnifiedSong> songs=java.util.stream.IntStream.range(0,20).mapToObj(QQQueueMetadataTest::song).toList();
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger(),active=new AtomicInteger(),max=new AtomicInteger();Set<String> covers=new HashSet<>();
        try(var q=new QQQueueMetadata(s->{calls.incrementAndGet();max.accumulateAndGet(active.incrementAndGet(),Math::max);Thread.sleep(3);active.decrementAndGet();return info(Integer.parseInt(s.platformId().substring(4)));},main::add,()->1,url->{covers.add(url);return true;})){
            QQPlaybackControllerTest.pump(main,()->{q.update(songs,true);return songs.stream().allMatch(s->q.song(s).durationSeconds()>0);});
            assertEquals(20,calls.get());assertEquals(20,covers.size());assertTrue(max.get()<=2);assertTrue(q.pendingCount()<=2);
            for(int n=0;n<20;n++){assertEquals(180+n,q.song(songs.get(n)).durationSeconds());assertEquals(info(n).coverUrl(),q.song(songs.get(n)).coverUrl());}
            for(int n=0;n<20;n++)q.update(songs,true);assertEquals(20,calls.get());assertEquals(20,covers.size());
        }
    }
    @Test void duplicateMidsShareOneRequestAndRemovedMetadataIsCleared()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger();
        try(var q=new QQQueueMetadata(s->{calls.incrementAndGet();return info(1);},main::add,()->1,url->true)){
            q.update(List.of(song(1),song(1)),true);QQPlaybackControllerTest.pump(main,()->q.song(song(1)).durationSeconds()>0);assertEquals(1,calls.get());
            q.update(List.of(),true);assertEquals(0,q.song(song(1)).durationSeconds());
            q.update(List.of(song(1)),true);QQPlaybackControllerTest.pump(main,()->q.song(song(1)).durationSeconds()>0);assertEquals(2,calls.get());
        }
    }
    @Test void rejectedCoverPrefetchIsRetriedWithoutRefetchingMetadata()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger calls=new AtomicInteger(),covers=new AtomicInteger();
        try(var q=new QQQueueMetadata(s->{calls.incrementAndGet();return info(1);},main::add,()->1,url->covers.incrementAndGet()>2)){
            q.update(List.of(song(1)),true);QQPlaybackControllerTest.pump(main,()->q.song(song(1)).durationSeconds()>0);
            for(int i=0;i<8;i++)q.update(List.of(song(1)),true);
            assertEquals(1,calls.get());assertEquals(3,covers.get());
        }
    }
    @Test void failedTrackRetriesAfterBackoffWithoutErasingGoodTracks()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger badCalls=new AtomicInteger();AtomicLong time=new AtomicLong();
        try(var q=new QQQueueMetadata(s->{if(s.platformId().equals("Song2")&&badCalls.incrementAndGet()==1)throw new java.io.IOException("offline");return info(1);},main::add,()->1,url->true,time::get)){
            List<UnifiedSong> songs=List.of(song(1),song(2));q.update(songs,true);
            QQPlaybackControllerTest.pump(main,()->q.pendingCount()==0);
            assertEquals(181,q.song(song(1)).durationSeconds());assertEquals(0,q.song(song(2)).durationSeconds());assertFalse(q.status().isBlank());
            q.update(songs,true);assertEquals(1,badCalls.get());
            time.set(TimeUnit.SECONDS.toNanos(30));q.update(songs,true);QQPlaybackControllerTest.pump(main,()->q.pendingCount()==0);
            assertEquals(2,badCalls.get());assertTrue(q.status().isBlank());assertEquals(181,q.song(song(1)).durationSeconds());
        }
    }
    @Test void sourceAccountRemovalAndCloseDiscardAlreadyQueuedCallbacks()throws Exception{
        for(String action:List.of("source","account","remove","close")){
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicLong revision=new AtomicLong(1);
            try(var q=new QQQueueMetadata(s->info(1),main::add,revision::get,url->true)){
                q.update(List.of(song(1)),true);QQAudioPlayerTest.waitFor(()->!main.isEmpty());
                switch(action){case "source"->q.update(List.of(song(1)),false);case "account"->{revision.incrementAndGet();q.update(List.of(),false);}case "remove"->q.update(List.of(),true);case "close"->q.close();}
                Runnable callback;while((callback=main.poll())!=null)callback.run();assertEquals(0,q.song(song(1)).durationSeconds(),action);
            }
        }
    }
}
