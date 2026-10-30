package dev.lzasn.cloudxmusic.client.omni;

import java.util.List;

/** 可选的逐字时间轴。一个词可含多个 Unicode 字符，原始服务时间不按 UTF-16 人为拆分。 */
public final class TimedLyrics {
    private TimedLyrics() {}
    public record Word(int startMs,int durationMs,String text) {
        public Word {if(startMs<0||durationMs<0||text==null)throw new IllegalArgumentException("逐字歌词时间无效");}
        public double progress(double positionMs){return durationMs==0?(positionMs>=startMs?1:0):Math.clamp((positionMs-startMs)/durationMs,0,1);}
    }
    public record Line(int startMs,int durationMs,String text,List<Word> words) {
        public Line {words=List.copyOf(words);if(startMs<0||durationMs<0||text==null)throw new IllegalArgumentException("歌词时间无效");}
    }
    public static Line at(List<Line> lines,int milliseconds){
        int lo=0,hi=lines.size()-1,found=-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;if(lines.get(mid).startMs()<=milliseconds){found=mid;lo=mid+1;}else hi=mid-1;}
        return found<0?null:lines.get(found);
    }
}
