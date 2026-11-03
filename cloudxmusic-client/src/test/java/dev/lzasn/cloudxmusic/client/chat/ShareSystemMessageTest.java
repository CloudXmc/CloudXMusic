package dev.lzasn.cloudxmusic.client.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareSystemMessageTest {
    @Test void formattedServerPrefixKeepsColorsFontsAndInheritedStyles() {
        var font = new FontDescription.Resource(Identifier.fromNamespaceAndPath("server", "ranks"));
        var tooltip = new HoverEvent.ShowText(Component.literal("服务器头衔"));
        Component received = Component.empty().withStyle(style -> style.withFont(font).withBold(true))
                .append(Component.literal("[小歪可爱☆]").withStyle(style -> style.withColor(0x55FF55).withHoverEvent(tooltip)))
                .append(Component.literal("『御园莓华』Niko_QwQ: ").withColor(0xFF55FF))
                .append(Component.literal("分享了 再生 · Perfume [点击播放] #cxm:n:1406598531:283:v#").withColor(0xFFFFFF));

        Component result = ShareChat.modifyGameMessage(received, false);

        int link = received.getString().indexOf(ShareCodec.CLICK);
        for (int index = 0; index < link; index++) assertEquals(styleAt(received, index), styleAt(result, index));
        Style first = styleAt(result, 0);
        assertEquals(0x55FF55, first.getColor().getValue());
        assertEquals(font, first.getFont());
        assertTrue(first.isBold());
        assertEquals(tooltip, first.getHoverEvent());
        Style linkStyle = styleAt(result, link);
        assertEquals(font, linkStyle.getFont());
        assertInstanceOf(ClickEvent.RunCommand.class, linkStyle.getClickEvent());
        assertFalse(result.getString().contains("#cxm:"));
    }

    @Test void markerAndClickTextSplitAcrossStyledComponentsAreRewrittenOnce() {
        Component received = Component.literal("<Alice> 分享了 夜曲 · 周杰伦 ").withColor(0x12ABCD)
                .append(Component.literal("[点击").withStyle(style -> style.withItalic(true)))
                .append(Component.literal("播放] #cxm:q:0039MnYb"))
                .append(Component.literal("0qxYhV# ").withColor(0xFF55FF))
                .append(Component.literal("频道后缀").withColor(0xFFAA00));
        Component result = ShareChat.modifyGameMessage(received, false);
        assertEquals("<Alice> 分享了 夜曲 · 周杰伦 [点击播放]  频道后缀", result.getString());
        assertEquals(0xFFAA00, styleAt(result, result.getString().indexOf("频道后缀")).getColor().getValue());
        long links = result.getSiblings().stream().filter(part -> part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand).count();
        assertEquals(1, links);
    }

    private static Style styleAt(Component component, int index) {
        int[] position = {0};
        return component.visit((style, text) -> {
            int start = position[0]; position[0] += text.length();
            return start <= index && index < position[0] ? Optional.of(style) : Optional.empty();
        }, Style.EMPTY).orElseThrow();
    }

    @Test void serverSystemBroadcastBecomesClickableWithoutAnExtraSender() {
        Component received = Component.literal(
                "[小歪可爱☆]『御园莓华』Niko_QwQ: 分享了 再生 · Perfume [点击播放] #cxm:n:1406598531:283:v#");

        Component result = ShareChat.modifyGameMessage(received, false);

        assertEquals("[小歪可爱☆]『御园莓华』Niko_QwQ: 分享了 再生 · Perfume [点击播放]", result.getString());
        Component play = result.getSiblings().stream()
                .filter(part -> ShareCodec.CLICK.equals(part.getString())).findFirst().orElseThrow();
        ClickEvent.RunCommand click = assertInstanceOf(ClickEvent.RunCommand.class, play.getStyle().getClickEvent());
        assertEquals("/cxm play netease 1406598531", click.command());
        assertNotNull(play.getStyle().getHoverEvent());
    }

    @Test void qqSystemBroadcastUsesTheQqPlaybackCommand() {
        Component received = Component.literal(
                "[全局] Player: 分享了 夜曲 · 周杰伦 [点击播放] #cxm:q:0039MnYb0qxYhV:245#");

        Component result = ShareChat.modifyGameMessage(received, false);

        Component play = result.getSiblings().stream()
                .filter(part -> ShareCodec.CLICK.equals(part.getString())).findFirst().orElseThrow();
        ClickEvent.RunCommand click = assertInstanceOf(ClickEvent.RunCommand.class, play.getStyle().getClickEvent());
        assertEquals("/cxm play qq 0039MnYb0qxYhV", click.command());
        assertFalse(result.getString().contains("#cxm:"));
    }

    @Test void ordinarySystemMessagesKeepTheirOriginalComponentAndStyle() {
        Component received = Component.literal("已传送到目标位置").withColor(0x55FF55);
        assertSame(received, ShareChat.modifyGameMessage(received, false));
    }

    @Test void actionBarSharesAreLeftUntouched() {
        Component received = Component.literal("分享了 再生 · Perfume [点击播放] #cxm:n:1406598531:283:v#");
        assertSame(received, ShareChat.modifyGameMessage(received, true));
    }
}
