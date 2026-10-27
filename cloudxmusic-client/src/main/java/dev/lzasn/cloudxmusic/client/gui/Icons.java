package dev.lzasn.cloudxmusic.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 矢量图标：用多边形/圆程序化绘制，任意尺寸都保持锐利。
 *
 * <p>不依赖字体也不依赖 emoji——emoji 在不同平台/字体下会变形，字符图标（▶ Ⅱ ⏮）在
 * 放大后又是位图锯齿。</p>
 *
 * <p><b>抗锯齿是二维的</b>：每条扫描线再纵向切 {@link #SUBSAMPLES} 个子采样，行的 alpha 按
 * "落在形状内的子采样比例"折算，横向仍按亚像素覆盖率补两端像素。
 * 只做横向 AA 的话，圆边和斜边会出现 1px 台阶，GUI scale 一放大就是"糊 + 毛边"。</p>
 */
public final class Icons {
    private Icons() {}

    /** 纵向子采样数。4 已经足够平滑，再高只是白花 fill。 */
    private static final int SUBSAMPLES = 4;

    // ------------------------------------------------------------ 播放控制

    public static void play(GuiGraphics graphics, int cx, int cy, int size, int color) {
        double s = size / 2.0;
        triangle(graphics, cx - s * 0.42, cy - s, cx - s * 0.42, cy + s, cx + s * 0.82, cy, color);
    }

    public static void pause(GuiGraphics graphics, int cx, int cy, int size, int color) {
        double s = size / 2.0;
        double barW = Math.max(2.0, s * 0.34);
        double gap = s * 0.34;
        int top = (int) Math.round(cy - s * 0.9);
        int bottom = (int) Math.round(cy + s * 0.9);
        block(graphics, cx - gap - barW, top, barW, bottom - top, color);
        block(graphics, cx + gap, top, barW, bottom - top, color);
    }

    public static void previous(GuiGraphics graphics, int cx, int cy, int size, int color) {
        double s = size / 2.0;
        int barW = Math.max(2, (int) Math.round(s * 0.3));
        int left = (int) Math.round(cx - s);
        int top = (int) Math.round(cy - s * 0.85);
        int bottom = (int) Math.round(cy + s * 0.85);
        graphics.fill(left, top, left + barW, bottom, color);
        triangle(graphics, cx + s * 0.72, cy - s * 0.85, cx + s * 0.72, cy + s * 0.85, cx - s * 0.18, cy, color);
    }

    public static void next(GuiGraphics graphics, int cx, int cy, int size, int color) {
        double s = size / 2.0;
        int barW = Math.max(2, (int) Math.round(s * 0.3));
        int right = (int) Math.round(cx + s);
        int top = (int) Math.round(cy - s * 0.85);
        int bottom = (int) Math.round(cy + s * 0.85);
        graphics.fill(right - barW, top, right, bottom, color);
        triangle(graphics, cx - s * 0.72, cy - s * 0.85, cx - s * 0.72, cy + s * 0.85, cx + s * 0.18, cy, color);
    }

    /** 八分音符：符头 + 符干 + 符尾。 */
    public static void note(GuiGraphics graphics, int cx, int cy, int size, int color) {
        double s = size / 2.0;
        int headR = Math.max(2, (int) Math.round(s * 0.42));
        circle(graphics, cx - s * 0.28, cy + s * 0.52, headR, color);
        int stemW = Math.max(1, (int) Math.round(s * 0.16));
        int stemX = (int) Math.round(cx - s * 0.28 + headR - stemW);
        graphics.fill(stemX, (int) Math.round(cy - s * 0.95), stemX + stemW, (int) Math.round(cy + s * 0.5), color);
        triangle(graphics, stemX + stemW, cy - s * 0.95, stemX + stemW, cy - s * 0.2, cx + s * 0.82, cy - s * 0.55, color);
    }

    // ------------------------------------------------------------ 导航图标

    /** 发现：罗盘（外环 + 斜置菱形指针）。 */
    public static void compass(GuiGraphics graphics, double cx, double cy, double size, int color) {
        double s = size / 2.0;
        double r = s * 0.94;
        ring(graphics, cx, cy, r, Math.max(1.25, s * 0.23), color);
        // 指针斜放（东北向）：竖直指针看起来像时钟
        double n = r * 0.66;
        double fx = cx + n * 0.70, fy = cy - n * 0.70;
        double tx = cx - n * 0.46, ty = cy + n * 0.46;
        double mx = (fx + tx) / 2, my = (fy + ty) / 2;
        double wx = n * 0.26, wy = n * 0.26;
        polygon(graphics,
                new double[]{fx, mx + wx, tx, mx - wx},
                new double[]{fy, my + wy, ty, my - wy}, color);
    }

    /** 搜索：放大镜（环 + 45° 把手）。 */
    public static void search(GuiGraphics graphics, double cx, double cy, double size, int color) {
        double s = size / 2.0;
        double r = s * 0.56;
        double ox = cx - s * 0.2;
        double oy = cy - s * 0.2;
        double thickness = Math.max(1.3, s * 0.26);
        ring(graphics, ox, oy, r, thickness, color);
        line(graphics, ox + r * 0.68, oy + r * 0.68, cx + s * 0.92, cy + s * 0.92, thickness, color);
    }

    /** 房间：房子（屋顶 + 左右墙 + 地面 + 门）。 */
    public static void home(GuiGraphics graphics, double cx, double cy, double size, int color) {
        double s = size / 2.0;
        double roofY = cy - s * 0.08;
        triangle(graphics, cx, cy - s, cx - s, roofY, cx + s, roofY, color);
        double wall = Math.max(1.2, size * 0.13);
        double bottom = cy + s;
        block(graphics, cx - s + wall * 0.5, roofY, wall, bottom - roofY, color);
        block(graphics, cx + s - wall * 1.5, roofY, wall, bottom - roofY, color);
        block(graphics, cx - s + wall * 0.5, bottom - wall, size - wall, wall, color);
        block(graphics, cx - wall * 0.75, bottom - (bottom - roofY) * 0.5, wall * 1.5, (bottom - roofY) * 0.5, color);
    }

    /** 队列：三行"点 + 线"。 */
    public static void list(GuiGraphics graphics, double cx, double cy, double size, int color) {
        double s = size / 2.0;
        double dotR = Math.max(1.0, s * 0.2);
        double lineH = Math.max(1.2, s * 0.24);
        for (int i = -1; i <= 1; i++) {
            double y = cy + i * s * 0.72;
            circle(graphics, cx - s * 0.72, y, dotR, color);
            block(graphics, cx - s * 0.22, y - lineH / 2, s * 1.16, lineH, color);
        }
    }

    /** 设置：两条推子（轨道 + 滑块）。 */
    public static void sliders(GuiGraphics graphics, double cx, double cy, double size, int color) {
        double s = size / 2.0;
        double trackH = Math.max(1.1, s * 0.2);
        double knobR = Math.max(1.5, s * 0.32);
        double y1 = cy - s * 0.45;
        double y2 = cy + s * 0.45;
        block(graphics, cx - s * 0.9, y1 - trackH / 2, s * 1.8, trackH, color);
        block(graphics, cx - s * 0.9, y2 - trackH / 2, s * 1.8, trackH, color);
        circle(graphics, cx + s * 0.34, y1, knobR, color);
        circle(graphics, cx - s * 0.36, y2, knobR, color);
    }

    // ------------------------------------------------------------ 基础图元

    public static void circle(GuiGraphics graphics, double cx, double cy, double radius, int color) {
        if (radius <= 0) return;
        int top = (int) Math.floor(cy - radius);
        int bottom = (int) Math.ceil(cy + radius);
        int baseAlpha = (color >>> 24) & 0xFF;
        for (int y = top; y < bottom; y++) {
            double loSum = 0, hiSum = 0;
            int inside = 0;
            for (int i = 0; i < SUBSAMPLES; i++) {
                double dy = y + (i + 0.5) / SUBSAMPLES - cy;
                double inner = radius * radius - dy * dy;
                if (inner <= 0) continue;
                double half = Math.sqrt(inner);
                loSum += cx - half;
                hiSum += cx + half;
                inside++;
            }
            if (inside == 0) continue;
            int alpha = (int) Math.round(baseAlpha * tune((double) inside / SUBSAMPLES));
            if (alpha <= 0) continue;
            span(graphics, loSum / inside, hiSum / inside, y, GuiPrimitives.alpha(color, alpha));
        }
    }

    /** 圆环：外径 radius，壁厚 thickness。 */
    public static void ring(GuiGraphics graphics, double cx, double cy, double radius, double thickness, int color) {
        if (radius <= 0 || thickness <= 0) return;
        double inner = Math.max(0, radius - thickness);
        int top = (int) Math.floor(cy - radius);
        int bottom = (int) Math.ceil(cy + radius);
        int baseAlpha = (color >>> 24) & 0xFF;
        for (int y = top; y < bottom; y++) {
            double outLo = 0, outHi = 0, inLo = 0, inHi = 0;
            int outerCount = 0, innerCount = 0;
            for (int i = 0; i < SUBSAMPLES; i++) {
                double dy = y + (i + 0.5) / SUBSAMPLES - cy;
                double outSq = radius * radius - dy * dy;
                if (outSq <= 0) continue;
                double half = Math.sqrt(outSq);
                outLo += cx - half;
                outHi += cx + half;
                outerCount++;
                double inSq = inner * inner - dy * dy;
                if (inSq <= 0) continue;
                double inHalf = Math.sqrt(inSq);
                inLo += cx - inHalf;
                inHi += cx + inHalf;
                innerCount++;
            }
            if (outerCount == 0) continue;
            int alpha = (int) Math.round(baseAlpha * tune((double) outerCount / SUBSAMPLES));
            if (alpha <= 0) continue;
            int col = GuiPrimitives.alpha(color, alpha);
            double left = outLo / outerCount;
            double right = outHi / outerCount;
            if (innerCount == 0) {
                span(graphics, left, right, y, col);
            } else {
                span(graphics, left, inLo / innerCount, y, col);
                span(graphics, inHi / innerCount, right, y, col);
            }
        }
    }

    public static void triangle(GuiGraphics graphics,
                                double ax, double ay, double bx, double by, double cx, double cy, int color) {
        polygon(graphics, new double[]{ax, bx, cx}, new double[]{ay, by, cy}, color);
    }

    /** 任意多边形：扫描线求交 + 纵向子采样。 */
    public static void polygon(GuiGraphics graphics, double[] xs, double[] ys, int color) {
        int n = Math.min(xs.length, ys.length);
        if (n < 3) return;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        int top = (int) Math.floor(minY);
        int bottom = (int) Math.ceil(maxY);
        int baseAlpha = (color >>> 24) & 0xFF;
        for (int y = top; y < bottom; y++) {
            double loSum = 0, hiSum = 0;
            int inside = 0;
            for (int s = 0; s < SUBSAMPLES; s++) {
                double sy = y + (s + 0.5) / SUBSAMPLES;
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double ay = ys[i], by = ys[j];
                    if (ay == by) continue;
                    if (sy < Math.min(ay, by) || sy >= Math.max(ay, by)) continue;
                    double t = (sy - ay) / (by - ay);
                    double x = xs[i] + t * (xs[j] - xs[i]);
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
                if (hi > lo) {
                    loSum += lo;
                    hiSum += hi;
                    inside++;
                }
            }
            if (inside == 0) continue;
            int alpha = (int) Math.round(baseAlpha * tune((double) inside / SUBSAMPLES));
            if (alpha <= 0) continue;
            span(graphics, loSum / inside, hiSum / inside, y, GuiPrimitives.alpha(color, alpha));
        }
    }

    /** 带圆头的粗线段。 */
    public static void line(GuiGraphics graphics, double x0, double y0, double x1, double y1,
                            double thickness, int color) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6 || thickness <= 0) return;
        double nx = -dy / len * thickness / 2;
        double ny = dx / len * thickness / 2;
        polygon(graphics, new double[]{x0 + nx, x1 + nx, x1 - nx, x0 - nx},
                new double[]{y0 + ny, y1 + ny, y1 - ny, y0 - ny}, color);
        double r = thickness / 2;
        circle(graphics, x0, y0, r, color);
        circle(graphics, x1, y1, r, color);
    }

    /** 整数对齐的实心矩形——横平竖直的东西不该有半透明边。 */
    public static void block(GuiGraphics graphics, double x, double y, double w, double h, int color) {
        if (w <= 0 || h <= 0) return;
        int x0 = (int) Math.round(x);
        int y0 = (int) Math.round(y);
        int x1 = Math.max(x0 + 1, (int) Math.round(x + w));
        int y1 = Math.max(y0 + 1, (int) Math.round(y + h));
        graphics.fill(x0, y0, x1, y1, color);
    }

    /** 一条水平线：两端按亚像素覆盖率补半透明像素。 */
    private static void span(GuiGraphics graphics, double lo, double hi, int y, int color) {
        int alpha = (color >>> 24) & 0xFF;
        if (alpha <= 0 || hi <= lo) return;
        int coreLeft = (int) Math.ceil(lo);
        int coreRight = (int) Math.floor(hi);
        if (coreRight > coreLeft) {
            graphics.fill(coreLeft, y, coreRight, y + 1, color);
        }
        double leftCoverage = tune(coreLeft - lo);
        if (leftCoverage > 0) {
            graphics.fill(coreLeft - 1, y, coreLeft, y + 1,
                    GuiPrimitives.alpha(color, (int) Math.round(alpha * leftCoverage)));
        }
        double rightCoverage = tune(hi - coreRight);
        if (rightCoverage > 0) {
            graphics.fill(coreRight, y, coreRight + 1, y + 1,
                    GuiPrimitives.alpha(color, (int) Math.round(alpha * rightCoverage)));
        }
    }

    /**
     * 把覆盖率往两头拉。
     *
     * <p>1px 的过渡带在 GUI scale 2~3 下会被放大成 2~3 个屏幕像素的渐变，
     * 视觉上就是"糊"。这里把 18% 以下的覆盖率直接抹掉、82% 以上按实心算，
     * 边缘只留中间段的半透明，既没有台阶也没有拖影。</p>
     */
    private static double tune(double coverage) {
        if (coverage <= 0.18) return 0;
        if (coverage >= 0.82) return 1;
        return (coverage - 0.18) / 0.64;
    }
}
