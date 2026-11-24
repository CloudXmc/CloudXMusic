package dev.lzasn.cloudxmusic.client.omni;

import java.util.function.Supplier;

/** 在已提交的音源上选择权威快照；不修改任一播放器的队列或登录态。 */
public final class PlaybackRouter {
    private final Supplier<MusicSource> source;
    private final Supplier<PlaybackSnapshot> legacy,kugou;
    public PlaybackRouter(Supplier<MusicSource> source,Supplier<PlaybackSnapshot> legacy,Supplier<PlaybackSnapshot> kugou){this.source=source;this.legacy=legacy;this.kugou=kugou;}
    public PlaybackSnapshot snapshot(){return (source.get()==MusicSource.KUGOU?kugou:legacy).get();}
}
