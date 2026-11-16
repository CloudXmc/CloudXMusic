package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.TimedLyrics;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.zip.InflaterInputStream;

/** KRC 解密、解压与逐字时间轴，保留中文与 Unicode 词片段；来源及许可见 THIRD_PARTY_NOTICES.md。 */
public final class KugouKrc {
    public record Lyrics(Lyric lyric,List<TimedLyrics.Line> timed){public Lyrics{timed=List.copyOf(timed);}}
    public static final Lyrics EMPTY=new Lyrics(Lyric.EMPTY,List.of());
    private static final int LIMIT=2*1024*1024;
    private static final int[] MASK={0x40,0x47,0x61,0x77,0x5e,0x32,0x74,0x47,0x51,0x36,0x31,0x2d,0xce,0xd2,0x6e,0x69};
    private static final Pattern LINE=Pattern.compile("^\\[(\\d+),(\\d+)](.*)$"),WORD=Pattern.compile("<(\\d+),(\\d+),\\d+>([^<]*)"),OFFSET=Pattern.compile("(?m)^\\[offset:([+-]?\\d+)]");
    private KugouKrc(){}
    public static Lyrics decode(String content)throws IOException {
        if(content==null||content.length()>3*1024*1024)throw new IOException("KRC 文件超过上限");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(content);}catch(IllegalArgumentException e){throw new IOException("KRC 编码无效");}
        if(bytes.length<=4||bytes[0]!='k'||bytes[1]!='r'||bytes[2]!='c'||bytes[3]!='1')throw new IOException("KRC 文件头无效");
        for(int i=4;i<bytes.length;i++)bytes[i]=(byte)(bytes[i]^MASK[(i-4)%16]);
        try(var input=new InflaterInputStream(new ByteArrayInputStream(bytes,4,bytes.length-4));var out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){if(out.size()+count>LIMIT)throw new IOException("KRC 解压超过上限");out.write(buffer,0,count);}
            return parse(out.toString(StandardCharsets.UTF_8));
        }
    }
    public static Lyrics parse(String text)throws IOException {
        if(text.length()>LIMIT)throw new IOException("KRC 文本超过上限");
        long offset=0;Matcher offsetTag=OFFSET.matcher(text);if(offsetTag.find())try{offset=Long.parseLong(offsetTag.group(1));}catch(NumberFormatException ignored){}
        List<String> translations=translations(text);List<TimedLyrics.Line> lines=new ArrayList<>();Map<Integer,String> translated=new HashMap<>();int row=0;
        for(String raw:text.split("\\r?\\n")){
            Matcher line=LINE.matcher(raw.trim());if(!line.matches())continue;
            String translation=row<translations.size()?translations.get(row):"";row++;
            try{
                long original=Long.parseLong(line.group(1)),duration=Long.parseLong(line.group(2));
                long start=Math.addExact(original,offset),end=Math.addExact(start,duration);
                if(start>Integer.MAX_VALUE||end>Integer.MAX_VALUE||end<0)continue;
                List<TimedLyrics.Word> words=new ArrayList<>();StringBuilder plain=new StringBuilder();Matcher word=WORD.matcher(line.group(3));
                while(word.find()){
                    long relative=Long.parseLong(word.group(1)),length=Long.parseLong(word.group(2));
                    if(relative>duration)continue;
                    long absolute=Math.addExact(start,relative),until=Math.addExact(absolute,Math.min(length,duration-relative));
                    if(until<0)continue;
                    int clipped=(int)Math.max(0,absolute);String value=word.group(3);
                    words.add(new TimedLyrics.Word(clipped,(int)Math.max(0,until-clipped),value));plain.append(value);
                }
                if(plain.toString().isBlank())continue;
                int clipped=(int)Math.max(0,start);lines.add(new TimedLyrics.Line(clipped,(int)Math.max(0,end-clipped),plain.toString(),words));
                if(!translation.isBlank())translated.put(clipped,translation);
            }catch(ArithmeticException|NumberFormatException ignored){/* 单行损坏不能让整首逐字歌词失效。 */}
        }
        lines.sort(Comparator.comparingInt(TimedLyrics.Line::startMs));StringBuilder lrc=new StringBuilder(),tlrc=new StringBuilder();
        for(var line:lines){String stamp=stamp(line.startMs());lrc.append(stamp).append(line.text()).append('\n');if(translated.containsKey(line.startMs()))tlrc.append(stamp).append(translated.get(line.startMs())).append('\n');}
        return new Lyrics(Lyric.parse(lrc.toString(),tlrc.toString()),lines);
    }
    private static String stamp(int ms){return String.format(Locale.ROOT,"[%02d:%02d.%03d]",ms/60000,ms/1000%60,ms%1000);}
    private static List<String> translations(String text){
        for(String raw:text.split("\\r?\\n"))if(raw.startsWith("[language:")&&raw.endsWith("]"))try{
            String json=new String(Base64.getDecoder().decode(raw.substring(10,raw.length()-1)),StandardCharsets.UTF_8);
            JsonObject root=JsonParser.parseString(json).getAsJsonObject();
            for(var entry:KugouJson.array(root,"content"))if(entry.isJsonObject()&&KugouJson.number(entry.getAsJsonObject(),"type")==1){
                List<String> rows=new ArrayList<>();
                for(var row:KugouJson.array(entry.getAsJsonObject(),"lyricContent")){StringBuilder value=new StringBuilder();if(row.isJsonArray())for(var part:row.getAsJsonArray())if(part.isJsonPrimitive())value.append(part.getAsString());rows.add(value.toString());}
                return rows;
            }
        }catch(RuntimeException ignored){/* 可选翻译损坏时仍使用主时间轴。 */}
        return List.of();
    }
}
