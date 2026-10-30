package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class QQCoverArtTest {
    @Test void wideAndTallSourcesAreCroppedAtTheCenterWithoutStretching(){
        for(boolean wide:new boolean[]{true,false}){
            var input=new BufferedImage(wide?300:100,wide?100:300,BufferedImage.TYPE_INT_RGB);var g=input.createGraphics();
            g.setColor(Color.RED);g.fillRect(0,0,input.getWidth(),input.getHeight());g.setColor(Color.GREEN);
            g.fillRect(wide?100:0,wide?0:100,100,100);g.setColor(Color.BLUE);g.fillOval(wide?125:25,wide?25:125,50,50);g.dispose();
            BufferedImage result=QQCoverArt.crop(input,100,100,0);
            assertEquals(Color.GREEN.getRGB(),result.getRGB(5,50));assertEquals(Color.GREEN.getRGB(),result.getRGB(50,5));
            assertEquals(Color.BLUE.getRGB(),result.getRGB(50,50));
            for(int delta:new int[]{-24,-10,10,24})assertEquals(result.getRGB(50+delta,50),result.getRGB(50,50+delta),"圆形不能变成椭圆");
        }
    }
    @Test void roundedMaskHasTransparentCornersAndAntialiasedEdgesAtBothUiScales(){
        var image=new BufferedImage(80,80,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,80,80);g.dispose();
        for(int scale:new int[]{1,2}){
            var rounded=QQCoverArt.crop(image,100*scale,100*scale,16*scale);
            assertEquals(0,rounded.getRGB(0,0)>>>24);assertEquals(255,rounded.getRGB(50*scale,50*scale)>>>24);
            int partial=0;for(int y=0;y<16*scale;y++)for(int x=0;x<16*scale;x++){int a=rounded.getRGB(x,y)>>>24;if(a>0&&a<255)partial++;}
            assertTrue(partial>0,"圆角蒙版必须有抗锯齿 alpha，不能仅依赖硬裁剪");
        }
    }
    @Test void gridHitGeometryExcludesGapsAndMapsEveryVisibleTitle(){
        for(int n=0;n<4;n++){
            var box=QQCatalogLayout.tile(n);assertEquals(box.w(),QQCatalogLayout.COVER_SIZE);
            assertEquals(8+n,QQCatalogLayout.playlistAt(box.x()+20,box.y()+box.h()-8,2,12));
            assertEquals(-1,QQCatalogLayout.playlistAt(box.x()+box.w()+3,box.y()+20,2,12));
        }
        assertEquals(-1,QQCatalogLayout.playlistAt(300,590,2,12));
        assertEquals(-1,QQCatalogLayout.playlistAt(QQCatalogLayout.tile(3).cx(),400,2,11));
    }
}
