package dev.lzasn.cloudxmusic.client.chat;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.netease.NeteaseApi;
import dev.lzasn.cloudxmusic.client.omni.MusicSource;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration;
import dev.lzasn.cloudxmusic.client.room.MusicRoomClient;
import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.util.Async;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 点击分享消息后的播放。
 *
 * <p>音源不一致时先走既有的声卡交接（{@code MusicSourceCoordinator.select}），交接是异步的、
 * 内部有 8 秒兜底；这里只等 3 秒，等不到就提示手动切——比自动切失败把两边都卡死好收场。</p>
 */
public final class SharePlay {
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(4001);
    private static final long SWITCH_TIMEOUT_MS = 3000;
    /**
     * 渲染过的分享按 {@code kind:id} 记住，点「点击播放」时用来还原歌名/歌手。
     *
     * <p>命令里只放 source 和 id——标题带空格和各种符号，塞进命令要引号转义，很容易出岔子；
     * QQ 侧播放又必须带标题，否则界面是空的。用一张小表记住渲染过的内容最省事。</p>
     */
    private static final Map<String, ShareCodec.Share> SEEN = new LinkedHashMap<>(16, .75f, true);
    private static Pending pending;

    private record Pending(MusicSource target, ShareCodec.Share share, long deadline) {}

    private SharePlay() {}

    /** 收到分享消息时登记一条，供之后点击时还原元数据。 */
    public static void remember(ShareCodec.Share share) {
        if (share == null) return;
        SEEN.put(share.source() + ":" + share.id(), share);
        if (SEEN.size() > 64) SEEN.remove(SEEN.keySet().iterator().next());
    }

    /** 命令入口：只用 id，元数据从登记表里补。 */
    public static void playByCommand(String source, String id) {
        char kind = "qq".equalsIgnoreCase(source) ? 'q' : 'n';
        ShareCodec.Share known = SEEN.get(("qq".equalsIgnoreCase(source) ? "qq" : "netease") + ":" + id);
        play(known != null ? known : new ShareCodec.Share(kind, id, "", "", "", 0, false));
    }

    public static void play(ShareCodec.Share share) {
        if (share == null) return;
        // 房间里由房主统一控队列，成员点播会和跟播打架。
        if (MusicRoomClient.INSTANCE.joined()) {
            notify("房间里请让房主添加这首歌");
            return;
        }
        MusicSource target = "qq".equals(share.source()) ? MusicSource.QQ : MusicSource.NETEASE;
        if (QQMusicIntegration.coordinator().allowsPlayback(target)) {
            start(share);
            return;
        }
        notify("正在切换到" + target.label() + "…");
        QQMusicIntegration.coordinator().select(target);
        pending = new Pending(target, share, System.currentTimeMillis() + SWITCH_TIMEOUT_MS);
    }

    /** 客户端每 tick 调一次，等声卡交接完成；超时就放弃，交给用户手动切源。 */
    public static void tick() {
        Pending waiting = pending;
        if (waiting == null) return;
        if (QQMusicIntegration.coordinator().allowsPlayback(waiting.target)) {
            pending = null;
            start(waiting.share);
            return;
        }
        if (System.currentTimeMillis() > waiting.deadline) {
            pending = null;
            notify("音源切换未完成，请在左侧手动切到" + waiting.target.label());
        }
    }

    /** 世界切换、断开连接时清掉等待中的分享，避免把歌播到下一个世界。 */
    public static void reset() { pending = null; }

    private static void start(ShareCodec.Share share) {
        if ("qq".equals(share.source())) {
            // QQ 侧按 songmid 自己取链并补时长，歌名/歌手从消息正文带回去，界面才不会空着。
            UnifiedSong song = new UnifiedSong("qq", share.id(), share.title(), share.artist(), "", share.vip());
            QQMusicIntegration.playback().prepend(song);
            notify("已置顶播放：" + display(share));
            return;
        }
        Async.run(() -> {
            try {
                Track track = NeteaseApi.songDetail(share.id());
                Async.main(() -> {
                    CloudXMusicClient.STATE.playShared(track);
                    notify("已置顶播放：" + display(share));
                });
            } catch (Exception failure) {
                String reason = failure.getMessage() == null ? "网络或响应异常" : failure.getMessage();
                Async.main(() -> notify("取不到这首歌：" + reason));
            }
        });
    }

    private static String display(ShareCodec.Share share) {
        if (share.title().isBlank()) return "分享的歌曲";
        return share.artist().isBlank() ? share.title() : share.title() + " · " + share.artist();
    }

    static void notify(String message) {
        Minecraft client = Minecraft.getInstance();
        SystemToast.add(client.getToastManager(), TOAST_ID,
                Component.literal("云朵音乐盒 · 分享"), Component.literal(message));
    }
}
