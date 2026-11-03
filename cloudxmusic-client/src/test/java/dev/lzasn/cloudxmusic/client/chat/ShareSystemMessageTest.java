package dev.lzasn.cloudxmusic.client.chat;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShareSystemMessageTest {
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
