package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** 在共享搜索列表上显示酷狗请求状态，不改动其他音源搜索画布。 */
public final class KugouSearchPainter {
    public static final int STATUS_BASELINE=163, STATUS_FONT_SIZE=11, HEADER_BASELINE=194, HEADER_FONT_SIZE=11, MIN_HEADER_GAP=15;
    private KugouSearchPainter(){}
    public static int statusHeaderGap(){return HEADER_BASELINE-HEADER_FONT_SIZE-(STATUS_BASELINE+1);}
    public static BufferedImage decorate(BufferedImage base,double scale,String status){
        if(status==null||status.isBlank())return base;
        BufferedImage image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)){
            var transform=c.g.getTransform();c.g.setTransform(new java.awt.geom.AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(transform);c.g.translate(PAD,PAD);
            c.text(status,MAIN_X,STATUS_BASELINE,STATUS_FONT_SIZE,false,status.contains("失败")?new java.awt.Color(0xB94F6A):MUTED,720);
        }
        return image;
    }
}
