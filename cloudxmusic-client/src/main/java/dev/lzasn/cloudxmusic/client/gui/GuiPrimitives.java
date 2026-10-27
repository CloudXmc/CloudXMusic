package dev.lzasn.cloudxmusic.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 自绘原语：抗锯齿圆角、渐变、柔影、边缘羽化、玻璃面板。
 *
 * <p><b>性能约束</b>：1.21.9 之后每次 {@code fill} 都会进 GuiRenderState，逐行填充一个 236px 高的面板
 * 就是 236 次提交，一帧几千次必掉帧。所以这里只在"圆角行"逐行绘制（约 2×radius 行），
 * 中间直边按 6 个色带整体填充，单面板约 60~80 次 fill。</p>
 */
public final class GuiPrimitives {
    private GuiPrimitives() {}

    private static final int GRADIENT_BANDS = 10;

    // ------------------------------------------------------------ 圆角矩形

    public static void roundedRect(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color) {
        shape(graphics, left, top, right, bottom, radius, color, color, true);
    }

    /** 不做抗锯齿的快速版本，给阴影/羽化这类不在乎边缘的图层用。 */
    public static void roundedRectFast(GuiGraphics graphics, int left, int top, int right, int bottom, int radius, int color) {
        shape(graphics, left, top, right, bottom, radius, color, color, false);
    }

    public static void roundedRectGradient(GuiGraphics graphics, int left, int top, int right, int bottom,
                                           int radius, int topColor, int bottomColor) {
        shape(graphics, left, top, right, bottom, radius, topColor, bottomColor, true);
    }

    public static void pill(GuiGraphics graphics, int left, int top, int right, int bottom, int topColor, int bottomColor) {
        shape(graphics, left, top, right, bottom, (bottom - top) / 2, topColor, bottomColor, true);
    }

    private static void shape(GuiGraphics graphics, int left, int top, int right, int bottom,
                              int radius, int topColor, int bottomColor, boolean antialias) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return;

        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            for (int i = 0; i < GRADIENT_BANDS; i++) {
                int y0 = top + height * i / GRADIENT_BANDS;
                int y1 = top + height * (i + 1) / GRADIENT_BANDS;
                graphics.fill(left, y0, right, y1, lerpColor(topColor, bottomColor, (i + 0.5f) / GRADIENT_BANDS));
            }
            return;
        }

        // 上下圆角区：逐行，带亚像素覆盖率
        for (int i = 0; i < r; i++) {
            double distance = r - i - 0.5;
            double inside = Math.sqrt(Math.max(0.0, (double) r * r - distance * distance));
            double inset = r - inside;
            double leftEdge = left + inset;
            double rightEdge = right - inset;

            int yTop = top + i;
            int colorTop = lerpColor(topColor, bottomColor, (float) i / Math.max(1, height - 1));
            rowSpan(graphics, leftEdge, rightEdge, yTop, colorTop, antialias);

            int yBottom = bottom - 1 - i;
            if (yBottom != yTop) {
                int colorBottom = lerpColor(topColor, bottomColor, (float) (height - 1 - i) / Math.max(1, height - 1));
                rowSpan(graphics, leftEdge, rightEdge, yBottom, colorBottom, antialias);
            }
        }

        // 中间直边：整体分带填充，只有 6 次 fill
        int bodyTop = top + r;
        int bodyBottom = bottom - r;
        if (bodyBottom > bodyTop) {
            int bands = Math.min(GRADIENT_BANDS, bodyBottom - bodyTop);
            for (int i = 0; i < bands; i++) {
                int y0 = bodyTop + (bodyBottom - bodyTop) * i / bands;
                int y1 = bodyTop + (bodyBottom - bodyTop) * (i + 1) / bands;
                int mid = (y0 + y1) >> 1;
                graphics.fill(left, y0, right, y1,
                        lerpColor(topColor, bottomColor, (float) (mid - top) / Math.max(1, height - 1)));
            }
        }
    }

    /** 单行圆弧：核心实心 + 左右各一个半透明像素做抗锯齿。 */
    private static void rowSpan(GuiGraphics graphics, double leftEdge, double rightEdge, int y, int color, boolean antialias) {
        int coreLeft = (int) Math.ceil(leftEdge);
        int coreRight = (int) Math.floor(rightEdge);
        if (coreRight <= coreLeft) return;

        graphics.fill(coreLeft, y, coreRight, y + 1, color);
        if (!antialias) return;

        int baseAlpha = (color >>> 24) & 0xFF;
        double leftCoverage = coreLeft - leftEdge;
        if (leftCoverage > 0.03) {
            graphics.fill(coreLeft - 1, y, coreLeft, y + 1, alpha(color, (int) (baseAlpha * leftCoverage)));
        }
        double rightCoverage = rightEdge - coreRight;
        if (rightCoverage > 0.03) {
            graphics.fill(coreRight, y, coreRight + 1, y + 1, alpha(color, (int) (baseAlpha * rightCoverage)));
        }
    }

    // ------------------------------------------------------------ 描边 / 阴影 / 羽化

    /** 1px 圆角描边：直边整段画，只有圆角处逐行。 */
    public static void roundedOutline(GuiGraphics graphics, int left, int top, int right, int bottom,
                                      int radius, int color) {
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));

        graphics.fill(left, top + r, left + 1, bottom - r, color);
        graphics.fill(right - 1, top + r, right, bottom - r, color);
        graphics.fill(left + r, top, right - r, top + 1, color);
        graphics.fill(left + r, bottom - 1, right - r, bottom, color);

        for (int i = 0; i < r; i++) {
            double distance = r - i - 0.5;
            double inside = Math.sqrt(Math.max(0.0, (double) r * r - distance * distance));
            int inset = (int) Math.round(r - inside);
            graphics.fill(left + inset, top + i, left + inset + 1, top + i + 1, color);
            graphics.fill(right - inset - 1, top + i, right - inset, top + i + 1, color);
            graphics.fill(left + inset, bottom - 1 - i, left + inset + 1, bottom - i, color);
            graphics.fill(right - inset - 1, bottom - 1 - i, right - inset, bottom - i, color);
        }
    }

    /** 深色柔影：2 层即可，不做抗锯齿。 */
    public static void shadow(GuiGraphics graphics, int left, int top, int right, int bottom, int radius) {
        roundedRectFast(graphics, left - 2, top + 1, right + 2, bottom + 4, radius + 2, alpha(0xFF2A1822, 0x10));
        roundedRectFast(graphics, left - 1, top + 1, right + 1, bottom + 3, radius + 1, alpha(0xFF2A1822, 0x1A));
    }

    /** 边缘羽化：在面板外圈叠两层极淡的白色，做出"边缘融化"的观感。 */
    public static void feather(GuiGraphics graphics, int left, int top, int right, int bottom, int radius) {
        for (int i = 2; i >= 1; i--) {
            roundedRectFast(graphics, left - i, top - i, right + i, bottom + i, radius + i,
                    alpha(0xFFFFFFFF, i == 1 ? 0x22 : 0x0E));
        }
    }

    // ------------------------------------------------------------ 玻璃面板

    /**
     * 画一块磨砂玻璃面板：柔影 + 发丝边 + 竖向渐变 + 顶部高光。纯 {@code fill}，不碰纹理和 UV。
     *
     * <p><b>为什么边框不逐行描</b>：先铺一块比面板大 1px 的实心圆角底，再用主体盖住它，
     * 露出来的就是 1px 边——比 {@link #roundedOutline} 逐行画圆角省一半 fill。
     * 整块面板约 150 次 fill（阴影 2 层走 {@code roundedRectFast}，每层只有圆角行 + 色带）。</p>
     */
    public static void glass(GuiGraphics graphics, int left, int top, int right, int bottom, int radius) {
        if (right - left <= 0 || bottom - top <= 0) return;
        shadow(graphics, left, top, right, bottom, radius);
        roundedRectFast(graphics, left - 1, top - 1, right + 1, bottom + 1, radius + 1, Theme.GLASS_BORDER);
        roundedRectGradient(graphics, left, top, right, bottom, radius, Theme.GLASS_TOP, Theme.GLASS_BOTTOM);
        if (right - radius > left + radius) {
            graphics.fill(left + radius, top + 1, right - radius, top + 2, Theme.GLASS_HIGHLIGHT);
        }
    }

    // ------------------------------------------------------------ 颜色

    public static int lerpColor(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * k);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * k);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * k);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alpha(int color, int a) {
        return ((Math.max(0, Math.min(255, a))) << 24) | (color & 0x00FFFFFF);
    }

    /** 按比例缩放原有 alpha，用于淡入淡出。 */
    public static int dim(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        return alpha(color, (int) (a * Math.max(0f, Math.min(1f, factor))));
    }

    /** 指数趋近，帧率无关的缓动。 */
    public static float ease(float current, float target, float rate, float deltaSeconds) {
        float diff = target - current;
        if (Math.abs(diff) < 0.0015f) return target;
        return current + diff * (1f - (float) Math.exp(-rate * deltaSeconds));
    }
}
