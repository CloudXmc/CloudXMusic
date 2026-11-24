package dev.lzasn.cloudxmusic.client.chat;

import dev.lzasn.cloudxmusic.client.omni.MusicSource;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareCodecTest {
    private static SongView song(MusicSource source, String id, String title, String artist, boolean vip) {
        return new SongView(source, id, title, artist, "专辑", 245, "", vip);
    }

    @Test void durationTravelsInTheMarkAndRendersInTooltip() {
        String text = ShareCodec.compose(song(MusicSource.NETEASE, "1406598531", "夜曲", "周杰伦", false));
        assertTrue(text.endsWith("#cxm:n:1406598531:245#"), text);
        var share = ShareCodec.parse(text);
        assertNotNull(share);
        assertEquals(245, share.seconds());
        String tip = ShareChat.tooltip(share).getString();
        assertTrue(tip.contains("⏱ 时长：4:05"), tip);
        assertTrue(tip.contains("音源平台：网易云音乐"), tip);
    }

    @Test void vipSongCarriesBothMarkers() {
        var share = ShareCodec.parse(
                ShareCodec.compose(song(MusicSource.NETEASE, "1", "再生", "Perfume", true)));
        assertNotNull(share);
        assertEquals(245, share.seconds());
        assertTrue(share.vip());
        assertTrue(ShareChat.tooltip(share).getString().contains("👑"), "VIP 提示在悬停里");
    }

    @Test void neteaseRoundTripKeepsTitleArtistAndVip() {
        String text = ShareCodec.compose(song(MusicSource.NETEASE, "1406598531", "超级马里奥", "倒带", true));
        // 正文里不带「（VIP）」，VIP 与时长都由标记携带，悬停提示才展示。
        assertEquals("分享了 超级马里奥 · 倒带 [点击播放] #cxm:n:1406598531:245:v#", text);

        var share = ShareCodec.parse(text);
        assertNotNull(share);
        assertEquals("netease", share.source());
        assertEquals("1406598531", share.id());
        assertEquals("超级马里奥", share.title());
        assertEquals("倒带", share.artist());
        assertTrue(share.vip());
        assertEquals("分享了 超级马里奥 · 倒带 [点击播放]", share.body());
    }

    @Test void vipLineOnlyAppearsInTooltip() {
        var vip = ShareCodec.parse(ShareCodec.compose(song(MusicSource.NETEASE, "1", "夜曲", "周杰伦", true)));
        var plain = ShareCodec.parse(ShareCodec.compose(song(MusicSource.NETEASE, "2", "晴天", "周杰伦", false)));
        assertNotNull(vip);
        assertNotNull(plain);
        // 悬停文字在 Style 里，getString() 拿不到，必须直接查 tooltip 组件。
        assertTrue(ShareChat.tooltip(vip).getString().contains("VIP"), "VIP 歌的悬停里要有会员提示");
        assertFalse(ShareChat.tooltip(plain).getString().contains("VIP"), "非 VIP 不占位");
        assertFalse(vip.body().contains("VIP"), "正文始终不带 VIP 标签");
    }

    @Test void qqRoundTripUsesLetterDigitsAndSkipsVipWhenNotVip() {
        String text = ShareCodec.compose(song(MusicSource.QQ, "0039MnYb0qxYhV", "夜曲", "周杰伦", false));
        assertTrue(text.endsWith("#cxm:q:0039MnYb0qxYhV:245#"), text);
        assertFalse(text.contains("VIP"), "非 VIP 不该出现任何 VIP 字样");

        var share = ShareCodec.parse(text);
        assertNotNull(share);
        assertEquals("qq", share.source());
        assertEquals("0039MnYb0qxYhV", share.id());
        assertEquals("夜曲", share.title());
        assertEquals("周杰伦", share.artist());
        assertFalse(share.vip());
    }

    @Test void serverPrefixAndExtraTextDoNotBreakParsing() {
        // 群组服常把消息包成「[全局] 名字: 正文」，标记可能不在行尾。
        String raw = "[全局] Niko_QwQ: 分享了 夜曲 · 周杰伦[点击播放] #cxm:q:0039MnYb0qxYhV# ";
        var share = ShareCodec.parse(raw);
        assertNotNull(share);
        assertEquals("0039MnYb0qxYhV", share.id());
        assertEquals("夜曲", share.title());
        assertEquals("周杰伦", share.artist());
        assertEquals("[全局] Niko_QwQ: 分享了 夜曲 · 周杰伦[点击播放]", share.body());
    }

    @Test void renderKeepsServerNameAndNeverDoublesIt() {
        // 服务端已经把发送者拼进正文时，不能在组件前面再补一遍名字（曾经出现「名字 <名字>」）。
        var share = ShareCodec.parse("<Niko_QwQ> 分享了 夜曲 · 周杰伦 [点击播放] #cxm:q:0039MnYb0qxYhV#");
        assertNotNull(share);
        String line = ShareChat.render(profile("Niko_QwQ"), share).getString();
        assertEquals("<Niko_QwQ> 分享了 夜曲 · 周杰伦 [点击播放]", line);
    }

    @Test void renderPrefixesNameWhenServerSendsBareBody() {
        var share = ShareCodec.parse("分享了 夜曲 · 周杰伦 [点击播放] #cxm:q:0039MnYb0qxYhV#");
        assertNotNull(share);
        String line = ShareChat.render(profile("Niko_QwQ"), share).getString();
        assertEquals("Niko_QwQ 分享了 夜曲 · 周杰伦 [点击播放]", line);
        assertTrue(line.contains(" [点击播放]"), "可点击片段前必须留一个空格");
    }

    private static com.mojang.authlib.GameProfile profile(String name) {
        return new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name);
    }

    @Test void plainChatIsIgnored() {
        assertNull(ShareCodec.parse("有人在吗"));
        assertNull(ShareCodec.parse(""));
        assertNull(ShareCodec.parse(null));
    }

    @Test void malformedMarksAreRejected() {
        // 网易云只接受数字 id，QQ 只接受字母数字；超长直接判非法，避免把奇怪东西带进命令。
        assertNull(ShareCodec.parse("#cxm:n:abc#"));
        assertNull(ShareCodec.parse("#cxm:q:中文#"));
        assertNull(ShareCodec.parse("#cxm:n:123456789012345678901#"));
        assertNull(ShareCodec.parse("#cxm:x:1406598531#"));
    }

    @Test void longTitleIsTruncatedButMessageStaysShort() {
        String text = ShareCodec.compose(song(MusicSource.NETEASE, "1",
                "这是一个非常非常非常非常非常非常长的歌曲标题需要被截断", "同样很长很长的歌手名字需要被截断掉", true));
        assertTrue(text.length() < 100, "整条消息要压在群组服常见的长度限制内：" + text.length());
        var share = ShareCodec.parse(text);
        assertNotNull(share);
        assertEquals("1", share.id());
        assertTrue(share.title().endsWith("…"), share.title());
    }
}
