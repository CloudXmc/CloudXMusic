package dev.lzasn.cloudxmusic.client.netease;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.state.Track;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 网易云接口。
 *
 * <p>能不开加密就不开：查询类接口（歌曲详情、歌词、播放地址、歌单、搜索）走老的
 * {@code /api/...} 明文接口，只需要带 Cookie；只有登录必须走 weapi/eapi。</p>
 *
 * <p>所有方法都会抛异常（网络/解析），调用方负责在线程池里跑并处理失败。</p>
 */
public final class NeteaseApi {
    private static final String WEB = "https://music.163.com";
    private static final String INTERFACE = "https://interfacepc.music.163.com";

    private NeteaseApi() {}

    // ------------------------------------------------------------ 登录

    /** 二维码登录第一步：拿一个 unikey。 */
    public static String qrKey() throws Exception {
        JsonObject json = getJson(WEB + "/api/login/qrcode/unikey?type=1");
        JsonElement key = json.get("unikey");
        if (key == null) throw new NeteaseException("拿不到二维码 key：" + json);
        return key.getAsString();
    }

    /** 让用户扫码（或直接浏览器打开）的链接。 */
    public static String qrLoginUrl(String key) {
        return "https://music.163.com/login?codekey=" + key;
    }

    /**
     * 轮询扫码状态。
     *
     * @return 800 过期 / 801 等待扫码 / 802 已扫待确认 / 803 成功
     */
    public static QrStatus qrCheck(String key) throws Exception {
        JsonObject json = getJson(WEB + "/api/login/qrcode/client/login?type=1&key="
                + URLEncoder.encode(key, StandardCharsets.UTF_8));
        int code = intOr(json, "code", -1);
        String message = stringOr(json, "message", "");
        String nickname = "";
        JsonElement user = json.get("nickname");
        if (user != null && !user.isJsonNull()) nickname = user.getAsString();
        // 803 的 MUSIC_U 常在 JSON body 里，不能只依赖 Set-Cookie。
        String cookie = stringOr(json, "cookie", "");
        return new QrStatus(code, message, nickname, cookie);
    }

    /** 手机号 + 密码（这里传明文密码，内部做 MD5）。 */
    public static LoginResult cellphoneLogin(String phone, String password, String countryCode) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "1");
        data.put("https", "true");
        data.put("phone", phone);
        data.put("countrycode", countryCode);
        data.put("remember", "true");
        data.put("password", NeteaseCrypto.md5(password));
        String form = NeteaseCrypto.weapiForm(data);
        JsonObject json = postJson(WEB + "/weapi/w/login/cellphone", form);
        return loginResult(json);
    }

    /** 短信验证码登录。 */
    public static LoginResult cellphoneCaptchaLogin(String phone, String code, String countryCode) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "1");
        data.put("https", "true");
        data.put("phone", phone);
        data.put("countrycode", countryCode);
        data.put("remember", "true");
        data.put("captcha", code);
        String form = NeteaseCrypto.weapiForm(data);
        JsonObject json = postJson(WEB + "/weapi/w/login/cellphone", form);
        return loginResult(json);
    }

    /** 邮箱 + 密码登录（和手机号同一个 weapi 体系，字段不同）。 */
    public static LoginResult emailPasswordLogin(String email, String password) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "0");
        data.put("https", "true");
        data.put("username", email);
        data.put("password", NeteaseCrypto.md5(password));
        data.put("rememberLogin", "true");
        JsonObject json = postJson(WEB + "/weapi/w/login", NeteaseCrypto.weapiForm(data));
        return loginResult(json);
    }

    /** 发登录验证码（走 eapi）。 */
    public static void sendLoginCaptcha(String phone, String countryCode) throws Exception {
        String path = "/api/middle/captcha/sent/v1";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ctcode", countryCode);
        data.put("secrete", "music_middleuser_pclogin");
        data.put("cellphone", phone);
        data.put("scene", "0");
        JsonObject json = postJson(INTERFACE + "/eapi/middle/captcha/sent/v1", NeteaseCrypto.eapiForm(path, data));
        int code = intOr(json, "code", 200);
        if (code != 200) throw new NeteaseException("验证码发送失败：" + stringOr(json, "message", String.valueOf(code)));
    }

    private static LoginResult loginResult(JsonObject json) {
        int code = intOr(json, "code", -1);
        if (code != 200) {
            String raw = json.toString();
            if (raw.length() > 300) raw = raw.substring(0, 300) + "…";
            return new LoginResult(false, code, stringOr(json, "message", "登录失败"), "", "", "", raw, "");
        }
        JsonObject profile = json.has("profile") && json.get("profile").isJsonObject()
                ? json.getAsJsonObject("profile") : new JsonObject();
        String uid = stringOr(profile, "userId", "");
        String nickname = stringOr(profile, "nickname", "");
        String avatar = stringOr(profile, "avatarUrl", "");
        return new LoginResult(true, code, "登录成功", uid, nickname, avatar, "", stringOr(json, "cookie", ""));
    }

    /** 用当前 Cookie 查登录态（判断 MUSIC_U 还有没有效）。 */
    public static LoginResult account() throws Exception {
        JsonObject json = getJson(WEB + "/api/nuser/account/get");
        JsonObject profile = json.has("profile") && json.get("profile").isJsonObject()
                ? json.getAsJsonObject("profile") : null;
        if (profile == null) return new LoginResult(false, intOr(json, "code", -1), "未登录", "", "", "", "", "");
        return new LoginResult(true, 200, "已登录",
                stringOr(profile, "userId", ""),
                stringOr(profile, "nickname", ""),
                stringOr(profile, "avatarUrl", ""), "", "");
    }

    // ------------------------------------------------------------ 歌曲

    /** 取播放地址；版权受限或无权限时返回 null。 */
    public static String songUrl(String id, NeteaseQuality quality) throws Exception {
        // ids 的方括号必须编码：URI 里裸 [] 在某些实现下会抛 URISyntaxException
        String url = WEB + "/api/song/enhance/player/url/v1?encodeType=" + quality.encodeType + "&ids=%5B"
                + URLEncoder.encode(id, StandardCharsets.UTF_8) + "%5D&level=" + quality.apiName;
        JsonObject json = getJson(url);
        JsonArray data = json.getAsJsonArray("data");
        if (data == null || data.isEmpty()) return null;
        JsonObject first = data.get(0).getAsJsonObject();
        JsonElement playUrl = first.get("url");
        if (playUrl == null || playUrl.isJsonNull()) return null;
        return playUrl.getAsString();
    }

    /**
     * 取一个**真的能下**的播放地址。
     *
     * <p>网易云返回的直链不一定能用：带 {@code authSecret} 的会员/受限链接会在 CDN 上直接 403，
     * 而且不同档位可能有不同的可用性。这里逐个档位试，每个地址先发一个 1 字节的探测请求，
     * 通过才交给播放器，避免"看着有地址其实放不出来"。</p>
     *
     * <p>探测时会依次尝试：原链接 → 换成 https → 不带 Cookie。哪个通过用哪个。</p>
     */
    public static String playableUrl(String id, NeteaseQuality preferred) throws Exception {
        // 降级顺序：先试用户选的档位，再按「极高 → 较高 → 标准」回落。
        // 用列表去重，首选就是极高时不会重复请求同一档。
        java.util.List<NeteaseQuality> order = new java.util.ArrayList<>();
        order.add(preferred);
        for (NeteaseQuality quality : new NeteaseQuality[]{
                NeteaseQuality.EXHIGH, NeteaseQuality.HIGHER, NeteaseQuality.STANDARD}) {
            if (quality != preferred) order.add(quality);
        }
        for (NeteaseQuality quality : order) {
            String url = songUrl(id, quality);
            if (url == null || url.isBlank()) continue;
            String working = probe(url);
            if (working != null) return working;
        }
        // 上面全是 403 时的兜底：外链播放接口。它会 302 到一个**不带 authSecret** 的直链，
        // 能放就能放，不能放会 302 到 music.163.com/404（会员/版权限制）。
        String fallback = outerUrl(id);
        if (fallback != null) {
            CloudXMusicClient.LOGGER.info("改用外链播放地址：{}", fallback);
            return fallback;
        }
        return null;
    }

    /** {@code /song/media/outer/url} 的 302 目标；不可播放返回 null。 */
    private static String outerUrl(String id) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) java.net.URI
                    .create(WEB + "/song/media/outer/url?id=" + id + ".mp3").toURL().openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", NeteaseHttp.USER_AGENT);
            connection.setRequestProperty("Referer", "https://music.163.com/");
            int code = connection.getResponseCode();
            String location = connection.getHeaderField("Location");
            connection.disconnect();
            if (location == null || location.contains("music.163.com/404")) return null;
            String absolute = location.startsWith("http") ? location : "https:" + location;
            return probe(absolute);
        } catch (Exception e) {
            return null;
        }
    }

    /** @return 可用的地址（可能被换成 https），都不可用返回 null。 */
    private static String probe(String url) {
        String https = url.startsWith("http://") ? "https://" + url.substring(7) : url;
        String[] candidates = url.startsWith("http://") ? new String[]{https, url} : new String[]{url};
        for (String candidate : candidates) {
            if (headOk(candidate)) return candidate;
        }
        return null;
    }

    /**
     * 探测地址是否可播：**发普通 GET，不带 Range**。
     *
     * <p>带 Range 的探测在这类带签名的 CDN 链接上会被判成异常请求（403），
     * 会把本来能播的地址误杀。这里只要状态码，拿到头就断开，不读 body。</p>
     */
    private static boolean headOk(String url) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", NeteaseHttp.USER_AGENT);
            connection.setRequestProperty("Referer", "https://music.163.com/");
            connection.setRequestProperty("Accept", "*/*");
            String cookie = NeteaseHttp.cookieHeader(url);
            if (!cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
            int code = connection.getResponseCode();
            if (code == 200 || code == 206) return true;
            String reason = "";
            try (java.io.InputStream error = connection.getErrorStream()) {
                if (error != null) {
                    byte[] buf = error.readNBytes(300);
                    reason = new String(buf, java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\s+", " ");
                }
            } catch (Exception ignored) {
                // 拿不到 body 就算了
            }
            String server = connection.getHeaderField("Server");
            CloudXMusicClient.LOGGER.info("播放地址探测失败：HTTP {} server={} body={} url={}",
                    code, server, reason, url);
            return false;
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.info("播放地址探测异常：{} {}", e.getMessage(), url);
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static Track songDetail(String id) throws Exception {
        JsonObject json = getJson(WEB + "/api/v3/song/detail?c="
                + URLEncoder.encode("[{\"id\":" + id + "}]", StandardCharsets.UTF_8));
        JsonArray songs = json.getAsJsonArray("songs");
        if (songs == null || songs.isEmpty()) throw new NeteaseException("找不到歌曲 " + id);
        return trackOf(songs.get(0).getAsJsonObject());
    }

    public static Lyric lyric(String id) throws Exception {
        JsonObject json = getJson(WEB + "/api/song/lyric?id=" + id + "&lv=0&tv=0");
        String lrc = json.has("lrc") ? stringOr(json.getAsJsonObject("lrc"), "lyric", "") : "";
        String tlyric = json.has("tlyric") ? stringOr(json.getAsJsonObject("tlyric"), "lyric", "") : "";
        return Lyric.parse(lrc, tlyric);
    }

    public static List<Track> searchSongs(String keyword, int limit) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("s", keyword);
        form.put("type", "1");
        form.put("offset", "0");
        form.put("limit", String.valueOf(limit));
        form.put("total", "true");
        JsonObject json = postForm(WEB + "/api/cloudsearch/pc/", encode(form));
        JsonObject result = json.getAsJsonObject("result");
        if (result == null) return List.of();
        JsonArray songs = result.getAsJsonArray("songs");
        List<Track> tracks = new ArrayList<>();
        if (songs != null) for (JsonElement element : songs) tracks.add(trackOf(element.getAsJsonObject()));
        return tracks;
    }

    // ------------------------------------------------------------ 歌单

    public static List<Track> playlistTracks(String playlistId) throws Exception {
        JsonObject json = getJson(WEB + "/api/v6/playlist/detail?id=" + playlistId + "&n=1000");
        JsonObject playlist = json.getAsJsonObject("playlist");
        if (playlist == null) throw new NeteaseException("歌单不存在：" + playlistId);
        JsonArray tracks = playlist.getAsJsonArray("tracks");
        List<Track> result = new ArrayList<>();
        if (tracks != null) for (JsonElement element : tracks) result.add(trackOf(element.getAsJsonObject()));
        return result;
    }

    public static Playlist playlistInfo(String playlistId) throws Exception {
        JsonObject json = getJson(WEB + "/api/v6/playlist/detail?id=" + playlistId + "&n=1");
        JsonObject playlist = json.getAsJsonObject("playlist");
        if (playlist == null) throw new NeteaseException("歌单不存在：" + playlistId);
        return new Playlist(stringOr(playlist, "id", playlistId), stringOr(playlist, "name", "?"),
                stringOr(playlist, "coverImgUrl", ""), intOr(playlist, "trackCount", 0));
    }

    public static List<Playlist> userPlaylists(String uid) throws Exception {
        JsonObject json = getJson(WEB + "/api/user/playlist?uid=" + uid + "&limit=100&offset=0");
        JsonArray array = json.getAsJsonArray("playlist");
        List<Playlist> result = new ArrayList<>();
        if (array != null) {
            for (JsonElement element : array) {
                JsonObject item = element.getAsJsonObject();
                result.add(new Playlist(stringOr(item, "id", ""), stringOr(item, "name", "?"),
                        stringOr(item, "coverImgUrl", ""), intOr(item, "trackCount", 0)));
            }
        }
        return result;
    }

    /** 我喜欢的音乐：先拿 id 列表，再批量查详情。 */
    public static List<Track> likedTracks(String uid, int limit) throws Exception {
        return likedTracksAndIds(uid, limit).tracks();
    }

    public record LikedTracks(Set<String> ids, List<Track> tracks) {
        public LikedTracks {
            ids = Set.copyOf(ids);
            tracks = List.copyOf(tracks);
        }
    }

    /** {@code /api/likelist} 返回 404「接口未找到」后置位，之后直接走歌单，不再每次白撞一次。 */
    private static volatile boolean likeListUnavailable;

    /** 同时返回完整收藏 id 集和用于展示的限量歌曲详情。 */
    public static LikedTracks likedTracksAndIds(String uid, int limit) throws Exception {
        List<String> ids = fetchLikedIds(uid);
        if (ids.isEmpty()) return new LikedTracks(Set.of(), List.of());
        Set<String> likedIds = new LinkedHashSet<>(ids);
        List<String> all = ids.subList(0, Math.min(limit, ids.size()));
        List<Track> result = new ArrayList<>();
        for (int from = 0; from < all.size(); from += 100) {
            List<String> chunk = all.subList(from, Math.min(all.size(), from + 100));
            StringBuilder c = new StringBuilder("[");
            for (int i = 0; i < chunk.size(); i++) {
                if (i > 0) c.append(',');
                c.append("{\"id\":").append(chunk.get(i)).append('}');
            }
            c.append(']');
            JsonObject detail = getJson(WEB + "/api/v3/song/detail?c="
                    + URLEncoder.encode(c.toString(), StandardCharsets.UTF_8));
            JsonArray songs = detail.getAsJsonArray("songs");
            if (songs == null) continue;
            for (JsonElement song : songs) result.add(trackOf(song.getAsJsonObject()));
        }
        return new LikedTracks(likedIds, result);
    }

    /**
     * 取收藏 id：优先 {@code /api/likelist}，失败后改用「我喜欢的音乐」歌单。
     *
     * <p>该接口 2026-10 起对普通账号返回 404「接口未找到」，所以第一次失败后就记下来，
     * 之后直接走歌单，避免每次刷新收藏都先白撞一次 404。</p>
     */
    private static List<String> fetchLikedIds(String uid) throws Exception {
        if (!likeListUnavailable) {
            try {
                return likedIds(uid);
            } catch (Exception primary) {
                likeListUnavailable = true;
                CloudXMusicClient.LOGGER.warn("likelist 不可用，改用收藏歌单：{}", primary.getMessage());
            }
        }
        return likedIdsFromPlaylist(uid);
    }

    /** {@code /api/likelist}：只返回收藏的歌曲 id。 */
    private static List<String> likedIds(String uid) throws Exception {
        JsonObject json = getJson(WEB + "/api/likelist?uid=" + uid + "&type=0");
        JsonArray ids = json.getAsJsonArray("ids");
        if (ids == null) {
            int code = intOr(json, "code", -1);
            String message = stringOr(json, "message", "网易云返回 code " + code);
            CloudXMusicClient.LOGGER.warn("likelist 返回异常：code={} message={}", code, message);
            throw new NeteaseException(message);
        }
        List<String> result = new ArrayList<>();
        for (JsonElement element : ids) result.add(element.getAsString());
        return result;
    }

    /** 兜底：从「我喜欢的音乐」歌单里取全部 trackId（specialType == 5）。 */
    private static List<String> likedIdsFromPlaylist(String uid) throws Exception {
        JsonObject list = getJson(WEB + "/api/user/playlist?uid=" + uid + "&limit=1000&offset=0");
        JsonArray playlists = list.getAsJsonArray("playlist");
        if (playlists == null) throw new NeteaseException("拿不到歌单列表");
        String id = null;
        for (JsonElement element : playlists) {
            JsonObject playlist = element.getAsJsonObject();
            if (intOr(playlist, "specialType", 0) == 5) {
                id = stringOr(playlist, "id", "");
                break;
            }
        }
        if (id == null || id.isBlank()) throw new NeteaseException("没有找到「我喜欢的音乐」歌单");
        JsonObject detail = getJson(WEB + "/api/v6/playlist/detail?id=" + id + "&n=1000&s=0");
        JsonObject playlist = detail.getAsJsonObject("playlist");
        if (playlist == null) throw new NeteaseException("收藏歌单详情为空");
        JsonArray trackIds = playlist.getAsJsonArray("trackIds");
        List<String> result = new ArrayList<>();
        if (trackIds != null)
            for (JsonElement element : trackIds) result.add(stringOr(element.getAsJsonObject(), "id", ""));
        result.removeIf(String::isBlank);
        return result;
    }

    /** 收藏或取消收藏一首歌曲。 */
    public static void setLiked(String id, boolean liked) throws Exception {
        // 网易云 weapi 的参数名是 trackId/time/alg，不是 id；缺 alg 或 csrf 会直接返回「参数异常」。
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alg", "itembased");
        data.put("trackId", id);
        data.put("time", 3);
        data.put("like", liked);
        data.put("csrf_token", csrf());
        JsonObject json = postJson(WEB + "/weapi/song/like", NeteaseCrypto.weapiForm(data));
        int code = intOr(json, "code", -1);
        if (code != 200) {
            String message = stringOr(json, "message", "网易云未接受请求（code " + code + "）");
            CloudXMusicClient.LOGGER.warn("收藏接口失败：id={} like={} code={} message={}", id, liked, code, message);
            throw new NeteaseException(message);
        }
    }

    /** 取 csrf；Cookie 里没有就先访问一次主站让服务端下发。 */
    private static String csrf() {
        String token = NeteaseHttp.csrfToken();
        if (!token.isBlank()) return token;
        try {
            NeteaseHttp.get(WEB + "/");
            return NeteaseHttp.csrfToken();
        } catch (Exception e) {
            return "";
        }
    }

    /** 每日推荐。 */
    public static List<Track> dailyRecommendation() throws Exception {
        JsonObject json = postJson(WEB + "/api/v3/discovery/recommend/songs", "");
        JsonObject data = json.getAsJsonObject("data");
        if (data == null) return List.of();
        JsonArray songs = data.getAsJsonArray("dailySongs");
        List<Track> result = new ArrayList<>();
        if (songs != null) for (JsonElement element : songs) result.add(trackOf(element.getAsJsonObject()));
        return result;
    }

    // ------------------------------------------------------------ 解析

    static Track trackOf(JsonObject song) {
        String id = stringOr(song, "id", "");
        String name = stringOr(song, "name", "?");
        String artist = artists(song);
        long durationMs = longOr(song, "dt", longOr(song, "duration", 0L));
        String album = "";
        String cover = "";
        JsonObject al = song.has("al") && song.get("al").isJsonObject() ? song.getAsJsonObject("al")
                : song.has("album") && song.get("album").isJsonObject() ? song.getAsJsonObject("album") : null;
        if (al != null) {
            album = stringOr(al, "name", "");
            cover = stringOr(al, "picUrl", "");
        }
        return new Track(name, artist, (int) Math.max(0, durationMs / 1000), id, album, cover);
    }

    private static String artists(JsonObject song) {
        JsonArray array = song.has("ar") ? song.getAsJsonArray("ar")
                : song.has("artists") ? song.getAsJsonArray("artists") : null;
        if (array == null || array.isEmpty()) return "未知歌手";
        StringBuilder sb = new StringBuilder();
        for (JsonElement element : array) {
            if (sb.length() > 0) sb.append("/");
            sb.append(stringOr(element.getAsJsonObject(), "name", "?"));
        }
        return sb.toString();
    }

    private static JsonObject getJson(String url) throws Exception {
        return parse(NeteaseHttp.get(url));
    }

    private static JsonObject postJson(String url, String form) throws Exception {
        return parse(NeteaseHttp.postForm(url, form));
    }

    private static JsonObject postForm(String url, String form) throws Exception {
        return parse(NeteaseHttp.postForm(url, form));
    }

    private static JsonObject parse(NeteaseHttp.Response response) throws Exception {
        if (response.body() == null || response.body().isBlank()) {
            throw new NeteaseException("空响应（HTTP " + response.status() + "）");
        }
        JsonElement element = JsonParser.parseString(response.body());
        if (!element.isJsonObject()) throw new NeteaseException("响应不是 JSON 对象");
        return element.getAsJsonObject();
    }

    private static String encode(Map<String, String> values) {
        StringBuilder sb = new StringBuilder();
        values.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return element.getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return element.getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static long longOr(JsonObject json, String key, long fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        try {
            return element.getAsLong();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public record QrStatus(int code, String message, String nickname, String cookie) {
        public boolean waiting() {
            return code == 801 || code == 802;
        }

        public boolean success() {
            return code == 803;
        }

        public boolean expired() {
            return code == 800;
        }
    }

    public record LoginResult(boolean ok, int code, String message, String uid, String nickname, String avatarUrl,
                              String raw, String cookie) {}

    public record Playlist(String id, String name, String coverUrl, int trackCount) {}

    public static class NeteaseException extends Exception {
        public NeteaseException(String message) {
            super(message);
        }
    }
}
