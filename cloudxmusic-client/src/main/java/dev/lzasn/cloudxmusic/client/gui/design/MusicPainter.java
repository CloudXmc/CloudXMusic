package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import dev.lzasn.cloudxmusic.client.omni.MusicSource;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Function;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** 纯画布视图；业务操作由 Screen 适配器处理，可以离线生成同一份真实 UI。 */
public final class MusicPainter {
    private static final String[] TITLES = {"正在播放", "发现音乐", "搜索", "我的收藏", "播放队列",
            "音乐房间", "设置与关于", "账号"};
    private static final String[] ICONS = {"play", "discover", "search", "heart", "list", "group"};
    /** 音乐房间在导航里的序号；BETA 胶囊只贴在这一项和页面大标题后面。 */
    private static final int ROOM_INDEX = MusicView.Page.ROOM.ordinal();
    private static final Dev[] DEVELOPERS = {
            new Dev("Lzasn", "@Lzasn", "代码架构开发 · 主要开发者", "lzasn.png"),
            new Dev("xWtree", "@xWtree", "UI 美化 · 后期维护者", "xwtree.png"),
            new Dev("xiaota888", "@xiaota888", "项目开发者", "xiaota888.png")
    };
    private static final Map<String, BufferedImage> DEV_AVATAR_CACHE = new HashMap<>();
    private record Dev(String name, String handle, String role, String avatar) {}
    /** 整窗磨砂玻璃与圆角高光与数据无关，按画布尺寸缓存一次即可。 */
    private static BufferedImage backdrop;
    private static String backdropKey;

    private MusicPainter() {}

    public static BufferedImage paint(MusicView v, MusicLayout layout, double scale, Function<String, BufferedImage> covers) {
        return paint(v, layout, scale, covers, null);
    }

    /** 可选账号画布仅替换左侧账号块；null 完整沿用原网易云分支。 */
    public static BufferedImage paint(MusicView v, MusicLayout layout, double scale, Function<String, BufferedImage> covers,
                                      java.util.function.Consumer<FrostCanvas> accountPainter) {
        return paint(v,layout,scale,covers,accountPainter,null);
    }
    public static BufferedImage paint(MusicView v, MusicLayout layout, double scale, Function<String, BufferedImage> covers,
                                      java.util.function.Consumer<FrostCanvas> accountPainter,java.util.function.Consumer<FrostCanvas> catalogPainter) {
        BufferedImage image = new BufferedImage((int) Math.ceil((WIDTH + PAD * 2) * scale),
                (int) Math.ceil((HEIGHT + PAD * 2) * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            blitBackdrop(c, image.getWidth(), image.getHeight(), scale);
            c.g.translate(PAD, PAD);
            navigation(c, v, layout, covers, accountPainter);
            // 标题独占标题带（0~92），基线按视觉重心对齐带中轴，不再挂副标题。
            c.text(TITLES[v.page().ordinal()], 292, 54, 21, true, INK, 600);
            if (v.page() == MusicView.Page.ROOM) {
                betaTag(c, 292 + c.width(TITLES[ROOM_INDEX], 21, true) + 6, 47, 21, 10);
            }
            c.g.setColor(LINE);
            c.g.drawLine(272, 92, 1070, 92);
            button(c, v, layout, "close", "close", false);
            if(v.current().source()==MusicSource.QQ&&(v.page()==MusicView.Page.DISCOVER||v.page()==MusicView.Page.FAVORITES)){
                if(catalogPainter!=null)catalogPainter.accept(c);else dev.lzasn.cloudxmusic.client.qqmusic.QQSourcePainter.placeholder(c,v.page());
            } else switch (v.page()) {
                case NOW -> now(c, v, layout, covers);
                case SETTINGS -> {
                    // 设置页内容滚动时会越界，裁剪到标题下方到面板底部之间。
                    java.awt.Shape savedClip = c.g.getClip();
                    c.g.clip(new Rectangle2D.Double(272, 100, 798, 560));
                    settings(c, v, layout);
                    c.g.setClip(savedClip);
                }
                case ACCOUNT -> { /* 内容由 paintAccount 单独绘制 */ }
                case ROOM -> room(c, v, layout);
                default -> list(c, v, layout, covers);
            }
        }
        return image;
    }

    /** 只重绘随播放时间变化的条带，尺寸很小，可以按进度（10Hz）安全刷新。 */
    public static BufferedImage paintDynamic(MusicView v, MusicLayout l, double scale) {
        Box region = dynamicRegion(v.page());
        BufferedImage image = new BufferedImage((int) Math.ceil(region.w() * scale),
                (int) Math.ceil(region.h() * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(-region.x(), -region.y());
            Area bands = new Area();
            for (Box band : dynamicBands(v.page()))
                bands.add(new Area(new Rectangle2D.Double(band.x(), band.y(), band.w(), band.h())));
            c.g.clip(bands);
            if (v.page() == MusicView.Page.NOW) {
                seekBar(c, v, l.box("seek"));
                volumeBar(c, v, l.box("volume"));
            }
        }
        return image;
    }

    private static void blitBackdrop(FrostCanvas c, int w, int h, double scale) {
        BufferedImage image = backdrop(w, h, scale);
        AffineTransform saved = c.g.getTransform();
        Composite composite = c.g.getComposite();
        // Src 直接整幅拷贝，避免与透明底再做一次 SrcOver 混合。
        c.g.setComposite(AlphaComposite.Src);
        c.g.setTransform(new AffineTransform());
        c.g.drawImage(image, 0, 0, null);
        c.g.setComposite(composite);
        c.g.setTransform(saved);
    }

    private static BufferedImage backdrop(int w, int h, double scale) {
        String key = w + "x" + h + "@" + scale + (dark ? "#dark" : "#light");
        if (backdrop != null && key.equals(backdropKey)) return backdrop;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(PAD, PAD);
            c.glass(0, 0, WIDTH, HEIGHT, 38, false);
        }
        backdrop = image;
        backdropKey = key;
        return image;
    }

    private static void navigation(FrostCanvas c, MusicView v, MusicLayout l, Function<String,BufferedImage> covers,
                                   java.util.function.Consumer<FrostCanvas> accountPainter) {
        c.text("CloudXMusic", 36, 46, 19, true, DEEP, 202);
        c.text("云朵音乐盒", 36, 73, 13, false, MUTED, 202);
        c.g.setColor(LINE);
        c.g.drawLine(24, 92, 252, 92);
        c.text("音乐库", 36, 124, 12, false, MUTED, 190);
        // 导航项多于可视高度时靠滚动查看，超出部分裁掉，避免压到下面的分隔线和账号组。
        Shape navClip = c.g.getClip();
        c.g.clip(new Rectangle2D.Double(24, MusicLayout.NAV_TOP - 10, 228,
                MusicLayout.NAV_BOTTOM - MusicLayout.NAV_TOP + 10));
        for (int i = 0; i < MusicLayout.NAV_COUNT; i++) {
            Box b = l.box("nav" + i);
            boolean on = i == v.page().ordinal();
            boolean hover = v.hover().equals("nav" + i);
            if (on || hover) {
                c.shadow(b.x(), b.y(), b.w(), b.h(), 19, 7, 4, new Color(160, 79, 113, on ? 22 : 12));
                c.round(b.x(), b.y(), b.w(), b.h(), 19, on ? SOFT : CARD_BG);
            }
            Color color = on ? DEEP : hover ? INK : MUTED;
            UiIcons.draw(c, ICONS[i], b.x()+20, b.cy(), 18, color);
            c.text(TITLES[i], b.x()+44, b.cy()+5, 14, on, color, 150);
            if (i == ROOM_INDEX) {
                betaTag(c, b.x() + 44 + c.width(TITLES[i], 14, on) + 4, b.cy(), 16, 9);
            }
        }
        c.g.setClip(navClip);
        c.g.setColor(LINE); c.g.drawLine(24,412,252,412);
        c.text("账号", 36, 437, 12, false, MUTED, 190);
        if (accountPainter != null) {
            accountPainter.accept(c);
        } else {
        Box account = l.box("account");
        if (v.hover().equals("account")) c.round(account.x(),account.y(),account.w(),account.h(),20,CARD_BG);
        BufferedImage avatar = v.loggedIn() && !v.avatarUrl().isBlank()
                ? dev.lzasn.cloudxmusic.client.gui.CoverCache.avatar(v.avatarUrl()) : null;
        if (avatar != null) {
            Shape previous = c.g.getClip();
            c.g.clip(shape(34, 461, 36, 36, 18));
            double zoom = 36.0 / Math.min(avatar.getWidth(), avatar.getHeight());
            int aw = (int) Math.ceil(avatar.getWidth() * zoom), ah = (int) Math.ceil(avatar.getHeight() * zoom);
            c.g.drawImage(avatar, 34 + (36 - aw) / 2, 461 + (36 - ah) / 2, aw, ah, null);
            c.g.setClip(previous);
        } else {
            c.round(34, 461, 36, 36, 18, SOFT);
            UiIcons.draw(c,"user",52,479,21,DEEP);
        }
        c.text(v.loggedIn() ? v.nickname() : "访客模式", 82, 474, 14, true, INK, 151);
        c.text(v.loggedIn() ? "管理账号" : "登录网易云音乐",82,495,11,false,MUTED,151);
        }
        c.text("让音乐陪你探索世界",36,552,12,false,MUTED,202);
        // 键位跟随游戏里的按键绑定，不再写死 H。
        c.text(v.openKey() + " 开关界面 · Esc 返回",36,575,11,false,MUTED,202);
        Box b=l.box("settings");
        UiIcons.draw(c,"settings",b.x()+20,b.cy(),19, v.page()==MusicView.Page.SETTINGS ? DEEP : MUTED);
        c.text("设置与关于", b.x()+44,b.cy()+5,13,false,INK,150);
    }

    /** 播放页：封面与歌词各自独立成层（便于动效），这里只画底部控制栏。 */
    private static void now(FrostCanvas c, MusicView v, MusicLayout l, Function<String,BufferedImage> covers) {
        // QQ 的完整底栏由动态层独占，静态层不能再画一份标题和按钮。
        if(v.current().source()!=MusicSource.QQ)playerBar(c, v, l);
    }

    /** 小号 BETA 胶囊：浅粉底 + 粉字，贴在导航项或页面大标题右侧。 */
    private static void betaTag(FrostCanvas c, double x, double centerY, double h, float fontSize) {
        double w = c.width("BETA", fontSize, true) + fontSize;
        c.round(x, centerY - h / 2, w, h, h / 2, SOFT);
        c.text("BETA", (float) (x + fontSize / 2.0), (float) (centerY + fontSize * .36), fontSize, true, PINK, (float) w);
    }

    /** 音乐房间：功能尚未上线，先放一个占位页说明后续计划。 */
    private static void room(FrostCanvas c, MusicView v, MusicLayout l) {
        int cx = MusicLayout.MAIN_X + MusicLayout.MAIN_W / 2;
        // 内容块 308~456，中心 382，对齐标题带下方到面板底部的视觉中轴。
        UiIcons.draw(c, "coffee", cx, 326, 54, PINK);
        c.center("音乐房间", cx, 400, 26, true, INK, 500);
        c.center("和朋友同步听同一首歌的房间功能还在开发中", cx, 434, 12, false, MUTED, 560);
        c.center("2.5.0 先落地客户端与服务端协议，再开放房间", cx, 456, 12, false, MUTED, 560);
    }

    /**
     * 封面层。{@code coverScale} 由播放状态驱动（播放时放大、暂停时回落），
     * 单独成纹理后缩放动画只需重画这一小块。
     */
    public static BufferedImage paintCover(MusicView v, double scale, float coverScale,
                                           Function<String, BufferedImage> covers) {
        Box region = coverRegion();
        BufferedImage image = new BufferedImage((int) Math.ceil(region.w() * scale),
                (int) Math.ceil(region.h() * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(-region.x(), -region.y());
            int size = (int) Math.round(240 * coverScale);
            int cx = region.x() + region.w() / 2, cy = region.y() + region.h() / 2;
            cover(c, v.current(), cx - size / 2, cy - size / 2, size, 8, covers);
        }
        return image;
    }

    /**
     * 歌词层：当前行清晰放大，上下边缘做 alpha 渐隐，形成「从中间淡出」的滚动感。
     */
    public static BufferedImage paintLyrics(MusicView v,double scale){return paintLyrics(v,scale,false);}
    public static BufferedImage paintLyrics(MusicView v,double scale,boolean skipActive) {
        Box region = lyricRegion();
        int w = (int) Math.ceil(region.w() * scale), h = (int) Math.ceil(region.h() * scale);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int center = region.y() + region.h() / 2, gap = 48, activeSlot = 3;
        int x = region.x() + 24, max = region.w() - 32;
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(-region.x(), -region.y());
            if (v.lyrics().isEmpty() || v.lyrics().stream().allMatch(r -> r.text().isBlank())) {
                c.center("让旋律陪伴你的世界", region.x() + region.w() / 2, center - 12, 15, true, DEEP, max);
                c.center("这首歌暂无时间轴歌词", region.x() + region.w() / 2, center + 14, 12, false, MUTED, max);
                return image;
            }
            for (int i = 0; i < v.lyrics().size(); i++) {
                MusicView.LyricRow row = v.lyrics().get(i);
                if (row.text().isBlank()) continue;
                int distance = Math.abs(i - activeSlot);
                int y = center + (i - activeSlot) * gap;
                if (row.active()) {
                    if(skipActive)continue;
                    c.text(row.text(), x, y, 19, true, INK, max);
                    if (!row.translation().isBlank())
                        c.text(row.translation(), x, y + 20, 11, false, mutedA(205), max);
                } else {
                    int fade = distance == 1 ? 190 : distance == 2 ? 140 : 105;
                    c.text(row.text(), x, y, 14, false, inkA(fade), max);
                    if (!row.translation().isBlank())
                        c.text(row.translation(), x, y + 17, 11, false,
                                mutedA(Math.max(70, fade - 60)), max);
                }
            }
        }
        fadeTopAndBottom(image, scale);
        return image;
    }

    /** 用 DstIn 把上下边缘的 alpha 拉成渐变，比逐行调透明度更自然。 */
    private static void fadeTopAndBottom(BufferedImage image, double scale) {
        int fade = Math.max(8, (int) Math.round(62 * scale));
        fade = Math.min(fade, image.getHeight() / 2);
        Graphics2D g = image.createGraphics();
        g.setComposite(AlphaComposite.DstIn);
        g.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 0), 0, fade, new Color(0, 0, 0, 255)));
        g.fillRect(0, 0, image.getWidth(), fade);
        g.setPaint(new GradientPaint(0, image.getHeight() - fade, new Color(0, 0, 0, 255),
                0, image.getHeight(), new Color(0, 0, 0, 0)));
        g.fillRect(0, image.getHeight() - fade, image.getWidth(), fade);
        g.dispose();
    }

    /** 底部控制栏：曲目信息、收藏、队列、播放模式、传输键、音质与音量。 */
    private static void playerBar(FrostCanvas c, MusicView v, MusicLayout l) {
        playerBar(c,v,l,new PlaybackBarView(v.current().title(),v.current().artist(),v.current().durationSeconds(),
                v.position(),v.playing(),v.volume(),v.quality(),v.current().source()==MusicSource.QQ));
    }

    /** 音源无关的只读绘制数据，不回写 Track 或 MusicState。 */
    public record PlaybackBarView(String title,String artist,int duration,double position,boolean playing,float volume,String quality,boolean qq,boolean vip,String coverUrl) {
        public PlaybackBarView(String title,String artist,int duration,double position,boolean playing,float volume,String quality,boolean qq,boolean vip){this(title,artist,duration,position,playing,volume,quality,qq,vip,"");}
        public PlaybackBarView(String title,String artist,int duration,double position,boolean playing,float volume,String quality,boolean qq){this(title,artist,duration,position,playing,volume,quality,qq,false);}
    }
    public static Box playbackBarRegion(){return new Box(292,586,758,76);}
    public static Box playbackInfoRegion(){return new Box(292,596,232,42);}
    public static BufferedImage paintPlaybackBar(MusicView music,PlaybackBarView bar,double scale){return paintPlaybackBar(music,bar,scale,url->null);}
    public static BufferedImage paintPlaybackBar(MusicView music,PlaybackBarView bar,double scale,java.util.function.Function<String,BufferedImage> covers) {
        Box region=playbackBarRegion();BufferedImage image=new BufferedImage((int)Math.ceil(region.w()*scale),(int)Math.ceil(region.h()*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)) {
            c.g.translate(-region.x(),-region.y());MusicLayout layout=new MusicLayout(MusicView.Page.NOW,0,0);
            playerBar(c,music,layout,bar,covers.apply(bar.coverUrl()));seekBar(c,bar,layout.box("seek"));volumeBar(c,bar.volume(),layout.box("volume"));
        }
        return image;
    }
    private static void playerBar(FrostCanvas c,MusicView v,MusicLayout l,PlaybackBarView bar){playerBar(c,v,l,bar,null);}
    private static void playerBar(FrostCanvas c,MusicView v,MusicLayout l,PlaybackBarView bar,BufferedImage artwork) {
        c.g.setColor(LINE);
        c.g.drawLine(292, 586, 1050, 586);

        if(bar.qq()){
            if(artwork==null){c.round(296,599,36,36,8,SOFT);UiIcons.draw(c,"note",314,617,20,PINK);}
            else {
                Shape clip=c.g.getClip();c.g.clip(shape(296,599,36,36,8));
                double zoom=36.0/Math.min(artwork.getWidth(),artwork.getHeight());
                int w=(int)Math.ceil(artwork.getWidth()*zoom),h=(int)Math.ceil(artwork.getHeight()*zoom);
                c.g.drawImage(artwork,296+(36-w)/2,599+(36-h)/2,w,h,null);c.g.setClip(clip);
            }
        }
        int textX=bar.qq()?342:296,textWidth=bar.qq()?178:200;
        dev.lzasn.cloudxmusic.client.qqmusic.QQVipBadge.title(c,bar.title(),textX,613,13,true,INK,textWidth,bar.qq()&&bar.vip());
        c.text(bar.artist(),textX,630,10,false,MUTED,textWidth);

        Box favorite = l.box("favorite");
        if(bar.qq()) { c.g.setColor(DEEP);c.g.draw(new RoundRectangle2D.Double(favorite.cx()-6,favorite.cy()-6,12,12,2,2)); }
        else UiIcons.draw(c, v.favorite() ? "heartFill" : "heart", favorite.cx(), favorite.cy(), 18,
                v.favorite() ? DEEP : PINK);
        Box queued = l.box("queueBtn");
        UiIcons.draw(c, "list", queued.cx(), queued.cy(), 18, MUTED);

        Box mode = l.box("mode");
        UiIcons.draw(c, v.playbackMode().icon, mode.cx(), mode.cy(), 18, bar.qq()?MUTED:DEEP);
        // 没有歌曲时传输键置灰，避免看起来像能点。
        boolean idle = !bar.qq() && bar.duration() <= 0;
        Color primary = idle ? MUTED : INK;
        Box prev = l.box("prev");
        UiIcons.draw(c, "prev", prev.cx(), prev.cy(), 24, primary);
        Box play = l.box("play");
        UiIcons.draw(c, bar.playing() ? "pause" : "play", play.cx(), play.cy(), 30, primary);
        Box next = l.box("next");
        UiIcons.draw(c, "next", next.cx(), next.cy(), 24, primary);

        Box quality = l.box("quality");
        c.round(quality.x(), quality.y(), quality.w(), quality.h(), 10, SOFT);
        c.center(bar.quality(), quality.cx(), quality.cy() + 4, 10, true, DEEP, quality.w() - 8);

        Box volume = l.box("volume");
        UiIcons.draw(c, "volume", volume.x() - 14, volume.cy(), 14, MUTED);
    }

    /** 菜单底边紧贴音质按钮（按钮顶部 605，留 11px 缝），减少一档后要整体下移，否则会浮在半空。 */
    private static final int QUALITY_MENU_TOP = 483;

    public static Box qualityMenuRegion() { return new Box(818, QUALITY_MENU_TOP, 132, 111); }
    public static Box qualityRow(int index) { return new Box(818, QUALITY_MENU_TOP + index * 37, 132, 37); }
    public static Box qualityMenuRegion(int count){return new Box(818,594-count*37,132,count*37);}
    public static Box qualityRow(int index,int count){Box menu=qualityMenuRegion(count);return new Box(menu.x(),menu.y()+index*37,menu.w(),37);}
    public static Box qualityMenuRegion(int count,int width){return new Box(950-width,594-count*37,width,count*37);}
    public static Box qualityRow(int index,int count,int width){Box menu=qualityMenuRegion(count,width);return new Box(menu.x(),menu.y()+index*37,menu.w(),37);}


    /** 音质选择浮层：圆角面板 + 纯列表（文字、分隔线、勾选），不套卡片阴影。 */
    public static BufferedImage paintQualityMenu(int selected, int hovered, double scale) {
        return paintQualityMenu(selected,hovered,scale,new String[]{"标准","较高","极高"});
    }
    public static BufferedImage paintQualityMenu(int selected,int hovered,double scale,String[] names) {
        return paintQualityMenu(selected,hovered,scale,names,132);
    }
    public static BufferedImage paintQualityMenu(int selected,int hovered,double scale,String[] names,int width) {
        Box region = qualityMenuRegion(names.length,width);
        BufferedImage image = new BufferedImage((int) Math.ceil(region.w() * scale),
                (int) Math.ceil(region.h() * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(-region.x(), -region.y());
            // 圆角面板，但不用卡片阴影，保持轻量列表感。
            c.round(region.x(), region.y(), region.w(), region.h(), 14,
                    dark ? new Color(42, 50, 54, 250) : new Color(255, 253, 255, 250));
            for (int i = 0; i < names.length; i++) {
                Box b = qualityRow(i,names.length,width);
                // 分隔线画在每行顶部（第一行除外），先画线再画 hover，避免被高亮盖掉。
                if (i > 0) {
                    c.g.setColor(LINE);
                    c.g.drawLine(b.x() + 10, b.y(), b.x() + b.w() - 10, b.y());
                }
                if (i == hovered) {
                    c.g.setColor(HOVER);
                    c.g.fillRect(b.x(), b.y() + 1, b.w(), b.h() - 1);
                }
                Color ink = i == selected ? DEEP : INK;
                c.text(names[i], b.x() + 14, b.cy() + 4, 12, i == selected, ink, b.w() - 40);
                if (i == selected) {
                    int cx = b.x() + b.w() - 18, cy = b.cy() - 1;
                    c.g.setColor(PINK);
                    c.g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    c.g.drawLine(cx - 5, cy, cx - 1, cy + 4);
                    c.g.drawLine(cx - 1, cy + 4, cx + 6, cy - 5);
                }
            }
        }
        return image;
    }

    /** 进度条：左端已播时间，右端倒计时。没有歌曲时不画。 */
    private static void seekBar(FrostCanvas c, MusicView v, Box b) {
        seekBar(c,new PlaybackBarView("","",v.current().durationSeconds(),v.position(),v.playing(),v.volume(),v.quality(),false),b);
    }
    private static void seekBar(FrostCanvas c,PlaybackBarView bar,Box b) {
        if (bar.duration() <= 0) return;
        float progress=(float)Math.max(0,Math.min(1,bar.position()/bar.duration()));
        c.text(Track.format((int) bar.position()), b.x() - 32, b.cy() + 4, 9, false, MUTED, 40);
        c.round(b.x(), b.cy() - 1.5, b.w(), 3, 1.5, TRACK);
        c.round(b.x(), b.cy() - 1.5, b.w() * progress, 3, 1.5, PINK);
        double knob = b.x() + b.w() * progress;
        c.round(knob - 4, b.cy() - 4, 8, 8, 4, Color.WHITE);
        c.g.setColor(PINK);
        c.g.setStroke(new BasicStroke(1.4f));
        c.g.draw(shape(knob - 4, b.cy() - 4, 8, 8, 4));
        int remain = Math.max(0, bar.duration() - (int) bar.position());
        c.text("-" + Track.format(remain), b.x() + b.w() + 8, b.cy() + 4, 9, false, MUTED, 40);
    }

    private static void volumeBar(FrostCanvas c, MusicView v, Box b) {
        volumeBar(c,v.volume(),b);
    }
    private static void volumeBar(FrostCanvas c,float volume,Box b) {
        c.round(b.x(), b.cy() - 1.5, b.w(), 3, 1.5, TRACK);
        c.round(b.x(), b.cy() - 1.5, b.w() * Math.max(0, Math.min(1, volume)), 3, 1.5, PINK);
    }

    private static void list(FrostCanvas c,MusicView v,MusicLayout l,Function<String,BufferedImage> covers) {
        if(v.page()==MusicView.Page.SEARCH) {
            Box b=l.box("field");c.card(b.x(),b.y(),b.w(),b.h(),24);
            UiIcons.draw(c,"search",b.x()+24,b.cy(),19,PINK);
            c.text(v.search().isEmpty()?"搜索歌名 / 歌手":v.search(),b.x()+46,b.cy()+5,14,false,v.search().isEmpty()?MUTED:INK,b.w()-70);
            if(v.focused()&&v.cursor()) {
                String fitted=c.fit(v.search(),14,false,b.w()-70);
                int x=b.x()+46+(int)c.width(fitted,14,false);
                c.g.setColor(PINK);c.g.drawLine(x,b.cy()-9,x,b.cy()+9);
            }
            labelButton(c,v,l,"search","搜索");
        } else {
            c.text(switch(v.page()){case DISCOVER->"每日推荐";case FAVORITES->"我喜欢的音乐";default->"你的播放队列";},292,143,17,true,INK,620);
            labelButton(c,v,l,"refresh",v.page()==MusicView.Page.QUEUE?"清空队列":"刷新列表");
        }
        c.text("歌曲 / 歌手",360,194,11,false,MUTED,360);
        c.text("专辑",748,194,11,false,MUTED,180);
        c.right("时长",972,194,11,MUTED);
        if(v.tracks().isEmpty()) {
            UiIcons.draw(c,"note",671,318,48,PINK);
            c.center(emptyText(v.page(), v.loggedIn()),671,392,15,true,INK,660);
        }
        for(int i=0;i<v.tracks().size();i++) {
            SongView t=v.tracks().get(i);Box b=l.box("row"+i);
            boolean on=t.source()==MusicSource.QQ?v.current().source()==MusicSource.QQ&&t.platformId().equals(v.current().platformId()):t.equals(v.current());
            // 行与行之间只用一条分隔线，不再给每行套圆角卡片。
            c.g.setColor(LINE);
            c.g.drawLine(292, b.y(), 1050, b.y());
            if(v.hover().equals("row"+i)||v.hover().equals("add"+i))
                c.round(292,b.y(),758,46,10,HOVER);
            cover(c,t,304,b.y()+5,36,11,covers);
            dev.lzasn.cloudxmusic.client.qqmusic.QQVipBadge.title(c,t.title(),360,b.y()+19,14,true,on?DEEP:INK,360,t.source()==MusicSource.QQ&&t.vip());
            c.text(t.artist(),360,b.y()+36,11,false,MUTED,360);
            c.text(t.album(),748,b.y()+29,12,false,MUTED,150);
            c.right(t.source()==MusicSource.QQ&&t.durationSeconds()==0?"—":t.durationText(),972,b.y()+29,12,MUTED);
            Box add=l.box("add"+i);
            UiIcons.draw(c, v.page()==MusicView.Page.QUEUE?"remove":"plus",
                    add.cx(), add.cy(), 16, v.hover().equals("add"+i)?DEEP:MUTED);
        }
        if(!v.tracks().isEmpty()) {
            c.g.setColor(LINE);
            int last = l.box("row"+(v.tracks().size()-1)).y()+46;
            c.g.drawLine(292, last, 1050, last);
        }
    }

    /** 各页空态的文案；状态行不再挤进这里。 */
    private static String emptyText(MusicView.Page page, boolean loggedIn) {
        return switch (page) {
            case SEARCH -> loggedIn ? "搜索歌名，或刷新每日推荐" : "点击左侧账号登录网易云音乐";
            case FAVORITES -> loggedIn ? "还没收藏歌曲，点刷新列表试试" : "点击左侧账号登录网易云音乐";
            case QUEUE -> "队列是空的，去发现喜欢的音乐";
            default -> loggedIn ? "刷新每日推荐，找一首喜欢的歌" : "点击左侧账号登录网易云音乐";
        };
    }

    private static void settings(FrostCanvas c,MusicView v,MusicLayout l) {
        sectionTitle(c, l.box("lyric").y() - 8, "灵动岛");
        setting(c,v,l,"lyric","灵动岛歌词",v.lyricMode());
        setting(c,v,l,"island","顶部灵动岛",v.islandEnabled()?"已开启":"已关闭");
        setting(c,v,l,"islandScale","灵动岛缩放",Math.round(v.islandScale()*100)+"%");
        setting(c,v,l,"islandPos","灵动岛位置","更改位置");

        sectionTitle(c, l.box("darkMode").y() - 12, "界面");
        setting(c,v,l,"darkMode","暗黑模式",v.darkMode()?"已开启":"已关闭");
        setting(c,v,l,"panelScale","主面板缩放",Math.round(v.panelScale()*100)+"%");

        // 关于信息跟随设置项一起滚动；开发者卡片不使用阴影，只靠描边和层级色区分。
        int aboutY = l.box("panelScale").y() + 88;
        sectionTitle(c, aboutY - 34, "关于");
        c.text("CloudXMusic", 318, aboutY, 23, true, DEEP, 620);
        c.round(946, aboutY - 24, 76, 24, 12, SOFT);
        c.center("v" + v.version(), 984, aboutY - 8, 10, true, DEEP, 68);
        c.g.setColor(LINE);
        c.g.drawLine(318, aboutY + 24, 1022, aboutY + 24);
        c.text("开发者", 318, aboutY + 46, 12, false, MUTED, 704);

        int cardX = 318, cardY = aboutY + 60, cardW = 224, cardH = 96, gap = 16;
        for (int i = 0; i < DEVELOPERS.length; i++) {
            developerCard(c, DEVELOPERS[i], cardX + i * (cardW + gap), cardY, cardW, cardH);
        }

        c.g.setColor(LINE);
        c.g.drawLine(318, aboutY + 174, 1022, aboutY + 174);
        c.text("Minecraft 1.21.11 · Fabric · Inter + Noto Sans SC",
                318, aboutY + 200, 11, false, MUTED, 704);
    }

    /** 设置页分组标题，视觉与左侧“音乐库 / 账号”分组一致。 */
    private static void sectionTitle(FrostCanvas c, float baseline, String title) {
        c.text(title, 318, baseline, 12, false, MUTED, 704);
    }

    private static void developerCard(FrostCanvas c, Dev dev, int x, int y, int w, int h) {
        Shape card = leftFlatCard(x, y, w, h, 18);
        c.g.setPaint(CARD_BG);
        c.g.fill(card);
        c.g.setColor(LINE);
        c.g.setStroke(new BasicStroke(1f));
        c.g.draw(leftFlatCard(x + .5, y + .5, w - 1, h - 1, 17.5));
        c.g.setColor(PINK);
        c.g.fillRect(x, y, 4, h);

        int avatarSize = 52, ax = x + 16, ay = y + 22;
        c.round(ax, ay, avatarSize, avatarSize, avatarSize / 2, SOFT);
        BufferedImage avatar = devAvatar(dev.avatar());
        if (avatar != null) {
            Shape previous = c.g.getClip();
            c.g.clip(shape(ax, ay, avatarSize, avatarSize, avatarSize / 2));
            double zoom = avatarSize / (double) Math.min(avatar.getWidth(), avatar.getHeight());
            int aw = (int) Math.ceil(avatar.getWidth() * zoom);
            int ah = (int) Math.ceil(avatar.getHeight() * zoom);
            c.g.drawImage(avatar, ax + (avatarSize - aw) / 2, ay + (avatarSize - ah) / 2, aw, ah, null);
            c.g.setClip(previous);
        } else {
            UiIcons.draw(c, "user", ax + avatarSize / 2, ay + avatarSize / 2, 24, DEEP);
        }

        c.text(dev.name(), x + 80, y + 30, 13, true, INK, w - 90);
        c.text(dev.handle(), x + 80, y + 49, 10, false, MUTED, w - 90);
        c.text(dev.role(), x + 80, y + 73, 10, false, DEEP, w - 90);
    }

    /** 左侧直角、右侧圆角的卡片轮廓；左侧留给无圆角的强调色条。 */
    private static Shape leftFlatCard(double x, double y, double w, double h, double radius) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y);
        p.lineTo(x + w - radius, y);
        p.quadTo(x + w, y, x + w, y + radius);
        p.lineTo(x + w, y + h - radius);
        p.quadTo(x + w, y + h, x + w - radius, y + h);
        p.lineTo(x, y + h);
        p.closePath();
        return p;
    }

    private static BufferedImage devAvatar(String file) {
        return DEV_AVATAR_CACHE.computeIfAbsent(file, key -> {
            String path = "/assets/cloudxmusic/textures/gui/devs/" + key;
            try (InputStream in = MusicPainter.class.getResourceAsStream(path)) {
                return in == null ? null : ImageIO.read(in);
            } catch (IOException ignored) {
                return null;
            }
        });
    }

    private static void setting(FrostCanvas c,MusicView v,MusicLayout l,String key,String title,String value) {
        Box b=l.box(key);
        c.text(title,b.x(),b.y()+20,13,true,INK,205);
        c.right(value,b.x()+b.w(),b.y()+20,13,v.hover().equals(key)?DEEP:MUTED);
        c.g.setColor(LINE);
        c.g.drawLine(b.x(),b.y()+35,b.x()+b.w(),b.y()+35);
    }

    private static final String[] ACC_MENU = {"用户名 + 密码登录", "验证码登录", "扫码登录（不可用）", "Cookie 登录"};
    private static final int ACC_X = 441, ACC_W = 460, ACC_H = 52, ACC_GAP = 16, ACC_TOP = 168;
    private static final String[] ACC_HINTS = {"用户名 / 邮箱", "密码", "手机号", "验证码", "粘贴浏览器 Cookie"};

    /** 账号页的绘制输入。 */
    public record AccountView(boolean loggedIn, String nickname, String uid, String avatarUrl, int mode,
                              String[] values, int focus, boolean caret, String status, int countdown) {}

    public static Box accMenuBox(int i) { return new Box(ACC_X, ACC_TOP + i * (ACC_H + ACC_GAP), ACC_W, ACC_H); }
    public static Box accBackBox() { return new Box(292, 104, 130, 28); }
    public static Box accFieldBox(int i) { return new Box(ACC_X, 210 + i * 64, ACC_W, 52); }
    public static Box accPhoneCodeBox() { return new Box(ACC_X, 274, 300, 52); }
    public static Box accSendBox() { return new Box(751, 274, 150, 52); }
    public static Box accSubmitBox() { return new Box(ACC_X, 350, ACC_W, 52); }
    public static Box accCookieHelpBox() { return new Box(ACC_X, 286, 300, 26); }
    public static Box accLogoutBox() { return new Box(ACC_X, 292, 180, 52); }
    public static Box accRefreshBox() { return new Box(ACC_X + 200, 292, 180, 52); }

    /** 账号页整块内容（菜单或表单），单独成纹理，打字只重画这一块。 */
    public static BufferedImage paintAccount(AccountView v, double scale) {
        Box region = accountRegion();
        BufferedImage image = new BufferedImage((int) Math.ceil(region.w() * scale),
                (int) Math.ceil(region.h() * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(-region.x(), -region.y());
            if (v.loggedIn()) {
                BufferedImage avatar = v.avatarUrl() != null && !v.avatarUrl().isBlank()
                        ? dev.lzasn.cloudxmusic.client.gui.CoverCache.avatar(v.avatarUrl()) : null;
                c.round(ACC_X, 176, 80, 80, 40, SOFT);
                if (avatar != null) {
                    Shape previous = c.g.getClip();
                    c.g.clip(shape(ACC_X, 176, 80, 80, 40));
                    double zoom = 80.0 / Math.min(avatar.getWidth(), avatar.getHeight());
                    int aw = (int) Math.ceil(avatar.getWidth() * zoom), ah = (int) Math.ceil(avatar.getHeight() * zoom);
                    c.g.drawImage(avatar, ACC_X + (80 - aw) / 2, 176 + (80 - ah) / 2, aw, ah, null);
                    c.g.setClip(previous);
                } else {
                    UiIcons.draw(c, "user", ACC_X + 40, 216, 36, DEEP);
                }
                c.text(v.nickname() == null || v.nickname().isBlank() ? "已登录" : v.nickname(),
                        ACC_X + 100, 208, 20, true, INK, 400);
                c.text("收藏、歌单与播放记录都会同步到该账号", ACC_X + 100, 232, 11, false, MUTED, 400);
                pill(c, accLogoutBox(), "退出登录", SOFT, DEEP);
                pill(c, accRefreshBox(), "刷新账号", CARD_BG, INK);
                return image;
            }
            if (v.mode() == 0) {
                for (int i = 0; i < 4; i++) {
                    Box b = accMenuBox(i);
                    boolean disabled = i == 2;
                    Color cardFill = disabled
                            ? (dark ? new Color(48, 58, 63, 150) : new Color(255, 255, 255, 110))
                            : (dark ? CARD_BG : new Color(255, 255, 255, 190));
                    Color cardBorder = disabled ? LINE
                            : (dark ? new Color(226, 130, 162, 90) : new Color(223, 115, 153, 120));
                    Color cardText = disabled ? MUTED : INK;
                    c.shadow(b.x(), b.y(), b.w(), b.h(), 28, 8, 0, 4,
                            dark ? new Color(0, 0, 0, disabled ? 8 : 30)
                                 : new Color(150, 65, 99, disabled ? 8 : 20));
                    c.round(b.x(), b.y(), b.w(), b.h(), 28, cardFill);
                    c.g.setColor(cardBorder);
                    c.g.setStroke(new BasicStroke(1.4f));
                    c.g.draw(shape(b.x() + .5, b.y() + .5, b.w() - 1, b.h() - 1, 28));
                    c.center(ACC_MENU[i], b.cx(), b.cy() + 6, 16, !disabled,
                            cardText, b.w() - 40);
                }
                return image;
            }
            form(c, v);
        }
        return image;
    }

    /** 某一登录方式的表单：返回链接在标题下方，下面是输入框与登录按钮。 */
    private static void form(FrostCanvas c, AccountView v) {
        String title = v.mode() == 1 ? "用户名 + 密码登录" : v.mode() == 2 ? "验证码登录" : "Cookie 登录";
        c.text("< 返回", 292, 124, 13, false, MUTED, 130);
        c.text(title, ACC_X, 176, 18, true, INK, ACC_W);
        Color fieldFill = dark ? new Color(31, 39, 43, 210) : new Color(255, 255, 255, 175);
        Color fieldBorder = dark ? new Color(96, 111, 117, 120) : LINE;
        Color hintInk = dark ? new Color(146, 158, 163) : new Color(0x9C, 0x8C, 0x96);
        boolean phone = v.mode() == 2;
        int count = v.mode() == 3 ? 1 : 2;
        for (int i = 0; i < count; i++) {
            int index = v.mode() == 1 ? i : v.mode() == 2 ? 2 + i : 4;
            Box b = phone && i == 1 ? accPhoneCodeBox() : accFieldBox(i);
            String value = v.values()[index] == null ? "" : v.values()[index];
            boolean masked = index == 1;
            String shown = masked && !value.isEmpty()
                    ? "●".repeat(Math.min(20, value.codePointCount(0, value.length()))) : value;
            c.round(b.x(), b.y(), b.w(), b.h(), 22, fieldFill);
            c.g.setColor(fieldBorder);
            c.g.setStroke(new BasicStroke(1f));
            c.g.draw(shape(b.x() + .5, b.y() + .5, b.w() - 1, b.h() - 1, 22));
            c.text(shown.isEmpty() ? ACC_HINTS[index] : shown, b.x() + 20, b.cy() + 5, 13, false,
                    shown.isEmpty() ? hintInk : INK, b.w() - 40);
            if (v.focus() == index) {
                c.g.setColor(PINK);
                c.g.setStroke(new BasicStroke(1.6f));
                c.g.draw(shape(b.x() + 1, b.y() + 1, b.w() - 2, b.h() - 2, 22));
                if (v.caret()) {
                    float cx = b.x() + 20 + c.width(shown, 13, false);
                    c.g.setColor(DEEP);
                    c.g.setStroke(new BasicStroke(1.6f));
                    c.g.drawLine((int) cx, b.cy() - 10, (int) cx, b.cy() + 10);
                }
            }
        }
        if (phone) {
            Box s = accSendBox();
            boolean coolingDown = v.countdown() > 0;
            Color sendFill = coolingDown
                    ? (dark ? new Color(46, 55, 60, 210) : new Color(255, 255, 255, 105))
                    : SOFT;
            Color sendInk = coolingDown
                    ? (dark ? new Color(132, 145, 150) : MUTED)
                    : DEEP;
            pill(c, s, coolingDown ? "重发 " + v.countdown() + "s" : "发送验证码", sendFill, sendInk);
        }
        if (v.mode() == 3) {
            Box help = accCookieHelpBox();
            String label = "如何获取网易云 Cookie？点这里查看教程";
            c.text(label, help.x(), help.y() + 17, 12, false, PINK, help.w());
            float width = c.width(label, 12, false);
            c.g.setColor(HOVER_STRONG);
            c.g.setStroke(new BasicStroke(1f));
            c.g.drawLine(help.x(), help.y() + 21, Math.round(help.x() + width), help.y() + 21);
        }
        pill(c, accSubmitBox(), "登录", SOFT, DEEP);
        if (v.status() != null && !v.status().isBlank())
            c.text(v.status(), ACC_X, 432, 11, false, MUTED, ACC_W);
    }

    private static void pillBox(FrostCanvas c, Box b, Color fill) {
        c.shadow(b.x(), b.y(), b.w(), b.h(), b.h() / 2, 6, 0, 3, new Color(150, 65, 99, 18));
        c.round(b.x(), b.y(), b.w(), b.h(), b.h() / 2, fill);
    }

    private static void pill(FrostCanvas c, Box b, String label, Color fill, Color ink) {
        pillBox(c, b, fill);
        c.center(label, b.cx(), b.cy() + 5, 12, true, ink, b.w() - 16);
    }

    private static void button(FrostCanvas c,MusicView v,MusicLayout l,String key,String icon,boolean accent) {
        Box b=l.box(key);boolean hover=v.hover().equals(key);
        c.shadow(b.x(),b.y(),b.w(),b.h(),b.h()/2,accent?10:6,accent?5:3,new Color(150,65,99,accent?38:18));
        c.round(b.x(),b.y(),b.w(),b.h(),b.h()/2,accent?new GradientPaint(b.x(),b.y(),new Color(245,162,189),b.x()+b.w(),b.y()+b.h(),hover?DEEP:PINK):CARD_BG);
        UiIcons.draw(c,icon,b.cx(),b.cy(),accent?26:19,accent?Color.WHITE:hover?DEEP:INK);
    }

    private static void labelButton(FrostCanvas c,MusicView v,MusicLayout l,String key,String label) {
        Box b=l.box(key);c.shadow(b.x(),b.y(),b.w(),b.h(),b.h()/2,8,3,new Color(160,70,109,20));
        c.round(b.x(),b.y(),b.w(),b.h(),b.h()/2,v.hover().equals(key)?HOVER:SOFT);
        c.center(label,b.cx(),b.cy()+5,13,true,DEEP,b.w()-16);
    }

    public static void cover(FrostCanvas c,SongView track,int x,int y,int size,int radius,Function<String,BufferedImage> covers) {
        if(size>100) c.shadow(x,y,size,size,radius,14,7,new Color(89,45,65,28));
        Shape previous=c.g.getClip();c.g.clip(shape(x,y,size,size,radius));
        BufferedImage image=covers.apply(track.coverUrl());
        if(image!=null) {
            double zoom=(double)size/Math.min(image.getWidth(),image.getHeight());
            int w=(int)Math.ceil(image.getWidth()*zoom),h=(int)Math.ceil(image.getHeight()*zoom);
            c.g.drawImage(image,x+(size-w)/2,y+(size-h)/2,w,h,null);
        } else {
            c.round(x,y,size,size,radius,new GradientPaint(x,y,new Color(178,216,207),x+size,y+size,new Color(226,137,168)));
            c.round(x+size*.17,y+size*.17,size*.66,size*.66,size*.33,new Color(255,236,244,100));
            c.round(x+size*.27,y+size*.27,size*.46,size*.46,size*.23,new Color(229,147,174,120));
            c.round(x+size*.42,y+size*.42,size*.16,size*.16,size*.08,new Color(255,248,244,220));
        }
        c.g.setClip(previous);
    }

    /** 带指定 alpha 的当前 INK 色（暗黑模式下 INK 是浅色，歌词渐隐才能看清）。 */
    private static Color inkA(int alpha) {
        return new Color(INK.getRed(), INK.getGreen(), INK.getBlue(), alpha);
    }

    private static Color mutedA(int alpha) {
        return new Color(MUTED.getRed(), MUTED.getGreen(), MUTED.getBlue(), alpha);
    }
}
