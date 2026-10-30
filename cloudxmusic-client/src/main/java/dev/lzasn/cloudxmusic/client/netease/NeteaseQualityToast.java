package dev.lzasn.cloudxmusic.client.netease;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * 网易云的右上角提示，复用原版 {@link SystemToast}。
 *
 * <p>同一个 id 复用同一个 Toast，连续切歌时只会刷新内容，不会在右上角堆一列。
 * 必须在客户端线程调用。</p>
 */
public final class NeteaseQualityToast {
    private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId(4000);

    private NeteaseQualityToast() {}

    /** 音质被降级时提示实际档位。 */
    public static void quality(String message) {
        show("网易云 · 音质调整", message);
    }

    /** 播放过程里的其他提示。 */
    public static void playback(String message) {
        show("网易云 · 播放提示", message);
    }

    private static void show(String titleText, String message) {
        Minecraft client = Minecraft.getInstance();
        var manager = client.getToastManager();
        var title = Component.literal(titleText);
        var body = Component.literal(message);
        SystemToast existing = manager.getToast(SystemToast.class, ID);
        if (existing == null) manager.addToast(SystemToast.multiline(client, ID, title, body));
        else existing.reset(title, body);
    }
}
