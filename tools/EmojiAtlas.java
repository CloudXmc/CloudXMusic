import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 把若干 256×256 的 emoji PNG 拼成一张 16px 单元格的小图集，供模组直接 1:1 blit。
 *
 * <p>为什么不运行时缩放：MC 的 GUI blit 是最近邻采样，从 256px 缩到 16px 会很难看；
 * 在构建期用面积平均缩好，游戏里就是像素级 1:1。</p>
 *
 * <p>用法：{@code java EmojiAtlas.java <输入目录> <输出png>}</p>
 */
public final class EmojiAtlas {
    /** 单元格边长。 */
    static final int CELL = 20;
    /** 单元格内留白，避免贴图线性过滤时相邻单元渗色。 */
    static final int MARGIN = 1;
    static final int COLS = 8;

    static final String[] NAMES = {
            "01_note", "02_compass", "03_search", "04_house", "05_clipboard",
            "06_gear", "07_prev", "08_play", "09_pause", "10_next"
    };

    public static void main(String[] args) throws Exception {
        File inDir = new File(args[0]);
        File out = new File(args[1]);
        int rows = (NAMES.length + COLS - 1) / COLS;
        BufferedImage atlas = new BufferedImage(COLS * CELL, rows * CELL, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        g.setComposite(AlphaComposite.Src);

        for (int i = 0; i < NAMES.length; i++) {
            BufferedImage src = ImageIO.read(new File(inDir, NAMES[i] + ".png"));
            BufferedImage trimmed = trim(src);
            int max = CELL - MARGIN * 2;
            double scale = Math.min((double) max / trimmed.getWidth(), (double) max / trimmed.getHeight());
            int w = Math.max(1, (int) Math.round(trimmed.getWidth() * scale));
            int h = Math.max(1, (int) Math.round(trimmed.getHeight() * scale));
            BufferedImage scaled = resize(trimmed, w, h);
            int x = (i % COLS) * CELL + (CELL - w) / 2;
            int y = (i / COLS) * CELL + (CELL - h) / 2;
            g.drawImage(scaled, x, y, null);
            System.out.println(NAMES[i] + " -> " + w + "x" + h + " @ " + x + "," + y);
        }

        g.dispose();
        ImageIO.write(atlas, "png", out);
        System.out.println("atlas " + atlas.getWidth() + "x" + atlas.getHeight() + " -> " + out.getPath());
    }

    /** 裁掉四周全透明的边，让 emoji 尽量撑满单元格。 */
    static BufferedImage trim(BufferedImage src) {
        int minX = src.getWidth(), minY = src.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                if (((src.getRGB(x, y) >>> 24) & 0xFF) > 8) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX) return src;
        return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** 逐级减半再收到目标尺寸：缩小时不会丢细节，也不会过冲。 */
    static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage cur = toPre(src);
        int cw = cur.getWidth();
        int ch = cur.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = scale(cur, cw, ch);
        }
        return scale(cur, w, h);
    }

    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    static BufferedImage toPre(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB_PRE) return src;
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = dst.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dst;
    }
}
