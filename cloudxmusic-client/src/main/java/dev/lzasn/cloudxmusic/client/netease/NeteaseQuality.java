package dev.lzasn.cloudxmusic.client.netease;

/** 音质档位。只走 mp3：无损是 flac，当前播放器解不了。 */
public enum NeteaseQuality {
    STANDARD("standard", "标准 128K"),
    HIGHER("higher", "较高 192K"),
    EXHIGH("exhigh", "极高 320K");

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
