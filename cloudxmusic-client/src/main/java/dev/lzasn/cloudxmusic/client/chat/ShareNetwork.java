package dev.lzasn.cloudxmusic.client.chat;

import dev.lzasn.cloudxmusic.client.network.SharePayload;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import dev.lzasn.cloudxmusic.protocol.MusicSourceType;
import dev.lzasn.cloudxmusic.protocol.ProtocolException;
import dev.lzasn.cloudxmusic.protocol.ShareWire;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 仅通过专用通道发送分享；接收后沿用本地歌曲点播与富文本链接。 */
public final class ShareNetwork {
    private static volatile long generation;
    private static final ShareSession SESSION = new ShareSession(ShareNetwork::send,
            ShareNetwork::display, SharePlay::notify, System::nanoTime);
    private ShareNetwork() {}
    public static void register() {
        SharePayload.register();
        ClientPlayNetworking.registerGlobalReceiver(SharePayload.TYPE, (payload, context) -> {
            long epoch = generation;
            var connection = context.client().getConnection();
            context.client().execute(() -> {
                if (generation != epoch || connection != context.client().getConnection()) return;
                try { SESSION.receive(ShareWire.decode(payload.data())); }
                catch (ProtocolException ignored) { }
            });
        });
    }
    public static void joined() { generation++; SESSION.joined(); }
    public static void disconnected() { generation++; SESSION.disconnected(); }
    public static void tick() { SESSION.tick(); }
    public static boolean share(SongView song) {
        try { return SESSION.share(ShareCodec.wireSong(song)); }
        catch (IllegalArgumentException error) { SharePlay.notify("歌曲信息不完整，无法分享"); return false; }
    }
    private static boolean send(ShareWire.Message message) {
        if (Minecraft.getInstance().getConnection() == null) return false;
        try {
            // 允许代理透明转发；只有收到 WELCOME 后才会发送 Request。
            ClientPlayNetworking.send(new SharePayload(ShareWire.encode(message)));
            return true;
        } catch (ProtocolException | RuntimeException error) { return false; }
    }
    private static void display(ShareWire.Broadcast broadcast) {
        var client = Minecraft.getInstance();
        if (client.gui == null || client.getConnection() == null) return;
        var song = broadcast.song();
        var player = client.getConnection().getPlayerInfo(broadcast.sender());
        Component displayName = player != null && player.getTabListDisplayName() != null
                ? player.getTabListDisplayName().copy() : Component.literal(broadcast.sender());
        String name = song.artist().isBlank() ? song.title() : song.title() + " · " + song.artist();
        var share = new ShareCodec.Share(song.ref().source() == MusicSourceType.QQ ? 'q' : 'n',
                song.ref().songId(), "", song.title(), song.artist(), song.seconds(), song.vip());
        SharePlay.remember(share);
        client.gui.getChat().addMessage(Component.empty().append(displayName)
                .append(Component.literal(": 分享了 " + name + " ")).append(ShareChat.playable(share)));
    }
}
