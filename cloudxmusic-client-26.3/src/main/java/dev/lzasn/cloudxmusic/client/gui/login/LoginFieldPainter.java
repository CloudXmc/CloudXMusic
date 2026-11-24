package dev.lzasn.cloudxmusic.client.gui.login;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import java.awt.*;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 网易云、QQ 和酷狗登录表单共用同一输入框样式。 */
public final class LoginFieldPainter {
    private LoginFieldPainter(){}
    public static void paint(FrostCanvas c,Box b,String value,String hint,boolean masked,boolean focused,boolean caret){
        String shown=masked&&!value.isEmpty()?"●".repeat(Math.min(20,value.codePointCount(0,value.length()))):value;
        Color fill=dark?new Color(31,39,43,210):new Color(255,255,255,175),border=dark?new Color(96,111,117,120):LINE,hintInk=dark?new Color(146,158,163):new Color(0x9C,0x8C,0x96);
        c.round(b.x(),b.y(),b.w(),b.h(),22,fill);c.g.setColor(border);c.g.setStroke(new BasicStroke(1f));c.g.draw(shape(b.x()+.5,b.y()+.5,b.w()-1,b.h()-1,22));
        c.text(shown.isEmpty()?hint:shown,b.x()+20,b.cy()+5,13,false,shown.isEmpty()?hintInk:INK,b.w()-40);
        if(focused){c.g.setColor(PINK);c.g.setStroke(new BasicStroke(1.6f));c.g.draw(shape(b.x()+1,b.y()+1,b.w()-2,b.h()-2,22));if(caret){float x=b.x()+20+c.width(shown,13,false);c.g.setColor(DEEP);c.g.setStroke(new BasicStroke(1.6f));c.g.drawLine((int)x,b.cy()-10,(int)x,b.cy()+10);}}
    }
}
