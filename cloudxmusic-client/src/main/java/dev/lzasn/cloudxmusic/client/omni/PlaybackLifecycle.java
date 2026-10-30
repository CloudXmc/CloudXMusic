package dev.lzasn.cloudxmusic.client.omni;

import java.util.Objects;

/** 全局播放的世界生命周期门禁；变更由客户端线程提交，音频线程只读许可。 */
public final class PlaybackLifecycle implements AutoCloseable {
    private final Runnable stop;
    private volatile boolean allowed;
    private boolean stopped,disconnected,closed;
    public PlaybackLifecycle(Runnable stop){this.stop=Objects.requireNonNull(stop);}
    public boolean allowsPlayback(){return allowed;}
    public void update(boolean inWorld,boolean titleScreen){
        if(closed)return;
        if(!inWorld)disconnected=false;
        allowed=inWorld&&!titleScreen&&!disconnected;
        if(allowed)stopped=false;else stopOnce();
    }
    public void joined(){if(!closed)disconnected=false;}
    public void disconnect(){
        if(closed)return;
        // 先撤销许可，再取消取链、预取与声卡；旧回调即使先到达也不能发声。
        allowed=false;disconnected=true;stopOnce();
    }
    private void stopOnce(){if(stopped)return;stopped=true;stop.run();}
    @Override public void close(){if(closed)return;closed=true;allowed=false;stopOnce();}
}
