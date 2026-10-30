package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class QQQualityMenuTest {
    @Test void everyActualQualityHasItsOwnCheckmarkAndPendingTracksDoNotShowTheOldOne()throws Exception{
        var out=Path.of("../outputs/qq-upgrade-2.13.2/previews");Files.createDirectories(out);
        try{
            for(boolean dark:new boolean[]{false,true}){
                FrostCanvas.setDark(dark);
                for(var actual:QQQuality.values()){
                    var view=new QQPlaybackController.View(QQPlaybackControllerTest.A,false,true,false,42,180,.8f,actual.barLabel(),"",false,actual);
                    int selected=QQQualityMenu.selected(view,QQQuality.SQ);assertEquals(actual.ordinal(),selected);
                    ImageIO.write(QQQualityMenu.paint(selected,-1,1),"png",out.resolve((dark?"dark-":"light-")+"quality-"+actual.name().toLowerCase()+".png").toFile());
                }
            }
            var loading=new QQPlaybackController.View(QQPlaybackControllerTest.B,true,false,false,0,180,.8f,"QQ","正在取链",false,null);
            assertEquals(-1,QQQualityMenu.selected(loading,QQQuality.SQ));
            var stopped=new QQPlaybackController.View(null,false,false,false,0,0,.8f,"QQ","",false,null);
            assertEquals(QQQuality.HQ.ordinal(),QQQualityMenu.selected(stopped,QQQuality.HQ));
        }finally{FrostCanvas.setDark(false);}
    }
}
