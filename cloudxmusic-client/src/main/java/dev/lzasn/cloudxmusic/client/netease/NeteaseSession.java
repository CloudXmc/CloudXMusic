package dev.lzasn.cloudxmusic.client.netease;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import dev.lzasn.cloudxmusic.client.util.Async;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 账号 / 偏好设置的持久化。
 *
 * <p>只存 {@code MUSIC_U} Cookie 和昵称头像这些非敏感信息，密码永远不落盘。
 * 文件在 {@code config/cloudxmusic/netease.json}，删掉就等于退出登录。</p>
 */
public final class NeteaseSession {
    public static final NeteaseSession INSTANCE = new NeteaseSession();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private boolean loggedIn;
    private String uid = "";
    private String nickname = "";
    private String avatar = "";
    private String quality = NeteaseQuality.EXHIGH.apiName;
    private String lyricMode = LyricMode.BILINGUAL.name();
    /** 播放页滚动歌词是否显示译文；与上面的灵动岛歌词模式互相独立。 */
    private boolean playbackTranslation = true;

    private NeteaseSession() {}

    /**
     * 网易云的配置统一放在 {@code cloudxmusic/netease/} 下，与 {@code qqmusic/} 对称。
     *
     * <p>老版本把文件直接放在 {@code cloudxmusic/} 根目录，这里做一次性搬迁：
     * 新文件不存在而老文件存在时才移动，失败则继续用老路径，保证升级后不掉登录态。</p>
     */
    private static Path file() {
        Path base = FabricLoader.getInstance().getConfigDir().resolve("cloudxmusic");
        Path target = base.resolve("netease").resolve("netease.json");
        Path legacy = base.resolve("netease.json");
        if (!Files.exists(target) && Files.isRegularFile(legacy)) {
            try {
                Files.createDirectories(target.getParent());
                Files.move(legacy, target);
                CloudXMusicClient.LOGGER.info("已把 netease.json 迁移到 netease/ 子目录");
            } catch (java.io.IOException e) {
                CloudXMusicClient.LOGGER.warn("迁移 netease.json 失败，继续使用旧路径", e);
                return legacy;
            }
        }
        return target;
    }

    public void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) return;
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            loggedIn = json.has("loggedIn") && json.get("loggedIn").getAsBoolean();
            uid = string(json, "uid", "");
            nickname = string(json, "nickname", "");
            avatar = string(json, "avatar", "");
            quality = string(json, "quality", quality);
            lyricMode = string(json, "lyricMode", lyricMode);
            playbackTranslation = !json.has("playbackTranslation")
                    || json.get("playbackTranslation").getAsBoolean();

            // 0.3.4 及以前把 MUSIC_U 直接写在 netease.json 的 cookie 字段里。
            // 0.3.5 改用 cookies.json 后必须迁移，否则升级后会出现“显示已登录但没有 Cookie”。
            String legacyCookie = string(json, "cookie", "");
            if (!NeteaseHttp.hasSession() && !legacyCookie.isBlank()) {
                if (NeteaseHttp.importCookieString(legacyCookie)) {
                    CloudXMusicClient.LOGGER.info("已迁移旧版网易云登录 Cookie 到 cookies.json");
                    // save() 不再写 cookie 字段，同时清除旧文件里的敏感副本。
                    save();
                }
            }
            boolean hasSession = NeteaseHttp.hasSession();
            if (loggedIn != hasSession) {
                loggedIn = hasSession;
                if (hasSession) {
                    CloudXMusicClient.LOGGER.info("检测到有效 MUSIC_U，已恢复登录标记");
                } else {
                    CloudXMusicClient.LOGGER.warn("登录文件标记为已登录，但本地没有 MUSIC_U，已重置为未登录");
                }
                save();
            }
            CloudXMusicClient.LOGGER.info("读取登录态：loggedIn={} 有 MUSIC_U={}", loggedIn, hasSession);
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.warn("读取网易云登录态失败：{}", e.getMessage());
        }
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("loggedIn", loggedIn);
        json.addProperty("uid", uid);
        json.addProperty("nickname", nickname);
        json.addProperty("avatar", avatar);
        json.addProperty("quality", quality);
        json.addProperty("lyricMode", lyricMode);
        json.addProperty("playbackTranslation", playbackTranslation);
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(json), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.warn("保存网易云登录态失败：{}", e.getMessage());
        }
    }

    /** 只记 cookie 当已登录（账号信息稍后再补）。 */
    public void markLoggedIn(String name) {
        loggedIn = true;
        if (name != null && !name.isBlank()) nickname = name;
        save();
    }

    public void applyLogin(NeteaseApi.LoginResult result) {
        // 失败的新登录不能破坏已经存在的 MUSIC_U；之前的实现会把 loggedIn/uid/nickname 清空，
        // 导致下次启动虽然 Cookie 还在，却被误判为未登录。
        if (!result.ok()) {
            if (NeteaseHttp.hasSession()) {
                if (!loggedIn) {
                    loggedIn = true;
                    save();
                }
                CloudXMusicClient.LOGGER.warn("登录失败，但本地已有 MUSIC_U，保留现有登录态");
            } else {
                loggedIn = false;
                save();
            }
            return;
        }

        // 登录接口有时只把 Cookie 放在响应 JSON，显式导入后才能拿到受限歌曲直链。
        NeteaseHttp.importCookieString(result.cookie());
        if (!NeteaseHttp.hasSession()) {
            loggedIn = false;
            save();
            return;
        }
        loggedIn = true;
        if (result.uid() != null && !result.uid().isBlank()) uid = result.uid();
        if (result.nickname() != null && !result.nickname().isBlank()) nickname = result.nickname();
        if (result.avatarUrl() != null && !result.avatarUrl().isBlank()) avatar = result.avatarUrl();
        save();
    }

    /** 启动时用已有 Cookie 恢复账号信息；失败不会清掉现有登录态。 */
    public void refreshAccountAsync() {
        if (!NeteaseHttp.hasSession()) return;
        Async.run(() -> {
            try {
                NeteaseApi.LoginResult result = NeteaseApi.account();
                if (result.ok()) {
                    Async.main(() -> applyLogin(result));
                } else {
                    CloudXMusicClient.LOGGER.info("现有 Cookie 暂未通过账号校验：code={}", result.code());
                }
            } catch (Exception e) {
                CloudXMusicClient.LOGGER.debug("启动时恢复网易云账号信息失败：{}", e.getMessage());
            }
        });
    }

    public void logout() {
        loggedIn = false;
        uid = "";
        nickname = "";
        avatar = "";
        NeteaseHttp.clearCookies();
        save();
    }

    public boolean loggedIn() {
        // 以 CookieStore 为准：即使 netease.json 元数据损坏，只要 MUSIC_U 还在就仍算已登录。
        return NeteaseHttp.hasSession();
    }

    public String uid() {
        return uid;
    }

    public String nickname() {
        return nickname;
    }

    public String avatar() {
        return avatar;
    }

    public NeteaseQuality quality() {
        return NeteaseQuality.of(quality);
    }

    public void quality(NeteaseQuality value) {
        quality = value.apiName;
        save();
    }

    public LyricMode lyricMode() {
        try {
            return LyricMode.valueOf(lyricMode);
        } catch (IllegalArgumentException e) {
            return LyricMode.BILINGUAL;
        }
    }

    public void lyricMode(LyricMode mode) {
        lyricMode = mode.name();
        save();
    }

    /** 播放页滚动歌词是否带译文。只影响这一处，灵动岛仍按 {@link #lyricMode()} 显示。 */
    public boolean playbackTranslation() {
        return playbackTranslation;
    }

    public void playbackTranslation(boolean value) {
        playbackTranslation = value;
        save();
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
    }
}
