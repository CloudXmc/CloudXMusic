package dev.lzasn.cloudxmusic.client.qqmusic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** 只在客户端线程显示；复用同一个 Toast，避免连续切歌积压通知。 */
final class QQQualityToast {
    private static final SystemToast.SystemToastId ID=new SystemToast.SystemToastId(4000);
    private QQQualityToast(){}
    static void show(String message){show("QQ 音乐 · 音质调整",message);}
    static void playback(String message){show("QQ 音乐 · 播放提示",message);}
    private static void show(String titleText,String message){
        Minecraft client=Minecraft.getInstance();
        var manager=client.getToastManager();
        var title=Component.literal(titleText);
        var body=Component.literal(message);
        SystemToast existing=manager.getToast(SystemToast.class,ID);
        if(existing==null)manager.addToast(SystemToast.multiline(client,ID,title,body));
        else existing.reset(title,body);
    }
}
