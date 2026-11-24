package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.List;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class QQRenderingRegressionTest {
    static MusicView view(MusicView.Page page,String title,String status){
        return new MusicView(page,new SongView(MusicSource.QQ,"Mid",title,"歌手","专辑",300,""),List.of(),List.of(),List.of(),120,true,
                false,"","",false,false,status,"SQ FLAC","原文",true,PlaybackMode.REPEAT_LIST,false,"test","",0,-1,.8f,"",FrostCanvas.dark,1,1,.5f,.5f,"H");
    }
    static BufferedImage base(MusicView v){return MusicPainter.paint(v,new MusicLayout(v.page(),0,0),1,url->null);}
    static int[] region(BufferedImage image,Box box){return image.getRGB(PAD+box.x(),PAD+box.y(),box.w(),box.h(),null,0,box.w());}
    @Test void qqNowStaticLayerDoesNotContainAnotherCopyOfPlaybackBar(){
        FrostCanvas.setDark(false);
        assertArrayEquals(region(base(view(MusicView.Page.NOW,"上一首歌曲","")),MusicPainter.playbackBarRegion()),
                region(base(view(MusicView.Page.NOW,"完全不同的新歌曲","")),MusicPainter.playbackBarRegion()));
    }
    @Test void playbackStatusCannotLeakIntoDiscoverFavoritesQueueOrSettings(){
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            for(var page:List.of(MusicView.Page.NOW,MusicView.Page.DISCOVER,MusicView.Page.FAVORITES,MusicView.Page.QUEUE,MusicView.Page.SETTINGS))
                assertArrayEquals(region(base(view(page,"歌曲","")),new Box(292,98,758,95)),
                        region(base(view(page,"歌曲","QQ 播放中 · 已暂停网易云播放")),new Box(292,98,758,95)),page.name());
        }
        FrostCanvas.setDark(false);
    }
    @Test void explicitNowOverlayNeverDrawsNormalHandoffMessages(){
        var music=view(MusicView.Page.NOW,"歌曲","");
        var a=new QQPlaybackController.View(null,false,true,false,12,300,.8f,"SQ FLAC","",false);
        var b=new QQPlaybackController.View(null,false,true,false,12,300,.8f,"SQ FLAC","QQ 播放中 · 已暂停网易云播放",false);
        var region=new Box(292,98,758,95);
        assertArrayEquals(region(QQNowPainter.decorate(base(music),1,music,a),region),region(QQNowPainter.decorate(base(music),1,music,b),region));
    }
    @Test void newOverlaysPreserveExistingNavigationAndTitlePixels(){
        var music=view(MusicView.Page.NOW,"歌曲","");var base=base(music);
        var band=new Box(24,24,1026,68);int[] original=region(base,band);
        var playing=new QQPlaybackController.View(null,false,true,false,12,300,.8f,"SQ FLAC","",false);
        var result=QQNowPainter.decorate(base,1,music,playing);
        assertArrayEquals(original,region(result,band),"译按钮不能清空底图");
        base=base(view(MusicView.Page.QUEUE,"歌曲",""));
        original=region(base,band);
        result=QQQueuePainter.notice(base,1,"QQ 队列信息加载失败（1 首），稍后自动重试");
        assertArrayEquals(original,region(result,band),"队列警告不能清空底图");
    }
}
