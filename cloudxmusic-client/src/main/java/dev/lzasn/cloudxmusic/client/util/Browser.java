package dev.lzasn.cloudxmusic.client.util;

import java.awt.Desktop;
import java.net.URI;

/** 用系统默认浏览器打开链接。失败返回 false，由调用方决定退路（复制到剪贴板/写文件）。 */
public final class Browser {
    private Browser() {}

    public static boolean open(String url) {
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
}
