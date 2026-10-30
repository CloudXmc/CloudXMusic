package dev.lzasn.cloudxmusic.client.state;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class MusicHandoffTest {
    @Test void titleStopsNeteaseAndInvalidatesPendingStartWithoutClearingItsQueue()throws Exception{
        MusicState state=new MusicState();List<Runnable> starts=new ArrayList<>();state.playbackHandoff(starts::add);
        Track track=new Track("网易云原曲目","歌手",120,"test-mid","专辑","");
        var current=MusicState.class.getDeclaredField("current");current.setAccessible(true);current.set(state,track);state.queue().add(track);
        var playing=MusicState.class.getDeclaredField("playing");playing.setAccessible(true);playing.setBoolean(state,true);
        var start=MusicState.class.getDeclaredMethod("startNetease",Track.class,long.class);start.setAccessible(true);start.invoke(state,track,0L);
        try(var lifecycle=new dev.lzasn.cloudxmusic.client.omni.PlaybackLifecycle(state::stopAll)){
            lifecycle.update(true,false);lifecycle.update(false,true);assertFalse(state.playing());
            lifecycle.joined();lifecycle.update(true,false);starts.getFirst().run();
            assertFalse(state.playing());assertEquals(track,state.current());assertEquals(List.of(track),state.queue());
        }
    }
    @Test void externalPauseKeepsCurrentAndQueueButInvalidatesPendingStart()throws Exception{
        MusicState state=new MusicState();List<Runnable> starts=new ArrayList<>();state.playbackHandoff(starts::add);
        Track track=new Track("网易云原曲目","歌手",120,"test-mid","专辑","");
        // 只构造内存夹具，不启动 Fabric、不写真实 state.json。
        var current=MusicState.class.getDeclaredField("current");current.setAccessible(true);current.set(state,track);state.queue().add(track);
        var playing=MusicState.class.getDeclaredField("playing");playing.setAccessible(true);playing.setBoolean(state,true);
        var start=MusicState.class.getDeclaredMethod("startNetease",Track.class,long.class);start.setAccessible(true);start.invoke(state,track,0L);
        assertEquals(1,starts.size());List<Track> queue=List.copyOf(state.queue());
        state.pauseForExternalPlayback().get(1,TimeUnit.SECONDS);
        assertEquals(track,state.current());assertEquals(queue,state.queue());assertFalse(state.playing());
        starts.get(0).run();assertFalse(state.playing());
    }
}
