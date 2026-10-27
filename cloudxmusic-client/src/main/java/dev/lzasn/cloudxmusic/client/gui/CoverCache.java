package dev.lzasn.cloudxmusic.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.netease.NeteaseHttp;
import dev.lzasn.cloudxmusic.client.util.Async;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 专辑封面缓存：后台下载 → 主线程注册成动态纹理。
 *
 * <p>贴图只在整个区域上做 {@code (0,0,1,1)} 全图 blit——这一条对 UV 参数顺序不敏感，
 * 不会出现"只采样到一个角"的问题。</p>
 */
public final class CoverCache {
    private static final Map<String, Identifier> READY = new HashMap<>();
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static int counter;

    private CoverCache() {}

    /** 列表加载后预取全部封面，避免滚动到某一行时才开始下载。 */
    public static void prefetch(java.util.List<dev.lzasn.cloudxmusic.client.state.Track> tracks) {
        for (dev.lzasn.cloudxmusic.client.state.Track track : tracks) get(track.coverUrl());
    }

    /** 已经加载好就返回纹理 id，否则触发下载并返回 null。 */
    public static Identifier get(String url) {
        if (url == null || url.isBlank()) return null;
        Identifier id = READY.get(url);
        if (id != null) return id;
        if (FAILED.contains(url) || !LOADING.add(url)) return null;

        Async.run(() -> {
            try {
                String thumbnail = url + (url.contains("?") ? "&" : "?") + "param=96y96";
                HttpURLConnection connection = (HttpURLConnection) URI.create(thumbnail).toURL().openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setRequestProperty("User-Agent", NeteaseHttp.USER_AGENT);
                connection.setRequestProperty("Referer", "https://music.163.com/");
                byte[] bytes;
                try (InputStream in = connection.getInputStream()) {
                    bytes = in.readAllBytes();
                }
                NativeImage image = decode(bytes);
                Async.main(() -> {
                    DynamicTexture dynamic = null;
                    try {
                        Identifier texture = Identifier.fromNamespaceAndPath("cloudxmusic",
                                "covers/" + Integer.toHexString(url.hashCode()) + "_" + counter++);
                        // DynamicTexture 会持有并在 close() 时释放这张 NativeImage。
                        dynamic = new DynamicTexture(() -> "cloudxmusic cover", image);
                        Minecraft.getInstance().getTextureManager().register(texture, dynamic);
                        READY.put(url, texture);
                        FAILED.remove(url);
                    } catch (Exception e) {
                        FAILED.add(url);
                        if (dynamic != null) dynamic.close(); else image.close();
                        CloudXMusicClient.LOGGER.warn("封面注册失败：{}", e.getMessage());
                    } finally {
                        LOADING.remove(url);
                    }
                });
            } catch (Exception e) {
                FAILED.add(url);
                CloudXMusicClient.LOGGER.warn("封面下载或解码失败：{}", e.getMessage());
                LOADING.remove(url);
            }
        });
        return null;
    }

    /** 网易云缩略图多数是 JPEG；NativeImage.read 只认 PNG，因此先尝试 PNG，再转码 JPEG。 */
    private static NativeImage decode(byte[] bytes) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException pngError) {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
            if (source == null) throw pngError;
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            if (!ImageIO.write(source, "PNG", png)) throw pngError;
            return NativeImage.read(new ByteArrayInputStream(png.toByteArray()));
        }
    }
}
