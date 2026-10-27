package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** HUD 材质：80% 半透明、双音符、扩散光和双层软阴影。 */
public final class IslandPainter {
    public static final int WIDTH = 368, HEIGHT = 60, PAD = 72;
    public record View(String title, String subtitle, String time, int progress, boolean bilingual) {}
    private IslandPainter() {}

    public static BufferedImage paint(View view, double scale) {
        BufferedImage image = new BufferedImage((int)Math.ceil((WIDTH+2*PAD)*scale),
                (int)Math.ceil((HEIGHT+2*PAD)*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)) {
            c.g.translate(PAD,PAD);
            c.shadow(-4,-2,WIDTH+8,HEIGHT+4,32,18,3,0,new Color(241,136,178,38));
            c.glass(0,0,WIDTH,HEIGHT,30,true);
            c.round(14,14,32,32,16,new Color(249,219,232,205));
            UiIcons.draw(c,"note",30,30,22,PINK);
            c.text(view.title(),57,26,13,true,INK,231);
            c.text(view.subtitle(),57,44,11,false,MUTED,231);
            c.right(view.time(),338,35,11,MUTED);
            c.round(16,52,336,2,1,new Color(230,204,217,170));
            c.round(16,52,336*Math.max(0,Math.min(1000,view.progress()))/1000.0,2,1,PINK);
        }
        return image;
    }
}
