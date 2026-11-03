package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
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
    // 核心文字/强调色：暗黑模式下切换到深青灰配色，绘制处无需改动（引用同一批静态字段）。
    public static Color INK = new Color(0x40323C);
    public static Color MUTED = new Color(0x8A7884);
    public static Color PINK = new Color(0xDF7399);
    public static Color DEEP = new Color(0xB94F79);
    /** 当前是否暗黑模式，玻璃/卡片/阴影按此分支。 */
    public static boolean dark = false;
    /** 高频可切换色：分隔线 / 浅粉底 / 白色卡片底 / 悬停高亮。 */
    public static Color LINE = new Color(135, 90, 115, 30);
    public static Color SOFT = new Color(248, 218, 231, 220);
    public static Color CARD_BG = new Color(255, 255, 255, 160);
    public static Color HOVER = new Color(247, 215, 229, 90);
    public static Color HOVER_STRONG = new Color(223, 115, 153, 110);
    public static Color TRACK = new Color(220, 200, 211, 150);
    public final Graphics2D g;

    /** 切换亮色 / 暗黑（深青灰）配色。 */
    public static void setDark(boolean enabled) {
        dark = enabled;
        INK = enabled ? new Color(0xE2E8EA) : new Color(0x40323C);
        MUTED = enabled ? new Color(0xA8B4B8) : new Color(0x8A7884);
        PINK = enabled ? new Color(0xF28AA8) : new Color(0xDF7399);
        DEEP = enabled ? new Color(0xE57BA2) : new Color(0xB94F79);
        LINE = enabled ? new Color(120, 135, 140, 40) : new Color(135, 90, 115, 30);
        SOFT = enabled ? new Color(60, 72, 78, 220) : new Color(248, 218, 231, 220);
        CARD_BG = enabled ? new Color(60, 72, 78, 190) : new Color(255, 255, 255, 160);
        HOVER = enabled ? new Color(90, 105, 112, 110) : new Color(247, 215, 229, 90);
        HOVER_STRONG = enabled ? new Color(150, 130, 140, 120) : new Color(223, 115, 153, 110);
        TRACK = enabled ? new Color(90, 105, 112, 150) : new Color(220, 200, 211, 150);
        // 换主题后旧的软阴影缓存是亮色系的，直接清掉避免串色。
        SHADOWS.clear();
    }

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
            shadow(x, y, w, h, r, 18, 0, 3, dark ? new Color(0, 0, 0, 60) : new Color(48, 39, 49, 24));
            shadow(x, y, w, h, r, 8, 0, 1, dark ? new Color(0, 0, 0, 30) : new Color(177, 80, 117, 10));
        } else {
            shadow(x, y, w, h, r, 28, 2, 4, dark ? new Color(0, 0, 0, 70) : new Color(48, 39, 49, 24));
            shadow(x, y, w, h, r, 10, 1, 2, dark ? new Color(0, 0, 0, 36) : new Color(177, 80, 117, 10));
        }
        // 以 alpha 合成保留真正半透明，不能先铺一层不透明白底。
        if (dark) {
            round(x, y, w, h, r, new GradientPaint(x, y, new Color(42, 50, 54, island ? 216 : 232),
                    x + w / 3f, y + h, new Color(34, 41, 45, island ? 204 : 222)));
            g.setColor(new Color(70, 80, 85, 120));
            g.draw(shape(x + .5, y + .5, w - 1, h - 1, r));
        } else {
            round(x, y, w, h, r, new GradientPaint(x, y, new Color(255, 253, 255, island ? 211 : 224),
                    x + w / 3f, y + h, new Color(255, 240, 246, island ? 197 : 210)));
            g.setColor(new Color(255, 255, 255, 218));
            g.draw(shape(x + .5, y + .5, w - 1, h - 1, r));
        }
    }

    public void card(int x, int y, int w, int h, int r) {
        shadow(x, y, w, h, r, 12, 2, 5, dark ? new Color(0, 0, 0, 60) : new Color(80, 48, 65, 24));
        shadow(x, y, w, h, r, 5, 1, 2, dark ? new Color(0, 0, 0, 32) : new Color(184, 100, 132, 14));
        if (dark) {
            round(x, y, w, h, r, new Color(52, 61, 66, 190));
            g.setColor(new Color(80, 92, 97, 130));
            g.draw(shape(x + .5, y + .5, w - 1, h - 1, r));
        } else {
            round(x, y, w, h, r, new Color(255, 255, 255, 158));
            g.setColor(new Color(255, 255, 255, 220));
            g.draw(shape(x + .5, y + .5, w - 1, h - 1, r));
        }
    }

    public void shadow(int x, int y, int w, int h, int r, int blur, int dy, Color color) {
        shadow(x, y, w, h, r, blur, Math.max(1, blur / 10), dy, color);
    }

    public void shadow(int x, int y, int w, int h, int r, int blur, int spread, int dy, Color color) {
        String key = w + ":" + h + ":" + r + ":" + blur + ":" + spread + ":" + color.getRGB();
        BufferedImage shadow = SHADOWS.get(key);
        // 卷积核半径必须先算：ConvolveOp 会把「离图边不足一个核半径」的像素原样拷贝，
        // 蒙版只有 margin 那么宽时，外侧整圈都落在拷贝带里 —— 高斯根本没生效，
        // 阴影被切成一个硬边矩形（这就是面板/灵动岛外面那圈"直角阴影"）。
        // 所以蒙版要额外向四周留出 radius 的卷积余量，并用 EDGE_ZERO_FILL 让边界按 0 处理。
        int radius = Math.max(1, (int) Math.ceil(blur * 1.4));
        int margin = blur * 3 + spread;
        int pad = margin + radius;
        if (shadow == null) {
            // 在半分辨率生成阴影并缓存，避免大半径卷积占用每帧 CPU。
            int sw = Math.max(1, (w + pad * 2 + 1) / 2), sh = Math.max(1, (h + pad * 2 + 1) / 2);
            BufferedImage mask = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D sg = mask.createGraphics();
            sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            sg.setColor(color);
            double spreadHalf = spread / 2.0;
            sg.fill(shape((pad - spread) / 2.0, (pad - spread) / 2.0,
                    w / 2.0 + spreadHalf * 2, h / 2.0 + spreadHalf * 2,
                    r / 2.0 + spreadHalf));
            sg.dispose();
            float[] weights = new float[radius * 2 + 1];
            float total = 0;
            // sigma 取 blur 的一半（半分辨率坐标），让 3σ 刚好落在调用方的透明留白内：
            // 面板 3σ=84 < PAD 96，灵动岛 3σ=60 < PAD 72。
            double sigma = Math.max(1, blur * 0.5);
            for (int i = -radius; i <= radius; i++) {
                weights[i + radius] = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
                total += weights[i + radius];
            }
            for (int i = 0; i < weights.length; i++) weights[i] /= total;
            BufferedImage horizontal = new ConvolveOp(new Kernel(weights.length, 1, weights), ConvolveOp.EDGE_ZERO_FILL, null).filter(mask, null);
            shadow = new ConvolveOp(new Kernel(1, weights.length, weights), ConvolveOp.EDGE_ZERO_FILL, null).filter(horizontal, null);
            if (SHADOWS.size() >= 32) SHADOWS.remove(SHADOWS.keySet().iterator().next());
            SHADOWS.put(key, shadow);
        }
        // 阴影是半分辨率蒙版放大，本身已模糊；双线性足够，双三次会让每次拖尾多花一倍以上 CPU。
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(shadow, x - pad, y - pad + dy, w + pad * 2, h + pad * 2, null);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    /** 正方形便捷版。 */
    public void roundImage(BufferedImage source, double x, double y, double size, double radius) {
        roundImage(source, x, y, size, size, radius);
    }

    /**
     * 画一张等比铺满、圆角遮罩的图片（封面、头像、缩略图）。
     *
     * <p>不能直接 {@code g.clip(shape)} 再 {@code drawImage}：Java2D 的 clip 是硬遮罩，
     * 不做抗锯齿，圆边一圈必然是台阶；缩放默认又是最近邻，非整数倍时像素还会被不均匀丢弃。
     * 这里改成离屏一张「按设备像素铺满 + 双线性缩放 + AA 圆角遮罩」的贴图，最后 1:1 贴回画布，
     * 边缘和缩放都交给 Java2D 的抗锯齿路径处理。</p>
     *
     * <p>铺满语义与 {@code BackgroundImage} 的 cover 一致：短边填满、长边居中裁掉。</p>
     *
     * @param w 设计像素下的宽
     * @param h 设计像素下的高
     */
    public void roundImage(BufferedImage source, double x, double y, double w, double h, double radius) {
        if (source == null || w <= 0 || h <= 0) return;
        double s = Math.sqrt(Math.abs(g.getTransform().getDeterminant()));
        if (s <= 0) s = 1;
        int px = Math.max(1, (int) Math.ceil(w * s)), py = Math.max(1, (int) Math.ceil(h * s));
        // 留 1 像素余量：遮罩的 AA 边缘正好落在贴图最外圈，不留余量会被裁掉半个像素。
        int pad = 1, tw = px + pad * 2, th = py + pad * 2;
        BufferedImage tile = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D t = tile.createGraphics();
        t.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        t.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        t.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double zoom = Math.max(w / Math.max(1, source.getWidth()), h / Math.max(1, source.getHeight()));
        int iw = Math.max(1, (int) Math.round(source.getWidth() * zoom * s));
        int ih = Math.max(1, (int) Math.round(source.getHeight() * zoom * s));
        t.drawImage(source, pad + (px - iw) / 2, pad + (py - ih) / 2, iw, ih, null);
        // 遮罩必须是一张「盖满整块贴图」的图：合成只作用在源覆盖到的像素上，
        // 直接 fill 一个圆再 DstIn 的话圆外根本没被处理，等于没遮罩。
        BufferedImage mask = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = mask.createGraphics();
        m.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        m.setColor(Color.WHITE);
        m.fill(shape(pad, pad, px, py, radius * s));
        m.dispose();
        t.setComposite(AlphaComposite.DstIn);
        t.drawImage(mask, 0, 0, null);
        t.dispose();
        AffineTransform saved = g.getTransform();
        Composite savedComposite = g.getComposite();
        Point2D at = saved.transform(new Point2D.Double(x, y), null);
        g.setTransform(new AffineTransform());
        g.drawImage(tile, (int) Math.round(at.getX()) - pad, (int) Math.round(at.getY()) - pad, null);
        g.setTransform(saved);
        g.setComposite(savedComposite);
    }

    public float width(String text, float size, boolean bold) {
        if (text == null || text.isEmpty()) return 0;
        String key=size+":"+bold+":"+text;
        Float cached=widths.get(key);
        if(cached!=null)return cached;
        float width = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            g.setFont(UiFonts.font(cp, size, bold));
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
            g.setFont(UiFonts.font(cp, size, bold));
            g.setColor(new Color(75, 43, 60, Math.min(40, color.getAlpha() / 7)));
            g.drawString(glyph, cursor, baseline + .8f);
            g.setColor(color);
            g.drawString(glyph, cursor, baseline);
            cursor += (float) g.getFont().getStringBounds(glyph, g.getFontRenderContext()).getWidth();
            i += Character.charCount(cp);
        }
    }

    public void right(String text, float right, float baseline, float size, Color color) {
        right(text, right, baseline, size, false, color);
    }

    public void right(String text, float right, float baseline, float size, boolean bold, Color color) {
        text(text, right - width(text, size, bold), baseline, size, bold, color, 500);
    }

    public void center(String text, float cx, float baseline, float size, boolean bold, Color color, float max) {
        String fitted = fit(text, size, bold, max);
        text(fitted, cx - width(fitted, size, bold) / 2, baseline, size, bold, color, max);
    }

    public static void clearCaches() { SHADOWS.clear(); }

    @Override public void close() { g.dispose(); }
}
