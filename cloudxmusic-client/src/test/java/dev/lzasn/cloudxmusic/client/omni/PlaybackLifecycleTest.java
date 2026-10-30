package dev.lzasn.cloudxmusic.client.omni;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class PlaybackLifecycleTest {
    @Test void titleStopsOnceAndWorldMenusDoNotStopOrResumeMusic(){
        AtomicInteger stops=new AtomicInteger();
        try(var lifecycle=new PlaybackLifecycle(stops::incrementAndGet)){
            assertFalse(lifecycle.allowsPlayback());lifecycle.update(false,true);assertEquals(1,stops.get());
            for(int n=0;n<20;n++)lifecycle.update(false,true);assertEquals(1,stops.get());
            lifecycle.joined();lifecycle.update(true,false);assertTrue(lifecycle.allowsPlayback());
            lifecycle.update(true,false);assertEquals(1,stops.get(),"暂停菜单不应停止播放");
            lifecycle.update(true,true);assertFalse(lifecycle.allowsPlayback());assertEquals(2,stops.get());
            lifecycle.update(false,false);assertEquals(2,stops.get());
            lifecycle.update(true,false);assertTrue(lifecycle.allowsPlayback());assertEquals(2,stops.get(),"进入世界只放行，不自动恢复");
        }
        assertEquals(3,stops.get());
    }
    @Test void disconnectBlocksBeforeStopAndWaitsForANewWorld(){
        AtomicInteger stops=new AtomicInteger();var ref=new java.util.concurrent.atomic.AtomicReference<PlaybackLifecycle>();
        try(var lifecycle=new PlaybackLifecycle(()->{assertFalse(ref.get().allowsPlayback());stops.incrementAndGet();})){
            ref.set(lifecycle);lifecycle.update(true,false);lifecycle.disconnect();assertEquals(1,stops.get());
            lifecycle.update(true,false);assertFalse(lifecycle.allowsPlayback(),"断线后旧 level 尚未清空时不能重新授予播放权");
            lifecycle.disconnect();assertEquals(1,stops.get());
            lifecycle.joined();lifecycle.update(true,false);assertTrue(lifecycle.allowsPlayback());
            lifecycle.close();assertFalse(lifecycle.allowsPlayback());assertEquals(2,stops.get());
            lifecycle.joined();lifecycle.update(true,false);assertFalse(lifecycle.allowsPlayback());
        }
    }
}
