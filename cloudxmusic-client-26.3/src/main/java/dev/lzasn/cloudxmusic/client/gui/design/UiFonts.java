package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.Font;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/** 内置字体集合；按字形覆盖回退，不依赖 Minecraft 的位图字模。 */
public final class UiFonts {
    private static final Font LATIN = load("Inter.ttf");
    private static final Font CJK = load("NotoSansSC-Regular.otf");
    private static final Font PAN_CJK=load("NotoSansCJKkr-Regular.otf");
    private static final Font UNICODE=new Font(Font.DIALOG,Font.PLAIN,1);
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

    /** 每个字符使用同一字形做测宽和绘制；补充平面字符按 code point 处理，不拆代理项。 */
    public static Font font(int codepoint,float size,boolean bold){
        Font preferred=isCjk(codepoint)?CJK:LATIN;
        Font base=preferred.canDisplay(codepoint)?preferred:PAN_CJK.canDisplay(codepoint)?PAN_CJK:
                LATIN.canDisplay(codepoint)?LATIN:UNICODE;
        return derived(base,size,bold);
    }
    public static Font font(boolean cjk,float size,boolean bold){return derived(cjk?PAN_CJK:LATIN,size,bold);}
    private static synchronized Font derived(Font base,float size,boolean bold){
        String key=base.getFontName()+":"+size+":"+bold;
        Font cached=CACHE.get(key);if(cached!=null)return cached;
        if(CACHE.size()>=128)CACHE.clear();
        Font result=base.deriveFont(bold?Font.BOLD:Font.PLAIN,size);CACHE.put(key,result);return result;
    }

    public static boolean isCjk(int codepoint) {
        return codepoint > 0x2FF;
    }
}
