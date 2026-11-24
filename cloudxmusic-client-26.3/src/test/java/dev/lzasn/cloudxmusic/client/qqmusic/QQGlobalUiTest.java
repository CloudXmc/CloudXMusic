package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class QQGlobalUiTest {
    @Test void qqPlaceholdersIgnoreInjectedNeteaseLists(){
        FrostCanvas.setDark(false);
        for(var page:List.of(MusicView.Page.DISCOVER,MusicView.Page.FAVORITES)){
            MusicView v=view(page);
            MusicView contaminated=new MusicView(v.page(),v.current(),List.of(SongView.from(new Track("不得出现的网易云曲目","网易云歌手",123,"123","网易云专辑","netease-cover"))),v.queue(),v.lyrics(),v.positionTenths(),v.playing(),v.loggedIn(),v.nickname(),v.search(),v.focused(),v.cursor(),v.status(),v.quality(),v.lyricMode(),v.islandEnabled(),v.playbackMode(),v.favorite(),v.version(),v.hover(),v.coverRevision(),v.seekPreview(),v.volume(),v.avatarUrl(),v.darkMode(),v.panelScale(),v.islandScale(),v.islandX(),v.islandY(),v.openKey());
            BufferedImage clean=MusicPainter.paint(v,new MusicLayout(page,0,0),1,k->null);
            BufferedImage dirty=MusicPainter.paint(contaminated,new MusicLayout(page,1,0),1,k->{if(k.equals("netease-cover"))fail("QQ 占位页不能请求网易云封面");return null;});
            assertArrayEquals(clean.getRGB(0,0,clean.getWidth(),clean.getHeight(),null,0,clean.getWidth()),dirty.getRGB(0,0,dirty.getWidth(),dirty.getHeight(),null,0,dirty.getWidth()));
        }
    }
    private MusicView view(MusicView.Page page){
        SongView song=new SongView(MusicSource.QQ,"Song123","测试歌曲 · QQ 音乐","测试歌手","测试专辑",240,"qq-cover");
        return new MusicView(page,song,page==MusicView.Page.QUEUE?List.of(song):List.of(),List.of(),
                java.util.stream.IntStream.range(0,7).mapToObj(i->new MusicView.LyricRow("这是第 "+(i+1)+" 行测试歌词",i==3?"translation":"",i==3)).toList(),
                621,true,true,"测试QQ昵称","",false,false,"QQ 播放中 · 已暂停网易云播放","SQ FLAC","原文+翻译",true,PlaybackMode.REPEAT_LIST,false,"2.9.0","",1,-1,.6f,"",FrostCanvas.dark,1,1,.5f,.5f,"H");
    }
    @Test void globalQqPagesUseIndependentDataAndRenderModalAboveCoverAndControls()throws Exception{
        Path out=Path.of("../outputs/global-source-module1/previews");Files.createDirectories(out);
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            BufferedImage cover=new BufferedImage(300,300,BufferedImage.TYPE_INT_RGB);Graphics2D cg=cover.createGraphics();cg.setColor(new Color(75,139,160));cg.fillRect(0,0,300,300);cg.setColor(Color.WHITE);cg.fillOval(70,70,160,160);cg.dispose();
            for(var page:List.of(MusicView.Page.NOW,MusicView.Page.DISCOVER,MusicView.Page.FAVORITES,MusicView.Page.QUEUE)){
                MusicView v=view(page);MusicLayout l=new MusicLayout(page,v.tracks().size(),0);
                BufferedImage base=MusicPainter.paint(v,l,1,url->cover,c->QQLoginPainter.sidebar(c,new QQAccountManager.View(true,"测试QQ昵称","已登录",false),false));
                Graphics2D g=base.createGraphics();
                if(page==MusicView.Page.NOW){Box cr=coverRegion(),lr=lyricRegion();g.drawImage(MusicPainter.paintCover(v,1,1,url->cover),PAD+cr.x(),PAD+cr.y(),null);g.drawImage(MusicPainter.paintLyrics(v,1),PAD+lr.x(),PAD+lr.y(),null);}
                Box bar=MusicPainter.playbackBarRegion();g.drawImage(MusicPainter.paintPlaybackBar(v,new MusicPainter.PlaybackBarView(v.current().title(),v.current().artist(),240,62.1,true,.6f,"SQ FLAC",true),1),PAD+bar.x(),PAD+bar.y(),null);g.dispose();
                assertTrue(ImageIO.write(base,"png",out.resolve((dark?"dark-":"light-")+page.name().toLowerCase()+".png").toFile()));
                if(page==MusicView.Page.NOW){
                    var route=new MusicSourceCoordinator.View(MusicSource.QQ,MusicSource.QQ,1,false,false,"");
                    BufferedImage modal=QQSourcePainter.menu(1,route,"访客模式","测试QQ昵称");g=base.createGraphics();g.drawImage(modal,0,0,null);g.dispose();
                    ImageIO.write(base,"png",out.resolve((dark?"dark":"light")+"-source-menu.png").toFile());
                }
            }
        }
        FrostCanvas.setDark(false);
    }
    @Test void sourceMenuAndQualityChoicesShareTheirDrawnHitGeometry(){
        assertTrue(QQSourcePainter.QQ.contains(640,331));assertFalse(QQSourcePainter.NETEASE.contains(640,331));
        assertEquals(74,MusicPainter.qualityMenuRegion(2).h());assertEquals(594,MusicPainter.qualityRow(1,2).y()+37);
        assertEquals(MusicLayout.Action.QUALITY,QQPlaybackUi.actionAt(918,619));
    }
}
