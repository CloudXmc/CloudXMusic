package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.*;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;
import static org.junit.jupiter.api.Assertions.*;

/** 离线组合真实图层，检查长标题、VIP、完整队列、翻译和音质菜单的布局。 */
class QQFixPreviewTest {
    @Test void composeLightAndDarkPagesWithAllRealLayers()throws Exception{
        Path out=Path.of("../outputs/qq-playback-fixes-2.10.0/previews");Files.createDirectories(out);
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            BufferedImage cover=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);var cg=cover.createGraphics();
            cg.setPaint(new GradientPaint(0,0,new Color(91,141,164),300,300,new Color(185,93,135)));cg.fillRect(0,0,300,300);cg.dispose();
            List<SongView> songs=java.util.stream.IntStream.range(0,7).mapToObj(i->new SongView(MusicSource.QQ,"Song"+i,i==0?"发如雪 · 长歌名的排版验证":"队列歌曲 "+(i+1),"测试歌手","测试专辑",300+i*11,"cover"+i,i%2==0)).toList();
            for(var page:List.of(MusicView.Page.NOW,MusicView.Page.QUEUE,MusicView.Page.DISCOVER,MusicView.Page.SETTINGS)){
                MusicView view=new MusicView(page,songs.getFirst(),page==MusicView.Page.QUEUE?songs:List.of(),List.of(),
                        java.util.stream.IntStream.range(0,7).mapToObj(i->new MusicView.LyricRow("This is lyric line "+(i+1),"这是第 "+(i+1)+" 行译文",i==3)).toList(),
                        621,true,true,"测试 QQ 昵称","",false,false,"QQ 播放中 · 已暂停网易云播放","HQ 320k","原文+翻译",true,PlaybackMode.SHUFFLE,false,"2.10.0","",1,-1,.6f,"",dark,1,1,.5f,.5f,"H");
                var layout=new MusicLayout(page,view.tracks().size(),0);
                var base=MusicPainter.paint(view,layout,1,url->cover,c->QQLoginPainter.sidebar(c,new QQAccountManager.View(true,"测试 QQ 昵称","已登录",false),false));
                if(page==MusicView.Page.NOW)base=QQNowPainter.decorate(base,1,view,new QQPlaybackController.View(null,false,true,false,62.1,300,.6f,"HQ 320k","QQ 播放中 · 已暂停网易云播放",false));
                var g=base.createGraphics();
                if(page==MusicView.Page.NOW){var cr=coverRegion();var lr=lyricRegion();g.drawImage(MusicPainter.paintCover(view,1,1,url->cover),PAD+cr.x(),PAD+cr.y(),null);g.drawImage(MusicPainter.paintLyrics(view,1),PAD+lr.x(),PAD+lr.y(),null);}
                if(page!=MusicView.Page.SETTINGS){var b=MusicPainter.playbackBarRegion();g.drawImage(MusicPainter.paintPlaybackBar(view,new MusicPainter.PlaybackBarView(songs.getFirst().title(),"测试歌手",300,62.1,true,.6f,"HQ 320k",true,true),1),PAD+b.x(),PAD+b.y(),null);}
                g.dispose();String prefix=dark?"dark-":"light-";
                assertTrue(ImageIO.write(base,"png",out.resolve(prefix+page.name().toLowerCase()+".png").toFile()));
                if(page==MusicView.Page.NOW){g=base.createGraphics();var q=QQQualityMenu.region();g.drawImage(QQQualityMenu.paint(0,1,1),PAD+q.x(),PAD+q.y(),null);g.dispose();ImageIO.write(base,"png",out.resolve(prefix+"quality.png").toFile());}
            }
        }
        FrostCanvas.setDark(false);
    }
}
