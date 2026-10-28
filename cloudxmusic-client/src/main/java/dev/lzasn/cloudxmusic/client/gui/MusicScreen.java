package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.netease.NeteaseApi;
import dev.lzasn.cloudxmusic.client.netease.NeteaseHttp;
import dev.lzasn.cloudxmusic.client.netease.NeteaseQuality;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.client.util.Async;
import dev.lzasn.cloudxmusic.client.util.Browser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;
import dev.lzasn.cloudxmusic.client.gui.design.MusicView.Page;

/** 游戏输入/业务适配器；排版与绘制完全交给独立画布。 */
public final class MusicScreen extends Screen {
    /** 整窗占可用屏幕的比例；调小一点界面整体更紧凑。 */
    private static final double PANEL_FILL = 0.8;
    /** Cookie 登录方式里的教程地址。 */
    private static final String COOKIE_HELP_URL = "https://www.xunjinlu.fun/index.php/archives/10/";
    private static final String VERSION = FabricLoader.getInstance().getModContainer("cloudxmusic")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    private final MusicState state = CloudXMusicClient.STATE;
    private final UiTexture surface = new UiTexture();
    private final UiTexture overlay = new UiTexture();
    private final UiTexture lyricSurface = new UiTexture();
    private final UiTexture coverSurface = new UiTexture();
    private final UiTexture accountSurface = new UiTexture();
    private final UiTexture qualitySurface = new UiTexture();
    /** 账号页五个输入项：账号、密码、手机号、验证码、Cookie。 */
    private final String[] accountValues = {"", "", "", "", ""};
    /** 0 菜单，1 用户名密码，2 验证码，3 Cookie。 */
    private int accountMode;
    private int accountFocus = -1;
    private boolean accountSelectAll;
    private String accountStatus = "";
    private boolean accountBusy;
    private int accountCountdown;
    private long accountSecondAt;
    /** 已登录但没有头像地址时补拉一次账号信息；只尝试一次。 */
    private boolean avatarSynced;
    /** 音质选择浮层是否展开。 */
    private boolean qualityMenuOpen;
    private String paintedQualityKey;
    private String paintedAccountKey;
    private Page page = Page.NOW;
    private int scroll, originX, originY;
    private double scale, rasterScale;
    private String input = "";
    private boolean focused, selectAll, blurUnavailable;
    private float seekPreview = -1;
    private Action drag;
    private long lastPaint, lastOverlayPaint;
    private int lastCoverRevision;
    private long pageTransitionAt;
    private MusicView painted, paintedDynamic;
    private List<MusicView.LyricRow> paintedLyrics;
    private long lyricAnimStart;
    private float coverScale = PAUSED_COVER, coverFrom = PAUSED_COVER, coverTo = PAUSED_COVER;
    private long coverAnimStart;
    private String paintedCoverKey;
    private MusicLayout layout;
    private List<Track> rows = List.of(), upcoming = List.of();

    /** 暂停时封面回落到 0.9 倍，播放时放大到 1.0（基准 240）。 */
    private static final float PAUSED_COVER = .9f, PLAYING_COVER = 1f;
    private static final long COVER_ANIM_NANOS = 340_000_000L, LYRIC_ANIM_NANOS = 260_000_000L;

    public MusicScreen() { super(Component.literal("CloudXMusic · 云朵音乐盒")); }

    @Override protected void init() {
        // 同一个设计坐标变换用于绘制和命中，GUI 缩放不改变相对布局。
        // PANEL_FILL 控制整窗占可用屏幕的比例，1.0 为铺满，调小即整体缩小。
        scale = Math.max(.05, Math.min((width - 28.0) / WIDTH, (height - 48.0) / HEIGHT) * PANEL_FILL);
        originX = (int) Math.round((width - WIDTH * scale) / 2);
        originY = (int) Math.round((height - HEIGHT * scale) / 2 + 8);
        // 画布像素数按倍率平方增长，而 Java2D 路径填充是纯 CPU 光栅化。
        // 1.5 倍已接近原生清晰度（原先最高 2 倍只是超采样，代价是 4 倍像素）。
        rasterScale = Math.max(.75, Math.min(1.5, scale * minecraft.getWindow().getGuiScale()));
        painted = null; paintedDynamic = null; paintedLyrics = null; paintedCoverKey = null;
        lastPaint = 0; lastOverlayPaint = 0; rebuildLayout();
    }

    private List<Track> source() {
        return switch (page) {
            case DISCOVER -> state.library(); case SEARCH -> state.searchResults();
            case FAVORITES -> state.favorites(); case QUEUE -> state.queue(); default -> List.of();
        };
    }

    private void rebuildLayout() {
        List<Track> source = source();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, source.size() - ROWS)));
        rows = List.copyOf(source.subList(scroll, Math.min(source.size(), scroll + ROWS)));
        List<Track> queue = state.queue();
        List<Track> next = new ArrayList<>();
        int current = queue.indexOf(state.current());
        for (int i = 1; i <= queue.size() && next.size() < 3; i++) {
            Track track = queue.get(Math.floorMod(current + i, queue.size()));
            if (!track.equals(state.current())) next.add(track);
        }
        upcoming = List.copyOf(next);
        layout = new MusicLayout(page, rows.size(), upcoming.size());
    }

    private MusicView snapshot(String hover) {
        NeteaseSession session = NeteaseSession.INSTANCE;
        List<MusicView.LyricRow> lines = new ArrayList<>();
        Lyric lyric = state.lyric();
        if (!lyric.isEmpty()) {
            int active = Math.max(0, lyric.indexAt((int) (state.position() * 1000)));
            Lyric.Line[] all = lyric.lines();
            // 固定七槽窗口（当前行居中），越界留空槽，保证当前行始终停在同一条基线。
            for (int offset = -3; offset <= 3; offset++) {
                int index = active + offset;
                if (index < 0 || index >= all.length) {
                    lines.add(new MusicView.LyricRow("", "", false));
                    continue;
                }
                lines.add(new MusicView.LyricRow(all[index].text(), all[index].translated(), index == active));
            }
        }
        String status = state.statusLine();
        if (status.isBlank() && source().size() > ROWS) status = (scroll + 1) + "–" + (scroll + rows.size()) + " / " + source().size();
        return new MusicView(page, state.current(), rows, upcoming, List.copyOf(lines), (int) (state.position() * 10),
                state.playing(), session.loggedIn(), session.nickname().isBlank() ? "已登录" : session.nickname(),
                input, focused, focused && System.currentTimeMillis() / 500 % 2 == 0, status, session.quality().label,
                session.lyricMode().label, state.islandEnabled(), state.playbackMode(),
                state.isFavorite(state.current()), VERSION, hover,
                CoverCache.revision(), seekPreview, state.volume(), session.avatar());
    }

    @Override public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        rebuildLayout();
        if (!blurUnavailable && minecraft.level != null) {
            try { renderBlurredBackground(graphics); } catch (RuntimeException e) { blurUnavailable = true; }
        }
        graphics.fill(0, 0, width, height, 0x192C2430);
        Hit hover = layout.hit(localX(mx), localY(my));
        MusicView view = snapshot("");
        // 老版本持久化的账号可能没有头像地址，静默补一次。
        if (view.loggedIn() && NeteaseSession.INSTANCE.avatar().isBlank() && !avatarSynced) {
            avatarSynced = true;
            syncAccountQuietly();
        }
        // 静态层只在页面、列表、封面等真正变化时重画；播放进度不再驱动整幅重绘。
        MusicView still = view.staticView();
        long now = System.nanoTime();
        if (!still.equals(painted) && (painted == null || now - lastPaint >= 33_333_333L)) {
            surface.update(MusicPainter.paint(still, layout, rasterScale, CoverCache::image));
            painted = still; lastPaint = now; lastCoverRevision = CoverCache.revision();
        } else if (CoverCache.revision() != lastCoverRevision && now - lastPaint >= 33_333_333L) {
            // 头像/封面异步加载完成后 revision 变化，重画一次让头像现身。
            surface.update(MusicPainter.paint(still, layout, rasterScale, CoverCache::image));
            painted = still; lastPaint = now; lastCoverRevision = CoverCache.revision();
        }
        // 动态层只有播放页的进度条与音量，尺寸很小，按进度以 10Hz 刷新。
        boolean nowPage = page == Page.NOW;
        boolean accountPage = page == Page.ACCOUNT;
        if (accountPage) {
            // 账号页整块内容单独成纹理，打字/倒计时只重画这一块。
            boolean blink = accountFocus >= 0 && System.currentTimeMillis() / 500 % 2 == 0;
            String key = accountMode + "|" + String.join("\u0001", accountValues) + "|" + accountFocus + "|"
                    + accountStatus + "|" + accountCountdown + "|" + blink + "|" + view.loggedIn()
                    + "|" + view.nickname() + "|" + NeteaseSession.INSTANCE.uid()
                    + "|" + NeteaseSession.INSTANCE.avatar() + "|" + CoverCache.revision();
            if (!key.equals(paintedAccountKey)) {
                accountSurface.update(MusicPainter.paintAccount(new MusicPainter.AccountView(
                        view.loggedIn(), view.nickname(), NeteaseSession.INSTANCE.uid(),
                        NeteaseSession.INSTANCE.avatar(), accountMode, accountValues,
                        accountFocus, blink, accountStatus, accountCountdown), rasterScale));
                paintedAccountKey = key;
            }
        }
        if (nowPage && !view.equals(paintedDynamic)
                && (paintedDynamic == null || now - lastOverlayPaint >= 33_333_333L)) {
            overlay.update(MusicPainter.paintDynamic(view, layout, rasterScale));
            paintedDynamic = view; lastOverlayPaint = now;
        }
        if (nowPage) {
            // 歌词层：内容变化（当前行走了）才重画，滚动动效只移动贴图。
            if (!view.lyrics().equals(paintedLyrics)) {
                lyricSurface.update(MusicPainter.paintLyrics(view, rasterScale));
                paintedLyrics = view.lyrics();
                lyricAnimStart = now;
            }
            // 封面层：播放状态切换时做一次缩放动画。
            float target = view.playing() ? PLAYING_COVER : PAUSED_COVER;
            if (Math.abs(target - coverTo) > 1e-4f) {
                coverFrom = coverScale; coverTo = target; coverAnimStart = now;
            }
            double ct = coverAnimStart == 0 ? 1 : Math.min(1, (now - coverAnimStart) / (double) COVER_ANIM_NANOS);
            coverScale = coverFrom + (coverTo - coverFrom) * (float) (1 - Math.pow(1 - ct, 3));
            String coverKey = view.current().coverUrl() + "#" + CoverCache.revision();
            if (ct < 1 || !coverKey.equals(paintedCoverKey)) {
                coverSurface.update(MusicPainter.paintCover(view, rasterScale, coverScale, CoverCache::image));
                paintedCoverKey = coverKey;
            }
        }
        if (nowPage && qualityMenuOpen) {
            int hoverRow = qualityHover(mx, my);
            int selected = NeteaseSession.INSTANCE.quality().ordinal();
            Boolean support = state.losslessSupport(view.current().id());
            boolean losslessOk = support == null || support;
            String key = selected + "|" + hoverRow + "|" + losslessOk;
            if (!key.equals(paintedQualityKey)) {
                qualitySurface.update(MusicPainter.paintQualityMenu(selected, hoverRow, losslessOk, rasterScale));
                paintedQualityKey = key;
            }
        }
        int x = (int) Math.round(originX - PAD * scale), y = (int) Math.round(originY - PAD * scale);
        double eased = transitionEased(now);
        float transitionScale = (float) (0.985 + eased * 0.015);
        float shiftX = (float) (10 * scale * (1 - eased)), shiftY = (float) (5 * scale * (1 - eased));
        graphics.pose().pushMatrix();
        graphics.pose().translate(shiftX, shiftY);
        graphics.pose().scale(transitionScale, transitionScale);
        surface.draw(graphics, x, y, (int) Math.round((WIDTH + 2 * PAD) * scale), (int) Math.round((HEIGHT + 2 * PAD) * scale));
        if (nowPage) {
            Box cover = coverRegion();
            coverSurface.draw(graphics, (int) Math.round(originX + cover.x() * scale),
                    (int) Math.round(originY + cover.y() * scale),
                    (int) Math.round(cover.w() * scale), (int) Math.round(cover.h() * scale));
            // 歌词整体在行切换时向上滑入一行，靠裁剪把滑动过程限制在歌词区内。
            Box lyric = lyricRegion();
            double lt = lyricAnimStart == 0 ? 1 : Math.min(1, (now - lyricAnimStart) / (double) LYRIC_ANIM_NANOS);
            double ease = 1 - Math.pow(1 - lt, 3);
            double lyricOffset = (1 - ease) * 48 * scale;
            int lx = (int) Math.round((originX + lyric.x() * scale) * transitionScale + shiftX);
            int ly = (int) Math.round((originY + lyric.y() * scale) * transitionScale + shiftY);
            int lw = (int) Math.round(lyric.w() * scale * transitionScale);
            int lh = (int) Math.round(lyric.h() * scale * transitionScale);
            graphics.enableScissor(lx, ly, lx + lw, ly + lh);
            lyricSurface.draw(graphics, (int) Math.round(originX + lyric.x() * scale),
                    (int) Math.round(originY + lyric.y() * scale + lyricOffset),
                    (int) Math.round(lyric.w() * scale), (int) Math.round(lyric.h() * scale));
            graphics.disableScissor();
        }
        if (nowPage) {
            Box band = dynamicRegion(page);
            overlay.draw(graphics, (int) Math.round(originX + band.x() * scale),
                    (int) Math.round(originY + band.y() * scale),
                    (int) Math.round(band.w() * scale), (int) Math.round(band.h() * scale));
        }
        if (accountPage) {
            Box ar = accountRegion();
            accountSurface.draw(graphics, (int) Math.round(originX + ar.x() * scale),
                    (int) Math.round(originY + ar.y() * scale),
                    (int) Math.round(ar.w() * scale), (int) Math.round(ar.h() * scale));
        }
        if (nowPage && qualityMenuOpen) {
            Box qm = MusicPainter.qualityMenuRegion();
            qualitySurface.draw(graphics, (int) Math.round(originX + qm.x() * scale),
                    (int) Math.round(originY + qm.y() * scale),
                    (int) Math.round(qm.w() * scale), (int) Math.round(qm.h() * scale));
        }
        renderHover(graphics, hover);
        graphics.pose().popMatrix();
        super.render(graphics, mx, my, partialTick);
    }

    private double transitionEased(long now) {
        if (pageTransitionAt == 0) return 1;
        double t = Math.min(1, Math.max(0, (now - pageTransitionAt) / 220_000_000.0));
        return 1 - Math.pow(1 - t, 3);
    }

    private void renderHover(GuiGraphics graphics, Hit hit) {
        if (hit == null || (hit.action() == Action.PAGE
                && hit.index() == (page == Page.SETTINGS ? 5 : page.ordinal()))) return;
        Box box = hit.box();
        boolean settingRow = page == Page.SETTINGS
                && (hit.action() == Action.LYRIC || hit.action() == Action.ISLAND);
        int left = (int) Math.round(originX + box.x() * scale);
        int top = (int) Math.round(originY + box.y() * scale);
        int right = (int) Math.round(originX + (box.x() + box.w()) * scale);
        int bottom = (int) Math.round(originY + (box.y() + box.h()) * scale);
        // 设置项和歌曲行一样用浅粉整行高亮。
        int color = (hit.action() == Action.ROW || settingRow) ? 0x36F5D7E5 : 0x32FFFFFF;
        GuiPrimitives.roundedRect(graphics, left, top, right, bottom,
                settingRow ? 10 : Math.min(18, (bottom - top) / 2), color);
    }

    private double localX(double x) {
        double eased = transitionEased(System.nanoTime());
        return ((x - 10 * scale * (1 - eased)) / (0.985 + eased * 0.015) - originX) / scale;
    }
    private double localY(double y) {
        double eased = transitionEased(System.nanoTime());
        return ((y - 5 * scale * (1 - eased)) / (0.985 + eased * 0.015) - originY) / scale;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() != 0) return super.mouseClicked(event, handled);
        rebuildLayout();
        // 只有点在账号内容区才拦下，否则导航和关闭按钮还要能点。
        if (page == Page.ACCOUNT && accountRegion().contains(localX(event.x()), localY(event.y()))) {
            accountClick(localX(event.x()), localY(event.y()));
            return true;
        }
        if (page == Page.NOW && qualityMenuOpen) {
            double lx = localX(event.x()), ly = localY(event.y());
            Boolean support = state.losslessSupport(state.current().id());
            boolean losslessOk = support == null || support;
            for (int i = 0; i < NeteaseQuality.values().length; i++) {
                if (!MusicPainter.qualityRow(i).contains(lx, ly)) continue;
                if (i == 3 && !losslessOk) return true;   // 没有无损源，点了没反应
                NeteaseSession.INSTANCE.quality(NeteaseQuality.values()[i]);
                state.applyQuality();
                qualityMenuOpen = false;
                return true;
            }
            qualityMenuOpen = false;
            return true;
        }
        Hit hit = layout.hit(localX(event.x()), localY(event.y()));
        if (hit == null) { focused = false; return super.mouseClicked(event, handled); }
        if (hit.action() != Action.SEARCH_FIELD) focused = false;
        if (hit.action() == Action.SEEK || hit.action() == Action.VOLUME) {
            drag = hit.action(); updateDrag(localX(event.x())); return true;
        }
        activate(hit); return true;
    }

    private void activate(Hit hit) {
        NeteaseSession session = NeteaseSession.INSTANCE;
        switch (hit.action()) {
            case PAGE -> setPage(Page.values()[hit.index()]);
            case ACCOUNT -> setPage(Page.ACCOUNT);
            case CLOSE -> onClose();
            case PREVIOUS -> state.previous(); case PLAY -> state.togglePlaying(); case NEXT -> state.next();
            case SEARCH_FIELD -> focused = true; case SEARCH -> submit();
            case ROW -> state.select((page == Page.NOW ? upcoming : rows).get(hit.index()));
            case ADD -> { Track t = rows.get(hit.index()); if (page == Page.QUEUE) state.removeFromQueue(t); else state.enqueue(t); }
            case CLEAR -> state.clearQueue();
            case REFRESH -> { if (page == Page.FAVORITES) state.loadFavorites(); else state.refreshLibrary(); }
            case QUALITY -> qualityMenuOpen = !qualityMenuOpen;
            case LYRIC -> session.lyricMode(session.lyricMode().next());
            case ISLAND -> state.toggleIsland(); case STOP -> state.stopAll();
            case FAVORITE -> state.toggleFavoriteCurrent();
            case MODE_CYCLE -> state.playbackMode(nextMode());
            case PLAYBACK_MODE -> state.playbackMode(PlaybackMode.values()[hit.index()]);
            default -> { }
        }
        painted = null;
    }

    /** 播放页只有一个模式按钮，点击在三种循环策略间轮换。 */
    private static PlaybackMode nextMode() {
        PlaybackMode[] all = PlaybackMode.values();
        return all[(CloudXMusicClient.STATE.playbackMode().ordinal() + 1) % all.length];
    }

    private void setPage(Page next) {
        if (next != page) pageTransitionAt = System.nanoTime();
        page = next; scroll = 0; seekPreview = -1; drag = null; focused = page == Page.SEARCH;
        if (focused && input.isBlank()) input = state.searchKeyword();
        if (next == Page.FAVORITES) state.ensureFavoritesLoaded();
        if (next == Page.DISCOVER) state.refreshLibraryIfNeeded();
        rebuildLayout(); painted = null;
    }

    private void updateDrag(double x) {
        if (drag == Action.VOLUME) {
            Box b = layout.box("volume");
            float ratio = (float) Math.max(0, Math.min(1, (x - b.x()) / b.w()));
            state.volume(ratio);
            return;
        }
        Box b = layout.box("seek");
        float ratio = (float) Math.max(0, Math.min(1, (x - b.x()) / b.w()));
        seekPreview = ratio * state.current().durationSeconds();
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (drag != null) { updateDrag(localX(event.x())); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (drag != null) {
            updateDrag(localX(event.x()));
            // 拖动只更新预览，松开再跳转，避免每次鼠标移动都重启解码。
            if (drag == Action.SEEK) state.seek(seekPreview);
            drag = null; seekPreview = -1; painted = null; return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (page != Page.NOW && page != Page.SETTINGS && localX(mx) >= MAIN_X && localX(mx) <= 1050
                && localY(my) >= ROW_TOP && localY(my) < 620 && sy != 0) {
            scroll += sy > 0 ? -1 : 1; rebuildLayout(); painted = null; return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (page == Page.ACCOUNT && accountFocus >= 0) {
            if (event.isEscape()) { accountFocus = -1; accountSelectAll = false; return true; }
            if (event.key() == GLFW.GLFW_KEY_TAB) {
                accountFocus = nextAccountFocus(event.hasShiftDown());
                accountSelectAll = false;
                return true;
            }
            if (event.isConfirmation()) { submitAccount(accountMode - 1); return true; }
            if (event.isSelectAll()) { accountSelectAll = true; return true; }
            if (event.isPaste()) { appendAccount(minecraft.keyboardHandler.getClipboard()); return true; }
            if (event.isCopy() || event.isCut()) return true;
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                String value = accountValues[accountFocus];
                if (accountSelectAll) value = "";
                else if (!value.isEmpty()) value = value.substring(0, value.offsetByCodePoints(value.length(), -1));
                accountValues[accountFocus] = value;
                accountSelectAll = false;
                return true;
            }
        }
        if (focused) {
            if (event.isEscape()) { focused = false; selectAll = false; return true; }
            if (event.isConfirmation()) { submit(); return true; }
            if (event.isSelectAll()) { selectAll = true; return true; }
            if (event.isCopy()) { minecraft.keyboardHandler.setClipboard(input); return true; }
            if (event.isCut()) { minecraft.keyboardHandler.setClipboard(input); input = ""; return true; }
            if (event.isPaste()) { append(minecraft.keyboardHandler.getClipboard()); return true; }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (selectAll) input = "";
                else if (!input.isEmpty()) input = input.substring(0, input.offsetByCodePoints(input.length(), -1));
                selectAll = false; return true;
            }
        } else {
            if (event.key() == GLFW.GLFW_KEY_H) { onClose(); return true; }
            if (event.key() == GLFW.GLFW_KEY_SPACE) { state.togglePlaying(); return true; }
        }
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        if (page == Page.ACCOUNT && accountFocus >= 0 && event.isAllowedChatCharacter()) {
            appendAccount(event.codepointAsString());
            return true;
        }
        if (focused && event.isAllowedChatCharacter()) { append(event.codepointAsString()); return true; }
        return super.charTyped(event);
    }

    /** 账号页输入：按字段类型限制长度，Cookie 允许粘贴更长内容。 */
    private void appendAccount(String text) {
        if (text == null || text.isEmpty() || accountFocus < 0) return;
        if (accountSelectAll) { accountValues[accountFocus] = ""; accountSelectAll = false; }
        String clean = text.replaceAll("[\\p{Cntrl}]", "");
        String current = accountValues[accountFocus];
        int limit = accountFocus == 4 ? 8192 : accountFocus == 3 ? 8 : 64;
        int room = Math.max(0, limit - current.codePointCount(0, current.length()));
        if (room == 0) return;
        int end = clean.offsetByCodePoints(0, Math.min(room, clean.codePointCount(0, clean.length())));
        accountValues[accountFocus] = current + clean.substring(0, end);
    }

    /** 音质浮层里鼠标悬停到第几档。 */
    private int qualityHover(int mx, int my) {
        double lx = localX(mx), ly = localY(my);
        for (int i = 0; i < NeteaseQuality.values().length; i++)
            if (MusicPainter.qualityRow(i).contains(lx, ly)) return i;
        return -1;
    }

    /** 账号页点击：菜单选方式，表单里聚焦输入框 / 发送验证码 / 登录 / 返回。 */
    private void accountClick(double x, double y) {
        if (NeteaseSession.INSTANCE.loggedIn()) {
            if (MusicPainter.accLogoutBox().contains(x, y)) logoutAccount();
            else if (MusicPainter.accRefreshBox().contains(x, y)) refreshAccount();
            return;
        }
        if (accountMode == 0) {
            for (int i = 0; i < 4; i++) {
                if (!MusicPainter.accMenuBox(i).contains(x, y)) continue;
                if (i == 2) { accountStatus = "扫码登录暂不可用"; return; }
                accountMode = i == 3 ? 3 : i + 1;
                accountFocus = -1;
                accountStatus = "";
                return;
            }
            return;
        }
        if (MusicPainter.accBackBox().contains(x, y)) {
            accountMode = 0;
            accountFocus = -1;
            accountStatus = "";
            return;
        }
        int count = accountMode == 3 ? 1 : 2;
        for (int i = 0; i < count; i++) {
            Box box = accountMode == 2 && i == 1 ? MusicPainter.accPhoneCodeBox() : MusicPainter.accFieldBox(i);
            if (!box.contains(x, y)) continue;
            accountFocus = accountMode == 1 ? i : accountMode == 2 ? 2 + i : 4;
            accountSelectAll = false;
            return;
        }
        if (accountMode == 2 && MusicPainter.accSendBox().contains(x, y)) { sendLoginCode(); return; }
        if (accountMode == 3 && MusicPainter.accCookieHelpBox().contains(x, y)) {
            Browser.open(COOKIE_HELP_URL);
            return;
        }
        if (MusicPainter.accSubmitBox().contains(x, y)) { submitAccount(accountMode - 1); return; }
        accountFocus = -1;
    }

    /** Tab 只在当前方式的输入框之间轮转。 */
    private int nextAccountFocus(boolean back) {
        int[] order = accountMode == 1 ? new int[]{0, 1} : accountMode == 2 ? new int[]{2, 3} : new int[]{4};
        int position = 0;
        for (int i = 0; i < order.length; i++) if (order[i] == accountFocus) position = i;
        position = (position + (back ? order.length - 1 : 1)) % order.length;
        return order[position];
    }

    /** 发送登录验证码，成功后固定 60 秒倒计时。 */
    private void sendLoginCode() {
        String phone = accountValues[2].trim();
        if (phone.isEmpty()) { accountStatus = "先填手机号再发送验证码"; return; }
        if (accountCountdown > 0 || accountBusy) return;
        accountBusy = true;
        accountStatus = "正在发送验证码…";
        Async.run(() -> {
            String error = null;
            try {
                NeteaseApi.sendLoginCaptcha(phone, "86");
            } catch (Exception e) {
                error = e.getMessage() == null ? "网络异常" : e.getMessage();
            }
            String message = error;
            Async.main(() -> {
                accountBusy = false;
                if (message == null) {
                    accountCountdown = 60;
                    accountSecondAt = System.currentTimeMillis();
                    accountStatus = "";
                }
                else accountStatus = "发送失败：" + message;
            });
        });
    }

    /** route：0 账号密码，1 手机验证码，2 Cookie。 */
    private void submitAccount(int route) {
        if (accountBusy) return;
        String account = accountValues[0].trim(), password = accountValues[1].trim();
        String phone = accountValues[2].trim(), code = accountValues[3].trim();
        String cookie = accountValues[4].trim();
        if (route == 0 && (account.isEmpty() || password.isEmpty())) { accountStatus = "账号和密码都要填"; return; }
        if (route == 1 && (phone.isEmpty() || code.isEmpty())) { accountStatus = "手机号和验证码都要填"; return; }
        if (route == 2 && cookie.isEmpty()) { accountStatus = "请先粘贴 Cookie"; return; }
        accountBusy = true;
        accountStatus = "正在登录…";
        Async.run(() -> {
            String error = null;
            try {
                NeteaseApi.LoginResult result;
                if (route == 2) {
                    if (!NeteaseHttp.importCookieString(cookie)) error = "Cookie 里没有可用的登录项";
                    else {
                        result = NeteaseApi.account();
                        if (result.ok()) NeteaseSession.INSTANCE.applyLogin(result);
                        else error = result.message();
                    }
                } else {
                    result = route == 0 ? NeteaseApi.emailPasswordLogin(account, password)
                            : NeteaseApi.cellphoneCaptchaLogin(phone, code, "86");
                    if (result.ok()) NeteaseSession.INSTANCE.applyLogin(result);
                    else error = result.message();
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? "网络异常" : e.getMessage();
            }
            String message = error;
            Async.main(() -> {
                accountBusy = false;
                if (message == null) {
                    accountStatus = "登录成功";
                    accountFocus = -1;
                    accountMode = 0;
                    java.util.Arrays.fill(accountValues, "");
                } else accountStatus = "登录失败：" + message;
            });
        });
    }

    /** 静默补拉账号信息（补头像/昵称），不打扰状态行。 */
    private void syncAccountQuietly() {
        Async.run(() -> {
            try {
                NeteaseApi.LoginResult account = NeteaseApi.account();
                if (account.ok()) Async.main(() -> NeteaseSession.INSTANCE.applyLogin(account));
            } catch (Exception ignored) {
                // 拉不到就继续用占位头像，不影响其他功能
            }
        });
    }

    private void refreshAccount() {
        if (accountBusy) return;
        accountBusy = true;
        accountStatus = "正在刷新账号信息…";
        Async.run(() -> {
            String error = null;
            try {
                NeteaseApi.LoginResult account = NeteaseApi.account();
                if (account.ok()) NeteaseSession.INSTANCE.applyLogin(account);
                else error = account.message();
            } catch (Exception e) {
                error = e.getMessage() == null ? "网络异常" : e.getMessage();
            }
            String message = error;
            Async.main(() -> {
                accountBusy = false;
                accountStatus = message == null ? "账号信息已刷新" : "刷新失败：" + message;
            });
        });
    }

    private void logoutAccount() {
        NeteaseSession.INSTANCE.logout();
        accountStatus = "已退出登录";
        accountMode = 0;
        accountFocus = -1;
        avatarSynced = false;
        java.util.Arrays.fill(accountValues, "");
    }

    @Override public void tick() {
        super.tick();
        // 按墙钟每秒递减；倒计时只体现在发送按钮上，不写状态行。
        if (accountCountdown > 0 && System.currentTimeMillis() - accountSecondAt >= 1000) {
            accountSecondAt += 1000;
            accountCountdown--;
        }
    }
    private void append(String text) {
        if (selectAll) input = "";
        selectAll = false;
        String clean = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", " ");
        int room = Math.max(0, 64 - input.codePointCount(0, input.length()));
        int end = clean.offsetByCodePoints(0, Math.min(room, clean.codePointCount(0, clean.length())));
        input += clean.substring(0, end);
    }
    private void submit() {
        if (input.isBlank() || state.searchLoading()) return;
        state.search(input.trim()); scroll = 0; focused = false; selectAll = false; painted = null;
    }
    @Override public void removed() {
        surface.close(); overlay.close(); lyricSurface.close(); coverSurface.close();
        accountSurface.close(); qualitySurface.close();
        painted = null; paintedDynamic = null; paintedLyrics = null; paintedCoverKey = null; paintedAccountKey = null;
        drag = null; seekPreview = -1; super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
