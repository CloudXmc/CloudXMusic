package dev.lzasn.cloudxmusic.client.kugou;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** 单条原生 Toast 复用，连续权限失败不会堆积通知。调用方位于客户端线程。 */
final class KugouToast {
    private static final SystemToast.SystemToastId ID=new SystemToast.SystemToastId(3500);
    private KugouToast(){}
    static void show(String message){
        var client=Minecraft.getInstance();if(client==null)return;var manager=client.getToastManager();var title=Component.literal("CloudXMusic · 酷狗音乐");var body=Component.literal(message);
        var existing=manager.getToast(SystemToast.class,ID);if(existing==null)manager.addToast(SystemToast.multiline(client,ID,title,body));else existing.reset(title,body);
    }
}
