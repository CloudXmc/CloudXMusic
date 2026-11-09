package dev.lzasn.cloudxmusic.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.lzasn.cloudxmusic.client.gui.MusicScreen;
import dev.lzasn.cloudxmusic.client.gui.CoverCache;
import dev.lzasn.cloudxmusic.client.gui.MojangAvatarCache;
import dev.lzasn.cloudxmusic.client.gui.FirstRunNotice;
import dev.lzasn.cloudxmusic.client.gui.design.FirstRunNoticeScreen;
import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.netease.QrLogin;
import dev.lzasn.cloudxmusic.client.room.MusicRoomClient;
import dev.lzasn.cloudxmusic.client.room.RoomAudioFollower;import dev.lzasn.cloudxmusic.client.hud.DynamicIslandHud;
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
        MusicRoomClient.INSTANCE.register();
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
            if(dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.neteaseActive()) STATE.tick(client);
            // 分享点播可能正在等声卡交接，交接完成后再起播。
            dev.lzasn.cloudxmusic.client.chat.SharePlay.tick();
            dev.lzasn.cloudxmusic.client.chat.ShareNetwork.tick();
            MusicRoomClient.INSTANCE.tick();
            RoomAudioFollower.INSTANCE.tick();
            com.muoniumplayer.core.ncm.music.CloudMusic.update(
                    dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.snapshot(),
                    dev.lzasn.cloudxmusic.client.worldlyrics.WorldLyricConfig.from(STATE));
        });

        // 退出世界/回主菜单就停播，否则音乐会在菜单里继续放
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            dev.lzasn.cloudxmusic.client.chat.ShareNetwork.joined();
            dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.worldJoined();
            MusicRoomClient.INSTANCE.joinedWorld();
            if(dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.neteaseActive())STATE.refreshLibraryIfNeeded();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            dev.lzasn.cloudxmusic.client.chat.ShareNetwork.disconnected();
            QrLogin.cancel();
            dev.lzasn.cloudxmusic.client.chat.SharePlay.reset();
            MusicRoomClient.INSTANCE.disconnected();
            RoomAudioFollower.INSTANCE.shutdown();
            CoverCache.clear();
            MojangAvatarCache.clear();
            // 播放状态和纹理清理统一回客户端线程，避免断开回调跨线程修改会话。
            dev.lzasn.cloudxmusic.client.util.Async.main(() -> {
                dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.stopForMenu();
                DynamicIslandHud.clear();
                FrostCanvas.clearCaches();
            });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            dev.lzasn.cloudxmusic.client.chat.ShareNetwork.disconnected();
            dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.stopForMenu();
            QrLogin.cancel();
            MusicRoomClient.INSTANCE.disconnected();
            CoverCache.close();
            MojangAvatarCache.close();
            DynamicIslandHud.clear();
            FrostCanvas.clearCaches();
        });

        DynamicIslandHud.register();
        com.muoniumplayer.compat.mc.WorldLyricsBridge.install();
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
                (graphics, delta) -> com.muoniumplayer.compat.mc.WorldLyricsBridge.hudPhaseDraw());
        dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.register();
        dev.lzasn.cloudxmusic.client.chat.ShareCommands.register();
        dev.lzasn.cloudxmusic.client.chat.ShareNetwork.register();
        // 分享消息：挡下原消息，自己往聊天栏放一条带 ClickEvent 的组件。
        // Fabric 没有给玩家聊天提供 MODIFY_CHAT，只能 ALLOW_CHAT 拦下来再手动添加。
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_CHAT.register(
                (message, signedMessage, sender, params, receptionTimestamp) -> {
                    var share = dev.lzasn.cloudxmusic.client.chat.ShareCodec.parse(message.getString());
                    if (share == null) return true;
                    var client = Minecraft.getInstance();
                    // 无法写入替换组件时必须保留原消息，不能先返回 false 把它吞掉。
                    if (client.gui == null) return true;
                    try {
                        dev.lzasn.cloudxmusic.client.chat.SharePlay.remember(share);
                        client.gui.getChat().addMessage(
                                dev.lzasn.cloudxmusic.client.chat.ShareChat.render(sender, message, share));
                        return false;
                    } catch (RuntimeException error) {
                        return true;
                    }
                });
        // 聊天插件常将玩家发言改为系统消息广播，需覆盖独立的 GAME 接收路径。
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.MODIFY_GAME.register(
                dev.lzasn.cloudxmusic.client.chat.ShareChat::modifyGameMessage);
        // 正式播放由交接协调器控制；不再自动启动独立声卡测试。
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
