package dev.lzasn.cloudxmusic.client.omni;

import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import java.util.List;

/** 只描述搜索、取链和歌词；登录态由各音源自行管理。 */
public interface OnlineMusicProvider {
    String sourceId();

    /** offset 从 0 开始；网络方法可能阻塞，调用方必须放在后台线程执行。 */
    List<UnifiedSong> searchSongs(String keyword, int offset, int limit) throws Exception;

    /** 没有可用地址时返回 null，调用方负责向用户反馈。 */
    String getPlaybackUrl(UnifiedSong song) throws Exception;

    /** 复用现有公开歌词模型，无歌词时返回 Lyric.EMPTY。 */
    Lyric getLyrics(UnifiedSong song) throws Exception;
}
