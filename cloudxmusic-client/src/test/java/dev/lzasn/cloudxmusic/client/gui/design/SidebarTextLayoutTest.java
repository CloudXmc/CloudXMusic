package dev.lzasn.cloudxmusic.client.gui.design;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class SidebarTextLayoutTest {
    @Test void continuousLayoutKeepsLatinAdvancesAndUnicodeBoundariesAtEveryScale()throws Exception{
        Path out=Path.of("../outputs/integration/previews");Files.createDirectories(out);
        for(boolean dark:new boolean[]{false,true})for(double scale:new double[]{1,1.25,1.5,2}){
            FrostCanvas.setDark(dark);var image=new BufferedImage((int)(260*scale),(int)(70*scale),BufferedImage.TYPE_INT_ARGB);
            try(var c=new FrostCanvas(image,scale)){
                c.round(0,0,260,70,10,FrostCanvas.CARD_BG);
                var run=SidebarTextLayout.layout("QQ 音乐 · 切换音源 / 管理",11,false,c.g.getFontRenderContext());
                float first=run.getCaretInfo(java.awt.font.TextHitInfo.leading(1))[0];
                float second=run.getCaretInfo(java.awt.font.TextHitInfo.leading(2))[0];
                assertTrue(first>5);assertTrue(second-first>5);
                String fitted=SidebarTextLayout.fit("QQ 音乐 🎵 · 管理",11,false,43,c.g.getFontRenderContext());
                for(int i=0;i<fitted.length();i++)if(Character.isHighSurrogate(fitted.charAt(i))){assertTrue(i+1<fitted.length()&&Character.isLowSurrogate(fitted.charAt(++i)));}
                SidebarTextLayout.draw(c,"QQ 音乐 · 切换音源 / 管理",20,39,11,false,FrostCanvas.MUTED,220);
            }
            ImageIO.write(image,"png",out.resolve("sidebar-"+dark+"-"+scale+".png").toFile());
        }
        FrostCanvas.setDark(false);
    }
}
