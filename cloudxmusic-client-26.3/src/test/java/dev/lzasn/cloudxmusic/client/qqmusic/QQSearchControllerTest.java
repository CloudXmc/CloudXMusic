package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.OnlineMusicProvider;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.Track;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSearchController.Source.*;
import static org.junit.jupiter.api.Assertions.*;

class QQSearchControllerTest {
    private final LinkedBlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
    private final AtomicInteger changes = new AtomicInteger();
    private final AtomicReference<Exception> error = new AtomicReference<>();

    @Test void routesOnWorkerAndAppliesResultsOnlyOnMainCallback() throws Exception {
        Thread owner = Thread.currentThread();
        AtomicReference<Thread> worker = new AtomicReference<>();
        try (QQSearchController controller = controller(id -> {
            assertEquals("qq", id);
            return qq((keyword, offset, limit) -> {
                worker.set(Thread.currentThread());
                assertEquals("晴天", keyword); assertEquals(0, offset); assertEquals(30, limit);
                return songs(1);
            });
        })) {
            controller.switchTo(QQ, "网易云草稿", 0);
            assertTrue(controller.submit(" 晴天 "));
            assertFalse(controller.submit("重复"));
            Runnable callback = next();
            assertNotSame(owner, worker.get());
            assertTrue(controller.view().loading());
            assertTrue(controller.view().songs().isEmpty());
            callback.run();
            assertFalse(controller.view().loading());
            assertEquals("Mid0", controller.view().songs().getFirst().platformId());
            assertTrue(controller.neteaseTracks().isEmpty());
        }
    }

    @Test void slowNeteaseDoesNotBlockQqOrOverwriteSelectedTab() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        Track original = new Track("网易云原曲", "甲", 123, "123", "专辑", "cover");
        var netease = new NeteaseSearchAdapter((keyword, limit) -> {
            started.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) throw new IOException("timed out");
            return List.of(original);
        });
        try (QQSearchController controller = controller(id -> id.equals("qq") ? qq((k, o, l) -> songs(1)) : netease)) {
            controller.submit("网易云查询");
            assertTrue(started.await(2, TimeUnit.SECONDS));
            controller.switchTo(QQ, "网易云草稿", 0);
            controller.submit("QQ查询");
            next().run();
            assertEquals(QQ, controller.source());
            assertEquals("Mid0", controller.view().songs().getFirst().platformId());
            release.countDown();
            next().run();
            assertEquals(QQ, controller.source());
            assertEquals("Mid0", controller.view().songs().getFirst().platformId());
            controller.switchTo(NETEASE, "QQ草稿", 0);
            assertEquals("网易云草稿", controller.draft());
            assertSame(original, controller.neteaseTracks().getFirst());
            assertEquals("netease", controller.view().songs().getFirst().source());
        } finally { release.countDown(); }
    }

    @Test void preservesSeparateDraftsResultsAndScroll() throws Exception {
        Track original = new Track("原曲", "甲", 1, "123", "", "");
        try (QQSearchController controller = controller(id -> qq((k, o, l) -> songs(30)))) {
            controller.seedNetease(List.of(original), "原输入");
            controller.switchTo(QQ, "网易云草稿", 5);
            controller.submit("QQ查询"); next().run();
            controller.scrollQq(1000);
            assertEquals(22, controller.scroll());
            controller.switchTo(NETEASE, "QQ草稿", controller.scroll());
            assertEquals("网易云草稿", controller.draft());
            assertEquals(5, controller.scroll());
            assertSame(original, controller.neteaseTracks().getFirst());
            controller.switchTo(QQ, controller.draft(), controller.scroll());
            assertEquals("QQ草稿", controller.draft());
            assertEquals(22, controller.scroll());
            assertEquals(30, controller.view().songs().size());
            controller.scrollQq(-1000); assertEquals(0, controller.scroll());
            assertThrows(UnsupportedOperationException.class, () -> controller.view().songs().clear());
        }
    }

    @Test void reportsFailureWithoutPretendingItIsAnEmptySuccess() throws Exception {
        try (QQSearchController controller = controller(id -> qq((k, o, l) -> { throw new IOException("secret-url"); }))) {
            controller.switchTo(QQ, "", 0);
            controller.submit("查询"); next().run();
            assertTrue(controller.view().error());
            assertFalse(controller.view().loading());
            assertTrue(controller.view().status().contains("失败"));
            assertFalse(controller.view().status().contains("secret-url"));
            assertInstanceOf(IOException.class, error.get());
        }
    }

    @Test void closeDiscardsAlreadyQueuedCallback() throws Exception {
        QQSearchController controller = controller(id -> qq((k, o, l) -> songs(1)));
        controller.switchTo(QQ, "", 0); controller.submit("查询");
        Runnable late = next();
        controller.close();
        int before = changes.get(); late.run();
        assertEquals(before, changes.get());
        assertTrue(controller.view().songs().isEmpty());
        assertFalse(controller.submit("不能再查询"));
        controller.close();
    }

    @Test void closeInterruptsOutstandingNetworkWorker() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), interrupted = new CountDownLatch(1);
        QQSearchController controller = controller(id -> qq((k, o, l) -> {
            entered.countDown();
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException exception) { interrupted.countDown(); throw exception; }
            return List.of();
        }));
        try {
            controller.switchTo(QQ, "", 0); controller.submit("查询");
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            controller.close();
            assertTrue(interrupted.await(2, TimeUnit.SECONDS));
            next().run();
            assertFalse(controller.view().loading());
            assertTrue(controller.view().songs().isEmpty());
        } finally { controller.close(); }
    }

    @Test void rejectsMismatchedProviderSource() throws Exception {
        try (QQSearchController controller = controller(id -> qq((k, o, l) ->
                List.of(new UnifiedSong("netease", "123", "错音源", "", ""))))) {
            controller.switchTo(QQ, "", 0); controller.submit("查询"); next().run();
            assertTrue(controller.view().error());
            assertTrue(controller.neteaseTracks().isEmpty());
        }
    }

    @Test void blankSearchDoesNotUseRouter() {
        try (QQSearchController controller = controller(id -> { fail("must not route"); return null; })) {
            assertFalse(controller.submit(" "));
            assertFalse(controller.submit(null));
            assertFalse(controller.view().loading());
        }
    }

    @Test void bottomScrollAppendsDeduplicatedNextPageAndKeepsOffsetOnFailure() throws Exception {
        var offsets = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
        AtomicInteger second = new AtomicInteger();
        try (var controller = controller(id -> qq((k,o,l) -> {
            offsets.add(o);
            if (o == 0) return songs(30);
            if (second.getAndIncrement() == 0) throw new IOException("retry");
            return List.of(songs(1).getFirst(), new UnifiedSong("qq","Next","下一页","",""));
        }))) {
            controller.switchTo(QQ,"",0);controller.submit("分页");next().run();
            controller.scrollQq(1000); assertTrue(controller.view().loading());
            next().run();assertTrue(controller.view().error());assertEquals(30,controller.view().songs().size());
            controller.scrollQq(1);next().run();
            assertEquals(List.of(0,30,30),offsets);assertEquals(31,controller.view().songs().size());
            assertEquals("Next",controller.view().songs().getLast().platformId());
            controller.scrollQq(1000);assertFalse(controller.view().loading());
        }
    }

    private QQSearchController controller(java.util.function.Function<String, OnlineMusicProvider> router) {
        return new QQSearchController(main::add, changes::incrementAndGet, error::set, router);
    }
    private Runnable next() throws InterruptedException {
        Runnable callback = main.poll(4, TimeUnit.SECONDS);
        assertNotNull(callback, "worker must dispatch a completion");
        return callback;
    }
    private static List<UnifiedSong> songs(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i ->
                new UnifiedSong("qq", "Mid" + i, "歌曲" + i, "歌手", "专辑")).toList();
    }
    @FunctionalInterface private interface Search {
        List<UnifiedSong> run(String keyword, int offset, int limit) throws Exception;
    }
    private static OnlineMusicProvider qq(Search search) {
        return new OnlineMusicProvider() {
            public String sourceId() { return "qq"; }
            public List<UnifiedSong> searchSongs(String k, int o, int l) throws Exception { return search.run(k, o, l); }
            public String getPlaybackUrl(UnifiedSong song) { throw new UnsupportedOperationException(); }
            public Lyric getLyrics(UnifiedSong song) { throw new UnsupportedOperationException(); }
        };
    }
}
