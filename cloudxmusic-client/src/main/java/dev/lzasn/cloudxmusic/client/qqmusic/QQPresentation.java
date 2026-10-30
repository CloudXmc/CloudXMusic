package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 当前歌曲展示数据。代次保护覆盖换歌、换账号、切音源及关闭后的回调。 */
final class QQPresentation implements AutoCloseable {
    record View(SongView song,List<Lyric.Line> lyrics,String status,List<TimedLyrics.Line> timed) {
        View(SongView song,List<Lyric.Line> lyrics,String status){this(song,lyrics,status,List.of());}
    }
    @FunctionalInterface interface Metadata {QQTrackMetadata.Info load(UnifiedSong song)throws Exception;}
    @FunctionalInterface interface Lyrics {Lyric load(UnifiedSong song)throws Exception;}
    @FunctionalInterface interface DetailedLyrics {QQTimedLyrics load(UnifiedSong song)throws Exception;}
    private final Metadata metadata;
    private final DetailedLyrics lyricsLoader;
    private final Consumer<Runnable> main;
    private final LongSupplier account;
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"CloudXMusic-QQ-Presentation");t.setDaemon(true);return t;});
    private View view=new View(SongView.empty(MusicSource.QQ),List.of(),"");
    private String key="";
    private long generation;
    private Future<?> pending;
    private boolean closed;
    QQPresentation(QQProvider provider,Consumer<Runnable> main,LongSupplier account){this.metadata=provider::details;this.lyricsLoader=provider::timedLyrics;this.main=main;this.account=account;}
    QQPresentation(Metadata metadata,Lyrics lyrics,Consumer<Runnable> main,LongSupplier account){this.metadata=metadata;this.lyricsLoader=song->new QQTimedLyrics(lyrics.load(song),List.of());this.main=main;this.account=account;}
    View view(){return view;}
    void update(UnifiedSong song,boolean active){
        String next=active&&song!=null?song.platformId()+":"+account.getAsLong():"";
        if(next.equals(key)||closed)return;
        key=next;long token=++generation,revision=account.getAsLong();
        if(pending!=null)pending.cancel(true);io.getQueue().clear();io.purge();
        view=new View(song==null?SongView.empty(MusicSource.QQ):SongView.from(song,0,""),List.of(),next.isEmpty()?"":"正在加载封面和歌词…");
        if(next.isEmpty())return;
        pending=io.submit(()->{
            SongView display=SongView.from(song,0,"");List<Lyric.Line> lyrics=List.of();List<TimedLyrics.Line> timed=List.of();String warning="";
            try{var info=metadata.load(song);display=SongView.from(song,info.durationSeconds(),info.coverUrl(),info.vip());}
            catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            catch(Exception e){warning="QQ 封面信息加载失败";}
            SongView metadata=display;
            main.accept(()->{if(current(token,revision))view=new View(metadata,List.of(),"正在加载歌词…");});
            try{var loaded=lyricsLoader.load(song);lyrics=List.of(loaded.lyric().lines());timed=loaded.timed();if(lyrics.isEmpty())warning=warning.isEmpty()?"暂无逐行歌词":warning+" · 暂无逐行歌词";}
            catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            catch(Exception e){warning=warning.isEmpty()?"QQ 歌词加载失败":warning+" · 歌词加载失败";}
            View result=new View(display,lyrics,warning,timed);
            main.accept(()->{if(current(token,revision))view=result;});
        });
    }
    private boolean current(long token,long revision){return !closed&&generation==token&&account.getAsLong()==revision;}
    @Override public void close(){closed=true;generation++;if(pending!=null)pending.cancel(true);io.shutdownNow();view=new View(SongView.empty(MusicSource.QQ),List.of(),"");}
}
