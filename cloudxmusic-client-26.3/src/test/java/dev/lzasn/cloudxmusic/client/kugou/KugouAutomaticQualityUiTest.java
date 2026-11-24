package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class KugouAutomaticQualityUiTest {
    @Test void hiddenCapsuleCannotBeClickedWhileOtherControlsAndSourcesRemainAvailable(){
        var layout=new MusicLayout(MusicView.Page.NOW,0,0);
        for(var source:MusicSource.values()){
            if(source==MusicSource.KUGOU){assertFalse(MusicLayout.qualitySelectable(source));assertNull(layout.hit(900,619,source));}
            else{assertTrue(MusicLayout.qualitySelectable(source));assertEquals(MusicLayout.Action.QUALITY,layout.hit(900,619,source).action());}
            for(var hit:layout.hits())if(hit.action()!=MusicLayout.Action.QUALITY)
                assertEquals(layout.hit(hit.box().cx(),hit.box().cy()),layout.hit(hit.box().cx(),hit.box().cy(),source));
        }
    }
    @Test void kugouCapsuleIsAbsentInBothThemesWhileOtherSourcesKeepTheirButtons()throws Exception{
        var region=MusicPainter.playbackBarRegion();
        Path previews=Path.of("../outputs/kugou-auto-quality-20261116/previews");Files.createDirectories(previews);
        try{
            for(boolean dark:new boolean[]{false,true})for(var source:MusicSource.values()){
                FrostCanvas.setDark(dark);
                var view=new MusicView(MusicView.Page.NOW,SongView.empty(source),List.of(),List.of(),List.of(),0,false,
                        false,"","",false,false,"","标准音质","原文",true,PlaybackMode.REPEAT_LIST,false,
                        "test","",0,-1,.8f,"",dark,1,1,.5f,.5f,"H");
                var bar=new MusicPainter.PlaybackBarView("测试歌曲","歌手",240,60,true,.8f,"标准音质",source!=MusicSource.NETEASE);
                for(double scale:new double[]{1,1.5}){
                    var image=MusicPainter.paintPlaybackBar(view,bar,scale);
                    // 胶囊中心远离进度、音量与其他控件；隐藏时整个区域必须透明。
                    int alpha=0;for(int y=607;y<630;y++)for(int x=888;x<948;x++)
                        alpha+=image.getRGB((int)((x-region.x())*scale),(int)((y-region.y())*scale))>>>24;
                    if(source==MusicSource.KUGOU)assertEquals(0,alpha,"酷狗不能绘制空胶囊或音质文字");
                    else assertTrue(alpha>0,"网易云与 QQ 必须保留音质胶囊");
                    if(scale==1)ImageIO.write(image,"png",previews.resolve(source.name()+"-"+(dark?"dark":"light")+".png").toFile());
                }
            }
        }finally{FrostCanvas.setDark(false);}
    }
}
