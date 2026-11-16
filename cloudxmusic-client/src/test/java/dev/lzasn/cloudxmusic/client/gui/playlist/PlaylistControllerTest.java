package dev.lzasn.cloudxmusic.client.gui.playlist;

import dev.lzasn.cloudxmusic.client.omni.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistControllerTest {
    static Track song(String id,Long sort){var s=new UnifiedSong("qq",id,id,"歌手","专辑",false);return new Track(s,SongView.from(s,180,""),"","",sort);}
    static class Worker extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();boolean closed;
        public void execute(Runnable r){if(closed)throw new RejectedExecutionException();tasks.add(r);}public void shutdown(){closed=true;}
        public List<Runnable> shutdownNow(){closed=true;var all=List.copyOf(tasks);tasks.clear();return all;}
        public boolean isShutdown(){return closed;}public boolean isTerminated(){return closed;}public boolean awaitTermination(long n,TimeUnit u){return closed;}
        void run(){while(!tasks.isEmpty())tasks.remove().run();}
    }
    static class Source implements PlaylistSource {
        List<Track> played=List.of();int calls,selected=-1;Object stamp=1;boolean fail,repeated;
        final Entry entry=new Entry("qq:123:8","测试歌单","",3);
        public Page<Entry> playlists(Section section,int cursor){return new Page<>(List.of(entry),1,false,1);}
        public Page<Track> liked(int cursor){return new Page<>(List.of(),0,false,0);}
        public Page<Track> tracks(Entry e,int cursor)throws Exception{calls++;if(fail&&cursor>0)throw new java.io.IOException("失败");return cursor==0||repeated?new Page<>(List.of(song("A",2L),song("B",1L)),2,true,3):new Page<>(List.of(song("C",3L)),3,false,3);}
        public Object playbackStamp(){return stamp;}public void play(List<Track> tracks,int index){played=List.copyOf(tracks);selected=index;}
    }
    static class Fixture implements AutoCloseable {
        Worker worker=new Worker();ArrayDeque<Runnable> main=new ArrayDeque<>();Source source=new Source();AtomicLong revision=new AtomicLong(1);AtomicBoolean logged=new AtomicBoolean(true);List<String> notices=new ArrayList<>();
        PlaylistController controller=new PlaylistController(active->source,main::add,revision::get,logged::get,notices::add,worker);
        void pump(){worker.run();while(!main.isEmpty())main.remove().run();}
        void detail(){controller.update(true);pump();controller.select(Section.OWNED);pump();controller.open(source.entry);pump();}
        public void close(){controller.close();}
    }
    @Test void playAllWaitsForAllPagesAndSubmitsOnceInSortOrder(){try(var f=new Fixture()){f.detail();assertTrue(f.source.played.isEmpty());f.controller.playAll();f.controller.playAll();f.worker.run();assertTrue(f.source.played.isEmpty());while(!f.main.isEmpty())f.main.remove().run();assertEquals(List.of("B","A","C"),f.source.played.stream().map(t->t.song().platformId()).toList());assertFalse(f.controller.view().playingAll());}}
    @Test void intermediateProgressIsVisibleBeforeCommit(){try(var f=new Fixture()){f.detail();f.controller.playAll();f.worker.run();assertFalse(f.main.isEmpty());f.main.remove().run();assertTrue(f.controller.view().status().contains("3/3"));assertTrue(f.controller.view().playingAll());assertTrue(f.source.played.isEmpty());}}
    @Test void failedRemainderPreservesQueue(){try(var f=new Fixture()){f.detail();f.source.fail=true;f.controller.playAll();f.pump();assertTrue(f.source.played.isEmpty());assertTrue(f.controller.view().error());}}
    @Test void accountChangeDiscardsAlreadyQueuedSuccess(){try(var f=new Fixture()){f.detail();f.controller.playAll();f.worker.run();f.revision.incrementAndGet();while(!f.main.isEmpty())f.main.remove().run();assertTrue(f.source.played.isEmpty());f.controller.update(true);assertNull(f.controller.view().selected());}}
    @Test void leavingPageAndNewerPlaybackCancelPendingCommit(){try(var f=new Fixture()){f.detail();f.controller.playAll();f.worker.run();f.source.stamp=2;while(!f.main.isEmpty())f.main.remove().run();assertTrue(f.source.played.isEmpty());f.controller.playAll();f.worker.run();f.controller.update(false);while(!f.main.isEmpty())f.main.remove().run();assertTrue(f.source.played.isEmpty());}}
    @Test void repeatedPageFailsWithoutQueueReplacement(){try(var f=new Fixture()){f.detail();f.source.repeated=true;f.controller.playAll();f.pump();assertTrue(f.controller.view().error());assertTrue(f.source.played.isEmpty());assertTrue(f.source.calls<10);}}
    @Test void unauthenticatedTabUsesExactNativeToastMessage(){try(var f=new Fixture()){f.logged.set(false);f.controller.update(true);f.controller.select(Section.OWNED);assertEquals(List.of("请先登录账号"),f.notices);assertEquals(Section.LIKED,f.controller.view().section());assertTrue(f.worker.tasks.isEmpty());}}
    @Test void closingCancelsWorkerAndClearsView(){var f=new Fixture();f.detail();f.controller.playAll();f.controller.close();assertTrue(f.worker.closed);assertTrue(f.controller.view().tracks().isEmpty());f.pump();assertTrue(f.source.played.isEmpty());}
    @Test void switchingTabDiscardsQueuedPlayAllSuccess(){try(var f=new Fixture()){f.detail();f.controller.playAll();f.worker.run();f.controller.select(Section.COLLECTED);f.pump();assertTrue(f.source.played.isEmpty());assertEquals(Section.COLLECTED,f.controller.view().section());}}
    @Test void clickingASongKeepsItsIdentityAfterSort(){try(var f=new Fixture()){f.detail();f.controller.playFrom(1);f.pump();assertEquals("B",f.source.played.get(f.source.selected).song().platformId());}}
    @Test void clickingFirstVisibleSongDoesNotBecomePlayAllAfterSort(){try(var f=new Fixture()){f.detail();f.controller.playFrom(0);f.pump();assertEquals("A",f.source.played.get(f.source.selected).song().platformId());}}
}
