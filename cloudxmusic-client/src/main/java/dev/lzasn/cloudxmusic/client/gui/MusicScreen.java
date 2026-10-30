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
import dev.lzasn.cloudxmusic.client.qqmusic.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.qqmusic.QQSearchPainter;
import dev.lzasn.cloudxmusic.client.qqmusic.QQLoginUi;
import dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration;
import dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackController;
import dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackUi;
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
    /** Cookie 登录方式里的教程地址。 */
    private static final String COOKIE_HELP_URL = "https://www.xunjinlu.fun/index.php/archives/10/";
    private static final String VERSION = FabricLoader.getInstance().getModContainer("cloudxmusic")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    private final MusicState state = CloudXMusicClient.STATE;
    private final QQSearchController search;
    private final QQSearchMetadata searchMetadata=QQMusicIntegration.createSearchMetadata();
    private final QQLoginUi qqLogin = QQMusicIntegration.createLoginUi();
    private final QQNowLyricsRenderer qqLyrics=new QQNowLyricsRenderer();
    private final QQPlaybackController qqPlayback=QQMusicIntegration.playback();
    private final QQCatalogController qqCatalog=QQMusicIntegration.createCatalog();
    private MusicPainter.PlaybackBarView paintedQqBar;
    private int lastBarArtwork=-1;
    private PlaybackMode paintedQqMode;
    private boolean paintedQqDark;
    private String paintedQqStatus="";
    private boolean overlayQq,qqVolumeDrag;
    private boolean sourceMenuOpen;
    private MusicSource displayedSource;
    private final UiTexture modalSurface = new UiTexture();
    private String paintedModalKey;
    private List<UnifiedSong> qqRows = List.of();
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
    private int scroll, settingsScroll, navScroll, originX, originY;
    /** 当前页列表总条数，配合 scroll 给右侧滚动条用。 */
    private int totalRows;
    private double scale, rasterScale;
    private String input = "";
    private boolean focused, selectAll, blurUnavailable;
    private float seekPreview = -1;
    private Action drag;
    private long lastPaint, lastOverlayPaint;
    private int lastCoverRevision;
    private int lastSettingsScroll;
    private long pageTransitionAt;
    /**
     * 切页不再缩放/位移整块面板——整窗一缩一弹看着像「跳一下」。
     * 面板本身保持不动，只有右侧内容随纹理更新，这是当前单纹理结构下最不打扰的做法。
     */
    private static final double PAGE_SCALE_FROM = 1.0, PAGE_SCALE_DELTA = 0.0;
    private static final long PAGE_TRANSITION_NANOS = 200_000_000L;
    private static final float PAGE_SHIFT_X = 0, PAGE_SHIFT_Y = 0;

    /** 打开/关闭：面板从屏幕底部滑入，接近居中位置时减速停住；关闭时加速滑出。 */
    private static final long SLIDE_NANOS = 420_000_000L;
    private long openAt;
    private long closeAt;
    private boolean closing;
    private boolean closeDone;
    /** 关闭动画进行中：HUD 据此提前展开，不用等界面彻底消失。 */
    private static volatile boolean panelClosing;

    public static boolean panelClosing() {
        return panelClosing;
    }
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

    public MusicScreen() {
        super(Component.literal("CloudXMusic · 云朵音乐盒"));
        search = new QQSearchController(Async::main, () -> painted = null,
                error -> CloudXMusicClient.LOGGER.warn("音乐搜索失败", error));
        search.seedNetease(state.searchResults(), state.searchKeyword());
        syncSource();
    }

    @Override protected void init() {
        // 同一个设计坐标变换用于绘制和命中，GUI 缩放不改变相对布局。
        // 面板缩放由设置页滑块控制（0.6~1.0），始终屏幕居中。
        FrostCanvas.setDark(state.darkMode());
        rebuildScale();
        openAt = System.nanoTime();
    }

    /** 关闭：先播下滑动画，动画结束再真正退出界面。 */
    @Override public void onClose() {
        if (qqLogin.isOpen()) { qqLogin.dismiss(); painted = null; return; }
        if (sourceMenuOpen) {sourceMenuOpen=false;return;}
        if (closing) return;
        closing = true;
        panelClosing = true;
        closeAt = System.nanoTime();
    }

    /** 0 表示面板在居中位置；打开过程中从 1 递减到 0，关闭时从 0 递增到 1。 */
    private double slideProgress(long now) {
        // 开/关共用同一条缓出曲线，只是方向相反：都是先快后慢。
        if (closing) {
            double t = Math.min(1, (now - closeAt) / (double) SLIDE_NANOS);
            return 1 - Math.pow(1 - t, 4);
        }
        if (openAt == 0) return 0;
        double t = Math.min(1, (now - openAt) / (double) SLIDE_NANOS);
        return Math.pow(1 - t, 4);
    }

    private float slideOffset(long now) {
        return (float) (slideProgress(now) * height * 1.15);
    }

    /** 重算面板缩放与居中位置（面板缩放滑块变化时调用）。 */
    private void rebuildScale() {
        scale = Math.max(.05, Math.min((width - 28.0) / WIDTH, (height - 48.0) / HEIGHT) * state.panelScale());
        originX = (int) Math.round((width - WIDTH * scale) / 2);
        originY = (int) Math.round((height - HEIGHT * scale) / 2 + 8);
        // 画布像素数按倍率平方增长，而 Java2D 路径填充是纯 CPU 光栅化。
        // 1.5 倍已接近原生清晰度（原先最高 2 倍只是超采样，代价是 4 倍像素）。
        rasterScale = Math.max(.75, Math.min(1.5, scale * minecraft.getWindow().getGuiScale()));
        painted = null; paintedDynamic = null; paintedQqBar = null; paintedLyrics = null; paintedCoverKey = null;
        paintedModalKey=null;paintedQualityKey=null;paintedAccountKey=null;
        lastPaint = 0; lastOverlayPaint = 0; rebuildLayout();
    }

    private List<Track> source() {
        if(QQMusicIntegration.isQq())return List.of();
        return switch (page) {
            case DISCOVER -> state.library(); case SEARCH -> search.isQq() ? List.of() : search.neteaseTracks();
            case FAVORITES -> state.favorites(); case QUEUE -> state.queue(); default -> List.of();
        };
    }

    private void rebuildLayout() {
        List<Track> source = source();
        qqRows=List.of();
        if(QQMusicIntegration.isQq()&&page==Page.QUEUE){
            var queue=qqPlayback.queue();scroll=Math.max(0,Math.min(scroll,Math.max(0,queue.size()-7)));
            qqRows=List.copyOf(queue.subList(scroll,Math.min(queue.size(),scroll+7)));
        }
        scroll = QQMusicIntegration.isQq() ? scroll
                : Math.max(0, Math.min(scroll, Math.max(0, source.size() - ROWS)));
        rows = QQMusicIntegration.isQq()?List.of():List.copyOf(source.subList(scroll, Math.min(source.size(), scroll + ROWS)));
        // rows 只是可见的一屏，滚动条还需要总条数才知道滑块该多长。
        totalRows = QQMusicIntegration.isQq() && page == Page.QUEUE
                ? qqPlayback.queue().size() : source.size();
        List<Track> queue = QQMusicIntegration.isQq()?List.of():state.queue();
        List<Track> next = new ArrayList<>();
        int current = queue.indexOf(state.current());
        for (int i = 1; i <= queue.size() && next.size() < 3; i++) {
            Track track = queue.get(Math.floorMod(current + i, queue.size()));
            if (!track.equals(state.current())) next.add(track);
        }
        upcoming = List.copyOf(next);
        layout = new MusicLayout(page, QQMusicIntegration.isQq()?qqRows.size():rows.size(), upcoming.size(), settingsScroll, navScroll);
    }

    /** 音源提交后只替换展示会话，不重建 Screen，不触碰另一音源队列。 */
    private void syncSource(){
        MusicSource selected=QQMusicIntegration.coordinator().source();
        if(displayedSource==selected)return;
        if(displayedSource==null)input=search.draft();
        displayedSource=selected;
        search.switchTo(selected==MusicSource.QQ?QQSearchController.Source.QQ:QQSearchController.Source.NETEASE,
                input,page==Page.SEARCH&&!search.isQq()?scroll:search.scroll());
        input=search.draft();scroll=search.isQq()?0:search.scroll();
        focused=false;selectAll=false;drag=null;seekPreview=-1;qualityMenuOpen=false;qqVolumeDrag=false;
        accountFocus=-1;accountSelectAll=false;
        if(selected==MusicSource.QQ&&page==Page.ACCOUNT)page=Page.NOW;
        qqLyrics.close();
        painted=null;paintedDynamic=null;paintedQqBar=null;paintedLyrics=null;paintedCoverKey=null;
        paintedModalKey=null;paintedQualityKey=null;paintedAccountKey=null;
    }
    private boolean sharedQqBar(){return QQMusicIntegration.isQq()&&page!=Page.SETTINGS&&page!=Page.ACCOUNT&&page!=Page.ROOM;}
    private int artworkRevision(){return CoverCache.revision()+QQMusicIntegration.artwork().revision();}
    private java.awt.image.BufferedImage artwork(String url){
        if(!FirstRunNotice.accepted())return null;
        return QQMusicIntegration.isQq()?QQMusicIntegration.artwork().image(url):CoverCache.image(url);
    }
    private MusicView snapshot(String hover) {
        NeteaseSession session=NeteaseSession.INSTANCE;
        PlaybackSnapshot playback=QQMusicIntegration.snapshot();
        List<MusicView.LyricRow> lines=new ArrayList<>();
        var lyrics=playback.lyrics();
        if(!lyrics.isEmpty()){
            // 首句尚未开始时下标为 -1，不能把 null 传给不可变列表的 indexOf。
            int active=playback.lyricIndexAt((int)(playback.position()*1000));
            int anchor=Math.max(0,active);
            for(int offset=-3;offset<=3;offset++){
                int i=anchor+offset;
                lines.add(i<0||i>=lyrics.size()?new MusicView.LyricRow("","",false):
                        new MusicView.LyricRow(lyrics.get(i).text(),playback.source()==MusicSource.QQ?QQTranslationControl.translation(lyrics.get(i),playback.lyricMode()):lyrics.get(i).translated(),i==active));
            }
        }
        boolean qq=playback.source()==MusicSource.QQ;
        String status=page==Page.SEARCH?search.view().status():playback.status();
        var route=QQMusicIntegration.coordinator().view();
        if(route.switching()||route.error())status=route.status();
        else if(qq&&(playback.loading()||playback.error()))status=playback.status();
        if(qq&&page==Page.QUEUE)status=QQMusicIntegration.queueStatus();
        var account=QQMusicIntegration.account();
        List<SongView> displayRows=qq?qqRows.stream().map(song->song.platformId().equals(playback.song().platformId())?playback.song():QQMusicIntegration.queueSong(song)).toList():rows.stream().map(SongView::from).toList();
        return new MusicView(page,playback.song(),displayRows,qq?List.of():upcoming.stream().map(SongView::from).toList(),List.copyOf(lines),
                (int)(playback.position()*10),playback.playing(),qq?account.loggedIn():session.loggedIn(),
                qq?account.nickname():session.nickname().isBlank()?"已登录":session.nickname(),input,focused,
                focused&&System.currentTimeMillis()/500%2==0,status,playback.quality(),playback.lyricMode().label,
                state.islandEnabled(),playback.playbackMode(),playback.favorite(),VERSION,hover,artworkRevision(),seekPreview,
                playback.volume(),qq?"":session.avatar(),state.darkMode(),state.panelScale(),state.islandScale(),state.islandX(),state.islandY(),CloudXMusicClient.openKeyLabel(),scroll,totalRows);
    }

    private java.awt.image.BufferedImage paintMain(MusicView still,QQPlaybackController.View qqView){
        var image=QQSearchPainter.decoratePlayback(MusicPainter.paint(still,layout,rasterScale,this::artwork,
                qqLogin.sidebarPainter(still,QQMusicIntegration.isQq()),QQMusicIntegration.isQq()&&catalogPage()?canvas->QQCatalogPainter.paint(canvas,qqCatalog.view(),this::artwork,qqView.song()):null),rasterScale,still,search.view(),qqView,searchMetadata::song,this::artwork);
        if(QQMusicIntegration.isQq()&&page==Page.QUEUE)image=QQQueuePainter.notice(image,rasterScale,QQMusicIntegration.queueStatus());
        if(QQMusicIntegration.isQq()&&page==Page.NOW)image=QQNowPainter.decorate(image,rasterScale,still,qqView);
        return image;
    }

    @Override public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        long now = System.nanoTime();
        // 动画结束后交回主线程切界面：在 render 里直接 setScreen 会让鼠标捕获与 HUD
        // 的恢复推迟到下一个渲染阶段，看起来就是「关掉后光标和灵动岛慢半拍」。
        // 遮罩此时 alpha 已经归零，多留一 tick 不会露出任何东西。
        if (closing && !closeDone && now - closeAt >= SLIDE_NANOS) {
            closeDone = true;
            if (minecraft != null) minecraft.execute(() -> minecraft.setScreen(null));
        }
        syncSource();
        syncCatalog();
        if(searchMetadata.update(search.view(),search.isQq()&&page==Page.SEARCH))painted=null;
        rebuildLayout();
        // 关闭时不再铺原版模糊：renderBlurredBackground 内部带一层固定不透明度的暗色，
        // 无法随动画渐隐，只要还在调用，遮罩就一定比面板晚走。改为只用可调 alpha 的纯色遮罩，
        // 让它和面板共用同一条 slideProgress 曲线退场。
        double slide = closing ? slideProgress(now) : 0;
        if (!blurUnavailable && minecraft.level != null && !closing) {
            try { renderBlurredBackground(graphics); } catch (RuntimeException e) { blurUnavailable = true; }
        }
        int dim = (int) Math.round(0x19 * (1 - slide));
        graphics.fill(0, 0, width, height, (dim << 24) | 0x2C2430);
        Hit hover = layout.hit(localX(mx), localY(my));
        MusicView view = snapshot("");
        // 老版本持久化的账号可能没有头像地址，静默补一次。
        if (!QQMusicIntegration.isQq() && view.loggedIn() && NeteaseSession.INSTANCE.avatar().isBlank() && !avatarSynced) {
            avatarSynced = true;
            syncAccountQuietly();
        }
        // 静态层只在页面、列表、封面等真正变化时重画；播放进度不再驱动整幅重绘。
        qqLogin.checkTerms(QQMusicIntegration.isQq());
        if(qqLogin.isOpen())sourceMenuOpen=false;
        boolean qqLoginChanged = qqLogin.changed();
        QQPlaybackController.View qqView=qqPlayback.view();
        boolean qqBar=sharedQqBar();
        search.visibleRows(qqBar?7:8);
        String qqKey=(qqBar&&qqView.song()!=null?qqView.song().platformId():"")+"|"+qqView.loading()+"|"+qqView.playing()+"|"+qqView.status()+"|"+qqView.error();
        if (!qqKey.equals(paintedQqStatus)||qqLoginChanged) {painted=null;paintedQqStatus=qqKey;}
        if(overlayQq!=qqBar){overlayQq=qqBar;paintedDynamic=null;paintedQqBar=null;}
        MusicView still = view.staticView();
        boolean scrollChanged = lastSettingsScroll != settingsScroll;
        if ((!still.equals(painted) || scrollChanged) && (painted == null || now - lastPaint >= 33_333_333L)) {
            surface.update(paintMain(still,qqView));
            painted=still;lastPaint=now;lastCoverRevision=artworkRevision();lastSettingsScroll=settingsScroll;
        } else if(artworkRevision()!=lastCoverRevision&&now-lastPaint>=33_333_333L){
            surface.update(paintMain(still,qqView));
            painted=still;lastPaint=now;lastCoverRevision=artworkRevision();lastSettingsScroll=settingsScroll;
        }
        // 动态层只有播放页的进度条与音量，尺寸很小，按进度以 10Hz 刷新。
        boolean nowPage = page == Page.NOW;
        boolean accountPage = page == Page.ACCOUNT && !QQMusicIntegration.isQq();
        if (accountPage) {
            // 账号页整块内容单独成纹理，打字/倒计时只重画这一块。
            boolean blink = accountFocus >= 0 && System.currentTimeMillis() / 500 % 2 == 0;
            String key = accountMode + "|" + String.join("\u0001", accountValues) + "|" + accountFocus + "|"
                    + accountStatus + "|" + accountCountdown + "|" + blink + "|" + view.loggedIn()
                    + "|" + view.nickname() + "|" + NeteaseSession.INSTANCE.uid()
                    + "|" + NeteaseSession.INSTANCE.avatar() + "|" + CoverCache.revision() + "|dark=" + view.darkMode();
            if (!key.equals(paintedAccountKey)) {
                accountSurface.update(MusicPainter.paintAccount(new MusicPainter.AccountView(
                        view.loggedIn(), view.nickname(), NeteaseSession.INSTANCE.uid(),
                        NeteaseSession.INSTANCE.avatar(), accountMode, accountValues,
                        accountFocus, blink, accountStatus, accountCountdown), rasterScale));
                paintedAccountKey = key;
            }
        }
        if (nowPage && !qqBar && !view.equals(paintedDynamic)
                && (paintedDynamic == null || now - lastOverlayPaint >= 33_333_333L)) {
            overlay.update(MusicPainter.paintDynamic(view, layout, rasterScale));
            paintedDynamic = view; lastOverlayPaint = now;
        }
        if(qqBar) {
            PlaybackSnapshot pb=QQMusicIntegration.snapshot();
            MusicPainter.PlaybackBarView bar=new MusicPainter.PlaybackBarView(pb.song().title(),pb.song().artist(),(int)Math.ceil(pb.duration()),
                    seekPreview>=0?seekPreview:pb.position(),pb.playing(),pb.volume(),pb.quality(),true,pb.song().vip(),pb.song().coverUrl());
            boolean styleChanged=paintedQqMode!=pb.playbackMode()||paintedQqDark!=view.darkMode()||lastBarArtwork!=artworkRevision();
            if((!bar.equals(paintedQqBar)||styleChanged)&&(paintedQqBar==null||styleChanged||now-lastOverlayPaint>=100_000_000L)) {
                overlay.update(MusicPainter.paintPlaybackBar(view,bar,rasterScale,this::artwork));paintedQqBar=bar;lastOverlayPaint=now;
                paintedQqMode=pb.playbackMode();paintedQqDark=view.darkMode();lastBarArtwork=artworkRevision();
            }
        }
        if (nowPage) {
            // 歌词层：内容变化（当前行走了）才重画，滚动动效只移动贴图。
            if (!view.lyrics().equals(paintedLyrics)) {
                lyricSurface.update(MusicPainter.paintLyrics(view,rasterScale,QQMusicIntegration.isQq()));
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
            String coverKey = view.current().coverUrl() + "#" + artworkRevision();
            if (ct < 1 || !coverKey.equals(paintedCoverKey)) {
                coverSurface.update(MusicPainter.paintCover(view, rasterScale, coverScale, this::artwork));
                paintedCoverKey = coverKey;
            }
        }
        if ((nowPage || qqBar) && qualityMenuOpen) {
            int hoverRow = qualityHover(mx, my);
            int selected = QQMusicIntegration.isQq()?QQQualityMenu.selected(qqView,qqPlayback.preferredQuality()):NeteaseSession.INSTANCE.quality().ordinal();
            String key = QQMusicIntegration.isQq()+"|"+view.darkMode()+"|"+selected + "|" + hoverRow;
            if (!key.equals(paintedQualityKey)) {
                qualitySurface.update(QQMusicIntegration.isQq()?QQQualityMenu.paint(selected,hoverRow,rasterScale):MusicPainter.paintQualityMenu(selected, hoverRow, rasterScale, neteaseQualityNames()));
                paintedQualityKey = key;
            }
        }
        int x = (int) Math.round(originX - PAD * scale), y = (int) Math.round(originY - PAD * scale);
        int drawW = (int) Math.round((WIDTH + 2 * PAD) * scale);
        int drawH = (int) Math.round((HEIGHT + 2 * PAD) * scale);
        double eased = transitionEased(now);
        float transitionScale = (float) (PAGE_SCALE_FROM + eased * PAGE_SCALE_DELTA);
        float shiftX = (float) (PAGE_SHIFT_X * scale * (1 - eased));
        float shiftY = (float) (PAGE_SHIFT_Y * scale * (1 - eased));
        // 围绕面板中心缩放：若以左上角为原点，缩小再放大时会朝右下「甩」，回弹方向是歪的。
        float ccx = (float) (originX + WIDTH * scale / 2.0);
        float ccy = (float) (originY + HEIGHT * scale / 2.0);
        // 滑入/滑出统一加在矩阵最外层，不影响面板内部任何布局。
        float slideY = slideOffset(now);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, slideY);
        graphics.pose().translate(ccx + shiftX, ccy + shiftY);
        graphics.pose().scale(transitionScale, transitionScale);
        graphics.pose().translate(-ccx, -ccy);
        surface.draw(graphics, x, y, drawW, drawH);
        if (nowPage) {
            Box cover = coverRegion();
            coverSurface.draw(graphics, (int) Math.round(originX + cover.x() * scale),
                    (int) Math.round(originY + cover.y() * scale),
                    (int) Math.round(cover.w() * scale), (int) Math.round(cover.h() * scale));
            // 歌词整体在行切换时向上滑入一行，靠裁剪把滑动过程限制在歌词区内。
            Box lyric = lyricRegion();
            double lt = lyricAnimStart == 0 ? 1 : Math.min(1, (now - lyricAnimStart) / (double) LYRIC_ANIM_NANOS);
            // 行切换时带一点回弹，滑到位后轻微下沉再停住。
            double lc1 = 1.1, lc3 = lc1 + 1, lu = lt - 1;
            double ease = 1 + lc3 * lu * lu * lu + lc1 * lu * lu;
            double lyricOffset = (1 - ease) * 48 * scale;
            int lx = (int) Math.round(ccx + (originX + lyric.x() * scale - ccx) * transitionScale + shiftX);
            int ly = (int) Math.round(ccy + (originY + lyric.y() * scale - ccy) * transitionScale + shiftY);
            int lw = (int) Math.round(lyric.w() * scale * transitionScale);
            int lh = (int) Math.round(lyric.h() * scale * transitionScale);
            graphics.enableScissor(lx, ly, lx + lw, ly + lh);
            lyricSurface.draw(graphics, (int) Math.round(originX + lyric.x() * scale),
                    (int) Math.round(originY + lyric.y() * scale + lyricOffset),
                    (int) Math.round(lyric.w() * scale), (int) Math.round(lyric.h() * scale));
            graphics.disableScissor();
            if(QQMusicIntegration.isQq()){
                graphics.pose().pushMatrix();
                try{
                    graphics.pose().translate((float)originX,(float)(originY+lyricOffset));
                    graphics.pose().scale((float)scale,(float)scale);
                    var lyricPlayback=QQMusicIntegration.snapshot();
                    qqLyrics.draw(graphics,view,rasterScale,now,lyricPlayback.lyricIndexAt((int)(lyricPlayback.position()*1000)));
                }finally{graphics.pose().popMatrix();}
            }
        }else qqLyrics.close();
        if (nowPage || qqBar) {
            Box band = qqBar?MusicPainter.playbackBarRegion():dynamicRegion(page);
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
        if ((nowPage || qqBar) && qualityMenuOpen) {
            Box qm = QQMusicIntegration.isQq()?QQQualityMenu.region()
                    :MusicPainter.qualityMenuRegion(NeteaseQuality.values().length);
            qualitySurface.draw(graphics, (int) Math.round(originX + qm.x() * scale),
                    (int) Math.round(originY + qm.y() * scale),
                    (int) Math.round(qm.w() * scale), (int) Math.round(qm.h() * scale));
        }
        if(!sourceMenuOpen&&!qqLogin.isOpen())renderHover(graphics,hover);
        // 模态层最后绘制，封面、歌词、控制栏均不能覆盖二维码或穿透点击。
        if(sourceMenuOpen||qqLogin.isOpen()){
            var route=QQMusicIntegration.coordinator().view();var account=QQMusicIntegration.account();
            String key=sourceMenuOpen+"|"+route+"|"+account+"|"+qqLoginChanged+"|"+view.darkMode();
            if(qqLoginChanged||!key.equals(paintedModalKey)){
                java.awt.image.BufferedImage modal=sourceMenuOpen?QQSourcePainter.menu(rasterScale,route,
                        NeteaseSession.INSTANCE.loggedIn()?NeteaseSession.INSTANCE.nickname():"访客模式",account.loggedIn()?account.nickname():"未登录"):
                        qqLogin.decorate(new java.awt.image.BufferedImage(drawWForRaster(),drawHForRaster(),java.awt.image.BufferedImage.TYPE_INT_ARGB),rasterScale,view,true);
                modalSurface.update(modal);paintedModalKey=key;
            }
            modalSurface.draw(graphics,x,y,drawW,drawH);
        }
        graphics.pose().popMatrix();
        super.render(graphics, mx, my, partialTick);
    }

    /**
     * 遮罩统一由 render() 按动画进度自绘，这里必须是空实现。
     *
     * <p>原版 Screen.render() 会自动调 renderBackground()，默认实现又铺一次模糊 + 暗色，
     * 那层的透明度写死在 MC 里、也不认 {@code closing}。只要它还在，面板滑走后画面依然
     * 被压着发糊——这正是「模糊层慢半拍」的来源。</p>
     */
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    private int drawWForRaster(){return (int)Math.ceil((WIDTH+2*PAD)*rasterScale);}
    private int drawHForRaster(){return (int)Math.ceil((HEIGHT+2*PAD)*rasterScale);}
    private double transitionEased(long now) {
        if (pageTransitionAt == 0) return 1;
        double t = Math.min(1, Math.max(0, (now - pageTransitionAt) / (double) PAGE_TRANSITION_NANOS));
        // Q 弹：末段冲过 1 再回落，缩放与位移都会反向弹一下（ease-out-back）。
        double c1 = 2.4, c3 = c1 + 1, u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    private void renderHover(GuiGraphics graphics, Hit hit) {
        if (hit == null) return;
        // 只保留歌曲列表行的悬停高亮；导航和底栏页签不铺遮罩。
        if (hit.action() != Action.ROW) return;
        Box box = hit.box();
        int left = (int) Math.round(originX + box.x() * scale);
        int top = (int) Math.round(originY + box.y() * scale);
        int right = (int) Math.round(originX + (box.x() + box.w()) * scale);
        int bottom = (int) Math.round(originY + (box.y() + box.h()) * scale);
        GuiPrimitives.roundedRect(graphics, left, top, right, bottom,
                Math.min(18, (bottom - top) / 2), 0x36F5D7E5);
    }

    private double localX(double x) {
        double eased = transitionEased(System.nanoTime());
        double s = PAGE_SCALE_FROM + eased * PAGE_SCALE_DELTA;
        double shift = PAGE_SHIFT_X * scale * (1 - eased);
        double ccx = originX + WIDTH * scale / 2.0;
        return ((x - shift - ccx) / s + ccx - originX) / scale;
    }
    private double localY(double y) {
        double eased = transitionEased(System.nanoTime());
        double s = PAGE_SCALE_FROM + eased * PAGE_SCALE_DELTA;
        double shift = PAGE_SHIFT_Y * scale * (1 - eased);
        double ccy = originY + HEIGHT * scale / 2.0;
        return ((y - slideOffset(System.nanoTime()) - shift - ccy) / s + ccy - originY) / scale;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean handled) {
        if (closing) return true;
        if (qqLogin.isOpen()) {
            if (event.button() == 0) qqLogin.click(localX(event.x()), localY(event.y()));
            painted = null; return true;
        }
        if (event.button() != 0) return super.mouseClicked(event, handled);
        rebuildLayout();
        syncSource();
        double x=localX(event.x()),y=localY(event.y());
        if(sourceMenuOpen){
            if(QQSourcePainter.NETEASE.contains(x,y))QQMusicIntegration.coordinator().select(MusicSource.NETEASE);
            else if(QQSourcePainter.QQ.contains(x,y))QQMusicIntegration.coordinator().select(MusicSource.QQ);
            else if(QQSourcePainter.CLOSE.contains(x,y))sourceMenuOpen=false;
            else if(QQSourcePainter.MANAGE.contains(x,y)&&!QQMusicIntegration.coordinator().view().switching()){
                sourceMenuOpen=false;if(QQMusicIntegration.isQq())qqLogin.open();else setPage(Page.ACCOUNT);
            }
            paintedModalKey=null;return true;
        }
        if(qualityMenuOpen){
            int count=QQMusicIntegration.isQq()?QQQuality.values().length:NeteaseQuality.values().length;
            for(int i=0;i<count;i++)if((QQMusicIntegration.isQq()?QQQualityMenu.row(i):MusicPainter.qualityRow(i,count)).contains(x,y)){
                if(QQMusicIntegration.isQq())QQMusicIntegration.quality(QQQuality.values()[i]);
                else{NeteaseSession.INSTANCE.quality(NeteaseQuality.values()[i]);state.applyQuality();}
                break;
            }
            qualityMenuOpen=false;painted=null;return true;
        }
        if(QQMusicIntegration.isQq()&&page==Page.NOW&&QQTranslationControl.BUTTON.contains(x,y)){
            QQMusicIntegration.toggleTranslation();painted=null;paintedLyrics=null;return true;
        }
        if(sharedQqBar()&&QQPlaybackUi.nowPlayingAt(x,y)){setPage(Page.NOW);return true;}
        if(sharedQqBar()&&MusicPainter.playbackBarRegion().contains(x,y)){
            Action action=QQPlaybackUi.actionAt(x,y);focused=false;selectAll=false;
            if(action!=null&&QQMusicIntegration.coordinator().allowsPlayback(MusicSource.QQ))switch(action){
                case PLAY->qqPlayback.toggle();case STOP->qqPlayback.stop();case PREVIOUS->qqPlayback.previous();case NEXT->qqPlayback.next();
                case PAGE->setPage(Page.QUEUE);case QUALITY->qualityMenuOpen=true;
                case MODE_CYCLE->qqPlayback.cycleMode();
                case VOLUME,SEEK->{drag=action;qqVolumeDrag=true;updateDrag(x);}
                default->{}
            }
            painted=null;return true;
        }
        if((QQMusicIntegration.coordinator().view().switching()||QQMusicIntegration.coordinator().view().error())&&x>=MAIN_X)return true;
        if(QQMusicIntegration.isQq()&&catalogPage()){
            syncCatalog();if(QQCatalogPainter.click(qqCatalog,x,y)){focused=false;painted=null;return true;}
        }
        if(page==Page.SEARCH&&QQMusicIntegration.isQq()){
            var results=search.view();int row=QQPlaybackUi.rowAt(x,y,results.scroll(),results.songs().size(),sharedQqBar());
            if(row>=0){qqPlayback.play(results.songs(),row);focused=false;selectAll=false;painted=null;return true;}
            if(QQSearchPainter.blocksQqClick(x,y)){focused=false;selectAll=false;return true;}
        }
        if (page == Page.ACCOUNT && accountRegion().contains(localX(event.x()), localY(event.y()))) {
            accountClick(localX(event.x()), localY(event.y()));
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
        if(QQMusicIntegration.isQq()){
            switch(hit.action()){
                case ROW->{if(page==Page.QUEUE&&hit.index()<qqRows.size())qqPlayback.play(qqPlayback.queue(),scroll+hit.index());painted=null;return;}
                case ADD->{if(page==Page.QUEUE)qqPlayback.remove(scroll+hit.index());painted=null;return;}
                case CLEAR->{qqPlayback.clearQueue();painted=null;return;}
                case REFRESH,FAVORITE->{return;}
                case LYRIC->{QQMusicIntegration.cycleLyrics();painted=null;return;}
                case STOP->{qqPlayback.stop();painted=null;return;}
                case PREVIOUS,PLAY,NEXT,SEEK,VOLUME,QUALITY,PLAYBACK_MODE,MODE_CYCLE->{return;}
                default->{}
            }
        }
        NeteaseSession session = NeteaseSession.INSTANCE;
        switch (hit.action()) {
            case PAGE -> setPage(Page.values()[hit.index()]);
            case ACCOUNT -> {sourceMenuOpen=true;focused=false;drag=null;seekPreview=-1;paintedModalKey=null;}
            case CLOSE -> onClose();
            case PREVIOUS -> state.previous(); case PLAY -> state.togglePlaying(); case NEXT -> state.next();
            case SEARCH_FIELD -> focused = true; case SEARCH -> submit();
            case ROW -> state.select((page == Page.NOW ? upcoming : rows).get(hit.index()));
            case ADD -> { Track t = rows.get(hit.index()); if (page == Page.QUEUE) state.removeFromQueue(t); else state.enqueue(t); }
            case CLEAR -> state.clearQueue();
            case REFRESH -> { if (page == Page.FAVORITES) state.loadFavorites(); else state.refreshLibrary(); }
            case QUALITY -> qualityMenuOpen = !qualityMenuOpen;
            case LYRIC -> session.lyricMode(session.lyricMode().next());
            case TRANSLATE -> {
                // 播放页的「译」只在双语与原文之间来回，不经过「关闭」那一档。
                dev.lzasn.cloudxmusic.client.state.LyricMode mode = session.lyricMode();
                session.lyricMode(mode == dev.lzasn.cloudxmusic.client.state.LyricMode.BILINGUAL
                        ? dev.lzasn.cloudxmusic.client.state.LyricMode.ORIGINAL
                        : dev.lzasn.cloudxmusic.client.state.LyricMode.BILINGUAL);
                paintedLyrics = null;
            }
            case ISLAND -> state.toggleIsland(); case STOP -> state.stopAll();
            case DARK_MODE -> { state.toggleDarkMode(); FrostCanvas.setDark(state.darkMode()); }
            case PANEL_SCALE -> { state.panelScale(cyclePanelScale(state.panelScale())); rebuildScale(); }
            case ISLAND_SCALE -> state.islandScale(cycleIslandScale(state.islandScale()));
            case ISLAND_POS -> startIslandPosAdjust();
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

    /** 主面板缩放在 0.6 / 0.7 / 0.8 / 0.9 / 1.0 之间循环。 */
    private static float cyclePanelScale(float current) {
        float[] steps = {0.6f, 0.7f, 0.8f, 0.9f, 1.0f};
        int idx = 0;
        for (int i = 0; i < steps.length; i++) if (current >= steps[i] - 0.01f) idx = i + 1;
        return steps[idx % steps.length];
    }

    /** 灵动岛缩放在 0.6 / 0.8 / 1.0 / 1.2 / 1.4 / 1.6 之间循环。 */
    private static float cycleIslandScale(float current) {
        float[] steps = {0.6f, 0.8f, 1.0f, 1.2f, 1.4f, 1.6f};
        int idx = 0;
        for (int i = 0; i < steps.length; i++) if (current >= steps[i] - 0.01f) idx = i + 1;
        return steps[idx % steps.length];
    }

    /** 点击「更改位置」：打开透明调整界面，由它接管鼠标并处理拖拽。 */
    private void startIslandPosAdjust() {
        state.setAdjustingIslandPos(true);
        minecraft.setScreen(new IslandPositionScreen());
    }

    private void setPage(Page next) {
        qqVolumeDrag=false;
        qualityMenuOpen=false;paintedQualityKey=null;
        paintedDynamic=null;paintedQqBar=null;paintedLyrics=null;paintedCoverKey=null;
        if (page == Page.SEARCH) search.remember(input, search.isQq() ? search.scroll() : scroll);
        if (next != page) pageTransitionAt = System.nanoTime();
        page = next; scroll = 0; settingsScroll = 0; seekPreview = -1; drag = null; focused = page == Page.SEARCH;
        if (focused) { input = search.draft(); scroll = search.isQq() ? 0 : search.scroll(); }
        if (!QQMusicIntegration.isQq() && next == Page.FAVORITES) state.ensureFavoritesLoaded();
        if (!QQMusicIntegration.isQq() && next == Page.DISCOVER) state.refreshLibraryIfNeeded();
        syncCatalog();
        rebuildLayout(); painted = null;
    }
    private boolean catalogPage(){return page==Page.DISCOVER||page==Page.FAVORITES;}
    private void syncCatalog(){
        if(qqCatalog.update(QQMusicIntegration.isQq()&&catalogPage()?page==Page.DISCOVER?QQCatalogController.Area.DISCOVER:QQCatalogController.Area.FAVORITES:QQCatalogController.Area.NONE))painted=null;
    }

    private void updateDrag(double x) {
        if (drag == Action.VOLUME) {
            Box b = qqVolumeDrag?QQPlaybackUi.volumeBox():layout.box("volume");
            float ratio = (float) Math.max(0, Math.min(1, (x - b.x()) / b.w()));
            if(qqVolumeDrag)QQMusicIntegration.volume(ratio);else state.volume(ratio);
            return;
        }
        Box b = qqVolumeDrag?QQPlaybackUi.seekBox():layout.box("seek");
        float ratio = (float) Math.max(0, Math.min(1, (x - b.x()) / b.w()));
        seekPreview = ratio * (float)QQMusicIntegration.snapshot().duration();
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (qqLogin.isOpen() || sourceMenuOpen) return true;
        if (drag != null) { updateDrag(localX(event.x())); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (qqLogin.isOpen() || sourceMenuOpen) return true;
        if (drag != null) {
            updateDrag(localX(event.x()));
            // 拖动只更新预览，松开再跳转，避免每次鼠标移动都重启解码。
            if (drag == Action.SEEK) {if(qqVolumeDrag)qqPlayback.seek(seekPreview);else state.seek(seekPreview);}
            drag = null;qqVolumeDrag=false; seekPreview = -1; painted = null; return true;
        }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if(qqLogin.isOpen()){qqLogin.scroll(localX(mx),localY(my),sy);return true;}
        if(sourceMenuOpen)return true;
        if (closing) return true;
        if(QQMusicIntegration.isQq()&&catalogPage()&&QQCatalogPainter.scroll(qqCatalog,localX(mx),localY(my),sx,sy)){painted=null;return true;}
        // 导航区滚动：项目多于可视高度时才生效，避免压到下面的账号组。
        int navMax = MusicLayout.navMaxScroll();
        if (navMax > 0 && localX(mx) < 252 && sy != 0
                && localY(my) >= MusicLayout.NAV_TOP - 8 && localY(my) <= MusicLayout.NAV_BOTTOM + 8) {
            navScroll = Math.max(0, Math.min(navMax, navScroll + (sy > 0 ? -1 : 1) * 22));
            rebuildLayout(); painted = null; return true;
        }
        if (page == Page.SETTINGS && localX(mx) >= MAIN_X && sy != 0) {
            settingsScroll = Math.max(0, Math.min(200, settingsScroll + (sy > 0 ? -1 : 1) * 28));
            rebuildLayout(); painted = null; return true;
        }
        if (page == Page.SEARCH && search.isQq() && sy != 0
                && QQSearchPainter.resultArea(localX(mx), localY(my))) {
            search.scrollQq(sy > 0 ? -1 : 1);
            painted = null; return true;
        }
        if (page != Page.NOW && page != Page.SETTINGS && localX(mx) >= MAIN_X && localX(mx) <= 1050
                && localY(my) >= ROW_TOP && localY(my) < 620 && sy != 0) {
            scroll += sy > 0 ? -1 : 1; rebuildLayout(); painted = null; return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (qqLogin.isOpen()) {
            if (event.isEscape()) { qqLogin.dismiss(); painted = null; }
            return true;
        }
        if (sourceMenuOpen){if(event.isEscape())sourceMenuOpen=false;return true;}
        if (closing) return true;
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
            // 跟随按键绑定：改成 M 就只认 M，不再写死 H。
            if (CloudXMusicClient.matchesOpenKey(event)) { onClose(); return true; }
            if (event.key() == GLFW.GLFW_KEY_SPACE) {
                if(QQMusicIntegration.isQq())qqPlayback.toggle();else if(QQMusicIntegration.neteaseActive())state.togglePlaying();return true;
            }
        }
        return super.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event) {
        if (qqLogin.isOpen() || sourceMenuOpen) return true;
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
        int count = QQMusicIntegration.isQq()?QQQuality.values().length:NeteaseQuality.values().length;
        for (int i = 0; i < count; i++)
            if ((QQMusicIntegration.isQq()?QQQualityMenu.row(i):MusicPainter.qualityRow(i, count)).contains(lx, ly)) return i;
        return -1;
    }

    /** 网易云音质档位名，档数由枚举自己决定（含无损共四档）。 */
    private static String[] neteaseQualityNames() {
        NeteaseQuality[] all = NeteaseQuality.values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) names[i] = all[i].label;
        return names;
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
        state.clearAccountData();
        accountStatus = "已退出登录";
        accountMode = 0;
        accountFocus = -1;
        avatarSynced = false;
        java.util.Arrays.fill(accountValues, "");
    }

    @Override public void tick() {
        super.tick();
        syncSource();
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
        if (page != Page.SEARCH || !search.submit(input)) return;
        scroll = 0; focused = false; selectAll = false; painted = null;
    }
    @Override public void removed() {
        qqCatalog.close();
        qqLogin.close();
        search.close();
        searchMetadata.close();
        qqLyrics.close();
        panelClosing = false;
        surface.close(); overlay.close(); lyricSurface.close(); coverSurface.close();
        accountSurface.close(); qualitySurface.close();modalSurface.close();
        painted = null; paintedDynamic = null; paintedQqBar = null; paintedLyrics = null; paintedCoverKey = null; paintedAccountKey = null;
        drag = null; seekPreview = -1; super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
