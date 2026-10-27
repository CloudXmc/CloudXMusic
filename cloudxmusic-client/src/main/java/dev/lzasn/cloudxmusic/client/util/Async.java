package dev.lzasn.cloudxmusic.client.util;

import net.minecraft.client.Minecraft;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** 后台线程 + 回主线程。网络请求绝不能在渲染线程跑。 */
public final class Async {
    private static final ThreadFactory FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "CloudXMusic-Net");
        thread.setDaemon(true);
        return thread;
    };

    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, FACTORY);

    private Async() {}

    /** 后台执行；异常只打日志，不打断游戏。 */
    public static void run(Runnable task) {
        POOL.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                dev.lzasn.cloudxmusic.client.CloudXMusicClient.LOGGER.error("云朵音乐盒后台任务失败", t);
            }
        });
    }

    /** 后台执行，结束后回主线程跑 {@code onMain}。 */
    public static void runThen(Runnable background, Runnable onMain) {
        run(() -> {
            try {
                background.run();
            } finally {
                main(onMain);
            }
        });
    }

    /** 回主线程（渲染/状态更新必须在这上面）。 */
    public static void main(Runnable task) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.execute(task);
    }
}
