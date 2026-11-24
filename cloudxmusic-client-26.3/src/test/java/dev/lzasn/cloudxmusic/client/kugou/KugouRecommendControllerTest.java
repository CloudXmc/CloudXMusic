package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class KugouRecommendControllerTest {
    @Test void discoveryLoadsAndClearsItsOwnRowsWhenActivatedAndDeactivated() throws Exception {
        Queue<Runnable> main = new ConcurrentLinkedQueue<>();
        AtomicInteger requests = new AtomicInteger();
        var api = new KugouRecommendApi(() -> {
            requests.incrementAndGet();
            return "<!-- 新歌首发新 --><div dataobj='[{\"hash\":\"" + "A".repeat(32)
                    + "\",\"songname\":\"首页推荐\",\"singername\":\"歌手\"}]'></div>";
        });

        try (var controller = new KugouRecommendController(api, main::add)) {
            controller.update(false);
            assertTrue(controller.view().songs().isEmpty());
            controller.update(true);
            pump(main, () -> !controller.view().loading());
            assertEquals("首页推荐", controller.view().songs().getFirst().song().title());
            assertEquals(1, requests.get());

            controller.update(false);
            assertTrue(controller.view().songs().isEmpty());
            controller.update(true);
            pump(main, () -> !controller.view().loading());
            assertEquals(2, requests.get());
        }
    }

    @Test void failedFetchLeavesAnEmptyRetryableDiscoveryView() throws Exception {
        Queue<Runnable> main = new ConcurrentLinkedQueue<>();
        AtomicInteger requests = new AtomicInteger();
        var api = new KugouRecommendApi(() -> {
            requests.incrementAndGet();
            return "<html>no recommendation section</html>";
        });

        try (var controller = new KugouRecommendController(api, main::add)) {
            controller.update(true);
            pump(main, () -> !controller.view().loading());
            assertTrue(controller.view().songs().isEmpty());
            assertTrue(controller.view().error());
            assertTrue(controller.view().status().contains("刷新重试"));
            controller.refresh();
            pump(main, () -> !controller.view().loading());
            assertEquals(2, requests.get());
        }
    }

    private static void pump(Queue<Runnable> main, java.util.function.BooleanSupplier done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!done.getAsBoolean() && System.nanoTime() < deadline) {
            Runnable task;
            while ((task = main.poll()) != null) task.run();
            Thread.sleep(2);
        }
        Runnable task;
        while ((task = main.poll()) != null) task.run();
        assertTrue(done.getAsBoolean(), "recommendation request did not finish");
    }
}
