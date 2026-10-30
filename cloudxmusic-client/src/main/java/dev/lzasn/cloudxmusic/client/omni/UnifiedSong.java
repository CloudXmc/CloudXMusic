package dev.lzasn.cloudxmusic.client.omni;

import java.util.Objects;

/** 音源之间传递的不可变歌曲信息，不保存账号或播放状态。 */
public final class UnifiedSong {
    private final String source;
    private final String platformId;
    private final String title;
    private final String artist;
    private final String album;
    private final boolean vip;

    public UnifiedSong(String source, String platformId, String title, String artist, String album) {
        this(source,platformId,title,artist,album,false);
    }
    public UnifiedSong(String source, String platformId, String title, String artist, String album,boolean vip) {
        this.source = Objects.requireNonNull(source, "source");
        this.platformId = Objects.requireNonNull(platformId, "platformId");
        this.title = Objects.requireNonNull(title, "title");
        this.artist = Objects.requireNonNull(artist, "artist");
        this.album = Objects.requireNonNull(album, "album");
        this.vip=vip;
        if (source.isBlank() || platformId.isBlank()) {
            throw new IllegalArgumentException("source and platformId must not be blank");
        }
    }

    public String source() { return source; }
    public String platformId() { return platformId; }
    public String title() { return title; }
    public String artist() { return artist; }
    public String album() { return album; }
    public boolean vip() { return vip; }
}
