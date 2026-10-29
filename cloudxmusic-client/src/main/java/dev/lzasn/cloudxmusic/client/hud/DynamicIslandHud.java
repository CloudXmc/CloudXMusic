package dev.lzasn.cloudxmusic.client.hud;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.gui.CoverCache;
import dev.lzasn.cloudxmusic.client.gui.MusicScreen;
import dev.lzasn.cloudxmusic.client.gui.UiTexture;
import dev.lzasn.cloudxmusic.client.gui.design.FirstRunNoticeScreen;
import dev.lzasn.cloudxmusic.client.gui.design.IslandPainter;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;

/** 游戏 HUD 实时显示；仅音乐盒界面会收起灵动岛。 */
public final class DynamicIslandHud {
    /**
     * 灵动岛自己的过渡时长，与面板的 420ms 无关。
     *
     * <p>面板是一整块大玻璃，420ms 看得清；灵动岛只有一条药丸，同样的时长和曲线
     * 会让它「一闪就没」。这里单独放慢，面板那边不受影响。</p>
     */
    private static final long TRANSITION_NANOS = 450_000_000L;
    /** 归一化位置落在中间这个带内就用缩放动画，否则从最近的一条屏幕边缘飞入/飞出。 */
    private static final double CENTER_BAND = 0.22;
    private static final UiTexture SURFACE = new UiTexture();
    private static IslandPainter.View previous;
    private static double previousScale;
    private static long lastPaint;
    private static long transitionStarted;
    /** 0 = 完全收起（缩成一点或已在屏幕外），1 = 完全展开。 */
    private static double transitionFrom = 1;
    private static double transitionTo = 1;
    private static boolean musicUiOpen;
    private static boolean visible;
    private static boolean adjustCached;

    private DynamicIslandHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(DynamicIslandHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        var client = CloudXMusicClient.client();
        MusicState state = CloudXMusicClient.STATE;
        long now = System.nanoTime();

        // 调整灵动岛位置模式：始终显示灵动岛，可拖动，底部提示，Esc/点中央退出。
        if (state.adjustingIslandPos()) {
            renderAdjustMode(graphics);
            return;
        }

        // 面板正在下滑时就不再算「界面打开」：灵动岛从这一刻开始展开，
        // 和面板退场同时进行，而不是等界面彻底消失后才动。
        boolean musicUi = (client.screen instanceof MusicScreen && !MusicScreen.panelClosing())
                || client.screen instanceof FirstRunNoticeScreen;

        if (!eligible(client, state)) {
            if (musicUi) {
                musicUiOpen = true;
                beginTransition(now, 0, 0);
            } else {
                musicUiOpen = false;
            }
            clear();
            return;
        }

        if (musicUi) {
            if (!musicUiOpen) beginTransition(now, animationProgress(now), 0);
            musicUiOpen = true;
            if (client.screen instanceof MusicScreen) {
                double progress = animationProgress(now);
                if (progress > 0.01) renderIsland(graphics, progress);
            }
            return;
        }

        if (musicUiOpen) {
            // 界面一收起就开始展开，不再等面板动画播完。
            beginTransition(now, animationProgress(now), 1);
            musicUiOpen = false;
        } else if (!visible) {
            beginTransition(now, 0, 1);
        }
        visible = true;
        renderIsland(graphics, animationProgress(now));
    }

    /** 调整模式：遮罩、灵动岛与底部提示；鼠标事件由 IslandPositionScreen 接管。 */
    private static void renderAdjustMode(GuiGraphics graphics) {
        var client = CloudXMusicClient.client();
        MusicState state = CloudXMusicClient.STATE;
        int gw = graphics.guiWidth(), gh = graphics.guiHeight();

        graphics.fill(0, 0, gw, gh, 0x99000000);
        // 提示固定在屏幕底部居中：不再画在 gh/2，灵动岛拖到中间时不会压住它。
        // 先画提示再画灵动岛，即使拖到最底部也是药丸盖住提示，保证灵动岛永远不被遮。
        graphics.drawCenteredString(client.font, "拖动灵动岛调整位置，Esc 或点击空白处保存",
                gw / 2, gh - 28, 0xFFFFFFFF);
        renderAdjustIsland(graphics, state);
    }

    private static boolean eligible(net.minecraft.client.Minecraft client, MusicState state) {
        return client.level != null && !client.options.hideGui && state.islandEnabled() && state.hasCurrentTrack();
    }

    private static void beginTransition(long now, double from, double to) {
        transitionStarted = now;
        transitionFrom = from;
        transitionTo = to;
    }

    /** 当前过渡进度：0 收起、1 展开。曲线与面板升降保持一致。 */
    private static double animationProgress(long now) {
        if (transitionStarted == 0) return transitionTo;
        double t = Math.min(1, Math.max(0, (now - transitionStarted) / (double) TRANSITION_NANOS));
        // 二次方缓出，与面板同一套参数。四次方太陡，灵动岛这种小元素会「一闪就没」。
        double eased = 1 - Math.pow(1 - t, 2);
        return transitionFrom + (transitionTo - transitionFrom) * eased;
    }

    /** 灵动岛停在屏幕中部时用缩放退场，靠边时改为从边缘飞入飞出。 */
    private static boolean zoomMode(MusicState state) {
        return Math.abs(state.islandX() - 0.5) <= CENTER_BAND
                && Math.abs(state.islandY() - 0.5) <= CENTER_BAND;
    }

    /** 飞出方向：取偏离中心更大的那一轴，朝最近的那条边离开。 */
    private static double[] exitDirection(MusicState state) {
        double dx = state.islandX() - 0.5, dy = state.islandY() - 0.5;
        if (Math.abs(dx) >= Math.abs(dy)) return new double[]{dx < 0 ? -1 : 1, 0};
        return new double[]{0, dy < 0 ? -1 : 1};
    }

    /** @param p 过渡进度：1 完全展开、0 完全收起。 */
    private static void renderIsland(GuiGraphics graphics, double p) {
        var client = CloudXMusicClient.client();
        MusicState state = CloudXMusicClient.STATE;
        LyricMode mode = NeteaseSession.INSTANCE.lyricMode();
        Lyric.Line line = mode == LyricMode.OFF ? null : state.lyric().lineAt((int) (state.position() * 1000));
        boolean hasLine = line != null && !line.text().isBlank();
        boolean bilingual = hasLine && mode == LyricMode.BILINGUAL && line.hasTranslation();
        String title = hasLine ? line.text() : state.current().title();
        // 双语有翻译时副标题就是译文；其余带歌词的情况下标题已被歌词占用，
        // 副标题补「歌名 · 歌手」，否则岛上看不出正在放的是哪首歌。
        String subtitle;
        if (bilingual) {
            subtitle = line.translated();
        } else if (hasLine) {
            subtitle = state.current().title() + " · " + state.current().artist();
        } else {
            subtitle = state.current().artist();
        }
        var view = new IslandPainter.View(title, subtitle, state.positionText(),
                (int) (1000 * Math.max(0, Math.min(1,
                        state.position() / Math.max(1, state.current().durationSeconds())))),
                bilingual);

        double display = Math.min(.58, Math.min(
                (graphics.guiWidth() - 16.0) / IslandPainter.WIDTH,
                (graphics.guiHeight() - 8.0) / IslandPainter.HEIGHT)) * state.islandScale();
        double raster = Math.max(.75, Math.min(2, display * client.getWindow().getGuiScale()));
        long now = System.nanoTime();
        if ((!view.equals(previous) || raster != previousScale)
                && (previous == null || now - lastPaint >= 100_000_000L)) {
            SURFACE.update(IslandPainter.paint(view, raster, CoverCache.image(state.current().coverUrl())));
            previous = view;
            previousScale = raster;
            lastPaint = now;
        }

        int w = (int) Math.round((IslandPainter.WIDTH + 2 * IslandPainter.PAD) * display);
        int h = (int) Math.round((IslandPainter.HEIGHT + 2 * IslandPainter.PAD) * display);
        double x = state.islandX() * (graphics.guiWidth() - w);
        double y = state.islandY() * (graphics.guiHeight() - h);
        if (p >= 0.999) {
            SURFACE.draw(graphics, (int) Math.round(x), (int) Math.round(y), w, h);
            return;
        }
        if (zoomMode(state)) {
            // 中部：围绕岛中心缩放，p 趋近 0 时缩成一个看不见的点。
            float s = (float) Math.max(0.01, p);
            float cx = (float) (x + w / 2.0), cy = (float) (y + h / 2.0);
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx, cy);
            graphics.pose().scale(s, s);
            graphics.pose().translate(-cx, -cy);
            SURFACE.draw(graphics, (int) Math.round(x), (int) Math.round(y), w, h);
            graphics.pose().popMatrix();
        } else {
            // 靠边：朝最近的那条边飞入/飞出。位移距离按「岛到该边缘的实际距离」算，
            // 而不是固定倍数屏幕——岛贴着上边时只需走几十像素，按整屏算会快到看不清。
            double[] dir = exitDirection(state);
            double travel;
            if (dir[0] != 0) {
                travel = dir[0] < 0 ? x + w : graphics.guiWidth() - x;
            } else {
                travel = dir[1] < 0 ? y + h : graphics.guiHeight() - y;
            }
            double off = (1 - p) * travel;
            SURFACE.draw(graphics, (int) Math.round(x + dir[0] * off),
                    (int) Math.round(y + dir[1] * off), w, h);
        }
    }

    /** 调整模式渲染：内容固定，只在光栅倍率变化时重绘一次，位置随 islandX/islandY 移动。 */
    private static void renderAdjustIsland(GuiGraphics graphics, MusicState state) {
        var client = CloudXMusicClient.client();
        double display = Math.min(.58, Math.min(
                (graphics.guiWidth() - 16.0) / IslandPainter.WIDTH,
                (graphics.guiHeight() - 8.0) / IslandPainter.HEIGHT)) * state.islandScale();
        double raster = Math.max(.75, Math.min(2, display * client.getWindow().getGuiScale()));
        if (!adjustCached || raster != previousScale) {
            SURFACE.update(IslandPainter.paintAdjustment(raster));
            // 置空播放态快照，退出调整模式后普通渲染必定重绘一次，不会沿用提示药丸。
            previous = null;
            previousScale = raster;
            lastPaint = System.nanoTime();
            adjustCached = true;
        }
        int w = (int) Math.round((IslandPainter.WIDTH + 2 * IslandPainter.PAD) * display);
        int h = (int) Math.round((IslandPainter.HEIGHT + 2 * IslandPainter.PAD) * display);
        double x = state.islandX() * (graphics.guiWidth() - w);
        double y = state.islandY() * (graphics.guiHeight() - h);
        SURFACE.draw(graphics, (int) Math.round(x), (int) Math.round(y), w, h);
    }

    public static void clear() {
        if (previous != null || adjustCached) {
            SURFACE.close();
            previous = null;
        }
        previousScale = 0;
        lastPaint = 0;
        adjustCached = false;
        visible = false;
    }
}
