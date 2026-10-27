package dev.lzasn.cloudxmusic.client.state;

/** 灵动岛歌词显示模式。 */
public enum LyricMode {
    /** 只显示歌名 · 歌手。 */
    OFF("关闭"),
    /** 单行：只显示原文。 */
    ORIGINAL("原文"),
    /** 双行：原文 + 翻译。翻译缺失时自动退回单行。 */
    BILINGUAL("原文+翻译");

    public final String label;

    LyricMode(String label) {
        this.label = label;
    }

    public LyricMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
