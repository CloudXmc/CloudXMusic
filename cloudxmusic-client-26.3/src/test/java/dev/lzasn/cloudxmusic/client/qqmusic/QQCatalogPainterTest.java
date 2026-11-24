package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.*;
import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class QQCatalogPainterTest {
    @Test void trackDurationDoesNotOverlapShareButton() {
        var song = new UnifiedSong("qq", "MID1", "测试歌曲", "测试歌手", "测试专辑");
        var withDuration = catalogRow(song, 240);
        var withoutDuration = catalogRow(song, 0);
        var share = QQCatalogPainter.shareBox(0);
        for (int y = share.y(); y < share.y() + share.h(); y++) {
            for (int x = share.x(); x < share.x() + share.w(); x++) {
                assertEquals(withoutDuration.getRGB(x, y), withDuration.getRGB(x, y),
                        "时长文字不能进入分享按钮区域");
            }
        }
    }

    private BufferedImage catalogRow(UnifiedSong song, int duration) {
        var track = new QQCatalogApi.Track(song, SongView.from(song, duration, ""), 1);
        var view = new QQCatalogController.View(QQCatalogController.Mode.PLAYLIST, "测试歌单",
                java.util.List.of(), java.util.List.of(track), false, false, false, "", 0, 0);
        var image = new BufferedImage(1100, 674, BufferedImage.TYPE_INT_ARGB);
        try (var canvas = new FrostCanvas(image, 1)) {
            QQCatalogPainter.paint(canvas, view, url -> null, null);
        }
        return image;
    }

    @Test void dailyEntryNeverBorrowsUnrelatedPlaylistCover(){
        var list=new QQCatalogApi.Playlist("1",0,"","普通歌单",30,"unrelated-playlist");
        var v=new QQCatalogController.View(QQCatalogController.Mode.HOME,"",java.util.List.of(list),java.util.List.of(),false,false,false,"",0,0);
        var urls=new ArrayList<String>();var output=new BufferedImage(1100,674,BufferedImage.TYPE_INT_ARGB);
        try(var c=new FrostCanvas(output,1)){QQCatalogPainter.paint(c,v,url->{urls.add(url);return null;},null);}
        assertEquals(1,Collections.frequency(urls,"unrelated-playlist"),"普通歌单封面只能用于网格自身，不能被固定入口借用或复制成轮播卡");
    }
    @Test void recommendedCoverUsesSquareSpaceInsteadOfTheOldShallowThumbnail(){
        var picture=new BufferedImage(240,100,BufferedImage.TYPE_INT_RGB);var g=picture.createGraphics();g.setColor(Color.GREEN);g.fillRect(0,0,240,100);g.dispose();
        var tile=new QQCatalogApi.Playlist("1",0,"","测试歌单",30,"cover");
        var view=new QQCatalogController.View(QQCatalogController.Mode.HOME,"",java.util.List.of(tile),java.util.List.of(),false,false,false,"",0,0);
        var output=new BufferedImage(1100,674,BufferedImage.TYPE_INT_ARGB);
        try(var c=new FrostCanvas(output,1)){QQCatalogPainter.paint(c,view,url->picture,null);}
        assertEquals(Color.GREEN.getRGB(),output.getRGB(381,470),"正方形封面下部也必须显示原图，而不是浅缩略图或文字区域");
    }
    static BufferedImage image(){var image=new BufferedImage(150,100,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setPaint(new GradientPaint(0,0,new Color(0x638AA5),150,100,new Color(0xD8779D)));g.fillRect(0,0,150,100);g.dispose();return image;}
    @Test void rendersCardsGridAndSongPagesInBothThemesWithoutOverdrawingTitleOrBar()throws Exception{
        var lists=java.util.stream.IntStream.range(0,12).mapToObj(i->new QQCatalogApi.Playlist(""+(i+1),0,"",new String[]{"热歌榜","飙升榜","新歌榜","流行指数榜"}[i%4],30,"https://y.gtimg.cn/cover",QQCatalogApi.Kind.CHART,"2026-10-30")).toList();
        var songs=java.util.stream.IntStream.range(0,20).mapToObj(i->{var s=new UnifiedSong("qq","MID"+i,"테스트 / 日本語 · 歌曲 "+i,"演唱者","专辑",true);return new QQCatalogApi.Track(s,SongView.from(s,240,"https://y.gtimg.cn/cover"),i+1);}).toList();
        var out=Path.of("../outputs/integration/previews");Files.createDirectories(out);BufferedImage cover=image();
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);
            for(var mode:QQCatalogController.Mode.values()){
                String title=switch(mode){case LIKED->"我喜欢";case PLAYLIST->"热歌榜";default->"每日30首 · 今日私享";};
                var cv=new QQCatalogController.View(mode,title,lists,mode==QQCatalogController.Mode.HOME?java.util.List.of():songs,false,true,false,"已加载 · 下拉继续",0,0);
                var view=new MusicView(mode==QQCatalogController.Mode.LIKED?MusicView.Page.FAVORITES:MusicView.Page.DISCOVER,songs.getFirst().display(),java.util.List.of(),java.util.List.of(),java.util.List.of(),0,true,true,"测试QQ昵称","",false,false,"","SQ 无损","双语",true,PlaybackMode.REPEAT_LIST,false,"2.6.0-alpha.1","",0,-1,.8f,"",dark,1,1,.5f,.5f,"H");
                var layout=new MusicLayout(view.page(),0,0);
                var blank=MusicPainter.paint(view,layout,1,url->cover,c->QQLoginPainter.sidebar(c,new QQAccountManager.View(true,"小獭.","已登录",false),false,cover),c->{});
                var painted=MusicPainter.paint(view,layout,1,url->cover,c->QQLoginPainter.sidebar(c,new QQAccountManager.View(true,"小獭.","已登录",false),false,cover),c->QQCatalogPainter.paint(c,cv,url->cover,songs.getFirst().song()));
                for(int y:new int[]{50,90,590,650})for(int x=280;x<1060;x+=17)assertEquals(blank.getRGB(x+96,y+96),painted.getRGB(x+96,y+96),"目录不得画到标题或底栏");
                ImageIO.write(painted,"png",out.resolve((dark?"dark-":"light-")+mode.name().toLowerCase()+".png").toFile());
            }
        }
        FrostCanvas.setDark(false);
    }
    @Test void cardArrowAndSongHitsCannotPassThroughToNetease() {
        assertFalse(QQCatalogPainter.REFRESH.contains(100,474));assertFalse(QQCatalogPainter.PLAY.contains(671,619));assertFalse(QQCatalogPainter.MORE.contains(671,619));
    }

    @Test void catalogTrackAddHitUsesAbsoluteTrackIndex() {
        assertEquals(5, QQCatalogPainter.trackAddAt(1022, 182 + 3 * 52 + 20, 2, 10));
        assertEquals(-1, QQCatalogPainter.trackAddAt(990, 202, 0, 10));
    }
}
