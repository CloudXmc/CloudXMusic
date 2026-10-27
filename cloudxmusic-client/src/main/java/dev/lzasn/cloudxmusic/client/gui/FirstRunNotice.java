package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.gui.design.FirstRunNoticeScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 本地记录免责声明确认；任何网易云网络请求都先检查该确认状态。 */
public final class FirstRunNotice {
    private static volatile boolean accepted;
    private FirstRunNotice() {}

    public static void load() {
        Path file=file();
        try { accepted=Files.isRegularFile(file)&&Files.readString(file,StandardCharsets.UTF_8).trim().equals("accepted=true"); }
        catch(Exception ignored) { accepted=false; }
    }
    public static boolean accepted() { return accepted; }

    public static synchronized void accept() {
        Path file=file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file,"accepted=true\n",StandardCharsets.UTF_8);
            accepted=true;
        } catch(Exception e) { throw new IllegalStateException("无法保存免责声明确认，请检查 config/cloudxmusic 权限",e); }
    }

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("cloudxmusic").resolve("disclaimer.txt"); }

    /** API 可从后台线程被调用；只把打开界面的动作排回客户端线程。 */
    public static void requireAccepted() {
        if(accepted) return;
        Minecraft client=Minecraft.getInstance();
        if(client!=null) client.execute(() -> {
            if(!accepted && !(client.screen instanceof FirstRunNoticeScreen))
                client.setScreen(new FirstRunNoticeScreen(client.screen,null));
        });
        throw new IllegalStateException("首次使用前请先在云朵音乐盒中阅读并确认网易云音乐免责声明（按 H 打开）");
    }
}
