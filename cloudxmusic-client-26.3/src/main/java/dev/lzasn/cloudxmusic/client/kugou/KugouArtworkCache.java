package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.util.Async;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** 酷狗专用封面缓存，独立于网易云和 QQ 的 CDN/缓存策略。 */
public final class KugouArtworkCache implements AutoCloseable {
    private static final int CACHE_LIMIT=64, PENDING_LIMIT=8;
    private static final long RETRY_NANOS=TimeUnit.SECONDS.toNanos(30);
    private final Map<String,BufferedImage> ready=new LinkedHashMap<>(64,.75f,true);
    private final Map<String,Long> failed=new LinkedHashMap<>(64,.75f,true);
    private final Set<String> loading=new HashSet<>();
    private final ExecutorService pool;
    private final Consumer<Runnable> main;
    private final Function<String,BufferedImage> download;
    private final LongSupplier clock;
    private long generation;
    private int revision; private boolean closed;
    public KugouArtworkCache(){
        this(Executors.newFixedThreadPool(2,r->{var t=new Thread(r,"CloudXMusic-Kugou-Cover");t.setDaemon(true);return t;}),Async::main,KugouArtworkCache::download,System::nanoTime);
    }
    KugouArtworkCache(ExecutorService pool,Consumer<Runnable> main,Function<String,BufferedImage> download,LongSupplier clock){
        this.pool=Objects.requireNonNull(pool);this.main=Objects.requireNonNull(main);this.download=Objects.requireNonNull(download);this.clock=Objects.requireNonNull(clock);
    }
    public synchronized int revision(){return revision;}
    public synchronized BufferedImage image(String url){
        if(closed||url==null||url.isBlank())return null;
        var hit=ready.get(url);if(hit!=null)return hit;
        Long lastFailure=failed.get(url);
        if(lastFailure!=null&&clock.getAsLong()-lastFailure<RETRY_NANOS)return null;
        // 画布会重复索要同一封面；失败退避与待处理上限避免每帧重试和无限排队。
        if(loading.contains(url)||loading.size()>=PENDING_LIMIT)return null;
        loading.add(url);long token=generation;
        try {pool.execute(()->{
            BufferedImage image=null;try{image=download.apply(url);}catch(RuntimeException ignored){}
            BufferedImage result=image;main.accept(()->complete(url,token,result));
        });}catch(RejectedExecutionException ignored){loading.remove(url);}
        return null;
    }
    private synchronized void complete(String url,long token,BufferedImage image){
        loading.remove(url);if(closed||token!=generation)return;
        if(image==null){putBounded(failed,url,clock.getAsLong());return;}
        failed.remove(url);putBounded(ready,url,image);revision++;
    }
    private static <T> void putBounded(Map<String,T> map,String key,T value){
        if(!map.containsKey(key)&&map.size()>=CACHE_LIMIT)map.remove(map.keySet().iterator().next());map.put(key,value);
    }
    private static BufferedImage download(String url){
        try{
            var uri=URI.create(url);if(!"https".equalsIgnoreCase(uri.getScheme())&&!"http".equalsIgnoreCase(uri.getScheme()))return null;
            var connection=(HttpURLConnection)uri.toURL().openConnection();
            connection.setConnectTimeout(6000);connection.setReadTimeout(8000);connection.setRequestProperty("User-Agent","CloudXMusic/2.6");
            try(var in=connection.getInputStream()){
                var data=in.readNBytes(2*1024*1024+1);if(data.length>2*1024*1024)return null;
                // 解码前检查尺寸，限制压缩很小但解码后占用过大的图片。
                try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(data))){
                    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return null;
                    var reader=readers.next();try{reader.setInput(input);if(reader.getWidth(0)>2048||reader.getHeight(0)>2048)return null;return reader.read(0);}finally{reader.dispose();}
                }
            }finally{connection.disconnect();}
        }catch(Exception ignored){return null;}
    }
    public synchronized void clear(){generation++;ready.clear();failed.clear();revision++;}
    @Override public synchronized void close(){if(closed)return;closed=true;clear();loading.clear();pool.shutdownNow();}
}
