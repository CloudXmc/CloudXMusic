package dev.lzasn.cloudxmusic.client.omni;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.*;

/** 客户端线程提交选择；声卡确认后才授予新音源播放权。没有自动播放或账号业务。 */
public final class MusicSourceCoordinator implements AutoCloseable {
    public record View(MusicSource source,MusicSource requested,long revision,boolean switching,boolean error,String status){}
    private final Function<MusicSource,CompletableFuture<Void>> pause;
    private final Consumer<Runnable> main;
    private final Consumer<MusicSource> committed;
    private volatile View view;
    private long generation;
    private volatile boolean closed;
    private CompletableFuture<Void> barrier=CompletableFuture.completedFuture(null);
    public MusicSourceCoordinator(MusicSource initial,Function<MusicSource,CompletableFuture<Void>> pause,
                                  Consumer<Runnable> main,Consumer<MusicSource> committed){
        this.pause=Objects.requireNonNull(pause);this.main=Objects.requireNonNull(main);this.committed=Objects.requireNonNull(committed);
        view=new View(Objects.requireNonNull(initial),initial,0,false,false,"");
    }
    public View view(){return view;}
    public MusicSource source(){return view.source();}
    public boolean allowsPlayback(MusicSource source){View current=view;return !closed&&!current.switching()&&!current.error()&&current.source()==source;}
    public void select(MusicSource target){
        Objects.requireNonNull(target);if(closed||(!view.switching()&&!view.error()&&view.source()==target))return;
        long token=++generation;view=new View(view.source(),target,token,true,false,"正在切换到"+target.label()+"…");
        barrier=CompletableFuture.allOf(barrier,quiet(MusicSource.NETEASE),quiet(MusicSource.QQ));
        barrier.whenComplete((v,failure)->main.accept(()->{
            if(closed||token!=generation)return;
            if(failure!=null){view=new View(view.source(),target,token,false,true,"声卡交接失败，未切换音源；请重启游戏");return;}
            view=new View(target,target,token,false,false,"");committed.accept(target);
        }));
    }
    private CompletableFuture<Void> quiet(MusicSource source){
        try{return Objects.requireNonNull(pause.apply(source));}catch(Exception e){return CompletableFuture.failedFuture(e);}
    }
    @Override public void close(){closed=true;++generation;}
}
