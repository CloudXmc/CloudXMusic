package dev.lzasn.cloudxmusic.client.util;

import java.awt.Desktop;
import java.net.URI;
import java.util.Locale;

/** 用系统默认浏览器打开链接。先走 Desktop，失败再按平台调系统命令。 */
public final class Browser {
    private Browser() {}

    public static boolean open(String url) {
        return viaDesktop(url) || viaSystem(url);
    }

    private static boolean viaDesktop(String url) {
        try {
            if (!Desktop.isDesktopSupported()) return false;
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) return false;
            desktop.browse(URI.create(url));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** 有些 JVM/启动器下 Desktop 不可用，退回系统命令更可靠。 */
    private static boolean viaSystem(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            if (start("cmd", "/c", "start", "", url)) return true;
            return start("rundll32", "url.dll,FileProtocolHandler", url);
        }
        if (os.contains("mac")) return start("open", url);
        return start("xdg-open", url);
    }

    private static boolean start(String... command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
