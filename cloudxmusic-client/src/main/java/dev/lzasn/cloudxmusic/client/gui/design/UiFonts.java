package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.Font;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** 内置字体集合；不访问系统字体，不依赖 Minecraft 的位图字模。 */
public final class UiFonts {
    private static final Font LATIN = load("Inter.ttf");
    private static final Font CJK = load("NotoSansSC-Regular.otf");
    private static final Map<String, Font> CACHE = new LinkedHashMap<>();

    private UiFonts() {}

    private static Font load(String name) {
        try (InputStream in = UiFonts.class.getResourceAsStream("/assets/cloudxmusic/fonts/" + name)) {
            if (in == null) throw new IllegalStateException("缺少内置字体：" + name);
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            throw new IllegalStateException("无法读取内置字体：" + name, e);
        }
    }

    public static Font font(boolean cjk, float size, boolean bold) {
        String key = cjk + ":" + size + ":" + bold;
        Font cached = CACHE.get(key);
        if (cached != null) return cached;
        if (CACHE.size() >= 128) CACHE.clear();
        Font result = (cjk ? CJK : LATIN).deriveFont(bold ? Font.BOLD : Font.PLAIN, size);
        CACHE.put(key, result);
        return result;
    }

    public static boolean isCjk(int codepoint) {
        return codepoint > 0x2FF;
    }
}
