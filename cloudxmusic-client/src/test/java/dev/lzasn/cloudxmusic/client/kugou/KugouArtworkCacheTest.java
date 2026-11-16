package dev.lzasn.cloudxmusic.client.kugou;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KugouArtworkCacheTest {
    private static final BufferedImage COVER=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);

    @Test void failedDownloadsBackOffInsteadOfRetryingOnEveryFrame() {
        var io=new ManualExecutor();var attempts=new AtomicInteger();var time=new AtomicLong();
        try(var cache=new KugouArtworkCache(io,Runnable::run,url->{attempts.incrementAndGet();return null;},time::get)) {
            cache.image("cover");io.runAll();
            for(int frame=0;frame<100;frame++)cache.image("cover");
            io.runAll();assertEquals(1,attempts.get());
            time.set(TimeUnit.MINUTES.toNanos(1));cache.image("cover");io.runAll();
            assertEquals(2,attempts.get());
        }
    }

    @Test void duplicateAndDistinctPendingRequestsStayBounded() {
        var io=new ManualExecutor();
        try(var cache=new KugouArtworkCache(io,Runnable::run,url->COVER,System::nanoTime)) {
            for(int i=0;i<100;i++){cache.image("cover-"+i);cache.image("cover-"+i);}
            assertTrue(io.tasks.size()<=8);assertFalse(io.tasks.isEmpty());
            io.runAll();assertSame(COVER,cache.image("cover-0"));
            cache.image("cover-99");assertEquals(1,io.tasks.size());
        }
    }

    @Test void completedCoversHaveABoundedLeastRecentlyUsedCache() {
        var io=new ManualExecutor();var attempts=new AtomicInteger();
        try(var cache=new KugouArtworkCache(io,Runnable::run,url->{attempts.incrementAndGet();return COVER;},System::nanoTime)) {
            for(int i=0;i<70;i++){cache.image("cover-"+i);io.runAll();}
            assertSame(COVER,cache.image("cover-69"));assertEquals(70,attempts.get());
            assertNull(cache.image("cover-0"));io.runAll();assertEquals(71,attempts.get());
        }
    }

    @Test void closingDiscardsQueuedClientResultsAndRejectsNewRequests() {
        var io=new ManualExecutor();var client=new ArrayDeque<Runnable>();
        var cache=new KugouArtworkCache(io,client::add,url->COVER,System::nanoTime);
        cache.image("cover");io.runAll();assertEquals(1,client.size());
        cache.close();int revision=cache.revision();client.remove().run();
        assertEquals(revision,cache.revision());assertNull(cache.image("cover"));
        assertTrue(io.isShutdown());assertTrue(io.tasks.isEmpty());
    }

    private static final class ManualExecutor extends AbstractExecutorService {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();private boolean shutdown;
        public void execute(Runnable task){if(shutdown)throw new RejectedExecutionException();tasks.add(task);}
        void runAll(){while(!tasks.isEmpty())tasks.remove().run();}
        public void shutdown(){shutdown=true;}
        public List<Runnable> shutdownNow(){shutdown=true;var remaining=List.copyOf(tasks);tasks.clear();return remaining;}
        public boolean isShutdown(){return shutdown;}
        public boolean isTerminated(){return shutdown&&tasks.isEmpty();}
        public boolean awaitTermination(long timeout,TimeUnit unit){return isTerminated();}
    }
}
