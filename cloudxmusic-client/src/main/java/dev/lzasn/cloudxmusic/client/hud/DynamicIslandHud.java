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
    private static final long TRANSITION_NANOS = 240_000_000L;
    private static final UiTexture SURFACE = new UiTexture();
    private static IslandPainter.View previous;
    private static double previousScale;
    private static long lastPaint;
    private static long transitionStarted;
    private static double transitionFrom = 1;
    private static double transitionTo = 1;
    private static boolean musicUiOpen;
    private static boolean visible;

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

        boolean musicUi = client.screen instanceof MusicScreen
                || client.screen instanceof FirstRunNoticeScreen;

        if (!eligible(client, state)) {
            if (musicUi) {
                musicUiOpen = true;
                beginTransition(now, 0.04, 0.04);
            } else {
                musicUiOpen = false;
            }
            clear();
            return;
        }

        if (musicUi) {
            if (!musicUiOpen) beginTransition(now, visible ? animationScale(now) : 0.72, 0.04);
            musicUiOpen = true;
            if (client.screen instanceof MusicScreen) {
                double scale = animationScale(now);
                if (scale > 0.04) renderIsland(graphics, scale);
            }
            return;
        }

        if (musicUiOpen) {
            beginTransition(now, animationScale(now), 1);
            musicUiOpen = false;
        } else if (!visible) {
            beginTransition(now, 0.72, 1);
        }
        visible = true;
        renderIsland(graphics, animationScale(now));
    }

    /** 调整模式：只绘制遮罩、灵动岛与提示，鼠标事件由 IslandPositionScreen 接管。 */
    private static void renderAdjustMode(GuiGraphics graphics) {
        var client = CloudXMusicClient.client();
        MusicState state = CloudXMusicClient.STATE;
        int gw = graphics.guiWidth(), gh = graphics.guiHeight();

        graphics.fill(0, 0, gw, gh, 0x99000000);
        renderIslandAt(graphics, state, 1.0);
        graphics.drawCenteredString(client.font, "拖动灵动岛调整位置，Esc 或点击空白处保存",
                gw / 2, gh / 2 - 7, 0xFFFFFFFF);
    }

    private static boolean eligible(net.minecraft.client.Minecraft client, MusicState state) {
        return client.level != null && !client.options.hideGui && state.islandEnabled() && state.hasCurrentTrack();
    }

    private static void beginTransition(long now, double from, double to) {
        transitionStarted = now;
        transitionFrom = from;
        transitionTo = to;
    }

    private static double animationScale(long now) {
        if (transitionStarted == 0) return transitionTo;
        double t = Math.min(1, Math.max(0, (now - transitionStarted) / (double) TRANSITION_NANOS));
        double eased = 1 - Math.pow(1 - t, 3);
        return transitionFrom + (transitionTo - transitionFrom) * eased;
    }

    private static void renderIsland(GuiGraphics graphics, double animation) {
        var client = CloudXMusicClient.client();
        MusicState state = CloudXMusicClient.STATE;
        LyricMode mode = NeteaseSession.INSTANCE.lyricMode();
        Lyric.Line line = mode == LyricMode.OFF ? null : state.lyric().lineAt((int) (state.position() * 1000));
        boolean hasLine = line != null && !line.text().isBlank();
        String title = hasLine ? line.text() : state.current().title();
        String subtitle = hasLine && mode == LyricMode.BILINGUAL && line.hasTranslation()
                ? line.translated() : state.current().artist();
        var view = new IslandPainter.View(title, subtitle, state.positionText(),
                (int) (1000 * Math.max(0, Math.min(1,
                        state.position() / Math.max(1, state.current().durationSeconds())))),
                hasLine && mode == LyricMode.BILINGUAL);

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

        double shown = display * animation;
        int w = (int) Math.round((IslandPainter.WIDTH + 2 * IslandPainter.PAD) * shown);
        int h = (int) Math.round((IslandPainter.HEIGHT + 2 * IslandPainter.PAD) * shown);
        double x = state.islandX() * (graphics.guiWidth() - w);
        double y = state.islandY() * (graphics.guiHeight() - h);
        SURFACE.draw(graphics, (int) Math.round(x), (int) Math.round(y), w, h);
    }

    /** 调整模式专用：用占位内容渲染灵动岛（即使没放歌也显示），返回其屏幕区域 [x, y, w, h]。 */
    private static double[] renderIslandAt(GuiGraphics graphics, MusicState state, double animation) {
        var client = CloudXMusicClient.client();
        var view = new IslandPainter.View("拖动调整位置", "Esc 或点击屏幕中央保存", "", 0, false);
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
        double shown = display * animation;
        int w = (int) Math.round((IslandPainter.WIDTH + 2 * IslandPainter.PAD) * shown);
        int h = (int) Math.round((IslandPainter.HEIGHT + 2 * IslandPainter.PAD) * shown);
        double x = state.islandX() * (graphics.guiWidth() - w);
        double y = state.islandY() * (graphics.guiHeight() - h);
        SURFACE.draw(graphics, (int) Math.round(x), (int) Math.round(y), w, h);
        return new double[]{x, y, w, h};
    }

    public static void clear() {
        if (previous != null) {
            SURFACE.close();
            previous = null;
        }
        previousScale = 0;
        lastPaint = 0;
        visible = false;
    }
}
