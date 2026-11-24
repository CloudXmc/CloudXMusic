package dev.lzasn.cloudxmusic.client.gui.design;

import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class IslandMarqueeTest {
    @Test void movementIsFrameIndependentAndPausesAtEndForOneSecond(){
        float width=340,viewport=100;double travel=(width-viewport)/MarqueeText.SPEED;
        assertEquals(0,MarqueeText.offset(width,viewport,0));assertEquals(0,MarqueeText.offset(width,viewport,1));
        assertEquals(24,MarqueeText.offset(width,viewport,2),.001);assertEquals(120,MarqueeText.offset(width,viewport,6),.001);
        assertEquals(240,MarqueeText.offset(width,viewport,1+travel+.5),.001);
        assertEquals(240,MarqueeText.offset(width,viewport,1+travel+.999),.001);
        assertEquals(240,MarqueeText.offset(width,viewport,2+travel),.001);
        assertEquals(0,MarqueeText.offset(width,viewport,1+travel+MarqueeText.END_PAUSE_SECONDS+MarqueeText.returnSeconds(width-viewport)),.001);
        assertEquals(0,MarqueeText.offset(90,100,999));
    }
    @Test void lyricIdentityResetsOnSongTimestampAndTranslationChange(){
        var clock=new MarqueeClock();assertEquals(0,clock.elapsed("qq/song/1000/original",0));
        assertEquals(4.5,clock.elapsed("qq/song/1000/original",4_500_000_000L));
        assertEquals(0,clock.elapsed("qq/song/9000/original",5_000_000_000L));
        assertEquals(0,clock.elapsed("qq/other/1000/original",6_000_000_000L));
        assertEquals(0,clock.elapsed("qq/other/1000/bilingual",7_000_000_000L));clock.reset();
        assertEquals(0,clock.elapsed("qq/other/1000/bilingual",10_000_000_000L));
    }
    @Test void shortTextIsCenteredAndDoesNotAnimate(){
        BufferedImage first=new BufferedImage(300,50,BufferedImage.TYPE_INT_ARGB),later=new BufferedImage(300,50,BufferedImage.TYPE_INT_ARGB);
        for(int i=0;i<2;i++)try(FrostCanvas c=new FrostCanvas(i==0?first:later,1)){MarqueeText.draw(c,"短句",50,28,13,true,Color.WHITE,200,i*100);}
        assertArrayEquals(first.getRGB(0,0,300,50,null,0,300),later.getRGB(0,0,300,50,null,0,300));
        int min=300,max=0;for(int y=0;y<50;y++)for(int x=0;x<300;x++)if((first.getRGB(x,y)>>>24)>0){min=Math.min(min,x);max=Math.max(max,x);}
        assertEquals(150,(min+max)/2.0,2);
        assertFalse(IslandPainter.scrolls(new IslandPainter.View("短句","翻译","1:23",500,true)));
    }
    @Test void fullSentenceScrollsWithoutEllipsisOrPixelsOutsideSafeArea()throws Exception{
        Path out=Path.of("../outputs/qq-upgrade-2.12.0/previews/legacy-painter");Files.createDirectories(out);
        String title="This is a long lyric line whose ending must be fully visible at the end of the scroll";
        String subtitle="这是一句很长的歌词翻译，文字只能在灵动岛的安全区域内缓慢滚动，不得覆盖封面、时间或背景边缘。";
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            var view=new IslandPainter.View(title,subtitle,"125:59",750,true);assertTrue(IslandPainter.scrolls(view));
            for(double scale:new double[]{.75,1,1.5,2}){
                var blank=IslandPainter.paint(new IslandPainter.View("","","125:59",750,true),scale,null,0);
                var start=IslandPainter.paint(view,scale,null,0);var mid=IslandPainter.paint(view,scale,null,5);
                boolean different=false;
                for(int y=0;y<start.getHeight();y++)for(int x=0;x<start.getWidth();x++){
                    double lx=x/scale-IslandPainter.PAD,ly=y/scale-IslandPainter.PAD;
                    if(lx<56||lx>289||ly<10||ly>49){assertEquals(blank.getRGB(x,y),start.getRGB(x,y));assertEquals(blank.getRGB(x,y),mid.getRGB(x,y));}
                    if(start.getRGB(x,y)!=mid.getRGB(x,y))different=true;
                }
                assertTrue(different,"长句应随时间滚动");
                if(scale==1){ImageIO.write(start,"png",out.resolve((dark?"dark":"light")+"-hud-start.png").toFile());ImageIO.write(mid,"png",out.resolve((dark?"dark":"light")+"-hud-scroll.png").toFile());}
            }
        }
        FrostCanvas.setDark(false);
    }
    @Test void wholeLineWidthDeterminesTravelDistanceIncludingTranslatedTail(){
        try(FrostCanvas c=new FrostCanvas(new BufferedImage(800,50,BufferedImage.TYPE_INT_ARGB),1)){
            String text="前半句与完整的最后一句歌词，完整宽度决定移动终点";float width=c.width(text,13,true),viewport=120;
            double atEnd=1+(width-viewport)/MarqueeText.SPEED;
            assertEquals(width-viewport,MarqueeText.offset(width,viewport,atEnd),.001);
            BufferedImage actual=new BufferedImage(300,50,BufferedImage.TYPE_INT_ARGB);
            try(FrostCanvas target=new FrostCanvas(actual,1)){MarqueeText.draw(target,text,50,28,13,true,Color.WHITE,viewport,atEnd);}
            assertTrue(width>viewport);
        }
    }
}
