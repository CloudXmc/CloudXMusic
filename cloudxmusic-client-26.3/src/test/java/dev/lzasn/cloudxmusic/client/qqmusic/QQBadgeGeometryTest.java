package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class QQBadgeGeometryTest {
    @Test void accountBadgeVisibleGlyphsStayCenteredAtDifferentScales()throws Exception{
        Path out=Path.of("../outputs/qq-badge-2.6.0-alpha.3/previews");Files.createDirectories(out);
        try{
            for(boolean dark:new boolean[]{false,true})for(double scale:new double[]{1,1.25,1.5,2,3,4}){
                FrostCanvas.setDark(dark);var image=new BufferedImage((int)Math.ceil(250*scale),(int)Math.ceil(70*scale),BufferedImage.TYPE_INT_ARGB);
                float bx,bw;var profile=new QQAccountProfile("",QQAccountProfile.Membership.SVIP,6);
                try(var c=new FrostCanvas(image,scale)){
                    c.g.translate(0,-440);bx=82+c.width("小猫.",14,true)+5;bw=c.width("SVIP6",9,true)+12;
                    QQLoginPainter.sidebar(c,new QQAccountManager.View(true,"小猫.","",false,profile),false);
                }
                int minX=image.getWidth(),minY=image.getHeight(),maxX=-1,maxY=-1;
                for(int y=(int)Math.ceil(25*scale);y<Math.floor(35*scale);y++)for(int x=(int)Math.ceil((bx+4)*scale);x<Math.floor((bx+bw-4)*scale);x++){
                    int pixel=image.getRGB(x,y);if((pixel>>>24)<160)continue;
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
                assertTrue(maxX>minX&&maxY>minY,"必须实际绘制 SVIP6");
                assertEquals((bx+bw/2)*scale,(minX+maxX+1)/2.0,.75,"字形左右留白应相同，scale="+scale);
                assertEquals(30*scale,(minY+maxY+1)/2.0,.75,"字形应垂直居中，scale="+scale);
                ImageIO.write(image,"png",out.resolve((dark?"dark":"light")+"-account-"+scale+"x.png").toFile());
            }
        }finally{FrostCanvas.setDark(false);}
    }
    @Test void playbackBadgeHasEightPixelGapAndNoOutlineAtItsLeftEdge()throws Exception{
        try{
            for(boolean dark:new boolean[]{false,true})for(double scale:new double[]{1,1.25,1.5,2,3}){
                FrostCanvas.setDark(dark);var view=QQLoginPainterTest.view();String title="YOSEMITE (Explicit)";float bx;
                var probe=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
                try(var c=new FrostCanvas(probe,scale)){String fitted=c.fit(title,13,true,178-40);bx=342+c.width(fitted,13,true)+8;}
                var image=MusicPainter.paintPlaybackBar(view,new MusicPainter.PlaybackBarView(title,"Travis Scott",151,65,true,.6f,"臻品母带",true,true),scale);
                int left=(int)Math.ceil((bx-292)*scale),middle=(int)Math.floor((609-586)*scale);
                assertTrue((image.getRGB(left,middle)>>>24)<=40,"左侧仅保留背景填色，不应有高不透明轮廓线");
                for(int x=(int)Math.ceil((bx-292-6)*scale);x<Math.floor((bx-292-1)*scale);x++)for(int y=(int)Math.ceil((601-586)*scale);y<Math.floor((617-586)*scale);y++)assertEquals(0,image.getRGB(x,y)>>>24,"文字与徽章之间保留干净间距");
                Path out=Path.of("../outputs/qq-badge-2.6.0-alpha.3/previews");Files.createDirectories(out);ImageIO.write(image,"png",out.resolve((dark?"dark":"light")+"-bar-"+scale+"x.png").toFile());
            }
        }finally{FrostCanvas.setDark(false);}
    }
}
