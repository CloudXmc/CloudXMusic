package dev.lzasn.cloudxmusic.client.gui.playlist;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import static dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.*;
import static org.junit.jupiter.api.Assertions.*;

class PlaylistPainterTest {
    @Test void tabsCardsAndRowsHaveSeparateHitsAndStopBeforePlaybackBar(){
        for(var section:Section.values()){var box=PlaylistLayout.tab(section);assertEquals(section,PlaylistLayout.tabAt(box.cx(),box.cy()));}
        assertNull(PlaylistLayout.tabAt(100,120));
        for(int n=0;n<8;n++){var b=PlaylistLayout.card(n);assertTrue(b.y()+b.h()<582);assertEquals(n,PlaylistLayout.cardAt(b.cx(),b.cy(),0,8));}
        for(int n=0;n<7;n++){var b=PlaylistLayout.row(n);assertTrue(b.y()+b.h()<=558);assertFalse(PlaylistLayout.PLAY.contains(b.cx(),b.cy()));}
        assertEquals(-1,PlaylistLayout.rowAt(670,620,0,20));
    }
    @Test void coverUsesRoundedMaskAndCenteredAspectCrop(){
        var art=new BufferedImage(300,100,BufferedImage.TYPE_INT_RGB);var g=art.createGraphics();g.setColor(Color.RED);g.fillRect(0,0,300,100);g.setColor(Color.GREEN);g.fillRect(100,0,100,100);g.dispose();
        var output=new BufferedImage(120,120,BufferedImage.TYPE_INT_ARGB);
        try(var c=new FrostCanvas(output,1)){PlaylistPainter.cover(c,art,new MusicLayout.Box(10,10,100,100),14);}
        assertEquals(0,output.getRGB(10,10)>>>24);assertEquals(Color.GREEN.getRGB(),output.getRGB(12,60));assertEquals(Color.GREEN.getRGB(),output.getRGB(108,60));
    }
    @Test void fullScreenUsesTheExistingSidebarAndPlaybackBar() throws Exception {
        Path out=Path.of("build/playlist-previews");Files.createDirectories(out);
        var art=new BufferedImage(320,180,BufferedImage.TYPE_INT_RGB);var g=art.createGraphics();g.setPaint(new GradientPaint(0,0,new Color(65,125,150),320,180,new Color(234,183,140)));g.fillRect(0,0,320,180);g.setColor(new Color(255,255,255,160));g.fillOval(150,35,82,82);g.dispose();
        var song=new dev.lzasn.cloudxmusic.client.omni.UnifiedSong("qq","TEST","测试歌曲","测试歌手","测试专辑",true);
        var display=dev.lzasn.cloudxmusic.client.omni.SongView.from(song,180,"cover");
        var entries=java.util.stream.IntStream.range(0,12).mapToObj(i->new Entry("qq:123:"+(i+1),"我的音乐收藏 "+(i+1),"cover",120+i)).toList();
        for(boolean dark:new boolean[]{false,true})for(boolean detail:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            var music=new MusicView(MusicView.Page.FAVORITES,display,List.of(),List.of(),List.of(),300,true,true,"测试账号","",false,false,"","SQ 无损","双语",true,dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST,false,"2.6.0-alpha.3","",0,-1,.8f,"",dark,1,1,.5f,.5f,"H");
            var view=new PlaylistView(Section.OWNED,detail?entries.getFirst():null,detail?List.of():entries,detail?List.of(new Track(song,display,"","",null)):List.of(),false,detail,true,false,detail?"正在加载 30/100 首":"已加载 12 个歌单",0,100);
            var image=MusicPainter.paint(music,new MusicLayout(music.page(),0,0),1,url->art,
                    c->dev.lzasn.cloudxmusic.client.qqmusic.QQLoginPainter.sidebar(c,new dev.lzasn.cloudxmusic.client.qqmusic.QQAccountManager.View(true,"测试账号","已登录",false),false,art),c->PlaylistPainter.paint(c,view,url->art,song));
            var bar=MusicPainter.paintPlaybackBar(music,new MusicPainter.PlaybackBarView(display.title(),display.artist(),180,30,true,.8f,"SQ",true,true,"cover"),1,url->art);
            var region=MusicPainter.playbackBarRegion();g=image.createGraphics();g.drawImage(bar,region.x()+MusicLayout.PAD,region.y()+MusicLayout.PAD,null);g.dispose();
            ImageIO.write(image,"png",out.resolve((dark?"dark":"light")+(detail?"-screen-detail":"-screen-grid")+".png").toFile());
        }
        FrostCanvas.setDark(false);
    }
    @Test void renderBothThemesAndKeepDrawingInsideContent() throws Exception {
        Path out=Path.of("build/playlist-previews");Files.createDirectories(out);
        var entries=java.util.stream.IntStream.range(0,12).mapToObj(i->new Entry("qq:123:"+(i+1),i==1?"很长的歌单名称也应该正确省略且不覆盖相邻卡片":"我的音乐收藏 "+(i+1),"cover",120+i)).toList();
        for(boolean dark:new boolean[]{false,true}){FrostCanvas.setDark(dark);
            for(boolean detail:new boolean[]{false,true}){
                var image=new BufferedImage(1100,674,BufferedImage.TYPE_INT_ARGB);
                var tracks=List.of(PlaylistControllerTest.song("VIP song",null),PlaylistControllerTest.song("测试歌曲",null));
                var view=new PlaylistView(Section.OWNED,detail?entries.getFirst():null,detail?List.of():entries,detail?tracks:List.of(),false,detail,true,false,detail?"正在加载 30/100 首":"已加载 12 个歌单",0,100);
                try(var c=new FrostCanvas(image,1)){
                    c.round(0,0,1100,674,30,dark?new Color(30,36,42):new Color(255,248,252));
                    PlaylistPainter.paint(c,view,url->null,null);
                }
                int background=image.getRGB(285,90);assertEquals(background,image.getRGB(700,90));assertEquals(background,image.getRGB(700,590));
                ImageIO.write(image,"png",out.resolve((dark?"dark":"light")+(detail?"-detail":"-grid")+".png").toFile());
            }
        }
        FrostCanvas.setDark(false);
    }
}
