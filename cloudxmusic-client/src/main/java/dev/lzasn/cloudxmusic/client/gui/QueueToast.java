package dev.lzasn.cloudxmusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Minecraft 原生 Toast；复用一条通知，连续点击不会堆积悬浮提示。 */
final class QueueToast {
    private static final SystemToast.SystemToastId ID=new SystemToast.SystemToastId(2500);
    private QueueToast(){}
    static void show(String message){
        Minecraft client=Minecraft.getInstance();
        var manager=client.getToastManager();
        var title=Component.literal("CloudXMusic · 播放队列");
        var body=Component.literal(message);
        SystemToast existing=manager.getToast(SystemToast.class,ID);
        if(existing==null)manager.addToast(SystemToast.multiline(client,ID,title,body));
        else existing.reset(title,body);
    }
}
