/*
 * Ported from MuoniumPlayer-multiversion (Apache-2.0):
 * https://github.com/zihe114514/MuoniumPlayer-multiversion
 * Modified by CloudXMusic project for Minecraft 1.21.11.
 */package com.muoniumplayer.core.lyrics3d;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.FilterBlurMode;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.MaskFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.humbleui.skija.PaintStrokeCap;
import io.github.humbleui.skija.PaintStrokeJoin;
import io.github.humbleui.skija.Path;
import io.github.humbleui.skija.PathBuilder;
import io.github.humbleui.skija.Shader;

/**
 * 绘光拖尾的 Skija 光栅化画布 —— 拖尾从「MC 顶点 quad 带（硬边锯齿、单像素
 * 宽度）」升级为 Skija Path 光栅化：圆头圆接、抗锯齿、三层辉光（外晕 blur +
 * 中间过渡 + 内芯白亮）、沿程渐变（尾透明 → 头满亮），质量对齐 folia 的
 * Canvas stroke（round cap/join）并超过其单色描边。
 *
 * <p>工作方式：渲染层在拖尾活跃时调 {@link #begin}（按句字面包围盒 + 超采样
 * 分配 CPU 位图），随后把每个飞行字的 21 点折线交给 {@link #strokeTrail}
 * （Skija Path 一次成笔、多层叠加），帧尾 {@link #readPixelsArgb} 产出直通
 * ARGB 像素交宿主上传纹理 —— 整块画布以一个 quad 覆盖句字面区域，一次收批。
 * 仅飞行窗口（约 1.15s）内每帧重绘，窗口外零成本。渲染线程单线程使用。</p>
 */
public final class HuiguangTrailCanvas {

    private HuiguangTrailCanvas() {
    }

    private static Bitmap bitmap;
    private static Canvas canvas;
    /** 本帧是否画过径迹（begin 清空画布时复位，见 {@link #isEmpty()}）。 */
    private static boolean dirty;

    private static int width, height;      // 画布像素（含超采样）
    private static float planeW, planeH;   // 覆盖的字面区域尺寸（设计像素）
    private static float offX, offY;       // 字面区域左上角（字面坐标，中心原点）
    /**
     * 超采样倍率。可直接调：{@code -Dmuonium.trailSS=1.5}（默认 2）。
     *
     * <p><b>为什么给旋钮</b>：实测每条径迹的 4 层抗锯齿描边在 2400×300 画布上要
     * <b>约 6ms</b>（每层约 1.5ms，与采样点数无关、是抗锯齿填充率决定的），
     * 同一帧有 3~5 条径迹在飞就是 20~30ms —— 绘光掉帧的大头在这里，不是像素搬运。
     * 描边面积随倍率平方变化：2 → 1.5 约省 44%，代价是边缘略软（拖尾本身是柔光，
     * 观感差异很小）。</p>
     */
    private static final float SS = Math.max(1f, Math.min(3f,
            Float.parseFloat(System.getProperty("muonium.trailSS", "2"))));

    /**
     * 描边层数：4 = 双层光晕 + 主体 + 内芯（默认）；3 = 去掉第二层光晕（约省 25%）。
     * {@code -Dmuonium.trailLayers=3} 切换 —— 单层光晕为了保持亮度会把 alpha 补到
     * 双层叠加的等效值，观感接近但外圈过渡会硬一点。
     */
    private static final int TRAIL_LAYERS = Math.max(2, Math.min(4,
            Integer.getInteger("muonium.trailLayers", 4)));


    /**
     * 开始一帧的拖尾光栅化。w/h 是要覆盖的字面区域尺寸（设计像素），
     * offX/offY 是该区域左上角的字面坐标（中心原点坐标系）。尺寸变化时重建。
     */
    public static boolean begin(float w, float h, float offX, float offY) {
        int pw = Math.max(8, (int) Math.ceil(w * SS));
        int ph = Math.max(8, (int) Math.ceil(h * SS));
        if (bitmap == null || pw != width || ph != height) {
            close();
            // 不能用 try-with-resources：Skija 的 Managed.close() 会立刻释放
            // 原生内存，块结束后 bitmap 已死，帧尾 readPixels 直接原生崩溃
            // （skija.dll EXCEPTION_ACCESS_VIOLATION，catch(Throwable) 拦不住）。
            // 所有权归静态字段，失败/异常路径手动 close。
            Bitmap bmp = new Bitmap();
            try {
                if (!bmp.allocPixels(ImageInfo.makeN32Premul(pw, ph))) {
                    bmp.close();
                    return false;
                }
                Canvas cv = new Canvas(bmp);
                cv.clear(0x00000000);
                bitmap = bmp;          // 接管所有权（close 时释放）
                canvas = cv;
                width = pw;
                height = ph;
            } catch (Throwable t) {
                bmp.close();
                bitmap = null;
                canvas = null;
                return false;
            }
        } else {
            canvas.clear(0x00000000);
        }
        planeW = w;
        planeH = h;
        HuiguangTrailCanvas.offX = offX;
        HuiguangTrailCanvas.offY = offY;
        // 本帧没有任何径迹 → 画布保持全透明：上屏侧可以整帧跳过
        // 「读像素 + 逐像素写纹理 + 上传」，画面完全等价（本来就什么都看不见）。
        dirty = false;
        return true;
    }

    /**
     * 本帧是否画过径迹。false = 画布是全透明的，宿主上屏那一步（整块位图读出 +
     * 纹理上传）可以整帧跳过 —— 一句歌词里径迹只在入场飞行的 1~2 秒存在，
     * 其余时间这是纯浪费。
     */
    public static boolean isEmpty() {
        return !dirty;
    }

    public static float offX() {
        return offX;
    }

    public static float offY() {
        return offY;
    }

    public static float planeW() {
        return planeW;
    }

    public static float planeH() {
        return planeH;
    }

    public static float superSample() {
        return SS;
    }

    /**
     * 把一条 21 点径迹折线画进画布（folia trackLayer stroke 的精修版）：
     * Skija Path 一笔成型 + 三层辉光 + 沿程渐变。xs/ys 为字面坐标
     * （已由调用方叠加呼吸/绕行），内部转画布像素。
     */
    // 三层描边的常驻 Paint（渲染线程单线程）：原先每字每帧 new 3 个 Paint +
    // 3 次 PaintBuilder→Path snapshot（同一形状烘 3 个 Path）。这里 Paint 常驻、
    // Path 只 snapshot 一次三层共用 —— 飞行窗口内每帧每字省 5~6 个原生对象。
    private static final Paint GLOW_PAINT = new Paint();
    private static final Paint MID_PAINT = new Paint();
    private static final Paint CORE_PAINT = new Paint();

    static {
        for (Paint p : new Paint[] {GLOW_PAINT, MID_PAINT, CORE_PAINT}) {
            p.setAntiAlias(true);
            p.setMode(PaintMode.STROKE);
            p.setStroke(true);
            p.setStrokeCap(PaintStrokeCap.ROUND);
            p.setStrokeJoin(PaintStrokeJoin.ROUND);
        }
    }

    public static void strokeTrail(float[] xs, float[] ys, int count,
                                   int rgb, float alpha, float fontPx) {
        if (canvas == null || count < 2 || alpha <= 0.01f) return;
        dirty = true;
        float headX = 0f, headY = 0f, tailX = 0f, tailY = 0f;
        int ai = Math.min(255, (int) (Math.min(1f, alpha) * 255f));
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int argbFull = (ai << 24) | (r << 16) | (g << 8) | b;
        int argbMid = ((int) (ai * 0.85f) << 24) | (r << 16) | (g << 8) | b;
        int argbGlow = ((int) (ai * 0.30f) << 24) | (r << 16) | (g << 8) | b;
        // 单层光晕档的等效亮度：两层 0.30 叠加 ≈ 1-(1-0.30)² ≈ 0.51
        int argbGlowBoost = ((int) (ai * 0.51f) << 24) | (r << 16) | (g << 8) | b;
        int argbCore = ((int) (ai * 0.95f) << 24) | 0xFFFFFF;
        try (PathBuilder builder = new PathBuilder()) {
            for (int i = 0; i < count; i++) {
                float cx = (xs[i] - offX) * SS;
                float cy = (ys[i] - offY) * SS;
                if (i == 0) {
                    builder.moveTo(cx, cy);
                    tailX = cx;
                    tailY = cy;
                } else {
                    builder.lineTo(cx, cy);
                }
                headX = cx;
                headY = cy;
            }
            // 注：这里原本有一份「脏矩形包围盒」统计，供子矩形上传用。子矩形上传
            // 因为跨行共享画布导致旧径迹擦不干净（拖尾撕裂）已停用，统计也一并去掉，
            // 不留在每帧的热路径上。要重开必须把「上一帧矩形」改成按 slot 记账。

            // 沿程渐变：尾透明 → 头满亮。首尾重合（整帧纹丝未动）时轴向退化，
            // 退回无 shader 的平色描边。
            boolean flat = Math.abs(headX - tailX) < 0.01f
                    && Math.abs(headY - tailY) < 0.01f;
            try (Shader grad = flat ? null : Shader.makeLinearGradient(
                    tailX, tailY, headX, headY,
                    new int[] {0x00000000, argbFull}, new float[] {0f, 1f});
                 Path path = builder.snapshot()) {
                // bloom 用「三层宽度递增、alpha 递减」的硬边叠加模拟：
                // 真正的 MaskFilter.blur 会把细中间层一起晕软（整体发糊的主因）；
                // 硬边多层的叠色边界被 LINEAR 纹理采样自然柔化，观感更亮更挺。
                // 每多一层就是一次独立的抗锯齿描边（实测约 1.5ms/条径迹），
                // 所以层数做成可调：TRAIL_LAYERS=3 时只画一层光晕，alpha 补到
                // 双层叠加的等效值（亮度和外扩半径都跟着放宽一点）。
                boolean singleGlow = TRAIL_LAYERS < 4;
                GLOW_PAINT.setColor(singleGlow ? argbGlowBoost : argbGlow);
                GLOW_PAINT.setStrokeWidth(fontPx * (singleGlow ? 0.54f : 0.62f) * SS);
                GLOW_PAINT.setShader(grad);
                GLOW_PAINT.setMaskFilter(null);
                canvas.drawPath(path, GLOW_PAINT);
                if (!singleGlow) {
                    GLOW_PAINT.setColor(argbGlow);
                    GLOW_PAINT.setStrokeWidth(fontPx * 0.40f * SS);
                    canvas.drawPath(path, GLOW_PAINT);
                }
                // 中间层：主体笔画（沿程渐变）—— 加宽加亮，视觉主干
                MID_PAINT.setColor(argbMid);
                MID_PAINT.setStrokeWidth(fontPx * 0.26f * SS);
                MID_PAINT.setShader(grad);
                canvas.drawPath(path, MID_PAINT);
                // 内芯：细白亮线（能量感）
                CORE_PAINT.setColor(argbCore);
                CORE_PAINT.setStrokeWidth(fontPx * 0.09f * SS);
                CORE_PAINT.setShader(grad);
                canvas.drawPath(path, CORE_PAINT);
            }
        }
    }

    /**
     * 预乘 → 直通的查表（索引 = {@code a << 8 | c}，值 = {@code min(255, (c*255 + a/2)/a)}）。
     *
     * <p><b>为什么查表</b>：逐像素做三次整数除法在 2400×300 的画布上实测
     * <b>11.1ms/帧</b>（int 除法 ~20-40 周期 × 3 × 72 万像素），而拖尾活跃期间
     * 这是每帧都要走的路径 —— 单这一项就能吃掉十几毫秒帧时间。同样口径的查表版
     * 实测 <b>1.9ms/帧</b>（5.7×），且输出与逐像素公式<b>逐位相同</b>
     * （表就是用同一个公式预生成的）。</p>
     */
    private static final byte[] UNPREMUL = new byte[65536];

    static {
        for (int a = 0; a < 256; a++) {
            for (int c = 0; c < 256; c++) {
                int v = (a == 0 || a == 255)
                        ? c
                        : Math.min(255, (c * 255 + a / 2) / a);
                UNPREMUL[(a << 8) | c] = (byte) v;
            }
        }
    }

    /**
     * 读出整块画布的直通 ARGB 像素（预乘 → 直通，文字管线口径）。
     * 每帧新分配（宿主按返回数组全量写纹理，任何复用/截断假设都是
     * 越界写 native 内存的隐患 —— 曾有复用缓冲大于画布把相邻纹理写黑的
     * 事故，这里不再优化分配，只把逐像素除法换成查表）。
     */
    public static int[] readPixelsArgb() {
        if (bitmap == null) return null;
        try {
            byte[] srcBytes = bitmap.readPixels();
            if (srcBytes == null || srcBytes.length < width * height * 4) return null;
            int[] out = new int[width * height];
            for (int i = 0, o = 0; i < out.length; i++, o += 4) {
                int a = srcBytes[o + 3] & 0xFF;
                int ia = a << 8;
                int b = UNPREMUL[ia | (srcBytes[o] & 0xFF)] & 0xFF;
                int g = UNPREMUL[ia | (srcBytes[o + 1] & 0xFF)] & 0xFF;
                int r = UNPREMUL[ia | (srcBytes[o + 2] & 0xFF)] & 0xFF;
                out[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 读出整块画布像素，<b>同时</b>完成预乘→直通与 ARGB→ABGR 换位：
     * 输出 int 的字节序就是 NativeImage 的内存序 (a<<24|b<<16|g<<8|r)，
     * 宿主写入循环退化成纯数组拷贝 —— 省掉宿主侧每像素三次位运算与移位
     * （拖尾活跃帧的像素量在几十万到上百万级）。
     * 预乘除回直通与换位在同一次遍历里做，比「readPixelsArgb 再宿主换位」
     * 少一整遍逐像素扫描；除法走 {@link #UNPREMUL} 查表。
     */
    public static int[] readPixelsAbgr() {
        if (bitmap == null) return null;
        try {
            byte[] srcBytes = bitmap.readPixels();
            if (srcBytes == null || srcBytes.length < width * height * 4) return null;
            int[] out = new int[width * height];
            for (int i = 0, o = 0; i < out.length; i++, o += 4) {
                int a = srcBytes[o + 3] & 0xFF;
                int ia = a << 8;
                int b = UNPREMUL[ia | (srcBytes[o] & 0xFF)] & 0xFF;
                int g = UNPREMUL[ia | (srcBytes[o + 1] & 0xFF)] & 0xFF;
                int r = UNPREMUL[ia | (srcBytes[o + 2] & 0xFF)] & 0xFF;
                // NativeImage 内存序 ABGR：(a<<24)|(b<<16)|(g<<8)|r
                out[i] = (a << 24) | (b << 16) | (g << 8) | r;
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    public static int canvasWidth() {
        return width;
    }

    public static int canvasHeight() {
        return height;
    }

    public static void close() {
        if (canvas != null) canvas.close();
        if (bitmap != null) bitmap.close();
        canvas = null;
        bitmap = null;
        width = height = 0;
    }
}
