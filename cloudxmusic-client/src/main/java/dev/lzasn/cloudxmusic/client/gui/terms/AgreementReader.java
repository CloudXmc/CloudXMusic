package dev.lzasn.cloudxmusic.client.gui.terms;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import java.awt.image.BufferedImage;
import java.util.*;

/** 单个弹窗的阅读位置；按完整字形测宽，保留段落和 Unicode 代理对。 */
public final class AgreementReader {
    public static final int LINE_HEIGHT=24,CONTENT_HEIGHT=348,TEXT_WIDTH=692;
    private final List<String> lines;
    private final double maxScroll;
    private double offset;
    public AgreementReader(){
        try(var c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){
            lines=wrap(c,UserAgreement.text(),TEXT_WIDTH);
        }
        maxScroll=Math.max(0,lines.size()*LINE_HEIGHT-CONTENT_HEIGHT);
    }
    public List<String> lines(){return lines;}
    public double offset(){return offset;}
    public double maxScroll(){return maxScroll;}
    public boolean atEnd(){return offset>=maxScroll-1;}
    public void reset(){offset=0;}
    public boolean scroll(double vertical){
        if(!Double.isFinite(vertical))return false;
        double next=Math.clamp(offset-vertical*36,0,maxScroll);
        if(next==offset)return false;offset=next;return true;
    }
    public static List<String> wrap(FrostCanvas c,String text,float width){
        var result=new ArrayList<String>();
        for(String paragraph:text.split("\n",-1)){
            var row=new StringBuilder();
            for(int i=0;i<paragraph.length();){
                int cp=paragraph.codePointAt(i);String glyph=new String(Character.toChars(cp));
                if(!row.isEmpty()&&c.width(row.toString()+glyph,13,false)>width){result.add(row.toString());row.setLength(0);}
                row.append(glyph);i+=Character.charCount(cp);
            }
            result.add(row.toString());
        }
        return List.copyOf(result);
    }
}
