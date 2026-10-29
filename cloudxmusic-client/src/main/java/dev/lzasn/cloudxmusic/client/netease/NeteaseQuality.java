package dev.lzasn.cloudxmusic.client.netease;

/** 音质档位。只保留 mp3 三档。无损（FLAC）已移除：它是 VBR 格式，seek 只能从头逐帧解码。 */
public enum NeteaseQuality {
    STANDARD("standard", "标准"),
    HIGHER("higher", "较高"),
    EXHIGH("exhigh", "极高");

    public final String apiName;
    public final String label;

    NeteaseQuality(String apiName, String label) {
        this.apiName = apiName;
        this.label = label;
    }

    public NeteaseQuality next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** 旧配置里的 {@code lossless} 会落到默认的「极高」，不需要额外迁移。 */
    public static NeteaseQuality of(String name) {
        for (NeteaseQuality quality : values()) {
            if (quality.apiName.equalsIgnoreCase(name) || quality.name().equalsIgnoreCase(name)) return quality;
        }
        return EXHIGH;
    }
}
