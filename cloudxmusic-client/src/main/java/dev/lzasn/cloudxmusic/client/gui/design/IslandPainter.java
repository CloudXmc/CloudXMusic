package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** HUD 材质：80% 半透明、正方形专辑封面、扩散光和双层软阴影。 */
public final class IslandPainter {
    public static final int WIDTH = 368, HEIGHT = 60, PAD = 72;
    public record View(String title, String subtitle, String time, int progress, boolean bilingual) {}
    private IslandPainter() {}

    public static BufferedImage paint(View view, double scale) {
        return paint(view, scale, null);
    }

    public static BufferedImage paint(View view, double scale, BufferedImage cover) {
        return paint(view,scale,cover,0);
    }
    /** 单位为逻辑像素；时间宽度动态扣除，长时长也不会盖住歌词。 */
    private static float textWidth(FrostCanvas c,View view){return Math.max(1,Math.min(231,338-c.width(view.time(),11,true)-12-57));}
    public static float availableTextWidth(View view){
        try(FrostCanvas c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){return textWidth(c,view);}
    }
    public static boolean scrolls(View view){
        try(FrostCanvas c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){
            float available=textWidth(c,view);
            return c.width(view.title(),13,true)>available||c.width(view.subtitle(),11,false)>available;
        }
    }
    public static BufferedImage paint(View view,double scale,BufferedImage cover,double scrollSeconds) {
        return paint(view,scale,cover,scrollSeconds,true);
    }
    public static BufferedImage paintBackdrop(View view,double scale,BufferedImage cover){
        return paint(view,scale,cover,0,false);
    }
    private static BufferedImage paint(View view,double scale,BufferedImage cover,double scrollSeconds,boolean text) {
        BufferedImage image = new BufferedImage((int)Math.ceil((WIDTH+2*PAD)*scale),
                (int)Math.ceil((HEIGHT+2*PAD)*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)) {
            c.g.translate(PAD,PAD);
            // 扩散光必须与药丸同矩形、同圆角，只靠模糊向外散开。
            // 一旦放大矩形或改圆角，阴影的实心部分就会从四角露出，形成带直边的灰板。
            c.shadow(0,0,WIDTH,HEIGHT,30,20,0,0,new Color(241,136,178,24));
            c.glass(0,0,WIDTH,HEIGHT,30,true);
            artwork(c, cover);
            float available=textWidth(c,view);
            if(text)MarqueeText.draw(c,view.title(),57,26,13,true,INK,available,scrollSeconds);
            if(text)MarqueeText.draw(c,view.subtitle(),57,44,11,false,MUTED,available,scrollSeconds);
            // 倒计时加粗并用主文字色：11px 常规字重 + 次级灰在半透明药丸上几乎看不清。
            c.right(view.time(),338,35,11,true,INK);
            // 轨道与文字栏对齐（标题左缘 57、时间右缘 338），不再横贯整条药丸。
            c.round(57,52,281,2,1,new Color(230,204,217,170));
            c.round(57,52,281*Math.max(0,Math.min(1000,view.progress()))/1000.0,2,1,PINK);
        }
        return image;
    }

    /**
     * 调整位置模式专用：与播放态共用同一块药丸材质，但只保留一行居中的大字提示。
     * 不画封面、时间和进度轨道，避免空轨道在纯文字药丸里被看成一条多余的横线。
     */
    public static BufferedImage paintAdjustment(double scale) {
        BufferedImage image = new BufferedImage((int)Math.ceil((WIDTH+2*PAD)*scale),
                (int)Math.ceil((HEIGHT+2*PAD)*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)) {
            c.g.translate(PAD,PAD);
            c.shadow(0,0,WIDTH,HEIGHT,30,20,0,0,new Color(241,136,178,24));
            c.glass(0,0,WIDTH,HEIGHT,30,true);
            // 单行居中：字号明显大于播放态标题，基线按视觉重心对齐药丸中轴。
            float size = 22;
            c.center("拖动调整位置", WIDTH/2f, HEIGHT/2f + size*.36f, size, true, INK, WIDTH - 24);
        }
        return image;
    }

    /** 正方形专辑封面，按短边等比裁切铺满；没有封面时退回音符占位。 */
    private static void artwork(FrostCanvas c, BufferedImage cover) {
        int x = 14, y = 14, size = 32, radius = 9;
        if (cover == null) {
            c.round(x, y, size, size, radius, new Color(249, 219, 232, 205));
            UiIcons.draw(c, "note", x + size / 2.0, y + size / 2.0, 20, PINK);
            return;
        }
        Shape previous = c.g.getClip();
        c.g.clip(shape(x, y, size, size, radius));
        double zoom = (double) size / Math.min(cover.getWidth(), cover.getHeight());
        int w = (int) Math.ceil(cover.getWidth() * zoom), h = (int) Math.ceil(cover.getHeight() * zoom);
        c.g.drawImage(cover, x + (size - w) / 2, y + (size - h) / 2, w, h, null);
        c.g.setClip(previous);
    }
}
