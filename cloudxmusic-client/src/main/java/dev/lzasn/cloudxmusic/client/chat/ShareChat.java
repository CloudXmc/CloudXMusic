package dev.lzasn.cloudxmusic.client.chat;

import com.mojang.authlib.GameProfile;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * 把一条分享消息渲染成可点击的聊天组件。
 *
 * <p>标题不重新拼：发送侧已经写好「歌名 · 歌手」，接收侧只把 {@code [点击播放]}
 * 换成带 {@code ClickEvent} 的片段，其余原样保留。这样以后改标题格式只动发送侧。</p>
 */
public final class ShareChat {
    /** 次级灰。聊天栏不跟界面主题走，用固定值保证明暗两套背景上都看得清。 */
    private static final int SUBTLE = 0x9A9A9A;
    /** 上一次发送时刻，用来挡连点。 */
    private static long lastSent;

    private ShareChat() {}

    /** 解析聊天插件通过系统消息广播的分享；动作栏和普通系统消息原样显示。 */
    public static Component modifyGameMessage(Component message, boolean overlay) {
        if (overlay) return message;
        ShareCodec.Share share = ShareCodec.parse(message.getString());
        if (share == null) return message;
        SharePlay.remember(share);
        return render(null, share);
    }

    public static Component render(GameProfile sender, ShareCodec.Share share) {
        // authlib 7 里 GameProfile 已经是 record，取名字用 name()。
        String name = sender == null || sender.name() == null ? "" : sender.name();
        String body = share.body();
        int at = body.indexOf(ShareCodec.CLICK);
        String head = (at >= 0 ? body.substring(0, at) : body).stripTrailing();
        String tail = at >= 0 ? body.substring(at + ShareCodec.CLICK.length()) : "";

        MutableComponent out = Component.empty();
        // 服务端几乎都会把发送者拼进正文（"<名字> …" / "名字: …" / "[频道] 名字 …"），
        // 再自己加一遍就成了两个 ID。正文里已经出现名字就不再补。
        if (!name.isBlank() && !head.contains(name)) out.append(Component.literal(name + " ").withColor(0xFFFFFF));
        out.append(Component.literal(head).withColor(SUBTLE));
        out.append(Component.literal(" "));
        out.append(playable(share));
        if (!tail.isBlank()) out.append(Component.literal(tail).withColor(SUBTLE));
        return out;
    }

    /**
     * 把当前曲目发到聊天栏。正文是普通文本，装不装模组都读得懂；
     * 只有末尾的 {@code #cxm:…} 标记需要模组才能变成可点击。
     */
    public static void send(SongView song) {
        Minecraft client = Minecraft.getInstance();
        var connection = client.getConnection();
        if (connection == null) return;
        if (song == null || song.platformId().isBlank()) {
            SharePlay.notify("当前没有可以分享的歌曲");
            return;
        }
        long now = System.currentTimeMillis();
        // 连点容易触发服务器的反刷屏限制，这里先自己挡一层。
        if (now - lastSent < 1500) return;
        lastSent = now;
        connection.sendChat(ShareCodec.compose(song));
    }

    private static Component playable(ShareCodec.Share share) {
        // 客户端命令：装了模组的人点在本地执行，不会把命令发给服务端。
        String command = "/cxm play " + share.source() + " " + share.id();
        // 黄色 + 下划线：MC 的文本样式只有下划线和删除线，没有上划线，
        // 这两样加上悬停就是聊天栏里"可点链接"的既有观感。
        return Component.literal(ShareCodec.CLICK).withStyle(style -> style
                .withColor(ChatFormatting.YELLOW)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(tooltip(share))));
    }

    /**
     * 悬停提示：标题 + 歌曲 / 歌手 / 音源平台。
     *
     * <p>emoji 依赖材质包提供字形（原版字体没有 U+1F3xx），服务端材质包已覆盖。
     * 空字段整行省略，不输出「歌手：」这种空标签。</p>
     */
    static Component tooltip(ShareCodec.Share share) {
        MutableComponent tip = Component.literal("🎵 在 CloudXMusic 中播放").withColor(0xFFFFFF);
        if (!share.title().isBlank()) {
            tip.append(Component.literal("\n📀 歌曲：" + share.title()).withColor(SUBTLE));
        }
        if (!share.artist().isBlank()) {
            tip.append(Component.literal("\n🎤 歌手：" + share.artist()).withColor(SUBTLE));
        }
        if (share.seconds() > 0) {
            tip.append(Component.literal("\n⏱ 时长：" + clock(share.seconds())).withColor(SUBTLE));
        }
        String platform = "qq".equals(share.source()) ? "QQ 音乐" : "网易云音乐";
        tip.append(Component.literal("\n🌐 音源平台：" + platform).withColor(SUBTLE));
        // VIP 只在悬停里提示，正文不带标签；非 VIP 不占位。
        if (share.vip()) {
            tip.append(Component.literal("\n👑 此歌曲为 VIP，请确认你的账号有没有会员")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return tip;
    }

    /** 4:05 这种短格式，聊天里不占地方。 */
    private static String clock(int seconds) {
        int total = Math.max(0, seconds);
        return total / 60 + ":" + String.format("%02d", total % 60);
    }
}
