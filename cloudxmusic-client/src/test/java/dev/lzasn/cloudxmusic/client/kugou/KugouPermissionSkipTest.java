package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.kugou.KugouPlaybackControllerTest.*;

class KugouPermissionSkipTest {
    @Test void lockedQueueIsDebouncedBoundedAndIgnoresRepeatOne()throws Exception{
        var main=new ConcurrentLinkedQueue<Runnable>();var now=new AtomicLong();var calls=new AtomicInteger();var notices=new ArrayList<String>();
        try(var c=new KugouPlaybackController(new Audio(),()->CompletableFuture.completedFuture(null),main::add,(s,q)->{calls.incrementAndGet();throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.SONG_LOCKED);},()->1)){
            c.clock(now::get);c.notices(notices::add);c.mode(PlaybackMode.REPEAT_ONE);c.play(List.of(song("1"),song("2"),song("1")),0);
            pump(main,()->c.view().error());for(int i=0;i<100;i++)c.tick();assertEquals(1,calls.get());assertEquals(1,notices.size());
            now.addAndGet(TimeUnit.SECONDS.toNanos(1));c.tick();pump(main,()->c.view().error()&&calls.get()==2);
            now.addAndGet(TimeUnit.SECONDS.toNanos(1));c.tick();assertTrue(c.view().status().contains("停止自动切歌"));for(int i=0;i<100;i++)c.tick();assertEquals(2,calls.get());
            assertEquals(List.of("当前歌曲为 VIP 专属，暂无播放资格","当前歌曲为 VIP 专属，暂无播放资格"),notices);
        }
    }
    @Test void accountChangeAndSourceRevocationCancelScheduledSkip()throws Exception{
        var main=new ConcurrentLinkedQueue<Runnable>();var now=new AtomicLong();var epoch=new AtomicLong(1);var calls=new AtomicInteger();var allowed=new AtomicBoolean(true);
        try(var c=new KugouPlaybackController(new Audio(),()->CompletableFuture.completedFuture(null),main::add,(s,q)->{calls.incrementAndGet();throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.SONG_LOCKED);},epoch::get)){
            c.clock(now::get);c.sourceGuard(allowed::get);c.play(List.of(song("1"),song("2")),0);pump(main,()->c.view().error());
            allowed.set(false);now.addAndGet(TimeUnit.SECONDS.toNanos(2));c.tick();allowed.set(true);c.tick();assertEquals(1,calls.get());
            c.play(List.of(song("1"),song("2")),0);pump(main,()->c.view().error()&&calls.get()==2);epoch.incrementAndGet();now.addAndGet(TimeUnit.SECONDS.toNanos(2));c.tick();assertFalse(c.hasTrack());assertEquals(2,calls.get());
        }
    }
    @Test void automaticPlaybackKeepsActualQualityWithoutManualDowngradeNotices()throws Exception{
        var main=new ConcurrentLinkedQueue<Runnable>();var notices=new ArrayList<String>();var audio=new Audio();
        try(var c=new KugouPlaybackController(audio,()->CompletableFuture.completedFuture(null),main::add,(s,q)->new KugouPlaybackResolver.Link("https://fs.kugou.com/a.mp3",120,KugouQuality.HQ,true),()->1)){
            c.notices(notices::add);c.play(List.of(song("1")),0);pump(main,()->audio.plays==1);
            audio.audible();pump(main,()->c.view().playing());
            assertEquals(KugouQuality.HQ,c.view().actualQuality());assertEquals("高品音质",c.view().quality());
            assertTrue(notices.isEmpty());assertEquals("",c.view().status());
        }
    }
}
