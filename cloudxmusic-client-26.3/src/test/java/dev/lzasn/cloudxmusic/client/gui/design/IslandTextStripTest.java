package dev.lzasn.cloudxmusic.client.gui.design;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class IslandTextStripTest {
    @Test void perFramePositionsRetainFractionalPixelsAndCenterShortLines(){
        var longLine=new IslandTextStrip("一段足够长的歌词用于检查每帧平滑滚动，不受帧率或纹理重绘间隔影响",13,true,Color.WHITE);
        float a=longLine.left(100,2),b=longLine.left(100,2+1.0/144);
        assertEquals(-24,a,.001);assertEquals(-24.0/144,b-a,.001);
        var shortLine=new IslandTextStrip("短句",13,true,Color.WHITE);
        assertEquals((200-shortLine.width())/2,shortLine.left(200,100),.001);
    }
    @Test void tiledGlyphsMatchWholeLineAcrossTileBoundary(){
        String text="Long sentence: "+"整句字体不会逐字跳动，边界不得丢字。".repeat(10);
        var strip=new IslandTextStrip(text,13,true,Color.WHITE);assertTrue(strip.width()>1024);
        BufferedImage stitched=new BufferedImage(1024,strip.height(),BufferedImage.TYPE_INT_ARGB);
        var g=stitched.createGraphics();g.drawImage(strip.tile(0,1),0,0,null);g.drawImage(strip.tile(1,1),512,0,null);g.dispose();
        BufferedImage full=new BufferedImage(1024,strip.height(),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(full,1)){c.text(text,IslandTextStrip.PADDING,strip.baseline(),13,true,Color.WHITE,strip.width()+1);}
        assertArrayEquals(full.getRGB(0,0,1024,strip.height(),null,0,1024),stitched.getRGB(0,0,1024,strip.height(),null,0,1024));
    }
    @Test void backdropAndClippedStripsGenerateBothThemePreviews()throws Exception{
        Path out=Path.of("../outputs/qq-upgrade-2.12.0/previews");Files.createDirectories(out);
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            var view=new IslandPainter.View("The distance and the time between us will never change my mind",
                    "即使我们相隔遥远，这份心意也不会改变，这是完整的一句中文翻译。","1:43",500,true);
            var title=new IslandTextStrip(view.title(),13,true,FrostCanvas.INK);
            var sub=new IslandTextStrip(view.subtitle(),11,false,FrostCanvas.MUTED);
            for(int seconds:new int[]{0,4,8}){
                BufferedImage image=IslandPainter.paintBackdrop(view,1,null);
                Graphics2D g=image.createGraphics();g.translate(IslandPainter.PAD,IslandPainter.PAD);
                float viewport=IslandPainter.availableTextWidth(view);
                Graphics2D clipped=(Graphics2D)g.create();clipped.clip(new Rectangle2D.Float(55,5,viewport+4,46));
                float offset=MarqueeText.sharedLeft(title.width(),sub.width(),viewport,seconds);
                clipped.translate(57+offset,0);
                for(int line=0;line<2;line++){
                    var strip=line==0?title:sub;int baseline=line==0?26:44;
                    for(int tile=0;tile*512<strip.width();tile++)clipped.drawImage(strip.tile(tile,1),tile*512-IslandTextStrip.PADDING,baseline-strip.baseline(),null);
                }
                clipped.dispose();
                g.dispose();ImageIO.write(image,"png",out.resolve((dark?"dark":"light")+"-hud-"+seconds+"s.png").toFile());
            }
        }
        FrostCanvas.setDark(false);
    }
}
