package dev.lzasn.cloudxmusic.client.gui.design;

import java.util.ArrayList;
import java.util.List;

/** 绘制和点击共享设计坐标；所有列宽、对齐和命中区域集中在这里。 */
public final class MusicLayout {
    /**
     * 画布透明留白。外层窗口阴影的 margin 为 {@code blur(28)*3 + spread(2) = 86}，
     * 再留出 10px 余量，低于此值阴影四边会被裁成硬线。
     */
    public static final int WIDTH = 1100, HEIGHT = 674, PAD = 96;
    public static final int MAIN_X = 292, MAIN_W = 758;
    public static final int ROW_TOP = 212, ROW_H = 50, ROWS = 8;
    public enum Action { PAGE, ACCOUNT, CLOSE, PREVIOUS, PLAY, NEXT, SEEK, SEARCH_FIELD, SEARCH,
        ROW, ADD, CLEAR, REFRESH, QUALITY, LYRIC, ISLAND, VOLUME, FAVORITE, PLAYBACK_MODE, MODE_CYCLE,
        STOP, ACC_FIELD, ACC_PASSWORD, ACC_PHONE, ACC_COOKIE, ACC_SEND, ACC_LOGOUT, ACC_REFRESH,
        DARK_MODE, PANEL_SCALE, ISLAND_SCALE, ISLAND_POS }
    public record Box(int x, int y, int w, int h) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
        public int cx() { return x + w / 2; }
        public int cy() { return y + h / 2; }
    }
    public record Hit(String id, Action action, int index, Box box) {}
    private final List<Hit> hits = new ArrayList<>();

    /** 导航区可视范围：从「音乐库」标签下方到分隔线之前，超出部分靠滚动查看。 */
    public static final int NAV_TOP = 137, NAV_BOTTOM = 405;
    /** 单个导航项高度与步进：保持原尺寸，项目变多时改由滚动承载。 */
    public static final int NAV_H = 38, NAV_STEP = 44;
    public static final int NAV_COUNT = 6;

    public static int navContentHeight() {
        return NAV_COUNT * NAV_STEP - (NAV_STEP - NAV_H);
    }

    public static int navMaxScroll() {
        return Math.max(0, navContentHeight() - (NAV_BOTTOM - NAV_TOP));
    }

    public MusicLayout(MusicView.Page page, int rowCount, int queueCount) {
        this(page, rowCount, queueCount, 0, 0);
    }

    public MusicLayout(MusicView.Page page, int rowCount, int queueCount, int settingsScroll) {
        this(page, rowCount, queueCount, settingsScroll, 0);
    }

    public MusicLayout(MusicView.Page page, int rowCount, int queueCount, int settingsScroll, int navScroll) {
        for (int i = 0; i < NAV_COUNT; i++) {
            int y = NAV_TOP + i * NAV_STEP - navScroll;
            // 完全滚出可视区的项不注册命中区，否则点空白处会误切页面。
            if (y + NAV_H <= NAV_TOP || y >= NAV_BOTTOM) continue;
            add("nav" + i, Action.PAGE, i, 32, y, 210, NAV_H);
        }
        add("settings", Action.PAGE, 6, 32, 602, 210, 42);
        add("account", Action.ACCOUNT, 0, 32, 442, 210, 64);
        add("close", Action.CLOSE, 0, 1012, 22, 36, 36);
        if (page == MusicView.Page.NOW) {
            // 播放页：上方歌词，底部一条控制栏。收藏与队列紧贴传输键两侧，控件整体居中。
            add("favorite", Action.FAVORITE, 0, 532, 601, 36, 36);
            add("queueBtn", Action.PAGE, 4, 570, 601, 36, 36);
            add("prev", Action.PREVIOUS, 0, 608, 601, 36, 36);
            add("play", Action.PLAY, 0, 645, 593, 52, 52);
            add("next", Action.NEXT, 0, 698, 601, 36, 36);
            add("mode", Action.MODE_CYCLE, 0, 736, 601, 36, 36);
            add("quality", Action.QUALITY, 0, 886, 605, 64, 28);
            add("volume", Action.VOLUME, 0, 982, 610, 62, 18);
            add("seek", Action.SEEK, 0, 328, 646, 672, 12);
        } else {
            if (page == MusicView.Page.SETTINGS) {
                add("lyric", Action.LYRIC, 0, 318, 132 - settingsScroll, 704, 48);
                add("island", Action.ISLAND, 0, 318, 184 - settingsScroll, 704, 48);
                add("islandScale", Action.ISLAND_SCALE, 0, 318, 236 - settingsScroll, 704, 48);
                add("islandPos", Action.ISLAND_POS, 0, 318, 288 - settingsScroll, 704, 48);
                add("darkMode", Action.DARK_MODE, 0, 318, 364 - settingsScroll, 704, 48);
                add("panelScale", Action.PANEL_SCALE, 0, 318, 416 - settingsScroll, 704, 48);
            } else if (page == MusicView.Page.ROOM) {
                // 占位页：没有任何可点控件，不能继承列表页的刷新按钮和行命中区，
                // 否则点空白处会取到空 rows 而越界。
            } else {
                if (page == MusicView.Page.SEARCH) {
                    add("field", Action.SEARCH_FIELD, 0, 292, 116, 634, 36);
                    add("search", Action.SEARCH, 0, 950, 116, 100, 36);
                } else {
                    add("refresh", page == MusicView.Page.QUEUE ? Action.CLEAR : Action.REFRESH, 0, 946, 116, 104, 36);
                }
                for (int i = 0; i < Math.min(ROWS, rowCount); i++) {
                    add("row" + i, Action.ROW, i, MAIN_X, ROW_TOP + i * ROW_H, 696, 46);
                    add("add" + i, Action.ADD, i, 1002, ROW_TOP + i * ROW_H + 3, 40, 40);
                }
            }
        }
    }

    private void add(String id, Action action, int index, int x, int y, int w, int h) {
        hits.add(new Hit(id, action, index, new Box(x, y, w, h)));
    }
    public List<Hit> hits() { return List.copyOf(hits); }
    public Hit hit(double x, double y) {
        for (Hit hit : hits) if (hit.box.contains(x, y)) return hit;
        return null;
    }

    public Box box(String id) {
        for (Hit hit : hits) if (hit.id.equals(id)) return hit.box;
        throw new IllegalArgumentException("Unknown layout box: " + id);
    }

    /**
     * 播放页的歌词栏：在整窗高度上垂直居中，左缘取到 632 换取更宽的排版，
     * 避免长句被省略号截断。七行槽位按 48 间距排布。
     */
    public static Box lyricRegion() {
        return new Box(632, 166, 416, 342);
    }

    /** 播放页封面区域，含封面投影所需留白；播放时封面 240、暂停时 0.9 倍。 */
    public static Box coverRegion() {
        return new Box(312, 169, 336, 336);
    }

    /** 账号页整块内容区域（菜单或表单），由 MusicPainter.paintAccount 独占绘制。 */
    public static Box accountRegion() {
        return new Box(292, 98, 758, 420);
    }

    /**
     * 随播放时钟高频变化的区域。只有这些条带需要按进度重绘，
     * 静态画布（磨砂玻璃、导航、卡片、文字）不再跟随播放时间刷新。
     */
    public static Box[] dynamicBands(MusicView.Page page) {
        if (page == MusicView.Page.NOW) {
            return new Box[]{
                    new Box(292, 640, 758, 22),   // 进度条与两端时间
                    new Box(958, 604, 92, 28)     // 音量滑条
            };
        }
        // 其他页面已经没有播放控件，不需要动态层。
        return new Box[0];
    }

    /** 动态条带的并集，用作叠加画布的尺寸与落点。 */
    public static Box dynamicRegion(MusicView.Page page) {
        Box[] bands = dynamicBands(page);
        if (bands.length == 0) return new Box(0, 0, 1, 1);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = 0, y1 = 0;
        for (Box b : bands) {
            x0 = Math.min(x0, b.x());
            y0 = Math.min(y0, b.y());
            x1 = Math.max(x1, b.x() + b.w());
            y1 = Math.max(y1, b.y() + b.h());
        }
        return new Box(x0, y0, x1 - x0, y1 - y0);
    }
}
