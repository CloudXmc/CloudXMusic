package dev.lzasn.cloudxmusic.client.kugou;

import java.awt.Color;
import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 复用 QQ 徽章的圆角框、字重和尺寸算法；酷狗按要求使用黄色/金色。 */
final class KugouBadges {
    private KugouBadges(){}
    static float width(FrostCanvas c,String label){return label.isEmpty()?0:c.width(label,9,true)+12;}
    static void draw(FrostCanvas c,String label,float x,float y,float height){
        if(label.isEmpty())return;float width=width(c,label);
        Color color=label.equals("SVIP")?(dark?new Color(0xF5CD76):new Color(0xA57316)):(dark?new Color(0xFFE38B):new Color(0xAF8619));
        c.round(x,y,width,height,5,new Color(color.getRed(),color.getGreen(),color.getBlue(),28));c.g.setColor(color);c.g.draw(shape(x,y,width,height,5));c.center(label,x+width/2,y+12,9,true,color,width-6);
    }
}
