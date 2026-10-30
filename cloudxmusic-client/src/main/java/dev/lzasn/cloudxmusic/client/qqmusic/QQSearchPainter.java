package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import dev.lzasn.cloudxmusic.client.gui.design.MusicView;
import dev.lzasn.cloudxmusic.client.gui.design.UiIcons;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSearchController.Source;

/** 搜索页的附加绘制与命中几何；不持有游戏状态，也不把 QQ MID 转成 Track。 */
public final class QQSearchPainter {
    private static final Box CONTENT = new Box(272, 98, 798, 562);
    private static final Box FIELD = new Box(292, 116, 634, 36);
    private static final Box SEARCH = new Box(950, 116, 100, 36);
    private static BufferedImage background;
    private static String backgroundKey;
    private QQSearchPainter() {}

    public static Source tabAt(double x, double y) {
        return null; // 顶部 Tab 已移除；搜索会话仅跟随全局选择。
    }
    public static boolean resultArea(double x, double y) {
        return x >= MAIN_X && x < 1070 && y >= ROW_TOP && y < 620;
    }
    public static boolean blocksQqClick(double x, double y) {
        // 输入框和搜索按钮继续复用 MusicScreen 输入事件，其他内容区不透传到网易云行。
        return CONTENT.contains(x, y) && !FIELD.contains(x, y) && !SEARCH.contains(x, y);
    }

    public static BufferedImage decorate(BufferedImage base, double scale, MusicView music,
                                         QQSearchController.View search) {
        return decorate(base, scale, music, search, null);
    }
    public static BufferedImage decorate(BufferedImage base, double scale, MusicView music,
                                         QQSearchController.View search, QQPlaybackProbeController.View probe) {
        return decorateInternal(base,scale,music,search,probe,null);
    }
    public static BufferedImage decoratePlayback(BufferedImage base,double scale,MusicView music,QQSearchController.View search,QQPlaybackController.View playback){
        return decorateInternal(base,scale,music,search,null,playback);
    }
    public static BufferedImage decoratePlayback(BufferedImage base,double scale,MusicView music,QQSearchController.View search,QQPlaybackController.View playback,
            java.util.function.Function<UnifiedSong,dev.lzasn.cloudxmusic.client.omni.SongView> metadata,
            java.util.function.Function<String,BufferedImage> covers){
        return decorateInternal(base,scale,music,search,null,playback,metadata,covers);
    }
    private static BufferedImage decorateInternal(BufferedImage base,double scale,MusicView music,QQSearchController.View search,QQPlaybackProbeController.View probe,QQPlaybackController.View playback){
        return decorateInternal(base,scale,music,search,probe,playback,song->dev.lzasn.cloudxmusic.client.omni.SongView.from(song,0,""),url->null);
    }
    private static BufferedImage decorateInternal(BufferedImage base,double scale,MusicView music,QQSearchController.View search,QQPlaybackProbeController.View probe,QQPlaybackController.View playback,java.util.function.Function<UnifiedSong,dev.lzasn.cloudxmusic.client.omni.SongView> metadata,java.util.function.Function<String,BufferedImage> covers){
        if (music.page() != MusicView.Page.SEARCH) return base;
        BufferedImage image = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            AffineTransform transform = c.g.getTransform();
            c.g.setTransform(new AffineTransform());
            c.g.drawImage(base, 0, 0, null);
            c.g.setTransform(transform);
            c.g.translate(PAD, PAD);
            if (search.source() == Source.QQ) {
                Shape clip = c.g.getClip();
                c.g.clip(new Rectangle2D.Double(CONTENT.x(), CONTENT.y(), CONTENT.w(), CONTENT.h()));
                // 恢复同一玻璃背景，清除网易云列表和登录空态，不影响侧栏账号。
                c.g.setComposite(AlphaComposite.Src);
                AffineTransform bodyTransform = c.g.getTransform();
                c.g.setTransform(new AffineTransform());
                c.g.drawImage(background(base.getWidth(), base.getHeight(), scale), 0, 0, null);
                c.g.setTransform(bodyTransform);
                c.g.setComposite(AlphaComposite.SrcOver);
                qqContent(c, music, search,playback,metadata,covers);
                c.g.setClip(clip);
            }
            boolean testing = search.source() == Source.QQ && probe != null && !probe.status().isEmpty();
            boolean playing=search.source()==Source.QQ&&playback!=null&&!playback.status().isEmpty();
            boolean playbackError=playing&&playback.error();
            String status=search.loading()||search.error()?search.status():playbackError?playback.status():testing?probe.status():search.status();
            c.text(status, 292, 174, 11, false,
                    (playbackError||search.error()||(testing&&probe.error())) ? errorColor() : MUTED, 758);
        }
        return image;
    }

    public static Color errorColor() { return dark ? new Color(0xFF8698) : new Color(0xB42338); }

    /** 只缓存一张背景；阴影与半透明玻璃的合成顺序必须与原绘制器一致。 */
    private static BufferedImage background(int width, int height, double scale) {
        String key = width + "x" + height + "@" + scale + "#" + dark;
        if (!key.equals(backgroundKey)) {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            try (FrostCanvas c = new FrostCanvas(image, scale)) {
                c.g.translate(PAD, PAD);
                c.glass(0, 0, WIDTH, HEIGHT, 38, false);
            }
            background = image;
            backgroundKey = key;
        }
        return background;
    }
    static void clear() { background = null; backgroundKey = null; }
    private static void qqContent(FrostCanvas c, MusicView music, QQSearchController.View search,QQPlaybackController.View playback,java.util.function.Function<UnifiedSong,dev.lzasn.cloudxmusic.client.omni.SongView> metadata,java.util.function.Function<String,BufferedImage> covers) {
        c.card(FIELD.x(), FIELD.y(), FIELD.w(), FIELD.h(), 24);
        UiIcons.draw(c, "search", FIELD.x() + 24, FIELD.cy(), 19, PINK);
        c.text(music.search().isEmpty() ? "搜索 QQ 音乐歌名 / 歌手" : music.search(),
                FIELD.x() + 46, FIELD.cy() + 5, 14, false,
                music.search().isEmpty() ? MUTED : INK, FIELD.w() - 70);
        if (music.focused() && music.cursor()) {
            int x = FIELD.x() + 46 + (int)c.width(c.fit(music.search(), 14, false, FIELD.w() - 70), 14, false);
            c.g.setColor(PINK);
            c.g.drawLine(x, FIELD.cy() - 9, x, FIELD.cy() + 9);
        }
        c.round(SEARCH.x(), SEARCH.y(), SEARCH.w(), SEARCH.h(), 18, search.loading() ? TRACK : SOFT);
        c.center(search.loading() ? "搜索中…" : "搜索", SEARCH.cx(), SEARCH.cy() + 5, 13, true, DEEP, 90);
        c.text("歌曲 / 歌手", 354, 194, 11, false, MUTED, 400);
        c.text("专辑", 748, 194, 11, false, MUTED, 280);
        int visible=7;
        int end = Math.min(search.songs().size(), search.scroll() + visible);
        for (int index = search.scroll(); index < end; index++) {
            UnifiedSong song = search.songs().get(index);
            int y = ROW_TOP + (index - search.scroll()) * ROW_H;
            if(playback!=null&&playback.song()!=null&&playback.song().platformId().equals(song.platformId()))c.round(292,y,758,46,10,SOFT);
            c.g.setColor(LINE);
            c.g.drawLine(292, y, 1050, y);
            var display=metadata.apply(song);
            var cover=covers.apply(display.coverUrl());
            if(cover==null){c.round(304,y+5,36,36,8,SOFT);UiIcons.draw(c,"note",322,y+23,20,PINK);}
            else {
                Shape clip=c.g.getClip();c.g.clip(shape(304,y+5,36,36,8));
                double zoom=36.0/Math.min(cover.getWidth(),cover.getHeight());
                int w=(int)Math.ceil(cover.getWidth()*zoom),h=(int)Math.ceil(cover.getHeight()*zoom);
                c.g.drawImage(cover,304+(36-w)/2,y+5+(36-h)/2,w,h,null);c.g.setClip(clip);
            }
            QQVipBadge.title(c,song.title(),354,y+19,14,true,INK,360,display.vip());
            c.text(song.artist(),354,y+36,11,false,MUTED,360);
            c.text(song.album(), 748, y + 29, 12, false, MUTED, 290);
        }
        if (search.songs().isEmpty()) {
            UiIcons.draw(c, "note", 671, 318, 48, PINK);
            c.center(search.loading() ? "正在搜索 QQ 音乐…" : search.error() ? "搜索失败，请查看上方提示"
                    : "输入歌名或歌手即可匿名搜索", 671, 392, 15, true, INK, 660);
        } else if (search.songs().size() > visible) {
            double viewport=visible*ROW_H;
            double height = Math.max(24, viewport * visible / search.songs().size());
            double top = ROW_TOP + (viewport - height) * search.scroll() / (search.songs().size() - visible);
            c.round(1058, ROW_TOP, 4, viewport, 2, TRACK);
            c.round(1058, top, 4, height, 2, PINK);
        }
    }
}
