package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.TimedLyrics;
import java.util.List;

/** 同时保留逐行翻译与可选逐字原文，普通 LRC 的公共契约保持不变。 */
public record QQTimedLyrics(Lyric lyric,List<TimedLyrics.Line> timed) {
    public QQTimedLyrics {timed=List.copyOf(timed);}
}
