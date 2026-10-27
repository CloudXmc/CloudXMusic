package dev.lzasn.cloudxmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lzasn.cloudxmusic.client.command.CloudXMusicCommand;
import dev.lzasn.cloudxmusic.client.gui.MusicScreen;
import dev.lzasn.cloudxmusic.client.hud.DynamicIslandHud;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
        STATE.loadPersisted();
        NeteaseSession.INSTANCE.refreshAccountAsync();

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
                    client.setScreen(new MusicScreen());
                }
            }
            STATE.tick(client);
        });

        // 退出世界/回主菜单就停播，否则音乐会在菜单里继续放
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> STATE.refreshLibraryIfNeeded());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STATE.stopAll());

        CloudXMusicCommand.register();
        DynamicIslandHud.register();
    }

    public static Minecraft client() {
        return Minecraft.getInstance();
    }
}
