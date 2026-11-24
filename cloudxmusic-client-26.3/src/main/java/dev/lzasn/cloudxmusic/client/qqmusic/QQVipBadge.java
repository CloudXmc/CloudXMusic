package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.gui.design.UiFonts;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** 标题和 VIP 一起排版；颜色在绘制当下读取主题，长标题为徽章预留空间。 */
public final class QQVipBadge {
    private QQVipBadge(){}
    public static void title(FrostCanvas c,String title,float x,float baseline,float size,boolean bold,Color ink,float max,boolean vip){
        title(c,title,x,baseline,size,bold,ink,max,vip,true);
    }
    public static void title(FrostCanvas c,String title,float x,float baseline,float size,boolean bold,Color ink,float max,boolean vip,boolean outline){
        if(!vip){c.text(title,x,baseline,size,bold,ink,max);return;}
        String fitted=c.fit(title,size,bold,Math.max(0,max-40));
        c.text(fitted,x,baseline,size,bold,ink,Math.max(0,max-40));
        float bx=x+c.width(fitted,size,bold)+8,by=baseline-12;
        Color accent=FrostCanvas.PINK;
        c.round(bx,by,32,16,5,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),FrostCanvas.dark?32:20));
        if(outline){
            Stroke saved=c.g.getStroke();c.g.setStroke(new BasicStroke(1));c.g.setColor(accent);
            c.g.draw(new RoundRectangle2D.Float(bx+.5f,by+.5f,31,15,8,8));c.g.setStroke(saved);
            c.center("VIP",bx+16,baseline-.5f,9,true,accent,28);
        }else centeredText(c,"VIP",bx,by,32,16,accent);
    }
    /** 小字号按可见字形居中，避免 advance 的左右侧承和文字阴影造成视觉偏移。 */
    static void centeredText(FrostCanvas c,String label,float x,float y,float width,float height,Color color){
        var glyphs=UiFonts.font(label.codePointAt(0),9,true).createGlyphVector(c.g.getFontRenderContext(),label);
        var bounds=glyphs.getVisualBounds();
        float textWidth=(float)bounds.getWidth(),textHeight=(float)bounds.getHeight();
        float textX=x+(width-textWidth)/2,textY=y+(height-textHeight)/2;
        Graphics2D g=(Graphics2D)c.g.create();
        try{g.setComposite(AlphaComposite.SrcOver);g.setColor(color);g.drawGlyphVector(glyphs,textX-(float)bounds.getX(),textY-(float)bounds.getY());}
        finally{g.dispose();}
    }
}
