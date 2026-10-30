package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.TimedLyrics;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** QQ 行时间 + 词后绝对时间。只取 LyricContent，不执行 XML 实体或外部 DTD。 */
public final class QQQrcParser {
    private static final Pattern ATTRIBUTE=Pattern.compile("LyricContent\\s*=\\s*([\"'])([\\s\\S]*?)\\1");
    private static final Pattern LINE=Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern WORD=Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
    private QQQrcParser() {}
    public static List<TimedLyrics.Line> parse(String xml)throws IOException{
        if(xml.length()>2*1024*1024)throw new IOException("QQ 逐字歌词过大");
        var attr=ATTRIBUTE.matcher(xml);String content=QQLyricsApi.entities(attr.find()?attr.group(2):xml);
        List<TimedLyrics.Line> result=new ArrayList<>();
        for(String raw:content.split("\\R")){
            var line=LINE.matcher(raw.strip());if(!line.matches())continue;
            int start=number(line.group(1)),duration=number(line.group(2));if(start<0||duration<0||(long)start+duration>Integer.MAX_VALUE)continue;
            String body=line.group(3);var matches=WORD.matcher(body);List<TimedLyrics.Word> words=new ArrayList<>();StringBuilder text=new StringBuilder();int end=0,last=start,previousEnd=start;
            while(matches.find()){
                int at=number(matches.group(2)),length=number(matches.group(3));String word=matches.group(1);
                if(at<last||length<0||(long)at+length>Integer.MAX_VALUE)throw new IOException("QQ 逐字歌词时间轴无效");
                text.append(word);words.add(new TimedLyrics.Word(at,length,word));last=at;previousEnd=at+length;end=matches.end();
            }
            String tail=body.substring(end);text.append(tail);
            if(!tail.isEmpty()&&!words.isEmpty())words.add(new TimedLyrics.Word(Math.max(previousEnd,start),Math.max(0,start+duration-Math.max(previousEnd,start)),tail));
            if(!text.toString().isBlank())result.add(new TimedLyrics.Line(start,duration,text.toString(),words));
            if(result.size()>20000)throw new IOException("QQ 歌词行数过多");
        }
        result.sort(Comparator.comparingInt(TimedLyrics.Line::startMs));return List.copyOf(result);
    }
    private static int number(String value){try{return Integer.parseInt(value);}catch(NumberFormatException e){return -1;}}
}
