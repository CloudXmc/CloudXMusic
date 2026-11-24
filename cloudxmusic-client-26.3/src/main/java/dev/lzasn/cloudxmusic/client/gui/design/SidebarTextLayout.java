package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.Rectangle2D;
import java.text.*;
import java.util.Locale;

/** 账号副标题专用连续排版；不改变歌词的测宽与逐字高亮契约。 */
public final class SidebarTextLayout {
    private SidebarTextLayout(){}
    public static TextLayout layout(String text,float size,boolean bold,FontRenderContext context){
        AttributedString value=new AttributedString(text);
        for(int start=0;start<text.length();){
            Font font=UiFonts.font(text.codePointAt(start),size,bold);int end=start;
            do{end+=Character.charCount(text.codePointAt(end));}while(end<text.length()&&font.equals(UiFonts.font(text.codePointAt(end),size,bold)));
            value.addAttribute(TextAttribute.FONT,font,start,end);start=end;
        }
        return new TextLayout(value.getIterator(),context);
    }
    public static String fit(String text,float size,boolean bold,float max,FontRenderContext context){
        if(text==null||text.isEmpty()||max<=0)return "";
        if(layout(text,size,bold,context).getAdvance()<=max)return text;
        if(layout("…",size,bold,context).getAdvance()>max)return "";
        BreakIterator boundaries=BreakIterator.getCharacterInstance(Locale.ROOT);boundaries.setText(text);int last=0;
        for(int end=boundaries.next();end!=BreakIterator.DONE;end=boundaries.next()){
            if(layout(text.substring(0,end)+"…",size,bold,context).getAdvance()>max)break;last=end;
        }
        return text.substring(0,last)+"…";
    }
    public static void draw(FrostCanvas canvas,String value,float x,float baseline,float size,boolean bold,Color color,float max){
        String text=fit(value,size,bold,max,canvas.g.getFontRenderContext());if(text.isEmpty())return;
        TextLayout run=layout(text,size,bold,canvas.g.getFontRenderContext());Shape clip=canvas.g.getClip();
        try{
            canvas.g.clip(new Rectangle2D.Float(x-1,baseline-run.getAscent()-2,max+2,run.getAscent()+run.getDescent()+4));
            canvas.g.setColor(color);run.draw(canvas.g,x,baseline);
        }finally{canvas.g.setClip(clip);}
    }
}
