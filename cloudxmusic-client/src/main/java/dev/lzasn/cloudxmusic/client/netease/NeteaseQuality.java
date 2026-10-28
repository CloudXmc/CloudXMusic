package dev.lzasn.cloudxmusic.client.netease;

/** 音质档位。无损是 FLAC，由 jFLAC 解码。 */
public enum NeteaseQuality {
    STANDARD("standard", "标准"),
    HIGHER("higher", "较高"),
    EXHIGH("exhigh", "极高"),
    LOSSLESS("lossless", "无损");

    public final String apiName;
    public final String label;

    NeteaseQuality(String apiName, String label) {
        this.apiName = apiName;
        this.label = label;
    }

    public NeteaseQuality next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static NeteaseQuality of(String name) {
        for (NeteaseQuality quality : values()) {
            if (quality.apiName.equalsIgnoreCase(name) || quality.name().equalsIgnoreCase(name)) return quality;
        }
        return EXHIGH;
    }
}
