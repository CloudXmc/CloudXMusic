package dev.lzasn.cloudxmusic.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.lzasn.cloudxmusic.client.util.Async;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** 从 Mojang 公开资料解析玩家 UUID、皮肤纹理并裁出头像；网络与渲染线程隔离。 */
public final class MojangAvatarCache {
    private static final int LIMIT = 64;
    private static final int MAX_JSON = 128 * 1024;
    private static final int MAX_SKIN = 1024 * 1024;
    private static final long FAIL_BACKOFF_MS = 5 * 60_000L;
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Map<String, BufferedImage> READY = new LinkedHashMap<>(LIMIT, .75f, true);
    private static final Map<String, Long> FAILED = new LinkedHashMap<>();
    private static final Map<String, Future<?>> LOADING = new LinkedHashMap<>();
    private static final ThreadPoolExecutor DOWNLOADS = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(24), runnable -> {
                Thread thread = new Thread(runnable, "CloudXmusic-MojangAvatar");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private static int generation;
    private static int revision;

    private MojangAvatarCache() {}

    public static synchronized BufferedImage avatar(String playerName) {
        String name = normalize(playerName);
        if (name == null || DOWNLOADS.isShutdown()) return null;
        BufferedImage ready = READY.get(name);
        if (ready != null) return ready;
        Long failed = FAILED.get(name);
        if (failed != null && System.currentTimeMillis() - failed < FAIL_BACKOFF_MS) return null;
        if (LOADING.containsKey(name)) return null;
        int token = generation;
        try {
            Future<?> future = DOWNLOADS.submit(() -> {
                BufferedImage result = null;
                try {
                    result = download(name);
                } catch (Exception ignored) {
                    // Mojang API 不可用时保持通用玩家占位头像。
                }
                BufferedImage image = result;
                Async.main(() -> complete(name, token, image));
            });
            LOADING.put(name, future);
            return null;
        } catch (RejectedExecutionException ignored) {
            return null;
        }
    }

    public static synchronized int revision() {
        return revision;
    }

    /** 仅离线预览使用：同步拉取并写入缓存，禁止在游戏渲染线程调用。 */
    public static BufferedImage loadNowForPreview(String playerName) {
        String name = normalize(playerName);
        if (name == null) return null;
        BufferedImage ready;
        synchronized (MojangAvatarCache.class) {
            ready = READY.get(name);
        }
        if (ready != null) return ready;
        BufferedImage loaded = null;
        try {
            loaded = download(name);
        } catch (Exception ignored) { }
        synchronized (MojangAvatarCache.class) {
            store(name, loaded);
        }
        return loaded;
    }

    private static void complete(String name, int token, BufferedImage image) {
        synchronized (MojangAvatarCache.class) {
            if (token != generation) return;
            LOADING.remove(name);
            store(name, image);
        }
    }

    private static void store(String name, BufferedImage image) {
        if (image == null) {
            if (FAILED.size() >= LIMIT) FAILED.remove(FAILED.keySet().iterator().next());
            FAILED.put(name, System.currentTimeMillis());
        } else {
            if (READY.size() >= LIMIT) READY.remove(READY.keySet().iterator().next());
            READY.put(name, image);
            FAILED.remove(name);
            revision++;
        }
    }

    private static BufferedImage download(String name) throws Exception {
        String uuid = resolveUuid(name);
        if (uuid == null) return null;
        String skinUrl = resolveSkinUrl(uuid);
        if (skinUrl == null) return null;
        byte[] skinBytes = get(skinUrl, MAX_SKIN);
        if (skinBytes == null) return null;
        BufferedImage skin = ImageIO.read(new ByteArrayInputStream(skinBytes));
        return cropHead(skin);
    }

    private static String resolveUuid(String name) throws Exception {
        byte[] payload = get("https://api.mojang.com/users/profiles/minecraft/" + name, MAX_JSON);
        if (payload == null) return null;
        JsonObject profile = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!profile.has("id")) return null;
        String id = profile.get("id").getAsString();
        return id.matches("[0-9a-fA-F]{32}") ? id : null;
    }

    private static String resolveSkinUrl(String uuid) throws Exception {
        byte[] payload = get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid, MAX_JSON);
        if (payload == null) return null;
        JsonObject profile = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!profile.has("properties") || !profile.get("properties").isJsonArray()) return null;
        JsonArray properties = profile.getAsJsonArray("properties");
        for (JsonElement element : properties) {
            if (!element.isJsonObject()) continue;
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.has("name") ? property.get("name").getAsString() : "")) continue;
            if (!property.has("value")) continue;
            byte[] decoded = Base64.getDecoder().decode(property.get("value").getAsString());
            JsonObject textures = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!textures.has("textures") || !textures.get("textures").isJsonObject()) return null;
            JsonObject values = textures.getAsJsonObject("textures");
            if (!values.has("SKIN") || !values.get("SKIN").isJsonObject()) return null;
            JsonObject skin = values.getAsJsonObject("SKIN");
            if (!skin.has("url")) return null;
            String url = skin.get("url").getAsString();
            if (url.startsWith("http://")) url = "https://" + url.substring("http://".length());
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())) return null;
            return url;
        }
        return null;
    }

    private static byte[] get(String url, int maxBytes) throws IOException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException bad) {
            return null;
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || (!"api.mojang.com".equals(host) && !"sessionserver.mojang.com".equals(host)
                && !"textures.minecraft.net".equals(host))) return null;
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "CloudXMusic/2.5");
        connection.setRequestProperty("Accept", "application/json,image/png");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            try (InputStream in = connection.getInputStream()) {
                byte[] bytes = in.readNBytes(maxBytes + 1);
                return bytes.length > maxBytes ? null : bytes;
            }
        } finally {
            connection.disconnect();
        }
    }

    private static BufferedImage cropHead(BufferedImage skin) {
        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) return null;
        BufferedImage head = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = head.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(skin, 0, 0, 40, 40, 8, 8, 16, 16, null);
        if (skin.getWidth() >= 64 && skin.getHeight() >= 16) {
            graphics.drawImage(skin, 0, 0, 40, 40, 40, 8, 48, 16, null);
        }
        graphics.dispose();
        return head;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String name = value.trim();
        return PLAYER_NAME.matcher(name).matches() ? name : null;
    }

    public static synchronized void clear() {
        generation++;
        LOADING.values().forEach(future -> future.cancel(true));
        LOADING.clear();
        READY.clear();
        FAILED.clear();
        DOWNLOADS.purge();
        revision++;
    }

    public static synchronized void close() {
        clear();
        DOWNLOADS.shutdownNow();
    }
}
