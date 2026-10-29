package dev.lzasn.cloudxmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lzasn.cloudxmusic.client.gui.MusicScreen;
import dev.lzasn.cloudxmusic.client.gui.CoverCache;
import dev.lzasn.cloudxmusic.client.gui.FirstRunNotice;
import dev.lzasn.cloudxmusic.client.gui.design.FirstRunNoticeScreen;
import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.netease.QrLogin;
import dev.lzasn.cloudxmusic.client.hud.DynamicIslandHud;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudXMusicClient implements ClientModInitializer {
    public static final String MOD_ID = "cloudxmusic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final MusicState STATE = new MusicState();
    private static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
    private static KeyMapping openGuiKey;

    @Override
    public void onInitializeClient() {
        NeteaseSession.INSTANCE.load();
        FirstRunNotice.load();
        STATE.loadPersisted();
        if(FirstRunNotice.accepted()) NeteaseSession.INSTANCE.refreshAccountAsync();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.cloudxmusic.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.consumeClick()) {
                if (client.screen instanceof MusicScreen) {
                    client.setScreen(null);
                } else if (client.screen == null) {
                    STATE.setAdjustingIslandPos(false);
                    client.setScreen(FirstRunNotice.accepted() ? new MusicScreen()
                            : new FirstRunNoticeScreen(null,new MusicScreen()));
                }
            }
            STATE.tick(client);
        });

        // 退出世界/回主菜单就停播，否则音乐会在菜单里继续放
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> STATE.refreshLibraryIfNeeded());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STATE.stopAll();
            QrLogin.cancel();
            CoverCache.clear();
            // 断开回调跑在网络线程，纹理释放必须回到客户端线程，否则会抛
            // "Rendersystem called from wrong thread" 并漏掉这张纹理。
            dev.lzasn.cloudxmusic.client.util.Async.main(() -> {
                DynamicIslandHud.clear();
                FrostCanvas.clearCaches();
            });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            STATE.stopAll();
            QrLogin.cancel();
            CoverCache.close();
            DynamicIslandHud.clear();
            FrostCanvas.clearCaches();
        });

        DynamicIslandHud.register();
    }

    /**
     * 这次按下的键是不是「打开/关闭音乐盒」的绑定键。
     *
     * <p>面板打开期间，原版不会再给 KeyMapping 记账（键盘事件优先交给 Screen），
     * 所以 consumeClick() 收不到，必须在 Screen.keyPressed 里自己比对绑定值，
     * 否则改了快捷键也永远只有默认的 H 能关。</p>
     */
    public static boolean matchesOpenKey(net.minecraft.client.input.KeyEvent event) {
        KeyMapping mapping = openGuiKey;
        return mapping != null && mapping.matches(event);
    }

    /** 绑定键的显示名（H / M / …）；离线预览或未初始化时回落默认值。 */
    public static String openKeyLabel() {
        KeyMapping mapping = openGuiKey;
        if (mapping == null) return "H";
        try {
            return mapping.getTranslatedKeyMessage().getString();
        } catch (RuntimeException e) {
            return "H";
        }
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }
}
