package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.PAD;

/** 队列自己的加载失败提示，不能接收全局播放状态文本。 */
public final class QQQueuePainter {
    private QQQueuePainter(){}
    public static BufferedImage notice(BufferedImage base,double scale,String status){
        if(status.isBlank())return base;
        BufferedImage image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)){
            var transform=c.g.getTransform();c.g.setTransform(new java.awt.geom.AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(transform);
            c.g.translate(PAD,PAD);c.text(status,292,170,11,false,QQSearchPainter.errorColor(),758);
        }
        return image;
    }
}
