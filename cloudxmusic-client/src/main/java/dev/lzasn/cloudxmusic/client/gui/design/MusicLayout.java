package dev.lzasn.cloudxmusic.client.gui.design;

import java.util.ArrayList;
import java.util.List;

/** 绘制和点击共享设计坐标；所有列宽、对齐和命中区域集中在这里。 */
public final class MusicLayout {
    public static final int WIDTH = 1100, HEIGHT = 674, PAD = 112;
    public static final int MAIN_X = 292, MAIN_W = 758;
    public static final int ROW_TOP = 212, ROW_H = 58, ROWS = 6;
    public enum Action { PAGE, ACCOUNT, CLOSE, PREVIOUS, PLAY, NEXT, SEEK, SEARCH_FIELD, SEARCH,
        ROW, ADD, CLEAR, REFRESH, QUALITY, LYRIC, ISLAND, VOLUME, FAVORITE, PLAYBACK_MODE, STOP }
    public record Box(int x, int y, int w, int h) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + w && py < y + h; }
        public int cx() { return x + w / 2; }
        public int cy() { return y + h / 2; }
    }
    public record Hit(String id, Action action, int index, Box box) {}
    private final List<Hit> hits = new ArrayList<>();

    public MusicLayout(MusicView.Page page, int rowCount, int queueCount) {
        for (int i = 0; i < 5; i++) add("nav" + i, Action.PAGE, i, 32, 137 + i * 44, 210, 38);
        add("settings", Action.PAGE, 5, 32, 602, 210, 42);
        add("account", Action.ACCOUNT, 0, 32, 400, 210, 64);
        add("close", Action.CLOSE, 0, 1012, 22, 36, 36);
        if (page == MusicView.Page.NOW) {
            add("seek", Action.SEEK, 0, 568, 304, 482, 20);
            add("prev", Action.PREVIOUS, 0, 731, 350, 38, 38);
            add("play", Action.PLAY, 0, 778, 340, 58, 58);
            add("next", Action.NEXT, 0, 845, 350, 38, 38);
            add("allQueue", Action.PAGE, 4, 948, 445, 80, 30);
            for (int i = 0; i < Math.min(3, queueCount); i++) add("queue" + i, Action.ROW, i, 685, 486 + i * 44, 350, 42);
        } else {
            add("prev", Action.PREVIOUS, 0, 736, 590, 34, 34);
            add("play", Action.PLAY, 0, 777, 583, 48, 48);
            add("next", Action.NEXT, 0, 832, 590, 34, 34);
            add("seek", Action.SEEK, 0, 380, 636, 325, 16);
            add("favorite", Action.FAVORITE, 0, 518, 590, 28, 28);
            add("mode0", Action.PLAYBACK_MODE, 0, 564, 592, 28, 28);
            add("mode1", Action.PLAYBACK_MODE, 1, 596, 592, 28, 28);
            add("mode2", Action.PLAYBACK_MODE, 2, 628, 592, 28, 28);
            add("volume", Action.VOLUME, 0, 940, 596, 84, 22);
            if (page == MusicView.Page.SETTINGS) {
                add("quality", Action.QUALITY, 0, 318, 132, 704, 48);
                add("lyric", Action.LYRIC, 0, 318, 188, 704, 48);
                add("island", Action.ISLAND, 0, 318, 244, 704, 48);
                add("stop", Action.STOP, 0, 920, 500, 96, 36);
            } else {
                if (page == MusicView.Page.SEARCH) {
                    add("field", Action.SEARCH_FIELD, 0, 292, 112, 642, 48);
                    add("search", Action.SEARCH, 0, 950, 112, 100, 48);
                } else {
                    add("refresh", page == MusicView.Page.QUEUE ? Action.CLEAR : Action.REFRESH, 0, 946, 116, 104, 36);
                }
                for (int i = 0; i < Math.min(ROWS, rowCount); i++) {
                    add("row" + i, Action.ROW, i, MAIN_X, ROW_TOP + i * ROW_H, 696, 52);
                    add("add" + i, Action.ADD, i, 1002, ROW_TOP + i * ROW_H + 6, 40, 40);
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
}
