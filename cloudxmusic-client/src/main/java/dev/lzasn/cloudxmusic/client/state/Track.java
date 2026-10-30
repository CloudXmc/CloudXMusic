package dev.lzasn.cloudxmusic.client.state;

/**
 * 一首歌。
 *
 * <p>网易云的歌带 {@link #id()}，本地演示曲则为 {@code null}；{@link #album()} / {@link #coverUrl()}
 * 只有网易云来源才有值。GUI 里仍然只用 title/artist/duration，不需要区分来源。</p>
 */
public record Track(String title, String artist, int durationSeconds, String id, String album, String coverUrl,
                    boolean vip) {

    public Track(String title, String artist, int durationSeconds, String id, String album, String coverUrl) {
        this(title, artist, durationSeconds, id, album, coverUrl, false);
    }

    public Track(String title, String artist, int durationSeconds) {
        this(title, artist, durationSeconds, null, "", "", false);
    }

    public boolean netease() {
        return id != null && !id.isBlank();
    }

    public String durationText() {
        return format(durationSeconds);
    }

    public static String format(int seconds) {
        return "%d:%02d".formatted(seconds / 60, seconds % 60);
    }

    /** 网易云给的是毫秒。 */
    public static String formatMs(long millis) {
        return format((int) Math.max(0, millis / 1000));
    }
}
