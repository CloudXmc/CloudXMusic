package dev.lzasn.cloudxmusic.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import dev.lzasn.cloudxmusic.client.state.Track;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ClickGUI 主界面：白色磨砂玻璃 + 微粉。
 *
 * <p>面板走 {@link GuiTextures}（默认纯 fill 自绘，烤 SDF 纹理是可选项），控件图标由 {@link Icons} 矢量绘制；
 * 点击依旧是"每帧算 hitbox、渲染判 hover、点击判命中"。</p>
 */
public final class MusicScreen extends Screen {
    private static final int GAP = 10;
    private static final int NAV_MIN_W = 132, NAV_MAX_W = 166;
    private static final int MOD_MIN_W = 196, MOD_MAX_W = 240;
    private static final int NAV_MIN_H = 236, NAV_MAX_H = 336;
    private static final int RADIUS = 10;

    private static final int NAV_ROWS_TOP = 36;
    private static final int NAV_ROW_H = 25;
    private static final int NAV_ROW_STEP = 27;
    private static final int NAV_MISC_DIVIDER = 175;
    private static final int NAV_SETTINGS_TOP = 190;

    private static final int MOD_HEADER = 30;
    private static final int LIST_TOP = 48;
    private static final int LIST_ROW_H = 25;
    private static final int LIST_ROW_STEP = 28;
    private static final int SEARCH_BAR_TOP = 40;
    private static final int SEARCH_BAR_H = 22;
    private static final int SEARCH_LIST_TOP = 82;
    private static final int LYRIC_TOP = 156;

    private static final String[] CATEGORIES = {"正在播放", "发现", "搜索", "收藏", "队列"};
    private static final int PAGE_DISCOVER = 1;
    private static final int PAGE_SEARCH = 2;
    private static final int PAGE_FAVORITES = 3;
    private static final int PAGE_QUEUE = 4;
    private static final int PAGE_SETTINGS = 5;
    private static final int MAX_ROWS = 12;

    /** 背景高斯模糊总开关。开界面仍掉帧就关掉。 */
    private static final boolean FROSTED_BLUR = true;
    private static final String MOD_VERSION = FabricLoader.getInstance().getModContainer(CloudXMusicClient.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    private static Identifier authorTexture;
    private static boolean authorTextureTried;

    private final MusicState state = CloudXMusicClient.STATE;

    private int navLeft, navTop, navWidth, navHeight;
    private int moduleLeft, moduleTop, moduleWidth, moduleHeight;
    private int page;
    private int listScroll;
    private int mouseXNow, mouseYNow;
    private String searchInput = "";
    private boolean searchFocused;
    private long openedAt;
    private long lastFrameNanos;
    private float deltaSeconds = 1f / 60f;
    private boolean blurUnavailable;

    private final float[] navHover = new float[6];
    private final float[] rowHover = new float[MAX_ROWS];
    private final float[] addHover = new float[MAX_ROWS];
    private float progressHover;
    private float searchHover;
    private float clearQueueHover;
    private float lyricScroll;
    private String lyricTrackKey = "";
    private float islandHover;
    private float nowPrevHover, nowPlayHover, nowNextHover;
    private float pageSlide = 1f;
    private float pressPulse;

    /** 进度条拖拽：按住后位置跟手，松开结束。 */
    private boolean draggingProgress;

    private final Map<String, Component> textCache = new HashMap<>();
    private final Map<String, String> fitCache = new HashMap<>();

    private final Hit[] navHits = new Hit[6];
    private final Hit nowPrevHit = new Hit();
    private final Hit nowPlayHit = new Hit();
    private final Hit nowNextHit = new Hit();
    private final Hit progressHit = new Hit();
    private final Hit searchFieldHit = new Hit();
    private final Hit searchSubmitHit = new Hit();
    private final Hit clearQueueHit = new Hit();
    private final Hit islandHit = new Hit();
    private final List<Hit> rowHits = new ArrayList<>(MAX_ROWS);
    private final List<Hit> rowAddHits = new ArrayList<>(MAX_ROWS);
    private final List<Track> rowTracks = new ArrayList<>(MAX_ROWS);

    public MusicScreen() {
        super(Component.translatable("key.cloudxmusic.open_gui"));
        for (int i = 0; i < navHits.length; i++) navHits[i] = new Hit();
        for (int i = 0; i < MAX_ROWS; i++) {
            rowHits.add(new Hit());
            rowAddHits.add(new Hit());
        }
    }

    // ---------------------------------------------------------------- 布局

    @Override
    protected void init() {
        navWidth = Math.max(NAV_MIN_W, Math.min(NAV_MAX_W, width / 5));
        moduleWidth = Math.max(MOD_MIN_W, Math.min(MOD_MAX_W, width / 3));
        navHeight = Math.max(NAV_MIN_H, Math.min(NAV_MAX_H, height - 40));
        int total = navWidth + GAP + moduleWidth;
        navLeft = Math.max(10, (width - total) / 2);
        navTop = Math.max(10, (height - navHeight) / 2);
        moduleLeft = navLeft + navWidth + GAP;
        moduleTop = navTop;
        moduleHeight = navHeight;
        openedAt = System.nanoTime();
        lastFrameNanos = openedAt;
    }

    private void layout(int slide) {
        int navY = navTop + slide;
        int moduleY = moduleTop + slide;

        for (int i = 0; i < 5; i++) {
            navHits[i].set(navLeft + 7, navY + NAV_ROWS_TOP + i * NAV_ROW_STEP, navWidth - 14, NAV_ROW_H);
        }
        navHits[5].set(navLeft + 7, navY + NAV_SETTINGS_TOP, navWidth - 14, 22);

        int center = moduleLeft + moduleWidth / 2;
        nowPrevHit.set(center - 46, moduleY + 123, 26, 26);
        nowPlayHit.set(center - 16, moduleY + 119, 32, 32);
        nowNextHit.set(center + 20, moduleY + 123, 26, 26);
        // 命中区比视觉高度大：视觉 2px 的进度条不可能点中，上下各放宽 4px 且不压到时间文字
        progressHit.set(moduleLeft + 12, moduleY + 95, moduleWidth - 24, 13);

        if (page == PAGE_SEARCH) {
            searchFieldHit.set(moduleLeft + 12, moduleY + SEARCH_BAR_TOP, moduleWidth - 24, SEARCH_BAR_H);
            searchSubmitHit.set(moduleLeft + moduleWidth - 51, moduleY + SEARCH_BAR_TOP + 2, 37, 18);
        } else {
            searchFieldHit.clear();
            searchSubmitHit.clear();
        }

        rowTracks.clear();
        for (Hit hit : rowHits) hit.clear();
        for (Hit hit : rowAddHits) hit.clear();

        clearQueueHit.clear();
        islandHit.clear();
        if (page == PAGE_SETTINGS) {
            islandHit.set(moduleLeft + moduleWidth - 52, moduleY + 164, 40, 22);
        } else if (listPage(page)) {
            if (page == PAGE_QUEUE && !state.queue().isEmpty()) {
                clearQueueHit.set(moduleLeft + moduleWidth - 50, moduleY + LIST_TOP - 20, 38, 16);
            }
            List<Track> tracks = listFor(page);
            int visible = visibleRows();
            int listTop = listTopFor(page);
            for (int i = 0; i < visible; i++) {
                int index = listScroll + i;
                if (index >= tracks.size()) break;
                int rowY = moduleY + listTop + i * LIST_ROW_STEP;
                rowTracks.add(tracks.get(index));
                rowHits.get(i).set(moduleLeft + 8, rowY, moduleWidth - 44, LIST_ROW_H);
                rowAddHits.get(i).set(moduleLeft + moduleWidth - 36, rowY, 28, LIST_ROW_H);
            }
        }
    }

    private int visibleRows() {
        int available = moduleHeight - listTopFor(page) - 14;
        return Math.max(1, Math.min(rowHits.size(), available / LIST_ROW_STEP));
    }

    private static int listTopFor(int page) {
        return page == PAGE_SEARCH ? SEARCH_LIST_TOP : LIST_TOP;
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        deltaSeconds = Math.min(0.05f, Math.max(0.001f, (now - lastFrameNanos) / 1_000_000_000f));
        lastFrameNanos = now;

        mouseXNow = mouseX;
        mouseYNow = mouseY;
        float open = Math.min(1f, (now - openedAt) / 150_000_000f);
        float ease = 1f - (1f - open) * (1f - open) * (1f - open);
        int slide = (int) ((1f - ease) * 8f);
        layout(slide);
        tickAnimations();

        applyFrostedBackdrop(graphics);

        GuiTextures.glass(graphics, navLeft, navTop + slide, navWidth, navHeight, RADIUS);
        GuiTextures.glass(graphics, moduleLeft, moduleTop + slide, moduleWidth, moduleHeight, RADIUS);

        renderNavigation(graphics, slide);
        renderModulePanel(graphics, slide);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void tickAnimations() {
        float dt = deltaSeconds;
        for (int i = 0; i < navHits.length; i++) {
            navHover[i] = GuiPrimitives.ease(navHover[i], navHits[i].hovered(mouseXNow, mouseYNow) ? 1f : 0f, 18f, dt);
        }
        for (int i = 0; i < MAX_ROWS; i++) {
            boolean rowOn = rowHits.get(i).hovered(mouseXNow, mouseYNow);
            boolean addOn = rowAddHits.get(i).hovered(mouseXNow, mouseYNow);
            rowHover[i] = GuiPrimitives.ease(rowHover[i], (rowOn || addOn) ? 1f : 0f, 18f, dt);
            addHover[i] = GuiPrimitives.ease(addHover[i], addOn ? 1f : 0f, 20f, dt);
        }
        progressHover = GuiPrimitives.ease(progressHover, page == 0 && progressHit.hovered(mouseXNow, mouseYNow) ? 1f : 0f, 20f, dt);
        searchHover = GuiPrimitives.ease(searchHover, searchSubmitHit.hovered(mouseXNow, mouseYNow) ? 1f : 0f, 20f, dt);
        clearQueueHover = GuiPrimitives.ease(clearQueueHover,
                clearQueueHit.hovered(mouseXNow, mouseYNow) ? 1f : 0f, 20f, dt);
        islandHover = GuiPrimitives.ease(islandHover, islandHit.hovered(mouseXNow, mouseYNow) ? 1f : 0f, 18f, dt);
        nowPrevHover = GuiPrimitives.ease(nowPrevHover, nowPrevHit.hovered(mouseXNow, mouseYNow) ? 1f : 0f, 20f, dt);
        nowPlayHover = GuiPrimitives.ease(nowPlayHover, nowPlayHit.hovered(mouseXNow, mouseYNow) ? 1f : 0f, 20f, dt);
        nowNextHover = GuiPrimitives.ease(nowNextHover, nowNextHit.hovered(mouseXNow, mouseYNow) ? 1f : 0f, 20f, dt);
        pageSlide = GuiPrimitives.ease(pageSlide, 1f, 16f, dt);
        pressPulse = GuiPrimitives.ease(pressPulse, 0f, 10f, dt);
        updateLyricScroll(dt);
    }

    private void applyFrostedBackdrop(GuiGraphics graphics) {
        // 整个界面都是自绘的，模糊理应由我们自己发起；MUI 只处理它自己的 Screen，不会重复模糊。
        if (FROSTED_BLUR && !blurUnavailable && minecraft != null && minecraft.level != null) {
            try {
                renderBlurredBackground(graphics);
            } catch (RuntimeException e) {
                blurUnavailable = true;
            }
        }
        graphics.fill(0, 0, width, height, 0x1CFFFFFF);
    }

    private void renderNavigation(GuiGraphics graphics, int slide) {
        int y0 = navTop + slide;
        graphics.drawString(font, txt("CLOUDX"), navLeft + 13, y0 + 13, Theme.ACCENT, false);
        graphics.fill(navLeft + 10, y0 + NAV_ROWS_TOP - 6, navLeft + navWidth - 10, y0 + NAV_ROWS_TOP - 5, Theme.DIVIDER);

        for (int i = 0; i < CATEGORIES.length; i++) {
            renderNavRow(graphics, navHits[i], i == page, CATEGORIES[i], navHover[i], navSymbol(i));
        }

        int miscY = y0 + NAV_MISC_DIVIDER;
        graphics.fill(navLeft + 10, miscY, navLeft + navWidth - 10, miscY + 1, Theme.DIVIDER);
        renderNavRow(graphics, navHits[5], page == PAGE_SETTINGS, "关于", navHover[5], navSymbol(5));

        int footY = navTop + navHeight - 21;
        graphics.fill(navLeft + 10, footY, navLeft + navWidth - 10, footY + 1, Theme.DIVIDER);
        Symbols.drawCentered(graphics, font, Symbols.NOTE, navLeft + 16, footY + 10, Theme.ACCENT);
        graphics.drawString(font, txt("H  开关界面"), navLeft + 30, footY + 6, Theme.MUTED, false);
    }

    private void renderNavRow(GuiGraphics graphics, Hit hit, boolean selected, String label, float hover, String icon) {
        if (selected) {
            GuiPrimitives.roundedRect(graphics, hit.x, hit.y, hit.x + hit.w, hit.y + hit.h, 6, Theme.CARD);
            GuiPrimitives.roundedRect(graphics, hit.x, hit.y + 5, hit.x + 2, hit.y + hit.h - 5, 1, Theme.ACCENT);
        } else if (hover > 0.02f) {
            GuiPrimitives.roundedRect(graphics, hit.x, hit.y, hit.x + hit.w, hit.y + hit.h, 6,
                    GuiPrimitives.alpha(Theme.ACCENT, (int) (0x17 * hover)));
        }
        int textColor = selected ? Theme.ACCENT_DEEP : GuiPrimitives.lerpColor(Theme.MUTED, Theme.TEXT, hover);
        Symbols.drawCentered(graphics, font, icon, hit.x + 15, hit.y + hit.h / 2, textColor);
        graphics.drawString(font, txt(label), hit.x + 28, hit.y + 8, textColor, false);
        if (selected) {
            Icons.play(graphics, hit.x + hit.w - 12, hit.y + hit.h / 2, 6, Theme.ACCENT);
        }
    }

    /** 导航行图标：与原版字体里确实存在的符号码点一一对应。 */
    private static String navSymbol(int index) {
        return switch (index) {
            case 0 -> Symbols.NOTE;
            case 1 -> Symbols.DISCOVER;
            case 2 -> Symbols.SEARCH;
            case 3 -> Symbols.ROOM;
            case 4 -> Symbols.QUEUE;
            default -> Symbols.INFO;
        };
    }

    private void renderModulePanel(GuiGraphics graphics, int slide) {
        int y0 = moduleTop + slide;
        String title = pageTitle();
        graphics.drawString(font, txt(title), moduleLeft + 13, y0 + 11, Theme.TEXT, false);
        boolean loggedIn = NeteaseSession.INSTANCE.loggedIn();
        String account = loggedIn
                ? (NeteaseSession.INSTANCE.nickname().isBlank() ? "已登录" : NeteaseSession.INSTANCE.nickname())
                : "未登录";
        String accountText = fit(account, Math.max(34, moduleWidth - font.width(title) - 34));
        int accountX = moduleLeft + moduleWidth - 13 - font.width(accountText);
        int titleRight = moduleLeft + 13 + font.width(title);
        if (accountX > titleRight + 10) {
            graphics.drawString(font, txt(accountText), accountX, y0 + 11,
                    loggedIn ? Theme.ACCENT_DEEP : Theme.FAINT, false);
        }
        graphics.fill(moduleLeft + 12, y0 + MOD_HEADER, moduleLeft + moduleWidth - 12, y0 + MOD_HEADER + 1, Theme.DIVIDER);

        int contentOffset = (int) ((1f - pageSlide) * 7f);
        int cy = y0 + contentOffset;

        switch (page) {
            case 0 -> renderNowPlaying(graphics, cy);
            case 1, 2, 3, 4 -> renderList(graphics, cy);
            case 5 -> renderAbout(graphics, cy);
            default -> { }
        }
    }

    private void renderNowPlaying(GuiGraphics graphics, int y0) {
        int artLeft = moduleLeft + 12;
        int artSize = 48;
        drawTrackCover(graphics, state.current(), artLeft, y0 + 42, artSize, 10);

        int textLeft = moduleLeft + 68;
        int textWidth = moduleWidth - 80;
        graphics.drawString(font, txt("NOW PLAYING"), textLeft, y0 + 44, Theme.FAINT, false);
        graphics.drawString(font, txt(fit(state.current().title(), textWidth)), textLeft, y0 + 57, Theme.TEXT, false);
        graphics.drawString(font, txt(fit(state.current().artist(), textWidth)), textLeft, y0 + 69, Theme.MUTED, false);
        String album = state.current().album();
        if (album != null && !album.isBlank()) {
            graphics.drawString(font, txt(fit(album, textWidth)), textLeft, y0 + 80, Theme.FAINT, false);
        }

        int barLeft = moduleLeft + 12;
        int barRight = moduleLeft + moduleWidth - 12;
        float barActive = draggingProgress ? 1f : progressHover;
        int barHeight = draggingProgress ? 3 : 2;
        int trackAlpha = 0x22 + (int) (0x14 * barActive);
        graphics.fill(barLeft, y0 + 100, barRight, y0 + 100 + barHeight, GuiPrimitives.alpha(0xFF8E6E7E, trackAlpha));
        int progress = (int) ((barRight - barLeft) * state.position()
                / Math.max(1, state.current().durationSeconds()));
        int filledRight = Math.min(barRight, barLeft + progress);
        graphics.fill(barLeft, y0 + 100, filledRight, y0 + 100 + barHeight,
                GuiPrimitives.lerpColor(Theme.ACCENT, Theme.ACCENT_DEEP, barActive));
        if (barActive > 0.05f) {
            Icons.circle(graphics, filledRight, y0 + 101, draggingProgress ? 5 : 4,
                    GuiPrimitives.dim(Theme.ACCENT_DEEP, barActive));
        }
        graphics.drawString(font, txt(state.positionText()), barLeft, y0 + 106, Theme.MUTED, false);
        String duration = state.current().durationText();
        graphics.drawString(font, txt(duration), barRight - font.width(duration), y0 + 106, Theme.MUTED, false);

        int center = moduleLeft + moduleWidth / 2;
        drawPrev(graphics, center - 33, y0 + 136, nowPrevHover);
        drawPlay(graphics, center, y0 + 135, Math.max(nowPlayHover, pressPulse));
        drawNext(graphics, center + 33, y0 + 136, nowNextHover);

        renderLyrics(graphics, y0);
    }

    // ---------------------------------------------------------------- 滚动歌词

    private void updateLyricScroll(float dt) {
        String key = state.current().id() == null || state.current().id().isBlank()
                ? state.current().title() + "|" + state.current().artist()
                : state.current().id();
        if (!key.equals(lyricTrackKey)) {
            lyricTrackKey = key;
            lyricScroll = 0f;
        }
        if (state.lyric().isEmpty()) return;
        int target = state.lyric().indexAt((int) (state.position() * 1000));
        if (target < 0) target = 0;
        lyricScroll = GuiPrimitives.ease(lyricScroll, target, 9f, dt);
    }

    private void renderLyrics(GuiGraphics graphics, int y0) {
        int left = moduleLeft + 8;
        int right = moduleLeft + moduleWidth - 8;
        int top = y0 + LYRIC_TOP;
        int bottom = moduleTop + moduleHeight - 8;
        if (bottom - top < 28) return;

        GuiPrimitives.roundedRect(graphics, left, top, right, bottom, 9, Theme.LYRIC_BG);

        int viewTop = top + 5;
        int viewBottom = bottom - 4;
        if (state.lyric().isEmpty()) {
            String message = "不支持滚动歌词";
            String sub = "这首歌没有时间轴歌词";
            int messageY = (viewTop + viewBottom) / 2 - 7;
            graphics.drawString(font, txt(message), left + (right - left - font.width(message)) / 2,
                    messageY, Theme.LYRIC_ACTIVE, false);
            graphics.drawString(font, txt(sub), left + (right - left - font.width(sub)) / 2,
                    messageY + 12, Theme.LYRIC_TRANSLATION, false);
            return;
        }

        graphics.enableScissor(left + 2, viewTop, right - 2, viewBottom);
        try {
            Lyric.Line[] lines = state.lyric().lines();
            int active = state.lyric().indexAt((int) (state.position() * 1000));
            if (active < 0) active = 0;
            int centerY = (viewTop + viewBottom) / 2;
            int textLeft = left + 10;
            int textRight = right - 8;
            int textWidth = textRight - textLeft;
            int step = 25;
            int from = Math.max(0, active - 3);
            int to = Math.min(lines.length - 1, active + 3);
            for (int i = from; i <= to; i++) {
                Lyric.Line line = lines[i];
                int distance = Math.abs(i - active);
                float fade = Math.max(0.38f, 1f - distance * 0.16f);
                int originalColor = i == active ? Theme.LYRIC_ACTIVE : GuiPrimitives.dim(Theme.LYRIC_TEXT, fade);
                int translationColor = i == active ? Theme.LYRIC_ACTIVE_TRANSLATION
                        : GuiPrimitives.dim(Theme.LYRIC_TRANSLATION, fade);
                int lineY = centerY + Math.round((i - lyricScroll) * step) - 5;
                if (lineY < viewTop - 12 || lineY > viewBottom - 2) continue;
                graphics.drawString(font, txt(fit(line.text(), textWidth)), textLeft, lineY, originalColor, false);
                if (line.hasTranslation()) {
                    graphics.drawString(font, txt(fit(line.translated(), textWidth)), textLeft, lineY + 11,
                            translationColor, false);
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /** 当前页对应的曲目列表。 */
    private List<Track> listFor(int page) {
        return switch (page) {
            case PAGE_DISCOVER -> state.library();
            case PAGE_SEARCH -> state.searchResults();
            case PAGE_FAVORITES -> state.favorites();
            case PAGE_QUEUE -> state.queue();
            default -> List.of();
        };
    }

    private static boolean listPage(int page) {
        return page >= PAGE_DISCOVER && page <= PAGE_QUEUE;
    }

    private String listCaption(int page) {
        return switch (page) {
            case PAGE_DISCOVER -> "每日推荐";
            case PAGE_SEARCH -> state.searchKeyword().isEmpty() ? "搜索结果" : "搜索：" + state.searchKeyword();
            case PAGE_FAVORITES -> "我喜欢的音乐";
            default -> "播放队列";
        };
    }

    private void renderList(GuiGraphics graphics, int y0) {
        int page = this.page;
        List<Track> tracks = listFor(page);
        int count = tracks.size();
        int listTop = listTopFor(page);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, count - visibleRows())));
        int prefetchEnd = Math.min(count, listScroll + visibleRows() + 8);
        if (prefetchEnd > listScroll) CoverCache.prefetch(tracks.subList(listScroll, prefetchEnd));
        if (page == PAGE_SEARCH) renderSearchBar(graphics, y0);
        graphics.drawString(font, txt(fit(listCaption(page), moduleWidth - 26)), moduleLeft + 13, y0 + listTop - 14,
                Theme.FAINT, false);
        if (page == PAGE_QUEUE && !tracks.isEmpty()) {
            int color = GuiPrimitives.lerpColor(Theme.ACCENT_SOFT, Theme.ACCENT, clearQueueHover);
            GuiPrimitives.roundedRect(graphics, clearQueueHit.x, clearQueueHit.y,
                    clearQueueHit.x + clearQueueHit.w, clearQueueHit.y + clearQueueHit.h, 5, color);
            String clear = "清空";
            graphics.drawString(font, txt(clear), clearQueueHit.x + (clearQueueHit.w - font.width(clear)) / 2,
                    clearQueueHit.y + 4, 0xFFFDF8FA, false);
        } else if (count > visibleRows()) {
            String hint = (listScroll + 1) + "-" + Math.min(count, listScroll + visibleRows()) + " / " + count;
            graphics.drawString(font, txt(hint), moduleLeft + moduleWidth - 13 - font.width(hint),
                    y0 + listTop - 14, Theme.FAINT, false);
        }

        for (int i = 0; i < rowTracks.size(); i++) {
            Hit row = rowHits.get(i);
            Hit add = rowAddHits.get(i);
            Track track = rowTracks.get(i);
            float hover = rowHover[i];
            if (hover > 0.02f) {
                GuiPrimitives.roundedRect(graphics, moduleLeft + 8, row.y, moduleLeft + moduleWidth - 8, row.y + row.h, 6,
                        GuiPrimitives.dim(Theme.CARD_HOVER, hover));
            }
            boolean current = track == state.current();
            drawTrackCover(graphics, track, row.x + 3, row.y + 4, 18, 4);
            if (current) {
                GuiPrimitives.roundedOutline(graphics, row.x + 3, row.y + 4, row.x + 21, row.y + 22, 4, Theme.ACCENT);
            }
            int trackTextWidth = Math.max(20, moduleWidth - 76);
            graphics.drawString(font, txt(fit(track.title(), trackTextWidth)), row.x + 25, row.y + 4,
                    current ? Theme.ACCENT_DEEP : Theme.TEXT, false);
            graphics.drawString(font, txt(fit(track.artist(), trackTextWidth)), row.x + 25, row.y + 14,
                    Theme.MUTED, false);
            String action = page == PAGE_QUEUE ? "×" : "+";
            graphics.drawString(font, txt(action), add.x + 9, row.y + 8,
                    GuiPrimitives.lerpColor(Theme.FAINT, Theme.ACCENT_DEEP, addHover[i]), false);
        }
        if (rowTracks.isEmpty()) {
            String empty = switch (page) {
                case PAGE_SEARCH -> state.searchLoading() ? "正在搜索…" : "输入歌名或歌手，按 Enter 搜索";
                case PAGE_FAVORITES -> state.favoritesLoading() ? "正在加载收藏…"
                        : NeteaseSession.INSTANCE.loggedIn() ? "收藏列表为空" : "尚未登录网易云";
                case PAGE_QUEUE -> "队列是空的，点列表里的 + 加进来";
                default -> state.discoveryLoading() ? "正在刷新每日推荐…" : "每日推荐还没加载";
            };
            graphics.drawString(font, txt(fit(empty, moduleWidth - 26)), moduleLeft + 13, y0 + listTop + 4,
                    Theme.FAINT, false);
        }
    }

    private void drawTrackCover(GuiGraphics graphics, Track track, int x, int y, int size, int radius) {
        Identifier cover = CoverCache.get(track.coverUrl());
        if (cover != null) {
            graphics.blit(cover, x, y, x + size, y + size, 0f, 0f, 1f, 1f);
            return;
        }
        int[] colors = {0xFF6F8187, 0xFF8A707B, 0xFF70827B, 0xFF7A748C, 0xFF8B7A6C};
        String source = track.album() == null || track.album().isBlank() ? track.title() : track.album();
        int color = colors[Math.floorMod(source.hashCode(), colors.length)];
        GuiPrimitives.roundedRect(graphics, x, y, x + size, y + size, radius, color);
        String initial = source == null || source.isBlank() ? "?" : source.substring(0, 1).toUpperCase();
        graphics.drawString(font, txt(initial), x + (size - font.width(initial)) / 2,
                y + (size - 8) / 2, 0xFFF7FAFA, false);
    }

    private void renderSearchBar(GuiGraphics graphics, int y0) {
        Hit field = searchFieldHit;
        Hit button = searchSubmitHit;
        int radius = 7;
        GuiPrimitives.roundedRect(graphics, field.x, field.y, field.x + field.w, field.y + field.h, radius,
                searchFocused ? Theme.CARD_HOVER : Theme.CARD);
        GuiPrimitives.roundedOutline(graphics, field.x, field.y, field.x + field.w, field.y + field.h, radius,
                searchFocused ? Theme.ACCENT : Theme.DIVIDER);

        int textLeft = field.x + 8;
        int textWidth = Math.max(10, button.x - textLeft - 5);
        String shown = searchInput.isEmpty() ? "搜索歌名 / 歌手" : fit(searchInput, textWidth);
        int textColor = searchInput.isEmpty() ? Theme.FAINT : Theme.TEXT;
        graphics.drawString(font, txt(shown), textLeft, field.y + 7, textColor, false);
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cursorX = textLeft + (searchInput.isEmpty() ? 0 : Math.min(textWidth, font.width(shown)));
            graphics.fill(cursorX, field.y + 5, cursorX + 1, field.y + 17, Theme.ACCENT);
        }

        int buttonColor = GuiPrimitives.lerpColor(Theme.ACCENT_SOFT, Theme.ACCENT, searchHover);
        GuiPrimitives.roundedRect(graphics, button.x, button.y, button.x + button.w, button.y + button.h, 5,
                buttonColor);
        String label = "搜索";
        graphics.drawString(font, txt(label), button.x + (button.w - font.width(label)) / 2, button.y + 5,
                Theme.ON_ACCENT, false);
    }

    private void renderAbout(GuiGraphics graphics, int y0) {
        int left = moduleLeft + 13;
        int right = moduleLeft + moduleWidth - 13;
        int avatarSize = 52;
        Identifier avatar = authorTexture();
        if (avatar != null) {
            graphics.blit(avatar, left, y0 + 44, left + avatarSize, y0 + 44 + avatarSize, 0f, 0f, 1f, 1f);
        } else {
            Icons.circle(graphics, left + avatarSize / 2f, y0 + 70, avatarSize / 2f, Theme.ACCENT_SOFT);
            String initial = "L";
            graphics.drawString(font, txt(initial), left + (avatarSize - font.width(initial)) / 2,
                    y0 + 66, Theme.ACCENT_DEEP, false);
        }

        int infoLeft = left + avatarSize + 10;
        graphics.drawString(font, txt("Lzasn"), infoLeft, y0 + 51, Theme.TEXT, false);
        graphics.drawString(font, txt("作者 / 开发者"), infoLeft, y0 + 65, Theme.MUTED, false);
        graphics.drawString(font, txt("CloudXMusic"), infoLeft, y0 + 81, Theme.ACCENT_DEEP, false);

        graphics.fill(left, y0 + 106, right, y0 + 107, Theme.DIVIDER);
        graphics.drawString(font, txt("轻量级 Minecraft 网易云音乐播放器"), left, y0 + 118, Theme.TEXT, false);
        graphics.drawString(font, txt("每日推荐 · 搜索 · 收藏 · 队列"), left, y0 + 132, Theme.MUTED, false);
        graphics.drawString(font, txt("滚动歌词 · HUD 灵动岛 · 会话持久化"), left, y0 + 146, Theme.MUTED, false);

        graphics.fill(left, y0 + 160, right, y0 + 161, Theme.DIVIDER);
        graphics.drawString(font, txt("HUD 灵动岛"), left, y0 + 171, Theme.TEXT, false);
        graphics.drawString(font, txt(state.islandEnabled() ? "播放时显示" : "已关闭"), left, y0 + 184,
                GuiPrimitives.lerpColor(Theme.MUTED, Theme.TEXT, islandHover), false);

        int toggleLeft = moduleLeft + moduleWidth - 49;
        int toggleTop = y0 + 164;
        int trackColor = state.islandEnabled()
                ? GuiPrimitives.lerpColor(Theme.ACCENT, Theme.ACCENT_DEEP, islandHover)
                : GuiPrimitives.lerpColor(0x338E6E7E, 0x558E6E7E, islandHover);
        GuiPrimitives.roundedRect(graphics, toggleLeft, toggleTop, toggleLeft + 34, toggleTop + 18, 9, trackColor);
        int knobLeft = state.islandEnabled() ? toggleLeft + 19 : toggleLeft + 3;
        Icons.circle(graphics, knobLeft + 6.5, toggleTop + 9, 6, 0xFFFFFFFF);

        graphics.drawString(font, txt("CloudXMusic " + MOD_VERSION), left, y0 + 205, Theme.FAINT, false);
        graphics.drawString(font, txt("Minecraft 1.21.11 · Fabric"), left, y0 + 218, Theme.FAINT, false);
    }

    private static Identifier authorTexture() {
        if (authorTextureTried) return authorTexture;
        authorTextureTried = true;
        try (InputStream stream = MusicScreen.class.getResourceAsStream(
                "/assets/cloudxmusic/textures/gui/author.png")) {
            if (stream == null) return null;
            NativeImage image = NativeImage.read(stream);
            DynamicTexture dynamic = null;
            try {
                authorTexture = Identifier.fromNamespaceAndPath(CloudXMusicClient.MOD_ID, "gui/author_avatar");
                dynamic = new DynamicTexture(() -> "cloudxmusic author avatar", image);
                Minecraft.getInstance().getTextureManager().register(authorTexture, dynamic);
            } catch (Exception e) {
                if (dynamic != null) dynamic.close(); else image.close();
                CloudXMusicClient.LOGGER.warn("作者头像加载失败：{}", e.getMessage());
            }
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.warn("作者头像读取失败：{}", e.getMessage());
        }
        return authorTexture;
    }

    // ---------------------------------------------------------------- 控件

    private void drawPlay(GuiGraphics graphics, int cx, int cy, float hover) {
        int shrink = (int) (pressPulse * 2f);
        Icons.circle(graphics, cx, cy, 15 - shrink,
                GuiPrimitives.lerpColor(Theme.ACCENT, Theme.ACCENT_DEEP, Math.max(hover, pressPulse) * 0.9f));
        Symbols.drawCentered(graphics, font, state.playing() ? Symbols.PAUSE : Symbols.PLAY, cx, cy, Theme.ON_ACCENT);
    }

    private void drawPrev(GuiGraphics graphics, int cx, int cy, float hover) {
        if (hover > 0.02f) {
            Icons.circle(graphics, cx, cy, 12, GuiPrimitives.alpha(Theme.ACCENT, (int) (0x1C * hover)));
        }
        Symbols.drawCentered(graphics, font, Symbols.PREV, cx, cy, GuiPrimitives.lerpColor(Theme.MUTED, Theme.ACCENT_DEEP, hover));
    }

    private void drawNext(GuiGraphics graphics, int cx, int cy, float hover) {
        if (hover > 0.02f) {
            Icons.circle(graphics, cx, cy, 12, GuiPrimitives.alpha(Theme.ACCENT, (int) (0x1C * hover)));
        }
        Symbols.drawCentered(graphics, font, Symbols.NEXT, cx, cy, GuiPrimitives.lerpColor(Theme.MUTED, Theme.ACCENT_DEEP, hover));
    }

    // ---------------------------------------------------------------- 点击

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (page == PAGE_SEARCH && searchFocused) {
            if (event.isEscape()) {
                searchFocused = false;
                return true;
            }
            if (event.isConfirmation()) {
                submitSearch();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchInput.isEmpty()) {
                    searchInput = searchInput.substring(0, searchInput.offsetByCodePoints(searchInput.length(), -1));
                }
                return true;
            }
            if (event.isPaste()) {
                appendSearch(minecraft == null ? "" : minecraft.keyboardHandler.getClipboard());
                return true;
            }
            if (event.isCut()) {
                if (minecraft != null) minecraft.keyboardHandler.setClipboard(searchInput);
                searchInput = "";
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (page == PAGE_SEARCH && searchFocused && event.isAllowedChatCharacter()) {
            appendSearch(event.codepointAsString());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (event.button() == 0) {
            // 进度条：按下即跳转，并进入拖拽态
            if (page == 0 && progressHit.test(event.x(), event.y())) {
                draggingProgress = true;
                seekTo(event.x());
                return true;
            }
            if (dispatchClick(event.x(), event.y())) {
                return true;
            }
        }
        return super.mouseClicked(event, handled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingProgress) {
            // 拖到面板外也继续跟手，只夹在进度条两端之间
            seekTo(event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingProgress) {
            seekTo(event.x());
            draggingProgress = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void seekTo(double mouseX) {
        int barLeft = moduleLeft + 12;
        int barRight = moduleLeft + moduleWidth - 12;
        double ratio = Math.max(0, Math.min(1, (mouseX - barLeft) / (double) (barRight - barLeft)));
        state.seek((float) (ratio * Math.max(1, state.current().durationSeconds())));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (listPage(page) && scrollY != 0) {
            int count = listFor(page).size();
            int maxScroll = Math.max(0, count - visibleRows());
            int next = listScroll + (scrollY > 0 ? -1 : 1);
            listScroll = Math.max(0, Math.min(maxScroll, next));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean dispatchClick(double mx, double my) {
        for (int i = 0; i < navHits.length; i++) {
            if (navHits[i].test(mx, my)) {
                setPage(i);
                return true;
            }
        }
        if (nowPrevHit.test(mx, my)) {
            state.previous();
            pressPulse = 1f;
            return true;
        }
        if (nowPlayHit.test(mx, my)) {
            state.togglePlaying();
            pressPulse = 1f;
            return true;
        }
        if (nowNextHit.test(mx, my)) {
            state.next();
            pressPulse = 1f;
            return true;
        }
        if (page == 0 && progressHit.test(mx, my)) {
            // 拖拽由 mouseClicked / mouseDragged 直接处理，这里不再重复
            return true;
        }
        if (page == PAGE_SEARCH && searchSubmitHit.test(mx, my)) {
            submitSearch();
            return true;
        }
        if (page == PAGE_QUEUE && clearQueueHit.test(mx, my)) {
            state.clearQueue();
            return true;
        }
        if (page == PAGE_SEARCH && searchFieldHit.test(mx, my)) {
            searchFocused = true;
            return true;
        }
        if (page == PAGE_SETTINGS && islandHit.test(mx, my)) {
            state.toggleIsland();
            return true;
        }
        for (int i = 0; i < rowTracks.size(); i++) {
            Track track = rowTracks.get(i);
            if (rowHits.get(i).test(mx, my)) {
                state.select(track);
                return true;
            }
            if (rowAddHits.get(i).test(mx, my)) {
                if (page == PAGE_QUEUE) state.removeFromQueue(track);
                else state.enqueue(track);
                return true;
            }
        }
        return false;
    }

    private void setPage(int next) {
        if (page != next) {
            page = next;
            listScroll = 0;
            pageSlide = 0f;
        }
        if (next == PAGE_SEARCH) {
            if (searchInput.isBlank() && !state.searchKeyword().isBlank()) searchInput = state.searchKeyword();
            searchFocused = true;
        } else {
            searchFocused = false;
        }
        if (next == PAGE_FAVORITES) {
            state.ensureFavoritesLoaded();
        }
    }

    private void submitSearch() {
        String query = searchInput.trim();
        if (query.isEmpty() || state.searchLoading()) return;
        listScroll = 0;
        searchFocused = false;
        state.search(query);
    }

    private void appendSearch(String text) {
        if (text == null || text.isEmpty()) return;
        String cleaned = text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        int room = 64 - searchInput.length();
        if (room <= 0) return;
        searchInput += cleaned.length() > room ? cleaned.substring(0, room) : cleaned;
    }

    // ---------------------------------------------------------------- 工具

    private Component txt(String text) {
        Component cached = textCache.get(text);
        if (cached != null) return cached;
        if (textCache.size() > 512) textCache.clear();
        Component created = Component.literal(text);
        textCache.put(text, created);
        return created;
    }

    private String fit(String text, int maxWidth) {
        String key = maxWidth + "\u0000" + text;
        String cached = fitCache.get(key);
        if (cached != null) return cached;
        String result;
        if (maxWidth <= 0) {
            result = "";
        } else if (font.width(text) <= maxWidth) {
            result = text;
        } else {
            int ellipsis = font.width("…");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (font.width(sb.toString() + c) + ellipsis > maxWidth) break;
                sb.append(c);
            }
            result = sb + "…";
        }
        if (fitCache.size() > 512) fitCache.clear();
        fitCache.put(key, result);
        return result;
    }

    private String pageTitle() {
        return page == PAGE_SETTINGS ? "关于" : CATEGORIES[Math.max(0, Math.min(page, CATEGORIES.length - 1))];
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final class Hit {
        int x, y, w, h;
        boolean active;

        Hit set(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.active = w > 0 && h > 0;
            return this;
        }

        void clear() {
            active = false;
            w = 0;
            h = 0;
        }

        boolean test(double mx, double my) {
            return active && mx >= x && mx < x + w && my >= y && my < y + h;
        }

        boolean hovered(int mx, int my) {
            return test(mx, my);
        }
    }
}
