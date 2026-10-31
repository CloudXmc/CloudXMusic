package dev.lzasn.cloudxmusic.client.room;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** 房间内的右上角提示，复用原版 {@link SystemToast}；同一 id 只刷新不堆叠。 */
public final class RoomToast {
    private static final SystemToast.SystemToastId ID = new SystemToast.SystemToastId(4000);

    private RoomToast() {}

    public static void show(String message) {
        Minecraft client = Minecraft.getInstance();
        var manager = client.getToastManager();
        var title = Component.literal("云朵音乐盒 · 房间");
        var body = Component.literal(message);
        SystemToast existing = manager.getToast(SystemToast.class, ID);
        if (existing == null) manager.addToast(SystemToast.multiline(client, ID, title, body));
        else existing.reset(title, body);
    }
}
