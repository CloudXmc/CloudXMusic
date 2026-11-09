package com.muoniumplayer.core.management;

import com.muoniumplayer.core.rendering.font.CFontRenderer;
import com.muoniumplayer.core.rendering.font.FontKerning;
import com.muoniumplayer.render.skija.SkijaText;

import java.awt.Font;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

/** Minimal font host for the imported world-lyrics glyph atlas. */
public final class FontManager {
    private static final String BOLD_MAIN = "/assets/cloudxmusic/fonts/Inter.ttf";
    private static final String BOLD_FALLBACK = "/assets/cloudxmusic/fonts/NotoSansSC-Regular.otf";

    public static final CFontRenderer pf25bold = createText(25f, BOLD_MAIN, BOLD_FALLBACK);

    private FontManager() {
    }

    private static CFontRenderer createText(float size, String mainPath, String fallbackPath) {
        try {
            Font main = readFont(mainPath);
            Font fallback = readFont(fallbackPath);
            return new CFontRenderer(main, size * 0.5f, new FontKerning(mainPath), fallback);
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    private static Font readFont(String path) throws Exception {
        byte[] data;
        try (InputStream in = FontManager.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled font " + path);
            }
            data = in.readAllBytes();
        }
        Font font = Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(data));
        SkijaText.register(font, data);
        return font;
    }
}
