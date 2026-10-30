package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QQPlaybackBarTest {
    @Test void coverAndTextNavigateWithoutOverlappingControls()throws Exception{
        assertTrue(QQPlaybackUi.nowPlayingAt(314,617));assertTrue(QQPlaybackUi.nowPlayingAt(410,615));
        assertFalse(QQPlaybackUi.nowPlayingAt(550,619));assertFalse(QQPlaybackUi.nowPlayingAt(500,652));
        var image=new BufferedImage(40,40,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();g.setColor(new java.awt.Color(0x4387C1));g.fillRect(0,0,40,40);g.dispose();
        var bar=new MusicPainter.PlaybackBarView("현재 노래 / 日本語","歌手",240,50,true,.8f,"SQ FLAC",true,true,"cover");
        Path out=Path.of("../outputs/qq-upgrade-2.12.0/previews");Files.createDirectories(out);
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            var rendered=MusicPainter.paintPlaybackBar(QQLoginPainterTest.view(),bar,1,url->image);
            assertEquals(image.getRGB(20,20),rendered.getRGB(314-292,617-586));
            assertEquals(0,rendered.getRGB(296-292,599-586)>>>24,"圆角封面不填满角落");
            ImageIO.write(rendered,"png",out.resolve((dark?"dark":"light")+"-bar-cover.png").toFile());
        }
        FrostCanvas.setDark(false);
    }
    @Test void reusesOriginalControlCoordinatesAndMapsStopWithoutTouchingNetease(){
        assertEquals(MusicLayout.Action.PLAY,QQPlaybackUi.actionAt(671,619));
        assertEquals(MusicLayout.Action.STOP,QQPlaybackUi.actionAt(550,619));
        assertEquals(MusicLayout.Action.NEXT,QQPlaybackUi.actionAt(716,619));
        assertEquals(MusicLayout.Action.PAGE,QQPlaybackUi.actionAt(580,619));
        assertEquals(MusicLayout.Action.QUALITY,QQPlaybackUi.actionAt(900,619));
        assertEquals(MusicLayout.Action.MODE_CYCLE,QQPlaybackUi.actionAt(754,619));
        assertEquals(6,QQPlaybackUi.rowAt(400,540,0,20,true));
        assertEquals(-1,QQPlaybackUi.rowAt(400,590,0,20,true));
        assertEquals(7,QQPlaybackUi.rowAt(400,590,0,20,false));
        assertEquals(MusicLayout.Action.FAVORITE,new MusicLayout(MusicView.Page.NOW,0,0).hit(550,619).action());
    }
    @Test void rendersSameStyleBarAndKeepsSearchResultsAboveIt()throws Exception{
        Path out=Path.of("../outputs/qq-upgrade-2.12.0/previews/full-screen").toAbsolutePath().normalize();Files.createDirectories(out);
        List<UnifiedSong> songs=new ArrayList<>();for(int i=0;i<12;i++)songs.add(new UnifiedSong("qq","mid"+i,"测试歌曲 "+i,"歌手","专辑"));
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);MusicView view=QQLoginPainterTest.view();
            BufferedImage base=MusicPainter.paint(view,new MusicLayout(view.page(),0,0),1,key->null,c->QQLoginPainter.sidebar(c,new QQAccountManager.View(true,"测试QQ昵称","已登录",false),false));
            var playback=new QQPlaybackController.View(songs.get(0),false,true,false,65.5,245,.65f,"SQ FLAC","已暂停网易云播放",false);
            base=QQSearchPainter.decoratePlayback(base,1,view,new QQSearchController.View(QQSearchController.Source.QQ,songs,0,false,false,"搜索完成"),playback);
            BufferedImage bar=MusicPainter.paintPlaybackBar(view,QQPlaybackUi.barView(playback),1);
            assertEquals(758,bar.getWidth());assertEquals(76,bar.getHeight());
            var g=base.createGraphics();g.drawImage(bar,MusicLayout.PAD+292,MusicLayout.PAD+586,null);g.dispose();
            ImageIO.write(base,"png",out.resolve(dark?"qq-playing-dark.png":"qq-playing-light.png").toFile());
        }
        FrostCanvas.setDark(false);
    }
}
