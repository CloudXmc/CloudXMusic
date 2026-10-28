package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.LinkedHashMap;
import java.util.Map;

/** 分辨率无关的绘制工具：统一字体基线、圆角、真实卷积软阴影。 */
public final class FrostCanvas implements AutoCloseable {
    private static final Map<String, BufferedImage> SHADOWS = new LinkedHashMap<>(32, .75f, true);
    private final Map<String, Float> widths = new LinkedHashMap<>();
    public static final Color INK = new Color(0x40323C);
    public static final Color MUTED = new Color(0x8A7884);
    public static final Color PINK = new Color(0xDF7399);
    public static final Color DEEP = new Color(0xB94F79);
    public final Graphics2D g;

    public FrostCanvas(BufferedImage image, double scale) {
        g = image.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setComposite(AlphaComposite.SrcOver);
        g.scale(scale, scale);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    public void round(double x, double y, double w, double h, double radius, Paint paint) {
        g.setPaint(paint);
        g.fill(shape(x, y, w, h, radius));
    }

    public static Shape shape(double x, double y, double w, double h, double radius) {
        return new RoundRectangle2D.Double(x, y, w, h, radius * 2, radius * 2);
    }

    public void glass(int x, int y, int w, int h, int r, boolean island) {
        if (island) {
            // 灵动岛只有 60 高，尺寸小、离屏幕边缘远，不能用带 spread 的投影：
            // spread 会把阴影轮廓整体撑大，形成一圈紧贴边缘的实心带（看起来像卷边）。
            shadow(x, y, w, h, r, 18, 0, 3, new Color(48, 39, 49, 24));
            shadow(x, y, w, h, r, 8, 0, 1, new Color(177, 80, 117, 10));
        } else {
            shadow(x, y, w, h, r, 28, 2, 4, new Color(48, 39, 49, 24));
            shadow(x, y, w, h, r, 10, 1, 2, new Color(177, 80, 117, 10));
        }
        // 以 alpha 合成保留真正半透明，不能先铺一层不透明白底。
        round(x, y, w, h, r, new GradientPaint(x, y, new Color(255, 253, 255, island ? 211 : 224),
                x + w / 3f, y + h, new Color(255, 240, 246, island ? 197 : 210)));
        g.setColor(new Color(255, 255, 255, 218));
        g.draw(shape(x + .5, y + .5, w - 1, h - 1, r));
    }

    public void card(int x, int y, int w, int h, int r) {
        shadow(x, y, w, h, r, 12, 2, 5, new Color(80, 48, 65, 24));
        shadow(x, y, w, h, r, 5, 1, 2, new Color(184, 100, 132, 14));
        round(x, y, w, h, r, new Color(255, 255, 255, 158));
        g.setColor(new Color(255, 255, 255, 220));
        g.draw(shape(x + .5, y + .5, w - 1, h - 1, r));
    }

    public void shadow(int x, int y, int w, int h, int r, int blur, int dy, Color color) {
        shadow(x, y, w, h, r, blur, Math.max(1, blur / 10), dy, color);
    }

    public void shadow(int x, int y, int w, int h, int r, int blur, int spread, int dy, Color color) {
        String key = w + ":" + h + ":" + r + ":" + blur + ":" + spread + ":" + color.getRGB();
        BufferedImage shadow = SHADOWS.get(key);
        int margin = blur * 3 + spread;
        if (shadow == null) {
            // 在半分辨率生成阴影并缓存，避免大半径卷积占用每帧 CPU。
            int sw = (w + margin * 2 + 1) / 2, sh = (h + margin * 2 + 1) / 2;
            BufferedImage mask = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = mask.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sg.setColor(color);
            double spreadHalf = spread / 2.0;
            sg.fill(shape((margin - spread) / 2.0, (margin - spread) / 2.0,
                    w / 2.0 + spreadHalf * 2, h / 2.0 + spreadHalf * 2,
                    r / 2.0 + spreadHalf));
            sg.dispose();
            int radius = Math.max(1, (int) Math.ceil(blur * 1.4));
            float[] weights = new float[radius * 2 + 1];
            float total = 0;
            double sigma = Math.max(1, blur * 0.6);
            for (int i = -radius; i <= radius; i++) {
                weights[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
                total += weights[i + radius];
            }
            for (int i = 0; i < weights.length; i++) weights[i] /= total;
            BufferedImage horizontal = new ConvolveOp(new Kernel(weights.length, 1, weights), ConvolveOp.EDGE_NO_OP, null).filter(mask, null);
            shadow = new ConvolveOp(new Kernel(1, weights.length, weights), ConvolveOp.EDGE_NO_OP, null).filter(horizontal, null);
            if (SHADOWS.size() >= 32) SHADOWS.remove(SHADOWS.keySet().iterator().next());
            SHADOWS.put(key, shadow);
        }
        // 阴影是半分辨率蒙版放大，本身已模糊；双线性足够，双三次会让每次拖尾多花一倍以上 CPU。
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(shadow, x - margin, y - margin + dy, w + margin * 2, h + margin * 2, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    public float width(String text, float size, boolean bold) {
        if (text == null || text.isEmpty()) return 0;
        String key=size+":"+bold+":"+text;
        Float cached=widths.get(key);
        if(cached!=null)return cached;
        float width = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            g.setFont(UiFonts.font(UiFonts.isCjk(cp), size, bold));
            String glyph = new String(Character.toChars(cp));
            width += (float) g.getFont().getStringBounds(glyph, g.getFontRenderContext()).getWidth();
            i += Character.charCount(cp);
        }
        if(widths.size()>=2048)widths.clear();
        widths.put(key,width);
        return width;
    }

    public String fit(String text, float size, boolean bold, float max) {
        if (text == null || max <= 0) return "";
        if (width(text, size, bold) <= max) return text;
        float used = width("…", size, bold);
        if (used > max) return "";
        int end = 0;
        while (end < text.length()) {
            int next = end + Character.charCount(text.codePointAt(end));
            float advance = width(text.substring(end, next), size, bold);
            if (used + advance > max) break;
            used += advance;
            end = next;
        }
        return text.substring(0, end) + "…";
    }

    /** y 是统一的文字基线；阴影使用柔和的亚像素位移，不使用原版像素阴影。 */
    public void text(String value, float x, float baseline, float size, boolean bold, Color color, float max) {
        String text = fit(value, size, bold, max);
        float cursor = x;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            String glyph = new String(Character.toChars(cp));
            g.setFont(UiFonts.font(UiFonts.isCjk(cp), size, bold));
            g.setColor(new Color(75, 43, 60, Math.min(40, color.getAlpha() / 7)));
            g.drawString(glyph, cursor, baseline + .8f);
            g.setColor(color);
            g.drawString(glyph, cursor, baseline);
            cursor += (float) g.getFont().getStringBounds(glyph, g.getFontRenderContext()).getWidth();
            i += Character.charCount(cp);
        }
    }

    public void right(String text, float right, float baseline, float size, Color color) {
        text(text, right - width(text, size, false), baseline, size, false, color, 500);
    }

    public void center(String text, float cx, float baseline, float size, boolean bold, Color color, float max) {
        String fitted = fit(text, size, bold, max);
        text(fitted, cx - width(fitted, size, bold) / 2, baseline, size, bold, color, max);
    }

    public static void clearCaches() { SHADOWS.clear(); }

    @Override public void close() { g.dispose(); }
}
