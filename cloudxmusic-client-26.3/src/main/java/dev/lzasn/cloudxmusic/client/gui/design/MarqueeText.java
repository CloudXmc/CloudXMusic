package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

/** 与帧率无关的整行跑马灯：开头停 1s、左滚、末尾停 1.5s，平滑回到开头。 */
public final class MarqueeText {
    public static final double SPEED=24.0,PAUSE_SECONDS=1.0;
    private MarqueeText(){}
    public static final double END_PAUSE_SECONDS=1.5;
    public static double returnSeconds(double distance){return Math.max(.75,Math.min(3,distance/72.0));}
    public static float offset(float textWidth,float viewport,double seconds){
        double distance=Math.max(0,textWidth-viewport);
        if(distance==0||!Double.isFinite(seconds)||seconds<=0)return 0;
        double travel=distance/SPEED,back=returnSeconds(distance);
        double phase=seconds%(PAUSE_SECONDS+travel+END_PAUSE_SECONDS+back);
        if(phase<=PAUSE_SECONDS)return 0;
        phase-=PAUSE_SECONDS;if(phase<travel)return (float)(phase*SPEED);
        phase-=travel;if(phase<=END_PAUSE_SECONDS)return (float)distance;
        double t=Math.min(1,(phase-END_PAUSE_SECONDS)/back);
        // 平滑起步并缓停，回程结束和下一周期开头都精确落在 0，禁止瞬间跳回。
        double eased=t*t*t*(t*(t*6-15)+10);
        return (float)(distance*(1-eased));
    }
    public static float sharedLeft(float primaryWidth,float secondaryWidth,float viewport,double seconds){
        return -offset(Math.max(primaryWidth,secondaryWidth),viewport,seconds);
    }
    public static void draw(FrostCanvas c,String text,float x,float baseline,float size,boolean bold,Color ink,float viewport,double seconds){
        float width=c.width(text,size,bold);
        Shape previous=c.g.getClip();
        // 先在画布里裁剪，再上传纹理，缩放、位移动画也无法让文字越过安全区。
        c.g.clip(new Rectangle2D.Float(x,baseline-size-2,viewport,size+6));
        try{
            float left=width<=viewport?x+(viewport-width)/2:x-offset(width,viewport,seconds);
            // 使用整行实际宽度，禁止 fit() 提前把歌词变成省略号。
            c.text(text,left,baseline,size,bold,ink,width+1);
        }finally{c.g.setClip(previous);}
    }
}
