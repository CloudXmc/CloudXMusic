package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import dev.lzasn.cloudxmusic.client.gui.design.MusicPainter;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.awt.Color;
import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** QQ 四档菜单；权益徽章与对勾独立占位，绘制和点击共用尺寸。 */
public final class QQQualityMenu {
    private static final int WIDTH=332;
    private QQQualityMenu(){}
    public static Box region(){return MusicPainter.qualityMenuRegion(QQQuality.values().length,WIDTH);}
    public static Box row(int index){return MusicPainter.qualityRow(index,QQQuality.values().length,WIDTH);}
    static java.awt.geom.Rectangle2D badge(FrostCanvas c,QQQuality quality,boolean selected){
        Box row=row(quality.ordinal());
        return new java.awt.geom.Rectangle2D.Double(row.x()+14+c.width(quality.menuLabel(),12,selected)+8,row.y()+10,quality==QQQuality.MASTER?42:34,17);
    }
    public static int selected(QQPlaybackController.View playback,QQQuality preferred){
        // 对勾只表示当前流的真实档位；未取到链接时不把偏好伪装成播放音质。
        if(playback.actualQuality()!=null)return playback.actualQuality().ordinal();
        return playback.song()==null?preferred.ordinal():-1;
    }
    public static BufferedImage paint(int selected,int hovered,double scale){
        BufferedImage base=MusicPainter.paintQualityMenu(selected,hovered,scale,Arrays.stream(QQQuality.values()).map(QQQuality::menuLabel).toArray(String[]::new),WIDTH);
        BufferedImage image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)){
            var transform=c.g.getTransform();c.g.setTransform(new java.awt.geom.AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(transform);
            c.g.translate(-region().x(),-region().y());
            for(QQQuality quality:new QQQuality[]{QQQuality.MASTER,QQQuality.SQ,QQQuality.HQ}){
                Box row=row(quality.ordinal());boolean master=quality==QQQuality.MASTER;
                Color color=master?(dark?new Color(0xF5CD76):new Color(0xA57316)):(dark?new Color(0x70D8AE):new Color(0x26815D));
                var bounds=badge(c,quality,selected==quality.ordinal());double x=bounds.getX(),w=bounds.getWidth();
                c.round(x,row.y()+10,w,17,5,new Color(color.getRed(),color.getGreen(),color.getBlue(),30));
                c.g.setColor(color);c.g.draw(shape(x+.5,row.y()+10.5,w-1,16,5));
                c.center(master?"SVIP":"VIP",(float)(x+w/2),row.y()+22,9,true,color,(float)w-4);
            }
        }
        return image;
    }
}
