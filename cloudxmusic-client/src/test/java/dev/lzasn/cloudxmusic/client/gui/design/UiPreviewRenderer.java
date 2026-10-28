package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.state.Track;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicView.Page;

/** Java21-only offline painter and alignment smoke check for the shipped interface. */
public final class UiPreviewRenderer {
    private static final Track CURRENT = new Track("春日序曲", "雲與微光", 258, "1001", "微光花園", "");
    private static final List<Track> TRACKS = List.of(
            new Track("雨中的花开", "雲與微光", 216, "1002", "柔软时刻", ""),
            new Track("安静的清晨", "星", 242, "1003", "打开的窗", ""),
            new Track("远方的世界", "森", 231, "1004", "云图", ""),
            new Track("晚风吹过花园", "橙子海", 205, "1005", "风的来信", ""),
            new Track("玻璃海岸", "时雨", 187, "1006", "夏日地图", ""),
            new Track("回家的路", "白日梦", 223, "1007", "北方的风", ""),
            new Track("夜色慢慢降落", "木棉", 198, "1008", "灯火温柔", ""),
            new Track("候鸟与信", "南屿", 236, "1009", "远行", ""));

    UiPreviewRenderer() {}

    @Test void layoutCoordinatesStayAlignedAndInsideTheCanvas() { validateLayout(); }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args.length==0?"../../outputs":args[0]).toAbsolutePath().normalize();
        Files.createDirectories(out);
        validateLayout();
        render(out,"cloudxmusic-now-playing.png",Page.NOW,"",false,0);
        render(out,"cloudxmusic-library.png",Page.DISCOVER,"",false,0);
        render(out,"cloudxmusic-search.png",Page.SEARCH,"微光",true,1);
        render(out,"cloudxmusic-settings.png",Page.SETTINGS,"",false,0);
        renderNotice(out);
        System.out.println("Java "+Runtime.version()+"; layout smoke check passed; previews: "+out);
    }

    private static void validateLayout() {
        for(Page page:Page.values()) {
            MusicLayout l=new MusicLayout(page,ROWS,3);
            for(Hit h:l.hits()) {
                Box b=h.box();
                if(b.w()<=0||b.h()<=0||b.x()<0||b.y()<0||b.x()+b.w()>WIDTH||b.y()+b.h()>HEIGHT)
                    throw new AssertionError(page+" hitbox out of bounds: "+h);
            }
            if(page!=Page.NOW&&page!=Page.SETTINGS&&page!=Page.ACCOUNT) {
                Box first=l.box("row0"),last=l.box("row5"),add=l.box("add0");
                if(first.y()!=ROW_TOP||last.y()!=ROW_TOP+5*ROW_H||add.cy()!=first.cy())
                    throw new AssertionError(page+" list columns are not aligned");
            }
        }
        if(new MusicLayout(Page.SEARCH,ROWS,3).hit(990,134)==null)
            throw new AssertionError("search action does not have a hitbox");
        if(new MusicLayout(Page.NOW,ROWS,3).hit(500,652)==null)
            throw new AssertionError("seek bar does not have a hitbox");
        if(new MusicLayout(Page.NOW,ROWS,3).hit(671,619)==null)
            throw new AssertionError("transport controls do not have a hitbox");
    }

    private static void render(Path out,String file,Page page,String search,boolean focused,int focusIndex) throws Exception {
        int width=1600,height=900;
        BufferedImage frame=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D bg=frame.createGraphics();
        bg.setPaint(new GradientPaint(0,0,new Color(213,231,228),0,420,new Color(241,227,232)));
        bg.fillRect(0,0,width,height);
        bg.setColor(new Color(117,159,137,170));
        for(int x=0;x<width;x+=160)bg.fillRect(x,470+(x%3)*17,160,430);
        bg.setColor(new Color(81,128,101,170));
        for(int x=0;x<width;x+=240){bg.fillRect(x+40,420,110,280);bg.fillRect(x,475,190,425);}
        bg.setColor(new Color(37,51,45,38));bg.fillRect(0,0,width,height);
        bg.setColor(new Color(242,238,239,130));bg.fillRect(0,0,width,height);
        bg.dispose();

        List<Track> queue=List.of(CURRENT,TRACKS.get(0),TRACKS.get(1),TRACKS.get(2));
        MusicLayout layout=new MusicLayout(page,page==Page.NOW||page==Page.SETTINGS?0:ROWS,queue.size()-1);
        List<Track> rows=page==Page.NOW||page==Page.SETTINGS?List.of():TRACKS.subList(0,ROWS);
        List<Track> upcoming=page==Page.NOW?queue.subList(1,4):List.of();
        var lyrics=List.of(
                new MusicView.LyricRow("夜色慢慢降落","",false),
                new MusicView.LyricRow("灯火温柔地摇曳","",false),
                new MusicView.LyricRow("雨滴轻轻落在叶片上","",false),
                new MusicView.LyricRow("把黄昏染成柔粉色","染成温柔的颜色",true),
                new MusicView.LyricRow("轻柔的旋律，陪伴方块世界","",false),
                new MusicView.LyricRow("风穿过打开的窗","",false),
                new MusicView.LyricRow("云图在远方缓缓展开","",false));
        MusicView view=new MusicView(page,CURRENT,rows,upcoming,lyrics,102,true,true,"CloudXmusic",
                search,focused,true,"每日推荐已更新", "极高 320K","原文 + 翻译",true,
                dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST,true,"2.4.7","",0,-1,.8f,"");
        BufferedImage canvas=MusicPainter.paint(view,layout,1d,url->null);
        Graphics2D oc=canvas.createGraphics();
        if(page==Page.NOW){
            // 封面与歌词是独立图层，预览按同一落点叠回去。
            BufferedImage cover=MusicPainter.paintCover(view,1d,1f,url->null);
            Box cr=coverRegion();
            oc.drawImage(cover,cr.x()+PAD,cr.y()+PAD,null);
            BufferedImage lyricLayer=MusicPainter.paintLyrics(view,1d);
            Box lr=lyricRegion();
            oc.drawImage(lyricLayer,lr.x()+PAD,lr.y()+PAD,null);
        }
        // 静态画布不再包含进度与时间，预览要把动态层按同一落点叠回去。
        BufferedImage overlay=MusicPainter.paintDynamic(view,layout,1d);
        Box band=dynamicRegion(page);
        oc.drawImage(overlay,band.x()+PAD,band.y()+PAD,null);
        oc.dispose();
        Graphics2D g=frame.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(canvas,(width-canvas.getWidth())/2,52,null);
        if(page==Page.NOW){
            BufferedImage island=IslandPainter.paint(new IslandPainter.View(CURRENT.title(),CURRENT.artist(),"01:42",395,false),1d);
            g.drawImage(island,(width-island.getWidth())/2,14,null);
        }
        g.dispose();
        ImageIO.write(frame,"png",out.resolve(file).toFile());
    }

    private static void renderNotice(Path out) throws Exception {
        BufferedImage image=new BufferedImage(1200,720,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setColor(new Color(237,232,236));g.fillRect(0,0,image.getWidth(),image.getHeight());
        BufferedImage notice=FirstRunNoticePainter.paint(false,1);
        g.drawImage(notice,(image.getWidth()-notice.getWidth())/2,(image.getHeight()-notice.getHeight())/2,null);
        g.dispose();ImageIO.write(image,"png",out.resolve("cloudxmusic-first-run-notice.png").toFile());
    }
}
