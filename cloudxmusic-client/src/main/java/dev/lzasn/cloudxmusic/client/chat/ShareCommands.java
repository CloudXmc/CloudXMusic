package dev.lzasn.cloudxmusic.client.chat;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;

/**
 * 客户端命令 {@code /cxm play <source> <id>}：分享消息里那个「点击播放」的落点。
 *
 * <p>注册在客户端命令树里，点一下就在本地执行，不会把命令发包给服务端；
 * 没装本模组的玩家点了，命令才会被发到服务端并被拒绝——对他们来说只是没反应。</p>
 */
public final class ShareCommands {
    private ShareCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("cxm")
                        .then(ClientCommandManager.literal("play")
                                .then(ClientCommandManager.argument("source", StringArgumentType.word())
                                        .then(ClientCommandManager.argument("id", StringArgumentType.word())
                                                .executes(context -> {
                                                    SharePlay.playByCommand(
                                                            StringArgumentType.getString(context, "source"),
                                                            StringArgumentType.getString(context, "id"));
                                                    return 1;
                                                }))))));
    }
}
