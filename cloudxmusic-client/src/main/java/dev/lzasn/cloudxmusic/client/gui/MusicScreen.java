package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import dev.lzasn.cloudxmusic.client.state.Track;
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
    private static final String VERSION = FabricLoader.getInstance().getModContainer("cloudxmusic")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    private final MusicState state = CloudXMusicClient.STATE;
    private final UiTexture surface = new UiTexture();
    private Page page = Page.NOW;
    private int scroll, originX, originY;
    private double scale, rasterScale;
    private String input = "";
    private boolean focused, selectAll, blurUnavailable;
    private float seekPreview = -1;
    private Action drag;
    private long lastPaint;
    private long pageTransitionAt;
    private MusicView painted;
    private MusicLayout layout;
    private List<Track> rows = List.of(), upcoming = List.of();

    public MusicScreen() { super(Component.literal("CloudXmusic · 云朵音乐盒")); }

    @Override protected void init() {
        // 同一个设计坐标变换用于绘制和命中，GUI 缩放不改变相对布局。
        scale = Math.max(.05, Math.min((width - 28.0) / WIDTH, (height - 48.0) / HEIGHT));
        originX = (int) Math.round((width - WIDTH * scale) / 2);
        originY = (int) Math.round((height - HEIGHT * scale) / 2 + 8);
        rasterScale = Math.max(.75, Math.min(2, scale * minecraft.getWindow().getGuiScale()));
        painted = null; lastPaint = 0; rebuildLayout();
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
            for (int i = Math.max(0, active - 1); i < Math.min(all.length, active + 2); i++)
                lines.add(new MusicView.LyricRow(all[i].text(), all[i].translated(), i == active));
        }
        String status = state.statusLine();
        if (status.isBlank() && source().size() > ROWS) status = (scroll + 1) + "–" + (scroll + rows.size()) + " / " + source().size();
        return new MusicView(page, state.current(), rows, upcoming, List.copyOf(lines), (int) (state.position() * 10),
                state.playing(), session.loggedIn(), session.nickname().isBlank() ? "已登录" : session.nickname(),
                input, focused, focused && System.currentTimeMillis() / 500 % 2 == 0, status, session.quality().label,
                session.lyricMode().label, state.islandEnabled(), state.playbackMode(),
                state.isFavorite(state.current()), VERSION, hover,
                CoverCache.revision(), seekPreview, state.volume());
    }

    @Override public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        rebuildLayout();
        if (!blurUnavailable && minecraft.level != null) {
            try { renderBlurredBackground(graphics); } catch (RuntimeException e) { blurUnavailable = true; }
        }
        graphics.fill(0, 0, width, height, 0x192C2430);
        Hit hover = layout.hit(localX(mx), localY(my));
        MusicView view = snapshot("");
        long now = System.nanoTime();
        if (!view.equals(painted) && (painted == null || now - lastPaint >= 33_333_333L)) {
            surface.update(MusicPainter.paint(view, layout, rasterScale, CoverCache::image));
            painted = view; lastPaint = now;
        }
        int x = (int) Math.round(originX - PAD * scale), y = (int) Math.round(originY - PAD * scale);
        double eased = transitionEased(now);
        float transitionScale = (float) (0.985 + eased * 0.015);
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) (10 * scale * (1 - eased)), (float) (5 * scale * (1 - eased)));
        graphics.pose().scale(transitionScale, transitionScale);
        surface.draw(graphics, x, y, (int) Math.round((WIDTH + 2 * PAD) * scale), (int) Math.round((HEIGHT + 2 * PAD) * scale));
        renderHover(graphics, hover);
        graphics.pose().popMatrix();
        super.render(graphics, mx, my, partialTick);
    }

    private double transitionEased(long now) {
        if (pageTransitionAt == 0) return 1;
        double t = Math.min(1, Math.max(0, (now - pageTransitionAt) / 220_000_000.0));
        return 1 - Math.pow(1 - t, 3);
    }

    private double transitionScale() {
        return 0.985 + transitionEased(System.nanoTime()) * 0.015;
    }

    private void renderHover(GuiGraphics graphics, Hit hit) {
        if (hit == null || (hit.action() == Action.PAGE
                && hit.index() == (page == Page.SETTINGS ? 5 : page.ordinal()))) return;
        Box box = hit.box();
        int left = (int) Math.round(originX + box.x() * scale);
        int top = (int) Math.round(originY + box.y() * scale);
        int right = (int) Math.round(originX + (box.x() + box.w()) * scale);
        int bottom = (int) Math.round(originY + (box.y() + box.h()) * scale);
        int color = hit.action() == Action.ROW ? 0x36F5D7E5 : 0x32FFFFFF;
        GuiPrimitives.roundedRect(graphics, left, top, right, bottom,
                Math.min(18, (bottom - top) / 2), color);
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
            case ACCOUNT -> { if (session.loggedIn()) setPage(Page.SETTINGS); else minecraft.setScreen(new QrLoginScreen(this)); }
            case CLOSE -> onClose();
            case PREVIOUS -> state.previous(); case PLAY -> state.togglePlaying(); case NEXT -> state.next();
            case SEARCH_FIELD -> focused = true; case SEARCH -> submit();
            case ROW -> state.select((page == Page.NOW ? upcoming : rows).get(hit.index()));
            case ADD -> { Track t = rows.get(hit.index()); if (page == Page.QUEUE) state.removeFromQueue(t); else state.enqueue(t); }
            case CLEAR -> state.clearQueue();
            case REFRESH -> { if (page == Page.FAVORITES) state.loadFavorites(); else state.refreshLibrary(); }
            case QUALITY -> { session.quality(session.quality().next()); state.applyQuality(); }
            case LYRIC -> session.lyricMode(session.lyricMode().next());
            case ISLAND -> state.toggleIsland(); case STOP -> state.stopAll();
            case FAVORITE -> state.toggleFavoriteCurrent();
            case PLAYBACK_MODE -> state.playbackMode(PlaybackMode.values()[hit.index()]);
            default -> { }
        }
        painted = null;
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
        Box b = layout.box(drag == Action.VOLUME ? "volume" : "seek");
        float ratio = (float) Math.max(0, Math.min(1, (x - b.x()) / b.w()));
        if (drag == Action.VOLUME) state.volume(ratio); else seekPreview = ratio * state.current().durationSeconds();
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
                && localY(my) >= ROW_TOP && localY(my) < 562 && sy != 0) {
            scroll += sy > 0 ? -1 : 1; rebuildLayout(); painted = null; return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
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
        if (focused && event.isAllowedChatCharacter()) { append(event.codepointAsString()); return true; }
        return super.charTyped(event);
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
        surface.close(); painted = null; drag = null; seekPreview = -1; super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
