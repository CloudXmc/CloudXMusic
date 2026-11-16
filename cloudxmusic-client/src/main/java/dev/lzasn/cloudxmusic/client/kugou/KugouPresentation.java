package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 当前歌词快照；换歌、切源或账号变更立即丢弃旧歌词及旧后台结果。 */
final class KugouPresentation implements AutoCloseable {
    record View(String hash,List<Lyric.Line> lyrics,List<TimedLyrics.Line> timed,String status){View{lyrics=List.copyOf(lyrics);timed=List.copyOf(timed);}}
    private final KugouProvider provider;private final Consumer<Runnable> main;private final LongSupplier account;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"CloudXMusic-Kugou-Lyrics");t.setDaemon(true);return t;});
    private View view=new View("",List.of(),List.of(),"");private String key="";private long generation;private boolean closed;private Future<?> pending;
    KugouPresentation(KugouProvider provider,Consumer<Runnable> main,LongSupplier account){this.provider=provider;this.main=main;this.account=account;}
    View view(){return view;}
    void update(UnifiedSong song,boolean active){
        String next=active&&song!=null?song.platformId()+":"+account.getAsLong():"";if(closed||next.equals(key))return;
        key=next;long token=++generation,revision=account.getAsLong();if(pending!=null)pending.cancel(true);worker.getQueue().clear();worker.purge();
        view=new View(song==null?"":song.platformId(),List.of(),List.of(),next.isEmpty()?"":"正在加载酷狗歌词…");if(next.isEmpty())return;
        pending=worker.submit(()->{
            KugouKrc.Lyrics lyrics=KugouKrc.EMPTY;String status="";
            try{lyrics=provider.lyrics(song);if(lyrics.lyric().isEmpty())status="暂无歌词";}
            catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(Exception e){status="酷狗歌词加载失败";}
            View result=new View(song.platformId(),List.of(lyrics.lyric().lines()),lyrics.timed(),status);
            main.accept(()->{if(!closed&&token==generation&&account.getAsLong()==revision)view=result;});
        });
    }
    @Override public void close(){closed=true;++generation;if(pending!=null)pending.cancel(true);worker.shutdownNow();view=new View("",List.of(),List.of(),"");}
}
