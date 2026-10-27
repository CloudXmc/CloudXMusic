package dev.lzasn.cloudxmusic.client.hud;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.gui.GuiPrimitives;
import dev.lzasn.cloudxmusic.client.gui.GuiTextures;
import dev.lzasn.cloudxmusic.client.gui.Symbols;
import dev.lzasn.cloudxmusic.client.gui.Theme;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 顶部灵动岛。
 *
 * <p>两种形态：默认一行 {@code 歌名 · 歌手}；开了歌词就换成歌词行，
 * 双语模式下高度从 30 涨到 44，上面原文、下面翻译（翻译缺失时自动退回单行）。</p>
 */
public final class DynamicIslandHud {
    private static final int WIDTH = 176;
    private static final int SINGLE_H = 30;
    private static final int DOUBLE_H = 44;
    private static final int TOP = 8;

    private static final Map<String, Component> TEXT_CACHE = new HashMap<>();
    private static final Map<String, String> FIT_CACHE = new HashMap<>();

    private static float visible;
    private static long lastNanos;

    private DynamicIslandHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(DynamicIslandHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        MusicState state = CloudXMusicClient.STATE;

        long now = System.nanoTime();
        float dt = Math.min(0.05f, Math.max(0.001f, (now - lastNanos) / 1_000_000_000f));
        lastNanos = now;

        boolean show = state.playing() && state.islandEnabled();
        visible = GuiPrimitives.ease(visible, show ? 1f : 0f, 14f, dt);
        if (visible < 0.04f) return;

        Font font = CloudXMusicClient.client().font;
        LyricMode mode = NeteaseSession.INSTANCE.lyricMode();
        Lyric.Line line = mode == LyricMode.OFF ? null
                : state.lyric().lineAt((int) (state.position() * 1000));
        boolean showLyric = line != null && !line.text().isBlank();
        boolean twoLines = showLyric && mode == LyricMode.BILINGUAL && line.hasTranslation();

        int height = twoLines ? DOUBLE_H : SINGLE_H;
        int x = graphics.guiWidth() / 2 - WIDTH / 2;
        int y = TOP - (int) ((1f - visible) * 6f);
        int cy = y + height / 2;

        GuiTextures.glass(graphics, x, y, WIDTH, height, height / 2);
        Symbols.drawCentered(graphics, font, Symbols.NOTE, x + 16, cy, Theme.ACCENT);

        String time = state.positionText();
        int timeWidth = font.width(time);
        int textLeft = x + 28;
        int textWidth = WIDTH - 44 - timeWidth;

        if (twoLines) {
            graphics.drawString(font, txt(fit(font, line.text(), textWidth)), textLeft, y + 13, Theme.TEXT, false);
            graphics.drawString(font, txt(fit(font, line.translated(), textWidth)), textLeft, y + 26,
                    Theme.MUTED, false);
        } else if (showLyric) {
            graphics.drawString(font, txt(fit(font, line.text(), textWidth)), textLeft, cy - 4, Theme.TEXT, false);
        } else {
            String label = state.current().title() + " · " + state.current().artist();
            graphics.drawString(font, txt(fit(font, label, textWidth)), textLeft, cy - 4, Theme.TEXT, false);
        }
        graphics.drawString(font, txt(time), x + WIDTH - 10 - timeWidth, cy - 4, Theme.MUTED, false);

        // 底部 2px 进度
        int barLeft = textLeft;
        int barRight = x + WIDTH - 12;
        int barY = y + height - 5;
        graphics.fill(barLeft, barY, barRight, barY + 2, GuiPrimitives.alpha(0xFF8E6E7E, 0x26));
        int filled = (int) ((barRight - barLeft) * state.position() / Math.max(1, state.current().durationSeconds()));
        graphics.fill(barLeft, barY, Math.min(barRight, barLeft + filled), barY + 2, Theme.ACCENT);
    }

    private static Component txt(String text) {
        Component cached = TEXT_CACHE.get(text);
        if (cached != null) return cached;
        if (TEXT_CACHE.size() > 256) TEXT_CACHE.clear();
        Component created = Component.literal(text);
        TEXT_CACHE.put(text, created);
        return created;
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (text == null) return "";
        String key = maxWidth + "\u0000" + text;
        String cached = FIT_CACHE.get(key);
        if (cached != null) return cached;
        String result;
        if (maxWidth <= 0) {
            result = "";
        } else if (font.width(text) <= maxWidth) {
            result = text;
        } else {
            int ellipsis = font.width("…");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (font.width(sb.toString() + c) + ellipsis > maxWidth) break;
                sb.append(c);
            }
            result = sb + "…";
        }
        if (FIT_CACHE.size() > 256) FIT_CACHE.clear();
        FIT_CACHE.put(key, result);
        return result;
    }
}
