package dev.lzasn.cloudxmusic.client;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * 客户端数据目录（登录态、播放状态、QQ 账号、免责声明）。
 *
 * <p>默认是 {@code config/cloudxmusic}。同一台机器上要同时开两个客户端做房间联调时，
 * 给第二个实例加 JVM 参数 {@code -Dcloudxmusic.home=<目录>} 即可让两边各自独立，
 * 否则两个进程会互相覆盖 {@code state.json} 与 Cookie。</p>
 */
public final class CloudHome {
    public static final String OVERRIDE_PROPERTY = "cloudxmusic.home";

    private CloudHome() {}

    public static Path dir() {
        String override = System.getProperty(OVERRIDE_PROPERTY, "");
        if (!override.isBlank()) {
            try {
                return Path.of(override);
            } catch (RuntimeException ignored) {
                // 路径非法时回落到默认目录，不要让客户端起不来。
            }
        }
        return FabricLoader.getInstance().getConfigDir().resolve("cloudxmusic");
    }

    public static Path resolve(String child) {
        return dir().resolve(child);
    }
}
