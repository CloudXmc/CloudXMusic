package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** 标题和 VIP 一起排版；颜色在绘制当下读取主题，长标题为徽章预留空间。 */
public final class QQVipBadge {
    private QQVipBadge(){}
    public static void title(FrostCanvas c,String title,float x,float baseline,float size,boolean bold,Color ink,float max,boolean vip){
        if(!vip){c.text(title,x,baseline,size,bold,ink,max);return;}
        String fitted=c.fit(title,size,bold,Math.max(0,max-40));
        c.text(fitted,x,baseline,size,bold,ink,Math.max(0,max-40));
        float bx=x+c.width(fitted,size,bold)+6,by=baseline-12;
        Color accent=FrostCanvas.PINK;
        c.round(bx,by,32,16,5,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),FrostCanvas.dark?32:20));
        Stroke saved=c.g.getStroke();c.g.setStroke(new BasicStroke(1));c.g.setColor(accent);
        c.g.draw(new RoundRectangle2D.Float(bx+.5f,by+.5f,31,15,8,8));c.g.setStroke(saved);
        c.center("VIP",bx+16,baseline-.5f,9,true,accent,28);
    }
}
