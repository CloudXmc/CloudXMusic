package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout;
import dev.lzasn.cloudxmusic.client.gui.design.MusicPainter;
import dev.lzasn.cloudxmusic.client.gui.design.MusicView;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import dev.lzasn.cloudxmusic.client.state.Track;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSearchController.Source.*;
import static org.junit.jupiter.api.Assertions.*;

class QQSearchPainterTest {
    @Test void removedTabsHaveNoHitTargets() {
        assertNull(QQSearchPainter.tabAt(790, 46));
        assertNull(QQSearchPainter.tabAt(918, 46));
        assertNull(QQSearchPainter.tabAt(1030, 40));
        assertNull(QQSearchPainter.tabAt(990, 134));
        assertFalse(QQSearchPainter.blocksQqClick(400, 134));
        assertFalse(QQSearchPainter.blocksQqClick(990, 134));
        assertFalse(QQSearchPainter.blocksQqClick(100, 200));
    }

    @Test void allLegacyRowsAndAddButtonsAreBlockedForQq() {
        MusicLayout layout = new MusicLayout(MusicView.Page.SEARCH, 8, 0);
        for (var hit : layout.hits()) {
            if (hit.action() == MusicLayout.Action.ROW || hit.action() == MusicLayout.Action.ADD) {
                assertTrue(QQSearchPainter.blocksQqClick(hit.box().cx(), hit.box().cy()), hit.id());
            }
        }
    }

    @Test void rendersBothThemesAndRedErrorWithoutChangingAccountPixels() throws Exception {
        Path out = Path.of("../outputs/qq-integration").toAbsolutePath().normalize();
        Files.createDirectories(out);
        for (boolean dark : new boolean[]{false, true}) {
            FrostCanvas.setDark(dark);
            MusicView music = view();
            BufferedImage base = MusicPainter.paint(music, new MusicLayout(MusicView.Page.SEARCH, 0, 0), 1, key -> null);
            var songs = java.util.stream.IntStream.range(0, 20).mapToObj(i -> new UnifiedSong("qq", "Mid" + i,
                    i == 0 ? "晴天" : "QQ 搜索结果 " + (i + 1), "周杰伦", "叶惠美")).toList();
            var search = new QQSearchController.View(QQ, songs, 0, false, false, "搜索到 20 首（仅展示，暂不支持播放）");
            BufferedImage result = QQSearchPainter.decorate(base, 1, music, search);
            assertEquals(base.getRGB(1096, 726), result.getRGB(1096, 726), "清空列表后须恢复同一背景透明度");
            for (int y = 100; y < 700; y += 23) for (int x = 100; x < 350; x += 19) {
                assertEquals(base.getRGB(x, y), result.getRGB(x, y), "侧栏像素必须保持不变");
            }
            assertTrue(ImageIO.write(result, "png", out.resolve(dark ? "qq-search-dark.png" : "qq-search-light.png").toFile()));
            var failure = new QQSearchController.View(QQ, List.of(), 0, false, true, "搜索失败，请检查网络后重试");
            BufferedImage failed = QQSearchPainter.decorate(base, 1, music, failure);
            Color expected = QQSearchPainter.errorColor();
            boolean foundRed = false;
            for (int y = 255; y < 273; y++) for (int x = 388; x < 800; x++) {
                Color pixel = new Color(failed.getRGB(x, y), true);
                if (Math.abs(pixel.getRed() - expected.getRed()) < 8
                        && Math.abs(pixel.getGreen() - expected.getGreen()) < 8
                        && Math.abs(pixel.getBlue() - expected.getBlue()) < 8) foundRed = true;
            }
            assertTrue(foundRed, "错误提示应使用红色绘制");
            ImageIO.write(failed, "png", out.resolve(dark ? "qq-error-dark.png" : "qq-error-light.png").toFile());
            ImageIO.write(QQSearchPainter.decorate(base, 1, music,
                    new QQSearchController.View(NETEASE, List.of(), 0, false, false, "")),
                    "png", out.resolve(dark ? "netease-tab-dark.png" : "netease-tab-light.png").toFile());
        }
        FrostCanvas.setDark(false);
    }

    @Test void metadataCoverAndVipArePaintedInBothThemes()throws Exception{
        Path out=Path.of("../outputs/qq-hotfixes-2.11.0/previews");Files.createDirectories(out);
        BufferedImage cover=new BufferedImage(40,40,BufferedImage.TYPE_INT_ARGB);
        var brush=cover.createGraphics();brush.setColor(new Color(0x26A77B));brush.fillRect(0,0,40,40);brush.dispose();
        for(boolean dark:new boolean[]{false,true}){
            FrostCanvas.setDark(dark);var music=view();
            var base=MusicPainter.paint(music,new MusicLayout(MusicView.Page.SEARCH,0,0),1,url->null);
            var song=new UnifiedSong("qq","Mid","测试歌曲","测试歌手","测试专辑");
            var search=new QQSearchController.View(QQ,List.of(song),0,true,false,"正在加载更多…");
            var result=QQSearchPainter.decoratePlayback(base,1,music,search,null,
                s->dev.lzasn.cloudxmusic.client.omni.SongView.from(s,240,"cover",true),url->cover);
            assertEquals(cover.getRGB(20,20),result.getRGB(MusicLayout.PAD+322,MusicLayout.PAD+MusicLayout.ROW_TOP+23));
            var without=QQSearchPainter.decoratePlayback(base,1,music,search,null,
                s->dev.lzasn.cloudxmusic.client.omni.SongView.from(s,240,"cover",false),url->cover);
            int different=0;
            for(int y=MusicLayout.ROW_TOP;y<MusicLayout.ROW_TOP+32;y++)for(int x=354;x<714;x++)
                if(result.getRGB(MusicLayout.PAD+x,MusicLayout.PAD+y)!=without.getRGB(MusicLayout.PAD+x,MusicLayout.PAD+y))different++;
            assertTrue(different>20,"VIP 必须实际绘制，不能只存在于元数据中");
            ImageIO.write(result,"png",out.resolve((dark?"dark":"light")+"-search-cover-vip.png").toFile());
        }
        FrostCanvas.setDark(false);
    }

    private static MusicView view() {
        Track current = new Track("原网易云播放曲目", "原歌手", 200, "123", "原专辑", "");
        return new MusicView(MusicView.Page.SEARCH, current, List.of(), List.of(), List.of(), 0,
                true, false, "", "周杰伦", true, true, "", "标准", "原文", true,
                PlaybackMode.values()[0], false, "test", "", 0, -1, .5f, "", FrostCanvas.dark,
                1f, 1f, .5f, .5f, "H");
    }
}
