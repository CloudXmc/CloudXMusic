package dev.lzasn.cloudxmusic.client.gui.login;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** 失效通知复用同一 Toast；只能在客户端线程调用。 */
public final class LoginNotice {
    private static final SystemToast.SystemToastId ID=new SystemToast.SystemToastId(6000);
    private LoginNotice(){}
    public static void show(String source,String message){var client=Minecraft.getInstance();if(client==null)return;SystemToast.addOrUpdate(client.getToastManager(),ID,Component.literal(source+"登录状态"),Component.literal(message));}
}
