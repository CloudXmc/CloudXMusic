package dev.lzasn.cloudxmusic.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 特殊符号图标。
 *
 * <p><b>只挑原版字体里确实有字形的码点</b>（逐个在 {@code assets/minecraft/font/include/default.json}
 * 的 chars 里核过，不会出现豆腐块）：</p>
 *
 * <pre>
 * ♪ U+266A   正在播放      ★ U+2605   发现        ◎ U+25CE   搜索
 * ⌂ U+2302   房间          ☰ U+2630   队列        ※ U+203B   设置
 * ⏮ U+23EE   ⏸ U+23F8   ▶ U+25B6   ⏭ U+23ED
 * </pre>
 *
 * <p><b>为什么不用 emoji</b>：U+1F3xx 那一段原版字体没有字形（以前能显示是靠 MUI 的 Noto Color Emoji），
 * 自己塞字体又会在竖直基线上折腾。符号的好处是直接吃文字颜色，选中/悬停能跟着主题变色。</p>
 *
 * <p><b>2 倍放大</b>：符号本身的字模只有 8px 高，太小。这里用整数 2 倍缩放（像素对齐，不会糊），
 * 画出来是 16px。</p>
 */
public final class Symbols {
    public static final String NOTE = "\u266A";
    public static final String DISCOVER = "\u2605";
    public static final String SEARCH = "\u25CE";
    public static final String ROOM = "\u2302";
    public static final String QUEUE = "\u2630";
    public static final String SETTINGS = "\u203B";
    public static final String INFO = "?";

    public static final String PREV = "\u23EE";
    public static final String PLAY = "\u25B6";
    public static final String PAUSE = "\u23F8";
    public static final String NEXT = "\u23ED";

    /** 整数倍放大，像素对齐。 */
    private static final int SCALE = 2;
    /** 原版字模高度（8px），放大后就是 16px。 */
    private static final int GLYPH_H = 8;

    private static final Map<String, Component> CACHE = new HashMap<>();

    private Symbols() {}

    private static Component of(String symbol) {
        Component cached = CACHE.get(symbol);
        if (cached != null) return cached;
        if (CACHE.size() > 32) CACHE.clear();
        Component created = Component.literal(symbol);
        CACHE.put(symbol, created);
        return created;
    }

    /** 以 (cx, cy) 为中心绘制，颜色跟随主题（选中态可以变粉）。 */
    public static void drawCentered(GuiGraphics graphics, Font font, String symbol, int cx, int cy, int color) {
        Component text = of(symbol);
        int width = font.width(text) * SCALE;
        int x = cx - width / 2;
        int y = cy - GLYPH_H * SCALE / 2;
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(SCALE, SCALE);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popMatrix();
    }
}
