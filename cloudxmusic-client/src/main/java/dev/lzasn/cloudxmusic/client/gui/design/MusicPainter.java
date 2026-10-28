package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** 纯画布视图；业务操作由 Screen 适配器处理，可以离线生成同一份真实 UI。 */
public final class MusicPainter {
    private static final String[] TITLES = {"正在播放", "发现音乐", "搜索", "我的收藏", "播放队列", "设置与关于", "账号"};
    private static final String[] ICONS = {"play", "discover", "search", "heart", "list", "settings"};
    /** 整窗磨砂玻璃与圆角高光与数据无关，按画布尺寸缓存一次即可。 */
    private static BufferedImage backdrop;
    private static String backdropKey;

    private MusicPainter() {}

    public static BufferedImage paint(MusicView v, MusicLayout layout, double scale, Function<String, BufferedImage> covers) {
        BufferedImage image = new BufferedImage((int) Math.ceil((WIDTH + PAD * 2) * scale),
                (int) Math.ceil((HEIGHT + PAD * 2) * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            blitBackdrop(c, image.getWidth(), image.getHeight(), scale);
            c.g.translate(PAD, PAD);
            navigation(c, v, layout, covers);
            c.text(TITLES[v.page().ordinal()], 292, 46, 21, true, INK, 600);
            c.text("为你的世界配一段旋律", 292, 69, 12, false, MUTED, 600);
            c.g.setColor(new Color(135, 90, 115, 30));
            c.g.drawLine(272, 92, 1070, 92);
            button(c, v, layout, "close", "close", false);
            switch (v.page()) {
                case NOW -> now(c, v, layout, covers);
                case SETTINGS -> settings(c, v, layout);
                case ACCOUNT -> { /* 内容由 paintAccount 单独绘制 */ }
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
        String key = w + "x" + h + "@" + scale;
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

    private static void navigation(FrostCanvas c, MusicView v, MusicLayout l, Function<String,BufferedImage> covers) {
        c.text("CloudXMusic", 36, 46, 19, true, DEEP, 202);
        c.text("云朵音乐盒", 36, 73, 13, false, MUTED, 202);
        c.g.setColor(new Color(135, 90, 115, 30));
        c.g.drawLine(24, 92, 252, 92);
        c.text("音乐库", 36, 124, 12, false, MUTED, 190);
        for (int i = 0; i < 5; i++) {
            Box b = l.box("nav" + i);
            boolean on = i == v.page().ordinal();
            boolean hover = v.hover().equals("nav" + i);
            if (on || hover) {
                c.shadow(b.x(), b.y(), b.w(), b.h(), 19, 7, 4, new Color(160, 79, 113, on ? 22 : 12));
                c.round(b.x(), b.y(), b.w(), b.h(), 19, on ? new Color(248,218,231,235) : new Color(255,255,255,145));
            }
            Color color = on ? DEEP : hover ? INK : new Color(0x73606F);
            UiIcons.draw(c, ICONS[i], b.x()+20, b.cy(), 18, color);
            c.text(TITLES[i], b.x()+44, b.cy()+5, 14, on, color, 150);
        }
        c.g.setColor(new Color(135,90,115,30)); c.g.drawLine(24,370,252,370);
        c.text("账号", 36, 395, 12, false, MUTED, 190);
        Box account = l.box("account");
        if (v.hover().equals("account")) c.round(account.x(),account.y(),account.w(),account.h(),20,new Color(255,255,255,110));
        BufferedImage avatar = v.loggedIn() && !v.avatarUrl().isBlank()
                ? dev.lzasn.cloudxmusic.client.gui.CoverCache.avatar(v.avatarUrl()) : null;
        if (avatar != null) {
            Shape previous = c.g.getClip();
            c.g.clip(shape(34, 419, 36, 36, 18));
            double zoom = 36.0 / Math.min(avatar.getWidth(), avatar.getHeight());
            int aw = (int) Math.ceil(avatar.getWidth() * zoom), ah = (int) Math.ceil(avatar.getHeight() * zoom);
            c.g.drawImage(avatar, 34 + (36 - aw) / 2, 419 + (36 - ah) / 2, aw, ah, null);
            c.g.setClip(previous);
        } else {
            c.round(34, 419, 36, 36, 18, new Color(245,208,223));
            UiIcons.draw(c,"user",52,437,21,DEEP);
        }
        c.text(v.loggedIn() ? v.nickname() : "访客模式", 82, 432, 14, true, INK, 151);
        c.text(v.loggedIn() ? "管理账号" : "登录网易云音乐",82,453,11,false,MUTED,151);
        c.text("让音乐陪你探索世界",36,552,12,false,MUTED,202);
        c.text("H 开关界面 · Esc 返回",36,575,11,false,MUTED,202);
        Box b=l.box("settings");
        UiIcons.draw(c,"settings",b.x()+20,b.cy(),19, v.page()==MusicView.Page.SETTINGS ? DEEP : MUTED);
        c.text("设置与关于", b.x()+44,b.cy()+5,13,false,INK,150);
    }

    /** 播放页：封面与歌词各自独立成层（便于动效），这里只画底部控制栏。 */
    private static void now(FrostCanvas c, MusicView v, MusicLayout l, Function<String,BufferedImage> covers) {
        playerBar(c, v, l);
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
    public static BufferedImage paintLyrics(MusicView v, double scale) {
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
                    c.text(row.text(), x, y, 19, true, INK, max);
                    if (!row.translation().isBlank())
                        c.text(row.translation(), x, y + 20, 11, false, new Color(0x8A, 0x78, 0x94, 205), max);
                } else {
                    int fade = distance == 1 ? 190 : distance == 2 ? 140 : 105;
                    c.text(row.text(), x, y, 14, false, new Color(0x40, 0x32, 0x3C, fade), max);
                    if (!row.translation().isBlank())
                        c.text(row.translation(), x, y + 17, 11, false,
                                new Color(0x8A, 0x78, 0x84, Math.max(70, fade - 60)), max);
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
        c.g.setColor(new Color(135, 90, 115, 28));
        c.g.drawLine(292, 586, 1050, 586);

        c.text(v.current().title(), 296, 613, 13, true, INK, 200);
        c.text(v.current().artist(), 296, 630, 10, false, MUTED, 200);

        Box favorite = l.box("favorite");
        UiIcons.draw(c, v.favorite() ? "heartFill" : "heart", favorite.cx(), favorite.cy(), 18,
                v.favorite() ? DEEP : PINK);
        Box queued = l.box("queueBtn");
        UiIcons.draw(c, "list", queued.cx(), queued.cy(), 18, MUTED);

        Box mode = l.box("mode");
        UiIcons.draw(c, v.playbackMode().icon, mode.cx(), mode.cy(), 18, DEEP);
        // 没有歌曲时传输键置灰，避免看起来像能点。
        boolean idle = v.current().durationSeconds() <= 0;
        Color primary = idle ? MUTED : INK;
        Box prev = l.box("prev");
        UiIcons.draw(c, "prev", prev.cx(), prev.cy(), 24, primary);
        Box play = l.box("play");
        UiIcons.draw(c, v.playing() ? "pause" : "play", play.cx(), play.cy(), 30, primary);
        Box next = l.box("next");
        UiIcons.draw(c, "next", next.cx(), next.cy(), 24, primary);

        Box quality = l.box("quality");
        c.round(quality.x(), quality.y(), quality.w(), quality.h(), 10, new Color(248, 218, 231, 200));
        c.center(v.quality(), quality.cx(), quality.cy() + 4, 10, true, DEEP, quality.w() - 8);

        Box volume = l.box("volume");
        UiIcons.draw(c, "volume", volume.x() - 14, volume.cy(), 14, MUTED);
    }

    public static Box qualityMenuRegion() { return new Box(818, 446, 132, 148); }
    public static Box qualityRow(int index) { return new Box(818, 446 + index * 37, 132, 37); }

    /** 音质选择浮层：圆角面板 + 纯列表（文字、分隔线、勾选），不套卡片阴影。 */
    public static BufferedImage paintQualityMenu(int selected, int hovered, boolean losslessAvailable, double scale) {
        Box region = qualityMenuRegion();
        BufferedImage image = new BufferedImage((int) Math.ceil(region.w() * scale),
                (int) Math.ceil(region.h() * scale), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            c.g.translate(-region.x(), -region.y());
            // 圆角面板，但不用卡片阴影，保持轻量列表感。
            c.round(region.x(), region.y(), region.w(), region.h(), 14, new Color(255, 253, 255, 250));
            String[] names = {"标准", "较高", "极高", "无损"};
            for (int i = 0; i < names.length; i++) {
                Box b = qualityRow(i);
                boolean disabled = i == 3 && !losslessAvailable;
                // 分隔线画在每行顶部（第一行除外），先画线再画 hover，避免被高亮盖掉。
                if (i > 0) {
                    c.g.setColor(new Color(135, 90, 115, 22));
                    c.g.drawLine(b.x() + 10, b.y(), b.x() + b.w() - 10, b.y());
                }
                if (i == hovered && !disabled) {
                    c.g.setColor(new Color(223, 115, 153, 20));
                    c.g.fillRect(b.x(), b.y() + 1, b.w(), b.h() - 1);
                }
                Color ink = disabled ? new Color(0xB6, 0xA8, 0xB0) : i == selected ? DEEP : INK;
                c.text(names[i], b.x() + 14, b.cy() + 4, 12, i == selected && !disabled, ink, b.w() - 40);
                if (i == selected && !disabled) {
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
        if (v.current().durationSeconds() <= 0) return;
        c.text(Track.format((int) v.position()), b.x() - 32, b.cy() + 4, 9, false, MUTED, 40);
        c.round(b.x(), b.cy() - 1.5, b.w(), 3, 1.5, new Color(220, 200, 211, 150));
        c.round(b.x(), b.cy() - 1.5, b.w() * v.progress(), 3, 1.5, PINK);
        double knob = b.x() + b.w() * v.progress();
        c.round(knob - 4, b.cy() - 4, 8, 8, 4, Color.WHITE);
        c.g.setColor(PINK);
        c.g.setStroke(new BasicStroke(1.4f));
        c.g.draw(shape(knob - 4, b.cy() - 4, 8, 8, 4));
        int remain = Math.max(0, v.current().durationSeconds() - (int) v.position());
        c.text("-" + Track.format(remain), b.x() + b.w() + 8, b.cy() + 4, 9, false, MUTED, 40);
    }

    private static void volumeBar(FrostCanvas c, MusicView v, Box b) {
        c.round(b.x(), b.cy() - 1.5, b.w(), 3, 1.5, new Color(220, 200, 211, 150));
        c.round(b.x(), b.cy() - 1.5, b.w() * Math.max(0, Math.min(1, v.volume())), 3, 1.5, PINK);
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
            Track t=v.tracks().get(i);Box b=l.box("row"+i);
            boolean on=t.equals(v.current());
            // 行与行之间只用一条分隔线，不再给每行套圆角卡片。
            c.g.setColor(new Color(135,90,115,26));
            c.g.drawLine(292, b.y(), 1050, b.y());
            if(v.hover().equals("row"+i)||v.hover().equals("add"+i))
                c.round(292,b.y(),758,46,10,new Color(247,215,229,90));
            cover(c,t,304,b.y()+5,36,11,covers);
            c.text(t.title(),360,b.y()+19,14,true,on?DEEP:INK,360);
            c.text(t.artist(),360,b.y()+36,11,false,MUTED,360);
            c.text(t.album(),748,b.y()+29,12,false,MUTED,150);
            c.right(t.durationText(),972,b.y()+29,12,MUTED);
            Box add=l.box("add"+i);
            UiIcons.draw(c, v.page()==MusicView.Page.QUEUE?"remove":"plus",
                    add.cx(), add.cy(), 16, v.hover().equals("add"+i)?DEEP:MUTED);
        }
        if(!v.tracks().isEmpty()) {
            c.g.setColor(new Color(135,90,115,26));
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
        setting(c,v,l,"lyric","灵动岛歌词",v.lyricMode());
        setting(c,v,l,"island","顶部灵动岛",v.islandEnabled()?"已开启":"已关闭");
        c.text("CloudXMusic",316,372,23,true,DEEP,700);
        c.text("云朵音乐盒 · "+v.version(),316,399,14,false,INK,690);
        c.text("开发者",316,438,12,false,MUTED,690);
        c.text("Lzasn,xWtree,cloudfl4re,xiaota888",316,465,17,true,INK,690);
        c.text("Minecraft 1.21.11 · Fabric · Inter + Noto Sans SC",316,503,12,false,MUTED,585);
    }

    private static void setting(FrostCanvas c,MusicView v,MusicLayout l,String key,String title,String value) {
        Box b=l.box(key);
        c.text(title,b.x(),b.y()+20,13,true,INK,205);
        c.right(value,b.x()+b.w(),b.y()+20,13,v.hover().equals(key)?DEEP:MUTED);
        c.g.setColor(new Color(135,90,115,30));
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
    public static Box accFieldBox(int i) { return new Box(ACC_X, 210 + i * 64, 460, 52); }
    public static Box accPhoneCodeBox() { return new Box(ACC_X, 274, 300, 52); }
    public static Box accSendBox() { return new Box(751, 274, 150, 52); }
    public static Box accSubmitBox() { return new Box(ACC_X, 350, 180, 52); }
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
                c.round(ACC_X, 176, 80, 80, 40, new Color(245, 208, 223));
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
                pill(c, accLogoutBox(), "退出登录", new Color(249, 225, 235), DEEP);
                pill(c, accRefreshBox(), "刷新账号", new Color(255, 255, 255, 155), MUTED);
                return image;
            }
            if (v.mode() == 0) {
                for (int i = 0; i < 4; i++) {
                    Box b = accMenuBox(i);
                    boolean disabled = i == 2;
                    c.shadow(b.x(), b.y(), b.w(), b.h(), 28, 8, 0, 4, new Color(150, 65, 99, disabled ? 8 : 20));
                    c.round(b.x(), b.y(), b.w(), b.h(), 28,
                            disabled ? new Color(255, 255, 255, 110) : new Color(255, 255, 255, 190));
                    c.g.setColor(disabled ? new Color(135, 90, 115, 22) : new Color(223, 115, 153, 120));
                    c.g.setStroke(new BasicStroke(1.4f));
                    c.g.draw(shape(b.x() + .5, b.y() + .5, b.w() - 1, b.h() - 1, 28));
                    c.center(ACC_MENU[i], b.cx(), b.cy() + 6, 16, !disabled,
                            disabled ? new Color(0x9C, 0x8C, 0x96) : INK, b.w() - 40);
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
        boolean phone = v.mode() == 2;
        int count = v.mode() == 3 ? 1 : 2;
        for (int i = 0; i < count; i++) {
            int index = v.mode() == 1 ? i : v.mode() == 2 ? 2 + i : 4;
            Box b = phone && i == 1 ? accPhoneCodeBox() : accFieldBox(i);
            String value = v.values()[index] == null ? "" : v.values()[index];
            boolean masked = index == 1;
            String shown = masked && !value.isEmpty()
                    ? "●".repeat(Math.min(20, value.codePointCount(0, value.length()))) : value;
            c.round(b.x(), b.y(), b.w(), b.h(), 22, new Color(255, 255, 255, 175));
            c.g.setColor(new Color(135, 90, 115, 34));
            c.g.setStroke(new BasicStroke(1f));
            c.g.draw(shape(b.x() + .5, b.y() + .5, b.w() - 1, b.h() - 1, 22));
            c.text(shown.isEmpty() ? ACC_HINTS[index] : shown, b.x() + 20, b.cy() + 5, 13, false,
                    shown.isEmpty() ? new Color(0x9C, 0x8C, 0x96) : INK, b.w() - 40);
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
            pill(c, s, v.countdown() > 0 ? "重发 " + v.countdown() + "s" : "发送验证码",
                    new Color(255, 255, 255, 155), v.countdown() > 0 ? MUTED : DEEP);
        }
        if (v.mode() == 3) {
            Box help = accCookieHelpBox();
            String label = "如何获取网易云 Cookie？点这里查看教程";
            c.text(label, help.x(), help.y() + 17, 12, false, PINK, help.w());
            float width = c.width(label, 12, false);
            c.g.setColor(new Color(223, 115, 153, 110));
            c.g.setStroke(new BasicStroke(1f));
            c.g.drawLine(help.x(), help.y() + 21, Math.round(help.x() + width), help.y() + 21);
        }
        pill(c, accSubmitBox(), "登录", new Color(249, 225, 235), DEEP);
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
        c.round(b.x(),b.y(),b.w(),b.h(),b.h()/2,accent?new GradientPaint(b.x(),b.y(),new Color(245,162,189),b.x()+b.w(),b.y()+b.h(),hover?DEEP:PINK):new Color(255,255,255,hover?240:185));
        UiIcons.draw(c,icon,b.cx(),b.cy(),accent?26:19,accent?Color.WHITE:hover?DEEP:MUTED);
    }

    private static void labelButton(FrostCanvas c,MusicView v,MusicLayout l,String key,String label) {
        Box b=l.box(key);c.shadow(b.x(),b.y(),b.w(),b.h(),b.h()/2,8,3,new Color(160,70,109,20));
        c.round(b.x(),b.y(),b.w(),b.h(),b.h()/2,v.hover().equals(key)?new Color(244,203,221):new Color(249,225,235));
        c.center(label,b.cx(),b.cy()+5,13,true,DEEP,b.w()-16);
    }

    public static void cover(FrostCanvas c,Track track,int x,int y,int size,int radius,Function<String,BufferedImage> covers) {
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
}
