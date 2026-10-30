package dev.lzasn.cloudxmusic.client.omni;

import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.state.*;
import java.util.*;

/** 界面和 HUD 共用的只读播放快照；所有集合复制后发布。 */
public record PlaybackSnapshot(MusicSource source,SongView song,List<SongView> queue,List<Lyric.Line> lyrics,
                               boolean playing,boolean loading,double position,double duration,float volume,
                               String quality,String preferredQuality,LyricMode lyricMode,PlaybackMode playbackMode,
                               boolean favorite,String status,boolean error,List<TimedLyrics.Line> timedLyrics) {
    public PlaybackSnapshot {queue=List.copyOf(queue);lyrics=List.copyOf(lyrics);timedLyrics=List.copyOf(timedLyrics);position=Math.max(0,position);duration=Math.max(0,duration);}
    public PlaybackSnapshot(MusicSource source,SongView song,List<SongView> queue,List<Lyric.Line> lyrics,
                            boolean playing,boolean loading,double position,double duration,float volume,
                            String quality,String preferredQuality,LyricMode lyricMode,PlaybackMode playbackMode,
                            boolean favorite,String status,boolean error){this(source,song,queue,lyrics,playing,loading,position,duration,volume,quality,preferredQuality,lyricMode,playbackMode,favorite,status,error,List.of());}
    public TimedLyrics.Line timedLine(){return TimedLyrics.at(timedLyrics,(int)(position*1000));}
    public boolean hasTrack(){return !song.platformId().isBlank();}
    public int lyricIndexAt(int milliseconds){int low=0,high=lyrics.size()-1,found=-1;while(low<=high){int m=(low+high)>>>1;if(lyrics.get(m).timeMs()<=milliseconds){found=m;low=m+1;}else high=m-1;}return found;}
    public Lyric.Line lineAt(int milliseconds){int index=lyricIndexAt(milliseconds);return index<0?null:lyrics.get(index);}
    public String positionText(){return Track.format((int)position);}
}
