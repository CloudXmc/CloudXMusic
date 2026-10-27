package dev.lzasn.cloudxmusic.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * 玻璃面板的统一入口。
 *
 * <p><b>默认走纯自绘</b>（{@link GuiPrimitives#glass}）：只用 {@code fill}，不注册纹理、不算 UV。
 * 坐标写错最多是位置偏，不会整块画不出来。</p>
 *
 * <p><b>烘焙路线（{@link #BAKED}）为什么曾经"面板全透明"</b>：
 * 1.21.11 的 {@code GuiGraphics.blit(Identifier, int, int, int, int, float, float, float, float)}
 * 中间四个 int 是<b>绝对坐标 x0, y0, x1, y1</b>——原版直接把它们塞进
 * {@code BlitRenderState.x0/y0/x1/y1}（见 {@code GuiGraphics#innerBlit}），不是老版本的
 * (x, y, width, height)。旧代码传的是 {@code (left, top, width + 8, height + 8)}，
 * 等于画一个从 {@code (left, top)} 到 {@code (248, 344)} 的反向矩形：
 * 面板越靠右下，矩形越退化，最后什么都看不见，只剩文字飘在世界上。
 * 现在改成 {@code x1 = left + texWidth}，这条路线才是可用的。</p>
 */
public final class GuiTextures {
    /** 面板渲染方式：{@code false} = 纯 fill 自绘（默认，稳定）；{@code true} = 烘焙 SDF 纹理（1 次 blit，边缘更锐）。 */
    private static final boolean BAKED = false;

    /** 面板外留白，给羽化和阴影往外扩散。 */
    private static final int MARGIN = 4;

    private static final Map<String, Identifier> CACHE = new HashMap<>();
    private static int counter;

    private GuiTextures() {}

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            CACHE.values().forEach(id -> minecraft.getTextureManager().release(id));
        }
        CACHE.clear();
    }

    /** 画一块磨砂玻璃面板（面板自带边框/高光/阴影，不要再额外描边）。 */
    public static void glass(GuiGraphics graphics, int left, int top, int width, int height, int radius) {
        if (width <= 0 || height <= 0) return;
        if (!BAKED) {
            GuiPrimitives.glass(graphics, left, top, left + width, top + height, radius);
            return;
        }
        String key = width + "x" + height + "r" + radius;
        Identifier id = CACHE.get(key);
        if (id == null) {
            if (CACHE.size() > 8) clear();
            id = bake(width, height, radius);
            if (id == null) {
                GuiPrimitives.glass(graphics, left, top, left + width, top + height, radius);
                return;
            }
            CACHE.put(key, id);
        }
        // 1.21.11：四个 int 是绝对坐标 x0, y0, x1, y1（不是宽高！）
        int x0 = left - MARGIN;
        int y0 = top - MARGIN;
        graphics.blit(id, x0, y0, x0 + width + MARGIN * 2, y0 + height + MARGIN * 2, 0f, 0f, 1f, 1f);
    }

    private static Identifier bake(int width, int height, int radius) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return null;

        int texWidth = width + MARGIN * 2;
        int texHeight = height + MARGIN * 2;
        NativeImage image = new NativeImage(texWidth, texHeight, false);

        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        int topColor = Theme.GLASS_TOP;
        int bottomColor = Theme.GLASS_BOTTOM;
        int borderColor = Theme.GLASS_BORDER;

        for (int ty = 0; ty < texHeight; ty++) {
            for (int tx = 0; tx < texWidth; tx++) {
                double px = tx - MARGIN + 0.5;
                double py = ty - MARGIN + 0.5;
                double d = sdf(px, py, width, height, r);

                // 1px 过渡带 = 抗锯齿
                float coverage = clamp01(0.5f - (float) d);
                int out = 0;

                float shadow = d > 0 ? clamp01(1f - (float) d / 5f) : 0f;
                if (shadow > 0f) out = over(out, 0x2A1822, (int) (shadow * shadow * 40f));

                if (coverage > 0f) {
                    int fill = GuiPrimitives.lerpColor(topColor, bottomColor, clamp01((float) py / Math.max(1, height)));
                    out = over(out, fill & 0xFFFFFF, (int) (((fill >>> 24) & 0xFF) * coverage));
                }

                if (d > -1.6 && d < 0.4) {
                    float edge = clamp01(1f - Math.abs((float) d + 0.6f));
                    out = over(out, borderColor & 0xFFFFFF,
                            (int) (((borderColor >>> 24) & 0xFF) * edge * Math.max(coverage, 0.35f)));
                }

                if (py < 2.2 && coverage > 0f) {
                    out = over(out, 0xFFFFFF, (int) ((2.2f - (float) py) / 2.2f * coverage * 140f));
                }

                int a = (out >>> 24) & 0xFF;
                int red = (out >>> 16) & 0xFF;
                int green = (out >>> 8) & 0xFF;
                int blue = out & 0xFF;
                // NativeImage 用 ABGR 打包
                image.setPixelABGR(tx, ty, (a << 24) | (blue << 16) | (green << 8) | red);
            }
        }

        Identifier id = Identifier.fromNamespaceAndPath("cloudxmusic",
                "baked/panel_" + counter++);
        minecraft.getTextureManager().register(id, new DynamicTexture(() -> "cloudxmusic panel", image));
        return id;
    }

    private static double sdf(double px, double py, double width, double height, double radius) {
        double cx = width / 2.0;
        double cy = height / 2.0;
        double qx = Math.abs(px - cx) - (cx - radius);
        double qy = Math.abs(py - cy) - (cy - radius);
        double ax = Math.max(qx, 0.0);
        double ay = Math.max(qy, 0.0);
        return Math.sqrt(ax * ax + ay * ay) + Math.min(Math.max(qx, qy), 0.0) - radius;
    }

    private static int over(int dst, int srcRgb, int srcAlpha) {
        if (srcAlpha <= 0) return dst;
        float sa = Math.min(255, srcAlpha) / 255f;
        int da = (dst >>> 24) & 0xFF;
        float dAlpha = da / 255f;
        float outA = sa + dAlpha * (1f - sa);
        if (outA <= 0f) return 0;
        int sr = (srcRgb >>> 16) & 0xFF;
        int sg = (srcRgb >>> 8) & 0xFF;
        int sb = srcRgb & 0xFF;
        int dr = (dst >>> 16) & 0xFF;
        int dg = (dst >>> 8) & 0xFF;
        int db = dst & 0xFF;
        int or = Math.round((sr * sa + dr * dAlpha * (1f - sa)) / outA);
        int og = Math.round((sg * sa + dg * dAlpha * (1f - sa)) / outA);
        int ob = Math.round((sb * sa + db * dAlpha * (1f - sa)) / outA);
        return ((int) (outA * 255) << 24) | (or << 16) | (og << 8) | ob;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
