package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import net.minecraft.client.gui.GuiGraphics;
import java.awt.Color;
import java.util.*;

/** 有界双语字形缓存；HUD 可独立滚动两行，主页面保留联动。纹理只在渲染线程操作。 */
public final class BilingualTextRenderer implements AutoCloseable {
    private final boolean independent;
    public BilingualTextRenderer(){this(false);}
    public BilingualTextRenderer(boolean independent){this.independent=independent;}
    private final Line primary=new Line(),secondary=new Line(),highlight=new Line();
    private String key="";
    private dev.lzasn.cloudxmusic.client.omni.TimedLyrics.Line timedKey;
    private QrcHighlight progress;
    public void draw(GuiGraphics graphics,String text,String translation,float size,float translatedSize,
                     Color ink,Color translatedInk,float x,float baseline,float translatedBaseline,float viewport,
                     int clipTop,int clipBottom,boolean centerShort,double raster,double seconds){
        draw(graphics,text,translation,size,translatedSize,ink,translatedInk,x,baseline,translatedBaseline,viewport,clipTop,clipBottom,centerShort,raster,seconds,null,0);
    }
    public void draw(GuiGraphics graphics,String text,String translation,float size,float translatedSize,
                     Color ink,Color translatedInk,float x,float baseline,float translatedBaseline,float viewport,
                     int clipTop,int clipBottom,boolean centerShort,double raster,double seconds,
                     dev.lzasn.cloudxmusic.client.omni.TimedLyrics.Line timed,double positionMs){
        if(timed!=null&&(!timed.text().equals(text)||timed.words().isEmpty()))timed=null;
        Color base=timed==null?ink:FrostCanvas.MUTED;
        String next=text+"\0"+translation+"\0"+size+"/"+translatedSize+"/"+base.getRGB()+"/"+translatedInk.getRGB()+"/"+FrostCanvas.PINK.getRGB()+"/"+raster;
        if(!key.equals(next)){
            close();key=next;primary.strip=new IslandTextStrip(text,size,true,base);
            secondary.strip=new IslandTextStrip(translation,translatedSize,false,translatedInk);
            highlight.strip=new IslandTextStrip(text,size,true,FrostCanvas.PINK);
        }
        if(!Objects.equals(timedKey,timed)){timedKey=timed;progress=timed==null?null:new QrcHighlight(timed,size,true);}
        var motion=BilingualMarquee.layout(primary.strip.width(),secondary.strip.width(),viewport,seconds,independent);
        float groupWidth=motion.primary().groupWidth(),offset=motion.primary().offset();
        // 外层裁剪固定在安全区；两行分别平移，QRC 高亮始终跟随原文矩阵。
        graphics.enableScissor((int)Math.floor(x-2),clipTop,(int)Math.ceil(x+viewport+2),clipBottom);
        try{
            graphics.pose().pushMatrix();
            try{
                graphics.pose().translate(x+offset,0);
                primary.draw(graphics,baseline,viewport,groupWidth,centerShort,raster,offset);
                if(progress!=null){
                    float width=progress.widthAt(positionMs),inset=centerShort&&groupWidth<=viewport?(viewport-primary.strip.width())/2:0;
                    if(width>0){
                        // 嵌套裁剪叠加外层跑马灯遮罩；两层始终使用相同的矩阵与字形位置。
                        graphics.enableScissor((int)Math.floor(inset-2),clipTop,(int)Math.ceil(inset+width),clipBottom);
                        try{highlight.draw(graphics,baseline,viewport,groupWidth,centerShort,raster,offset);}finally{graphics.disableScissor();}
                    }
                }
            }finally{graphics.pose().popMatrix();}
            graphics.pose().pushMatrix();
            try{
                graphics.pose().translate(x+motion.secondary().offset(),0);
                secondary.draw(graphics,translatedBaseline,viewport,motion.secondary().groupWidth(),centerShort,raster,motion.secondary().offset());
            }finally{graphics.pose().popMatrix();}
        }finally{graphics.disableScissor();}
    }
    private static final class Line implements AutoCloseable {
        IslandTextStrip strip;
        final LinkedHashMap<Integer,UiTexture> tiles=new LinkedHashMap<>(8,.75f,true);
        void draw(GuiGraphics graphics,float baseline,float viewport,float groupWidth,boolean centerShort,double raster,float offset){
            if(strip.width()==0)return;
            float inset=centerShort&&groupWidth<=viewport?(viewport-strip.width())/2:0;
            int first=Math.max(0,(int)Math.floor((-offset-inset-2)/IslandTextStrip.TILE));
            int last=Math.max(first,(int)Math.floor((viewport-offset-inset+4)/IslandTextStrip.TILE));
            graphics.pose().pushMatrix();
            try{
                graphics.pose().translate(inset,baseline-strip.baseline());
                for(int index=first;index<=last;index++){
                    if(index*IslandTextStrip.TILE>strip.width()+4)break;
                    UiTexture texture=tiles.get(index);
                    if(texture==null){
                        texture=new UiTexture();texture.update(strip.tile(index,raster));tiles.put(index,texture);
                        if(tiles.size()>4){var iterator=tiles.entrySet().iterator();var old=iterator.next();old.getValue().close();iterator.remove();}
                    }
                    texture.draw(graphics,index*IslandTextStrip.TILE-IslandTextStrip.PADDING,0,IslandTextStrip.TILE,strip.height());
                }
            }finally{graphics.pose().popMatrix();}
        }
        @Override public void close(){tiles.values().forEach(UiTexture::close);tiles.clear();strip=null;}
    }
    @Override public void close(){primary.close();secondary.close();highlight.close();key="";timedKey=null;progress=null;}
}
