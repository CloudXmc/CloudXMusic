package dev.lzasn.cloudxmusic.client.kugou;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 酷狗单曲搜索控制器；关闭页面可取消请求，不借用 QQ 搜索枚举。 */
public final class KugouSearchController implements AutoCloseable {
    public record View(String keyword,List<KugouTrack> songs,boolean loading,String status){public View{songs=List.copyOf(songs);}}
    private final KugouProvider provider;private final Consumer<Runnable> main;
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"CloudXMusic-Kugou-Search");t.setDaemon(true);return t;});
    private View view=new View("",List.of(),false,"");private long generation;private Future<?> pending;private boolean closed;
    public KugouSearchController(KugouProvider provider,Consumer<Runnable> main){this.provider=provider;this.main=main;}
    public View view(){return view;}
    public void search(String keyword,int offset,int limit){
        if(closed)return;long token=++generation;if(pending!=null)pending.cancel(true);worker.getQueue().clear();worker.purge();
        view=new View(keyword,List.of(),true,"正在搜索酷狗音乐…");
        pending=worker.submit(()->{List<KugouTrack> songs=List.of();String status="";try{songs=provider.searchTracks(keyword,offset,limit);if(songs.isEmpty())status="没有找到歌曲";}
            catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(Exception e){status="酷狗搜索失败，请稍后重试";}
            View result=new View(keyword,songs,false,status);main.accept(()->{if(!closed&&token==generation)view=result;});});
    }
    @Override public void close(){closed=true;++generation;if(pending!=null)pending.cancel(true);worker.shutdownNow();view=new View("",List.of(),false,"");}
}
