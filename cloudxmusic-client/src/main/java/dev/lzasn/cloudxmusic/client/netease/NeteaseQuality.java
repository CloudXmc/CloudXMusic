package dev.lzasn.cloudxmusic.client.netease;

/**
 * 音质档位。
 *
 * <p>无损档请求 {@code encodeType=flac}，返回的是原生 FLAC。FLAC 是 VBR、没有帧索引，
 * 顺序流无法跳转，因此播放前会落盘建一次帧索引（见 {@code FlacSeekable}），
 * 之后拖动进度只换解码器，不重新下载。</p>
 */
public enum NeteaseQuality {
    STANDARD("standard", "标准", "mp3"),
    HIGHER("higher", "较高", "mp3"),
    EXHIGH("exhigh", "极高", "mp3"),
    LOSSLESS("lossless", "无损", "flac");

    public final String apiName;
    public final String label;
    /** 请求时的 encodeType，决定服务端返回 mp3 还是 flac。 */
    public final String encodeType;

    NeteaseQuality(String apiName, String label, String encodeType) {
        this.apiName = apiName;
        this.label = label;
        this.encodeType = encodeType;
    }

    public boolean lossless() {
        return this == LOSSLESS;
    }

    public NeteaseQuality next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** 未知值回落到「极高」；历史配置里的 {@code lossless} 现在能正常解析。 */
    public static NeteaseQuality of(String name) {
        for (NeteaseQuality quality : values()) {
            if (quality.apiName.equalsIgnoreCase(name) || quality.name().equalsIgnoreCase(name)) return quality;
        }
        return EXHIGH;
    }
}
