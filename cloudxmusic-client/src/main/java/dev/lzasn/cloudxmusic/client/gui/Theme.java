package dev.lzasn.cloudxmusic.client.gui;

/**
 * 统一配色：白色磨砂玻璃 + 微粉。
 *
 * <p>面板本身是高不透明度白（约 92%~96%），叠在模糊背景上就是磨砂玻璃；文字走深梅色，
 * 强调色统一用粉。所有颜色都带 alpha，方便叠层。</p>
 */
public final class Theme {
    private Theme() {}

    /** 玻璃面板渐变：顶部偏亮白，底部带一点粉。alpha 93%~96%，既透出模糊背景又不糊文字。 */
    public static final int GLASS_TOP = 0xF5FDFAFC;
    public static final int GLASS_BOTTOM = 0xEEF8ECF2;
    /** 面板外发丝边（亮）。 */
    public static final int GLASS_BORDER = 0x8CFFFFFF;
    /** 面板顶部 1px 高光。 */
    public static final int GLASS_HIGHLIGHT = 0x8CFFFFFF;
    /** 面板内分隔线。 */
    public static final int DIVIDER = 0x1F9A7186;

    /** 内部卡片 / 行悬停。 */
    public static final int CARD = 0x8AFFFFFF;
    public static final int CARD_HOVER = 0xC4FFFFFF;

    /** 强调粉。 */
    public static final int ACCENT = 0xFFE8608F;
    public static final int ACCENT_DEEP = 0xFFC43C74;
    public static final int ACCENT_SOFT = 0x33E8608F;
    public static final int ACCENT_FAINT = 0x17E8608F;

    /** 文字。 */
    public static final int TEXT = 0xFF3B2F38;
    public static final int MUTED = 0xFF7C6C78;
    public static final int FAINT = 0xFF968691;

    /** 白色文字（用于粉底）。 */
    public static final int ON_ACCENT = 0xFFFFF6FA;

    /** 歌词面板：接近网易云的深青灰，避免白底歌词显得散。 */
    public static final int LYRIC_BG = 0xE633474B;
    public static final int LYRIC_TEXT = 0xFFB6C2C4;
    public static final int LYRIC_TRANSLATION = 0xFF819396;
    public static final int LYRIC_ACTIVE = 0xFFFFFFFF;
    public static final int LYRIC_ACTIVE_TRANSLATION = 0xFFE1E9EA;
}
