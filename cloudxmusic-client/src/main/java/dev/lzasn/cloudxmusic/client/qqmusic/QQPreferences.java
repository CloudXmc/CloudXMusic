package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** QQ 独立偏好；磁盘读写串行，滑块保存合并为一次。未知配置保留。 */
final class QQPreferences implements AutoCloseable {
    record View(QQQuality quality, float volume, LyricMode lyrics, String error) {}
    private final QQMusicConfig config;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {Thread t=new Thread(r,"CloudXMusic-QQ-Preferences");t.setDaemon(true);return t;});
    private volatile View view = new View(QQQuality.SQ,.8f,LyricMode.BILINGUAL,"");
    private JsonObject document;
    private ScheduledFuture<?> pending;
    private boolean changed;
    private volatile boolean closed;
    QQPreferences(QQMusicConfig config, Consumer<Runnable> main, Consumer<View> restored) {
        this.config=config;
        io.execute(() -> {
            try {
                JsonObject data=config.load();
                synchronized(this) {
                    document=data;
                    if(!changed) {
                        QQQuality quality=data.has("quality")?QQQuality.fromConfig(data.get("quality").getAsString()):QQQuality.SQ;
                        float volume=data.has("volume")?data.get("volume").getAsFloat():.8f;
                        if(!Float.isFinite(volume)||volume<0||volume>1)throw new IllegalArgumentException();
                        LyricMode lyrics=data.has("lyricMode")?LyricMode.valueOf(data.get("lyricMode").getAsString()):LyricMode.BILINGUAL;
                        view=new View(quality,volume,lyrics,"");
                    }
                }
                synchronized(this){if(changed&&!closed)io.execute(this::save);}
                main.accept(() -> {if(!closed)restored.accept(view);});
            } catch(Exception e) { view=new View(view.quality,view.volume,view.lyrics,"QQ 偏好读取失败，原文件已保留"); }
        });
    }
    View view(){return view;}
    synchronized void update(QQQuality quality,float volume,LyricMode lyrics) {
        if(closed)return;
        changed=true;view=new View(quality,Math.max(0,Math.min(1,volume)),lyrics,view.error);
        if(pending!=null)pending.cancel(false);
        pending=io.schedule(this::save,350,TimeUnit.MILLISECONDS);
    }
    private void save(){
        View v;
        synchronized(this){
            // 只在锁内复制快照；磁盘写入期间客户端线程仍能调节音量。
            if(!changed||document==null)return;
            v=view;
        }
        try{
            JsonObject next=config.update(current->{current.addProperty("quality",v.quality.name());current.addProperty("volume",v.volume);current.addProperty("lyricMode",v.lyrics.name());});
            synchronized(this){document=next;if(view==v){changed=false;view=new View(v.quality,v.volume,v.lyrics,"");}}
        }
        catch(Exception e){synchronized(this){View current=view;view=new View(current.quality,current.volume,current.lyrics,"QQ 偏好保存失败");}}
    }
    @Override public synchronized void close(){if(closed)return;closed=true;if(pending!=null)pending.cancel(false);io.execute(this::save);io.shutdown();}
}
