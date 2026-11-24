package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** 整句测宽、分块栅格化；超长句也不生成超过显卡尺寸的大纹理。 */
public final class IslandTextStrip {
    public static final int TILE=512,HEIGHT=28,PADDING=2;
    private final String text;
    private final float size,width;
    private final boolean bold;
    private final Color color;
    public IslandTextStrip(String text,float size,boolean bold,Color color){
        this.text=text;this.size=size;this.bold=bold;this.color=color;
        try(FrostCanvas c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){
            width=c.width(text,size,bold);
        }
    }
    public float width(){return width;}
    public int baseline(){return Math.max(19,(int)Math.ceil(size*1.3)+2);}
    public int height(){return Math.max(HEIGHT,baseline()+(int)Math.ceil(size*.4)+4);}
    public float left(float viewport,double seconds){
        return width<=viewport?(viewport-width)/2:-MarqueeText.offset(width,viewport,seconds);
    }
    public BufferedImage tile(int index,double raster){
        BufferedImage image=new BufferedImage((int)Math.ceil(TILE*raster),(int)Math.ceil(height()*raster),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,raster)){
            c.text(text,PADDING-index*TILE,baseline(),size,bold,color,width+1);
        }
        return image;
    }
}
