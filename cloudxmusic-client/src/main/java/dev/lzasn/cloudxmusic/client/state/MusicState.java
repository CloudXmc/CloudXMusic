package dev.lzasn.cloudxmusic.client.state;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.audio.Mp3Player;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.netease.NeteaseApi;
import dev.lzasn.cloudxmusic.client.netease.NeteaseQuality;
import dev.lzasn.cloudxmusic.client.netease.NeteaseSession;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lzasn.cloudxmusic.client.util.Async;
import dev.lzasn.cloudxmusic.client.gui.FirstRunNotice;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 全局播放状态。
 *
 * <p>两种来源并存：本地演示曲（{@code Track.id() == null}）只推进一个假进度；
 * 网易云的歌走 {@link Mp3Player} 真实播放，进度、跳转、暂停都转发给它。</p>
 *
 * <p>所有网络/解码都在后台线程，回主线程才改状态（{@link Async#main}）。</p>
 */
public final class MusicState {
    /** 还没有歌时占位，保证 GUI 拿到的 current 永远非空。 */
    private static final Track NOTHING = new Track("未在播放", "去「发现音乐」或「搜索」挑一首", 0);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Mp3Player player = new Mp3Player();
    /** 发现（每日推荐）。 */
    private final List<Track> discovery = new ArrayList<>();
    /** 我喜欢的音乐。 */
    private final List<Track> favorites = new ArrayList<>();
    private final Set<String> likedIds = new HashSet<>();
    private final Set<String> favoriteChanges = new HashSet<>();
    /** 搜索结果。 */
    private final List<Track> searchResults = new ArrayList<>();
    /** 队列只由用户动作填充（点歌/加队列），不再自动塞。 */
    private final List<Track> queue = new ArrayList<>();
    private String searchKeyword = "";
    private boolean searchLoading;
    private boolean favoritesLoading;
    private boolean favoritesLoaded;
    private Track pendingFavoriteTrack;
    private boolean discoveryLoading;

    private Track current = NOTHING;
    private boolean playing;
    private long lastTickNanos = System.nanoTime();
    private String roomName = "尚未加入房间";
    private int memberCount;
    private boolean islandEnabled = true;
    private PlaybackMode playbackMode = PlaybackMode.REPEAT_LIST;
    private Lyric lyric = Lyric.EMPTY;
    private String statusLine = "";
    private int consecutiveFailures;
    // 仅添加播放交接边界；默认仍立即执行，不改变网易云登录、队列或取链业务。
    private java.util.function.Consumer<Runnable> playbackHandoff = Runnable::run;
    private final java.util.concurrent.atomic.AtomicLong playbackRequest = new java.util.concurrent.atomic.AtomicLong();

    public void playbackHandoff(java.util.function.Consumer<Runnable> handoff) {
        playbackHandoff = java.util.Objects.requireNonNull(handoff);
    }

    public java.util.concurrent.CompletableFuture<Void> pauseForExternalPlayback() {
        playbackRequest.incrementAndGet(); // 旧取链回调不能在另一音源开始后重新启动。
        playing = false;
        return player.pauseForHandoff();
    }

    public void playbackHandoffFailed(String message) {
        playbackRequest.incrementAndGet();
        playing = false;
        statusLine = message;
    }

    // ------------------------------------------------------------ 外观配置
    private boolean darkMode;
    private float panelScale = .8f;
    private float islandScale = 1f;
    private float islandX = .5f;
    private float islandY = .06f;

    // ------------------------------------------------------------ tick

    public void tick(Minecraft client) {
        lastTickNanos = System.nanoTime();
        if (!current.netease()) return;
        if (player.finished() && playing) {
            // 先吃掉 finished：取播放地址是异步的，否则同一首放完会被连续触发好几次 next()。
            player.clearFinished();
            if (playbackMode == PlaybackMode.REPEAT_ONE) select(current);
            else next();
            return;
        }
        if (!player.error().isEmpty()) {
            statusLine = "播放失败：" + player.error();
        }
    }

    // ------------------------------------------------------------ 状态

    public List<Track> library() {
        return discovery;
    }

    public List<Track> favorites() {
        return favorites;
    }

    public List<Track> searchResults() {
        return searchResults;
    }

    public String searchKeyword() {
        return searchKeyword;
    }

    public boolean searchLoading() {
        return searchLoading;
    }

    public boolean favoritesLoading() {
        return favoritesLoading;
    }

    public boolean discoveryLoading() {
        return discoveryLoading;
    }

    public List<Track> queue() {
        return queue;
    }

    public Track current() {
        return current;
    }

    public boolean hasCurrentTrack() {
        return !current.equals(NOTHING);
    }

    public boolean playing() {
        // 解析地址期间 player 还没 active，用 playing 标志兜住，避免灵动岛闪一下消失
        if (current.netease()) return playing && !player.isPaused();
        return playing;
    }

    /** 秒。以解码进度为准，没有在播就是 0。 */
    public float position() {
        return current.netease() ? player.positionMs() / 1000f : 0f;
    }

    public String positionText() {
        return Track.format((int) position());
    }

    public String roomName() {
        return roomName;
    }

    public int memberCount() {
        return memberCount;
    }

    public boolean islandEnabled() {
        return islandEnabled;
    }

    public void toggleIsland() {
        islandEnabled = !islandEnabled;
    }

    // ------------------------------------------------------------ 外观配置存取

    public boolean darkMode() { return darkMode; }
    public void toggleDarkMode() { darkMode = !darkMode; savePersisted(); }
    public float panelScale() { return panelScale; }
    public void panelScale(float v) { panelScale = Math.max(.6f, Math.min(1f, v)); savePersisted(); }
    public float islandScale() { return islandScale; }
    public void islandScale(float v) { islandScale = Math.max(.6f, Math.min(1.6f, v)); savePersisted(); }
    public float islandX() { return islandX; }
    public void islandX(float v) { islandX = Math.max(0f, Math.min(1f, v)); savePersisted(); }
    public float islandY() { return islandY; }
    public void islandY(float v) { islandY = Math.max(0f, Math.min(1f, v)); savePersisted(); }
    /** 拖动灵动岛时只改内存不写盘，松开后由 {@link #persistAppearance()} 统一落盘。 */
    public void islandPosSilent(float x, float y) {
        islandX = Math.max(0f, Math.min(1f, x));
        islandY = Math.max(0f, Math.min(1f, y));
    }
    public void persistAppearance() { savePersisted(); }

    /** 是否处于「调整灵动岛位置」模式（运行时状态，不持久化）。 */
    private boolean adjustingIslandPos;
    public boolean adjustingIslandPos() { return adjustingIslandPos; }
    public void setAdjustingIslandPos(boolean v) { adjustingIslandPos = v; }


    public PlaybackMode playbackMode() {
        return playbackMode;
    }

    public void playbackMode(PlaybackMode value) {
        if (value == null || playbackMode == value) return;
        playbackMode = value;
        savePersisted();
    }

    public boolean isFavorite(Track track) {
        return track != null && track.netease() && likedIds.contains(track.id());
    }

    public void toggleFavoriteCurrent() {
        Track track = current;
        if (!track.netease()) {
            statusLine = "本地曲目无法同步到网易云收藏";
            return;
        }
        if (!NeteaseSession.INSTANCE.loggedIn()) {
            statusLine = "请先登录网易云再收藏歌曲";
            return;
        }
        if (!favoritesLoaded || favoritesLoading) {
            pendingFavoriteTrack = track;
            if (!favoritesLoading) loadFavorites();
            statusLine = "正在确认歌曲收藏状态…";
            return;
        }
        toggleFavorite(track);
    }

    private void toggleFavorite(Track track) {
        if (!NeteaseSession.INSTANCE.loggedIn()) {
            pendingFavoriteTrack = null;
            statusLine = "请先登录网易云再收藏歌曲";
            return;
        }
        String id = track.id();
        if (!favoriteChanges.add(id)) return;
        boolean liked = !likedIds.contains(id);
        Async.run(() -> {
            String failure = "";
            try {
                NeteaseApi.setLiked(id, liked);
            } catch (Exception e) {
                failure = e.getMessage() == null ? "网络请求失败" : e.getMessage();
            }
            String error = failure;
            Async.main(() -> {
                favoriteChanges.remove(id);
                if (!error.isEmpty()) {
                    statusLine = "收藏操作失败：" + error;
                    return;
                }
                if (liked) {
                    likedIds.add(id);
                    favorites.removeIf(item -> id.equals(item.id()));
                    favorites.add(0, track);
                } else {
                    likedIds.remove(id);
                    favorites.removeIf(item -> id.equals(item.id()));
                }
                statusLine = liked ? "已加入网易云收藏" : "已取消网易云收藏";
            });
        });
    }

    public Lyric lyric() {
        return lyric;
    }

    public String statusLine() {
        return statusLine;
    }

    public void statusLine(String value) {
        statusLine = value;
    }

    public float volume() {
        return player.volume();
    }

    public void volume(float value) {
        player.setVolume(value);
    }

    public NeteaseQuality quality() {
        return NeteaseSession.INSTANCE.quality();
    }

    // ------------------------------------------------------------ 持久化

    private static Path stateFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("cloudxmusic").resolve("state.json");
    }

    /** 恢复上次的队列和当前歌曲；只恢复为暂停态，不自动播放。 */
    public void loadPersisted() {
        Path path = stateFile();
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Track> restored = new ArrayList<>();
            JsonArray array = root.has("queue") ? root.getAsJsonArray("queue") : new JsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                Track track = trackFromJson(element.getAsJsonObject());
                if (track != null && !restored.contains(track)) restored.add(track);
            }
            queue.clear();
            queue.addAll(restored);

            Track restoredCurrent = root.has("current") && root.get("current").isJsonObject()
                    ? trackFromJson(root.getAsJsonObject("current")) : null;
            if (restoredCurrent != null) {
                current = restoredCurrent;
                if (!queue.contains(current)) queue.add(current);
            } else if (!queue.isEmpty()) {
                current = queue.getFirst();
            } else {
                current = NOTHING;
            }
            if (root.has("playbackMode")) {
                try {
                    playbackMode = PlaybackMode.valueOf(root.get("playbackMode").getAsString());
                } catch (IllegalArgumentException ignored) {
                    playbackMode = PlaybackMode.REPEAT_LIST;
                }
            }
            darkMode = getBool(root, "darkMode", false);
            panelScale = getNum(root, "panelScale", .8f);
            islandScale = getNum(root, "islandScale", 1f);
            islandX = getNum(root, "islandX", .5f);
            islandY = getNum(root, "islandY", .06f);
            playing = false;
            lyric = Lyric.EMPTY;
            if (current != NOTHING) {
                statusLine = "已恢复上次队列：" + queue.size() + " 首（按播放继续）";
            }
            CloudXMusicClient.LOGGER.info("已恢复播放队列：{} 首，当前歌曲：{} - {}",
                    queue.size(), current.title(), current.artist());
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.warn("恢复播放队列失败：{}", e.getMessage());
        }
    }

    private void savePersisted() {
        JsonObject root = new JsonObject();
        if (current != NOTHING) root.add("current", trackToJson(current));
        JsonArray array = new JsonArray();
        for (Track track : queue) array.add(trackToJson(track));
        root.add("queue", array);
        root.addProperty("playbackMode", playbackMode.name());
        root.addProperty("darkMode", darkMode);
        root.addProperty("panelScale", panelScale);
        root.addProperty("islandScale", islandScale);
        root.addProperty("islandX", islandX);
        root.addProperty("islandY", islandY);
        Path path = stateFile();
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.warn("保存播放队列失败：{}", e.getMessage());
        }
    }

    private static JsonObject trackToJson(Track track) {
        JsonObject json = new JsonObject();
        json.addProperty("title", track.title());
        json.addProperty("artist", track.artist());
        json.addProperty("durationSeconds", track.durationSeconds());
        json.addProperty("id", track.id());
        json.addProperty("album", track.album());
        json.addProperty("coverUrl", track.coverUrl());
        return json;
    }

    private static Track trackFromJson(JsonObject json) {
        String title = json.has("title") ? json.get("title").getAsString() : "";
        if (title.isBlank()) return null;
        String artist = json.has("artist") ? json.get("artist").getAsString() : "";
        int duration = json.has("durationSeconds") ? json.get("durationSeconds").getAsInt() : 0;
        String id = json.has("id") && !json.get("id").isJsonNull() ? json.get("id").getAsString() : null;
        String album = json.has("album") ? json.get("album").getAsString() : "";
        String cover = json.has("coverUrl") ? json.get("coverUrl").getAsString() : "";
        return new Track(title, artist, Math.max(0, duration), id, album, cover);
    }

    private static boolean getBool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key)) return fallback;
        try { return json.get(key).getAsBoolean(); } catch (Exception ignored) { return fallback; }
    }

    private static float getNum(JsonObject json, String key, float fallback) {
        if (!json.has(key)) return fallback;
        try { return json.get(key).getAsFloat(); } catch (Exception ignored) { return fallback; }
    }

    // ------------------------------------------------------------ 控制

    public void togglePlaying() {
        if (current.netease()) {
            // needsRestart()：没在播、线程僵死、或位置几秒没推进（重开流卡在 CDN 上），
            // 这时走 resume 只会空转，必须重新取地址从当前进度重启，否则表现为「点播放没反应」。
            if (player.needsRestart()) {
                startNetease(current, (long) (position() * 1000));
                playing = true;
                return;
            }
            if (player.isPaused()) {
                long request = playbackRequest.incrementAndGet();
                playbackHandoff.accept(() -> {
                    if (request != playbackRequest.get()) return;
                    player.resume();
                    playing = true;
                });
            } else {
                player.pause();
                playing = false;
            }
            return;
        }
        playing = !playing;
    }

    public void addToQueue(Track track) {
        if (!queue.contains(track)) {
            queue.add(track);
            savePersisted();
        }
    }

    public void next() {
        if (queue.isEmpty()) {
            playing = false;
            return;
        }
        PlaybackMode mode = playbackMode == PlaybackMode.REPEAT_ONE ? PlaybackMode.REPEAT_LIST : playbackMode;
        int index = mode.nextIndex(queue, current);
        if (index >= 0) select(queue.get(index));
    }

    public void previous() {
        if (queue.isEmpty()) {
            playing = false;
            return;
        }
        int index = queue.indexOf(current);
        select(queue.get(index < 0 ? 0 : (index - 1 + queue.size()) % queue.size()));
    }

    public void select(Track track) {
        if (!queue.contains(track)) queue.add(track);
        current = track;
        playing = true;
        lyric = Lyric.EMPTY;
        savePersisted();
        if (track.netease()) {
            startNetease(track, 0);
        } else {
            player.stop();
        }
    }

    public void seek(float value) {
        if (current.netease()) player.seek((long) (Math.max(0, value) * 1000));
    }

    /** 播放网易云的歌：先取播放地址和歌词，再交给播放器。 */
    private void startNetease(Track track, long startMs) {
        long request = playbackRequest.incrementAndGet();
        statusLine = "正在解析播放地址…";
        playbackHandoff.accept(() -> {
        if (request != playbackRequest.get() || !current.equals(track)) return;
        Async.run(() -> {
            String url = null;
            String failure = "";
            try {
                url = NeteaseApi.playableUrl(track.id(), NeteaseSession.INSTANCE.quality());
                if (url == null) failure = "没有可用播放地址（版权受限/需要会员，或 CDN 拒绝）";
            } catch (Exception e) {
                failure = "取播放地址失败：" + e.getMessage();
            }
            Lyric parsed = Lyric.EMPTY;
            try {
                parsed = NeteaseApi.lyric(track.id());
            } catch (Exception ignored) {
                // 没歌词不影响播放
            }
            String finalUrl = url;
            String finalFailure = failure;
            Lyric finalLyric = parsed;
            Async.main(() -> {
                // 退出登录或切歌后，旧请求不能再启动播放。
                if (request != playbackRequest.get() || !current.equals(track)) return;
                lyric = finalLyric;
                if (finalUrl == null) {
                    consecutiveFailures++;
                    playing = false;
                    statusLine = "《" + track.title() + "》播不了：" + finalFailure;
                    if (consecutiveFailures <= 3 && queue.size() > 1) {
                        statusLine += "（自动跳过）";
                        next();
                    } else if (consecutiveFailures > 3) {
                        statusLine = "连续 " + consecutiveFailures + " 首都播不了，请检查登录状态";
                    }
                    return;
                }
                consecutiveFailures = 0;
                statusLine = "";
                player.play(finalUrl, startMs, track.durationSeconds() * 1000L);
            });
        });
        });
    }

    /** 离开世界/退出时停掉播放，别在主菜单继续放。 */
    public void stopAll() {
        playbackRequest.incrementAndGet();
        playing = false;
        consecutiveFailures = 0;
        player.stop();
        lyric = Lyric.EMPTY;
        statusLine = "";
    }

    /** 退出登录：停止播放，并清空账号相关的推荐、收藏和播放队列。 */
    public void clearAccountData() {
        stopAll();
        discovery.clear();
        favorites.clear();
        likedIds.clear();
        favoriteChanges.clear();
        pendingFavoriteTrack = null;
        queue.clear();
        current = NOTHING;
        favoritesLoaded = false;
        favoritesLoading = false;
        discoveryLoading = false;
        lyric = Lyric.EMPTY;
        statusLine = "";
        savePersisted();
    }

    /** 刷新网易云"每日推荐"作为列表来源。 */
    public void refreshLibrary() {
        if (discoveryLoading) return;
        if (!NeteaseSession.INSTANCE.loggedIn()) {
            statusLine = "还没登录网易云";
            return;
        }
        discoveryLoading = true;
        statusLine = "正在拉取每日推荐…";
        Async.run(() -> {
            try {
                List<Track> tracks = NeteaseApi.dailyRecommendation();
                Async.main(() -> {
                    if (!NeteaseSession.INSTANCE.loggedIn()) { discoveryLoading = false; return; }
                    discovery.clear();
                    discovery.addAll(tracks);
                    discoveryLoading = false;
                    long withCover = tracks.stream().filter(t -> t.coverUrl() != null && !t.coverUrl().isBlank()).count();
                    CloudXMusicClient.LOGGER.info("每日推荐已加载：{} 首，有封面 {} 首", tracks.size(), withCover);
                    statusLine = "已加载 " + tracks.size() + " 首每日推荐（点歌才播放）";
                });
            } catch (Exception e) {
                Async.main(() -> {
                    discoveryLoading = false;
                    if (!NeteaseSession.INSTANCE.loggedIn()) return;
                    statusLine = "拉取每日推荐失败：" + e.getMessage();
                });
            }
        });
    }

    /** 每次进入世界自动刷新一次；已经加载过则不重复请求。 */
    public void refreshLibraryIfNeeded() {
        if(!FirstRunNotice.accepted()) return;
        if (discovery.isEmpty() && !discoveryLoading) refreshLibrary();
    }

    /** 用歌单替换当前列表。 */
    public void loadPlaylist(String playlistId) {
        statusLine = "正在拉取歌单…";
        Async.run(() -> {
            try {
                List<Track> tracks = NeteaseApi.playlistTracks(playlistId);
                Async.main(() -> {
                    if (!NeteaseSession.INSTANCE.loggedIn()) return;
                    discovery.clear();
                    discovery.addAll(tracks);
                    statusLine = "歌单已加载：" + tracks.size() + " 首（点歌才播放）";
                });
            } catch (Exception e) {
                Async.main(() -> statusLine = "拉取歌单失败：" + e.getMessage());
            }
        });
    }

    /** 搜索：只填搜索结果列表，播放由用户点。 */
    public void search(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty() || searchLoading) return;
        searchKeyword = query;
        searchLoading = true;
        searchResults.clear();
        statusLine = "正在搜索「" + query + "」…";
        Async.run(() -> {
            try {
                List<Track> tracks = NeteaseApi.searchSongs(query, 30);
                Async.main(() -> {
                    searchResults.clear();
                    searchResults.addAll(tracks);
                    searchLoading = false;
                    statusLine = tracks.isEmpty()
                            ? "没搜到：" + query
                            : "搜索到 " + tracks.size() + " 首（点行播放）";
                });
            } catch (Exception e) {
                Async.main(() -> {
                    searchLoading = false;
                    statusLine = "搜索失败：" + e.getMessage();
                });
            }
        });
    }

    /** 搜索并直接播第一首（命令用）。 */
    public void searchAndPlay(String keyword) {
        searchKeyword = keyword;
        statusLine = "正在搜索「" + keyword + "」…";
        Async.run(() -> {
            try {
                List<Track> tracks = NeteaseApi.searchSongs(keyword, 20);
                Async.main(() -> {
                    searchResults.clear();
                    searchResults.addAll(tracks);
                    if (tracks.isEmpty()) {
                        statusLine = "没搜到：" + keyword;
                        return;
                    }
                    statusLine = "搜索到 " + tracks.size() + " 首";
                    select(tracks.getFirst());
                });
            } catch (Exception e) {
                Async.main(() -> statusLine = "搜索失败：" + e.getMessage());
            }
        });
    }

    /** 我喜欢的音乐。 */
    public void loadFavorites() {
        if (favoritesLoading) return;
        if (!NeteaseSession.INSTANCE.loggedIn()) {
            favoritesLoaded = false;
            statusLine = "还没登录，拿不到收藏";
            return;
        }
        favoritesLoading = true;
        statusLine = "正在拉取我喜欢的音乐…";
        Async.run(() -> {
            try {
                String uid = NeteaseSession.INSTANCE.uid();
                if (uid == null || uid.isBlank()) {
                    // 启动时账号信息异步恢复，收藏页可能先被点开；这里补一次账号同步。
                    NeteaseApi.LoginResult account = NeteaseApi.account();
                    if (account.ok()) {
                        NeteaseSession.INSTANCE.applyLogin(account);
                        uid = account.uid();
                    }
                }
                if (uid == null || uid.isBlank()) throw new IllegalStateException("账号信息还没恢复，请稍后再试");
                NeteaseApi.LikedTracks liked = NeteaseApi.likedTracksAndIds(uid, 200);
                List<Track> tracks = liked.tracks();
                Async.main(() -> {
                    if (!NeteaseSession.INSTANCE.loggedIn()) { favoritesLoading = false; return; }
                    favorites.clear();
                    favorites.addAll(tracks);
                    likedIds.clear();
                    likedIds.addAll(liked.ids());
                    favoritesLoading = false;
                    favoritesLoaded = true;
                    statusLine = "收藏已加载：" + tracks.size() + " 首（点歌才播放）";
                Track pending = pendingFavoriteTrack;
                pendingFavoriteTrack = null;
                if (pending != null) toggleFavorite(pending);
                });
            } catch (Exception e) {
                Async.main(() -> {
                    favoritesLoading = false;
                    favoritesLoaded = false;
                    pendingFavoriteTrack = null;
                    if (!NeteaseSession.INSTANCE.loggedIn()) return;
                    statusLine = "拉取收藏失败：" + e.getMessage();
                });
            }
        });
    }

    /** 切到收藏页时自动加载一次；不会自动播放。 */
    public void ensureFavoritesLoaded() {
        if (!favoritesLoaded && !favoritesLoading) loadFavorites();
    }

    /** 「加队列」按钮：只入队，不改变播放。 */
    public void enqueue(Track track) {
        if (!queue.contains(track)) {
            queue.add(track);
            statusLine = "已加入队列：" + track.title();
            savePersisted();
        }
    }

    /** 从队列删除单首；删除当前歌曲时切到下一首并保持暂停。 */
    public void removeFromQueue(Track track) {
        if (track == null || !queue.remove(track)) return;
        if (track.equals(current)) {
            player.stop();
            playing = false;
            lyric = Lyric.EMPTY;
            consecutiveFailures = 0;
            current = queue.isEmpty() ? NOTHING : queue.getFirst();
            statusLine = queue.isEmpty()
                    ? "队列已清空"
                    : "已移除当前歌曲，已切到：" + current.title() + "（暂停）";
        } else {
            statusLine = "已从队列移除：" + track.title();
        }
        savePersisted();
    }

    /** 一键清空队列并停止播放。 */
    public void clearQueue() {
        if (queue.isEmpty()) return;
        player.stop();
        playing = false;
        lyric = Lyric.EMPTY;
        consecutiveFailures = 0;
        queue.clear();
        current = NOTHING;
        statusLine = "队列已清空";
        savePersisted();
    }

    /** 切歌时按新音质重取地址。 */
    public void applyQuality() {
        if (current.netease() && playing) {
            startNetease(current, (long) (position() * 1000));
        }
    }
}
