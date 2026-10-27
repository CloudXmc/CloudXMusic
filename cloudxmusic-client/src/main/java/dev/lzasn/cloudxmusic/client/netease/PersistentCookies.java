package dev.lzasn.cloudxmusic.client.netease;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import net.fabricmc.loader.api.FabricLoader;

import java.net.HttpCookie;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 落盘版 Cookie 存储。
 *
 * <p><b>为什么要整套 Cookie，而不是只存 MUSIC_U</b>：扫码登录时，unikey 和后面的轮询必须属于
 * **同一个会话**——服务端靠游客 Cookie（NMTID、_ntes_nuid 等）把"这个二维码"和"这个客户端"绑在一起。
 * 只手动拼 MUSIC_U 会让轮询跑到另一个会话里，于是永远收不到 803。
 * 用 {@link java.net.CookieManager} 之后，游客 Cookie 和登录 Cookie 都由它统一管理，
 * 顺带把"重启后登录掉了"也一起解决了。</p>
 */
public final class PersistentCookies implements java.net.CookieStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** key = domain|path|name */
    private final Map<String, HttpCookie> cookies = new LinkedHashMap<>();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("cloudxmusic").resolve("cookies.json");
    }

    public PersistentCookies() {
        load();
    }

    @Override
    public synchronized void add(URI uri, HttpCookie cookie) {
        if (cookie == null) return;
        String key = cookie.getDomain() + "|" + cookie.getPath() + "|" + cookie.getName();
        // maxAge = -1 是正常的“浏览器会话 Cookie”，不能当成删除指令。
        // CookieManager 依赖这些 Cookie 维持扫码会话；只有真正过期（maxAge = 0 或已到期）才移除。
        if (cookie.hasExpired()) {
            cookies.remove(key);
        } else {
            cookies.put(key, cookie);
        }
        save();
    }

    @Override
    public synchronized List<HttpCookie> get(URI uri) {
        List<HttpCookie> result = new ArrayList<>();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        for (HttpCookie cookie : cookies.values()) {
            if (cookie.hasExpired()) continue;
            if (!domainMatches(host, cookie.getDomain())) continue;
            if (!path.startsWith(cookie.getPath() == null ? "/" : cookie.getPath())) continue;
            result.add(cookie);
        }
        return result;
    }

    private static boolean domainMatches(String host, String domain) {
        if (domain == null || domain.isEmpty()) return true;
        String d = domain.startsWith(".") ? domain.substring(1) : domain;
        return host.equals(d) || host.endsWith("." + d);
    }

    @Override
    public synchronized List<HttpCookie> getCookies() {
        return new ArrayList<>(cookies.values());
    }

    @Override
    public synchronized List<URI> getURIs() {
        return new ArrayList<>();
    }

    @Override
    public synchronized boolean remove(URI uri, HttpCookie cookie) {
        if (cookie == null) return false;
        String key = cookie.getDomain() + "|" + cookie.getPath() + "|" + cookie.getName();
        boolean removed = cookies.remove(key) != null;
        if (removed) save();
        return removed;
    }

    @Override
    public synchronized boolean removeAll() {
        cookies.clear();
        save();
        return true;
    }

    /** 有没有登录态（MUSIC_U）。 */
    public synchronized boolean hasSession() {
        for (HttpCookie cookie : cookies.values()) {
            if (cookie.getName().equals("MUSIC_U") && !cookie.getValue().isBlank()) return true;
        }
        return false;
    }

    private void load() {
        Path path = file();
        if (!Files.isRegularFile(path)) return;
        try {
            JsonArray array = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement element : array) {
                JsonObject json = element.getAsJsonObject();
                HttpCookie cookie = new HttpCookie(json.get("name").getAsString(), json.get("value").getAsString());
                if (json.has("domain")) cookie.setDomain(json.get("domain").getAsString());
                if (json.has("path")) cookie.setPath(json.get("path").getAsString());
                if (json.has("maxAge")) cookie.setMaxAge(json.get("maxAge").getAsLong());
                cookies.put(cookie.getDomain() + "|" + cookie.getPath() + "|" + cookie.getName(), cookie);
            }
            CloudXMusicClient.LOGGER.info("已读取网易云 Cookie：{} 条（含 MUSIC_U：{}）", cookies.size(), hasSession());
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.warn("读取 Cookie 失败：{}", e.getMessage());
        }
    }

    private void save() {
        JsonArray array = new JsonArray();
        for (HttpCookie cookie : cookies.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("name", cookie.getName());
            json.addProperty("value", cookie.getValue());
            json.addProperty("domain", cookie.getDomain());
            json.addProperty("path", cookie.getPath());
            json.addProperty("maxAge", cookie.getMaxAge());
            array.add(json);
        }
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(array), StandardCharsets.UTF_8);
        } catch (Exception e) {
            CloudXMusicClient.LOGGER.warn("保存 Cookie 失败：{}", e.getMessage());
        }
    }
}
