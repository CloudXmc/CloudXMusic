package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 本地收藏与偏好。客户端线程持有列表；单个后台写入器合并滑块产生的连续保存。 */
public final class KugouLibrary implements AutoCloseable {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static final int LIMIT=1000;
    private record Saved(List<KugouTrack> favorites,KugouQuality quality,float volume,LyricMode lyrics) {}
    private final Path file;
    private final LinkedHashMap<String,KugouTrack> favorites=new LinkedHashMap<>();
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"CloudXMusic-Kugou-Library");t.setDaemon(false);return t;});
    private final AtomicReference<String> pending=new AtomicReference<>();
    private final AtomicBoolean writing=new AtomicBoolean();
    private KugouQuality quality=KugouQuality.HIGH;
    private float volume=.8f;
    private LyricMode lyrics=LyricMode.ORIGINAL;
    private volatile String status="";
    private boolean closed;
    public static KugouLibrary open(Path home) {return new KugouLibrary(home.resolve("kugou/library.json"));}
    private KugouLibrary(Path file) {
        this.file=file;
        if(!Files.exists(file))return;
        try {
            if(Files.size(file)>4*1024*1024)throw new IOException("oversize library");
            Saved saved=GSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Saved.class);
            if(saved==null)throw new IOException("empty library");
            if(saved.favorites()!=null)for(var track:saved.favorites()) {
                validate(track);if(favorites.size()<LIMIT)favorites.put(track.song().platformId(),track);
            }
            quality=saved.quality()==null?quality:saved.quality();
            if(Float.isFinite(saved.volume()))volume=Math.clamp(saved.volume(),0f,1f);
            lyrics=saved.lyrics()==null?lyrics:saved.lyrics();
        } catch(Exception invalid) {favorites.clear();status="酷狗本地收藏读取失败，原文件已保留";}
    }
    private static void validate(KugouTrack track) {
        if(track==null||track.song()==null||!"kugou".equals(track.song().source())||!track.song().platformId().matches("(?i)[a-f0-9]{32}"))throw new IllegalArgumentException("非酷狗曲目");
    }
    public List<KugouTrack> favorites(){return List.copyOf(favorites.values());}
    public boolean contains(UnifiedSong song){return song!=null&&"kugou".equals(song.source())&&favorites.containsKey(song.platformId());}
    public KugouQuality quality(){return quality;}
    public float volume(){return volume;}
    public LyricMode lyricMode(){return lyrics;}
    public String status(){return status;}
    public void toggle(KugouTrack track) {
        if(closed)return;validate(track);
        if(favorites.containsKey(track.song().platformId()))favorites.remove(track.song().platformId());
        else if(favorites.size()<LIMIT)favorites.put(track.song().platformId(),track);
        else {status="本地收藏已达 1000 首上限";return;}
        save();
    }
    public void preferences(KugouQuality quality,float volume,LyricMode lyrics) {
        if(closed||!Float.isFinite(volume))return;
        this.quality=Objects.requireNonNull(quality);this.volume=Math.clamp(volume,0f,1f);this.lyrics=Objects.requireNonNull(lyrics);save();
    }
    private void save(){pending.set(GSON.toJson(new Saved(favorites(),quality,volume,lyrics)));schedule();}
    private void schedule() {
        if(!writing.compareAndSet(false,true))return;
        try {writer.execute(()->{
            try {drainPending();}
            finally {writing.set(false);if(pending.get()!=null&&!writer.isShutdown())schedule();}
        });}catch(RejectedExecutionException closing){writing.set(false);}
    }
    private void drainPending(){String json;while((json=pending.getAndSet(null))!=null)write(json);}
    private void write(String json) {
        Path temp=null;
        try {
            Files.createDirectories(file.getParent());temp=Files.createTempFile(file.getParent(),"library-",".tmp");
            Files.writeString(temp,json,StandardCharsets.UTF_8);
            try {Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
            status="";
        } catch(IOException error) {status="酷狗本地收藏保存失败，请检查目录权限";}
        finally {if(temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){}}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        // 保存线程刚退出循环时可能又收到最后一次滑块更新，关闭前补一次串行排空。
        writer.execute(this::drainPending);writer.shutdown();
        try {if(!writer.awaitTermination(3,TimeUnit.SECONDS))writer.shutdownNow();}
        catch(InterruptedException interrupted){writer.shutdownNow();Thread.currentThread().interrupt();}
    }
}
