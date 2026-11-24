package dev.lzasn.cloudxmusic.client.qqmusic;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

/** QQ 图片缓存独立于网易云；最多 64 张、2 MiB/张、2048x2048，后台下载及解码。 */
public final class QQArtworkCache implements AutoCloseable {
    @FunctionalInterface interface Loader {BufferedImage load(URI uri)throws Exception;}
    @FunctionalInterface interface RetryDelay {void pause(int attempt)throws InterruptedException;}
    static final class HttpFailure extends IOException {
        private final int status;
        HttpFailure(int status){super("QQ 图片 HTTP "+status);this.status=status;}
        int status(){return status;}
    }
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(QQArtworkCache.class);
    private final Loader loader;
    private final RetryDelay retryDelay;
    private final LongSupplier clock;
    private final Map<String,BufferedImage> images=new LinkedHashMap<>();
    private final Set<String> requested=new HashSet<>();
    private final Map<String,Long> failed=new LinkedHashMap<>();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"CloudXMusic-QQ-Artwork");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Consumer<Runnable> main;
    private int revision;
    private volatile long generation;
    private volatile boolean closed;
    QQArtworkCache(Consumer<Runnable> main){this.main=main;this.loader=this::download;this.retryDelay=attempt->Thread.sleep(250L*attempt);this.clock=System::nanoTime;}
    QQArtworkCache(Consumer<Runnable> main,Loader loader){this(main,loader,attempt->Thread.sleep(250L*attempt));}
    QQArtworkCache(Consumer<Runnable> main,Loader loader,RetryDelay retryDelay){this(main,loader,retryDelay,System::nanoTime);}
    QQArtworkCache(Consumer<Runnable> main,Loader loader,RetryDelay retryDelay,LongSupplier clock){this.main=main;this.loader=loader;this.retryDelay=retryDelay;this.clock=clock;}
    public int revision(){return revision;}
    /** 静态界面不会持续调用 image；退避结束后由客户端 tick 主动恢复失败任务。 */
    void tick(){
        if(closed)return;
        long now=clock.getAsLong();
        for(String url:new ArrayList<>(failed.keySet()))if(now>=failed.get(url))image(url);
    }
    boolean prefetch(String url){
        image(url);return images.containsKey(url)||requested.contains(url);
    }
    public BufferedImage image(String url){
        if(url==null||url.isBlank()||closed)return null;
        BufferedImage image=images.get(url);if(image!=null||requested.contains(url))return image;
        Long retry=failed.get(url);if(retry!=null&&clock.getAsLong()<retry)return null;
        URI uri;try{uri=URI.create(url);if(!allowed(uri))return null;}catch(IllegalArgumentException e){return null;}
        requested.add(url);long token=generation;
        try{io.execute(()->{
            BufferedImage loaded=null;
            try{loaded=loadWithRetry(uri,token);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception error){LOGGER.warn("[QQ封面] 加载失败，host={}，原因={}，30 秒后允许重试",uri.getHost(),error.getClass().getSimpleName());}
            BufferedImage result=loaded;
            main.accept(()->{
                if(closed||generation!=token)return;requested.remove(url);
                if(result!=null){failed.remove(url);if(images.size()>=64)images.remove(images.keySet().iterator().next());images.put(url,result);revision++;
                    LOGGER.info("[QQ封面] 加载成功，host={}，已刷新 UI 缓存版本={}",uri.getHost(),revision);}
                else{if(failed.size()>=64)failed.remove(failed.keySet().iterator().next());failed.put(url,clock.getAsLong()+TimeUnit.SECONDS.toNanos(30));}
            });
        });}catch(RejectedExecutionException e){requested.remove(url);}
        return null;
    }
    private BufferedImage loadWithRetry(URI uri,long token)throws Exception{
        List<URI> candidates=candidates(uri);Exception last=null;
        for(int attempt=0;attempt<3;attempt++){
            if(closed||generation!=token||Thread.currentThread().isInterrupted())throw new InterruptedException();
            URI candidate=candidates.get(Math.min(attempt,candidates.size()-1));
            try{
                BufferedImage result=loader.load(candidate);
                if(result==null)throw new IOException("QQ 封面返回空图片");
                return result;
            }catch(InterruptedException e){throw e;}
            catch(Exception failure){
                last=failure;
                boolean fallback=attempt+1<candidates.size();
                boolean temporary=!(failure instanceof HttpFailure httpFailure)
                        ||httpFailure.status()==408||httpFailure.status()==429||httpFailure.status()>=500;
                boolean retry=attempt<2&&(temporary||fallback);
                if(!retry)throw failure;
                LOGGER.info("[QQ封面] 后台重试，host={}，attempt={}/3，HTTP={}，备用地址={}",candidate.getHost(),attempt+2,
                        failure instanceof HttpFailure httpFailure?httpFailure.status():"无响应",fallback);
                if(temporary)retryDelay.pause(attempt+1);
            }
        }
        throw last;
    }
    /** 仅为已知专辑图片生成无鉴权备用图；不猜测头像或任意外部图片地址。 */
    static List<URI> candidates(URI uri){
        if(!allowed(uri))return List.of();
        String path=uri.getPath();
        if(path==null||!path.matches("/music/photo_new/T002R[0-9]+x[0-9]+M000[A-Za-z0-9]+\\.jpg"))return List.of(uri);
        var urls=new LinkedHashSet<URI>();urls.add(uri);
        urls.add(URI.create("https://y.gtimg.cn"+path.replaceFirst("T002R[0-9]+x[0-9]+","T002R150x150")));
        urls.add(URI.create("https://pic.y.qq.com"+path));
        return List.copyOf(urls);
    }
    static boolean allowed(URI uri){String h=uri.getHost();return "https".equals(uri.getScheme())&&uri.getUserInfo()==null&&(uri.getPort()==-1||uri.getPort()==443)&&h!=null&&(h.equals("gtimg.cn")||h.endsWith(".gtimg.cn")||h.equals("y.qq.com")||h.equals("music-file.y.qq.com")||h.equals("qpic.y.qq.com")||h.matches("pic[0-9]*\\.y\\.qq\\.com")||h.equals("q1.qlogo.cn")||h.equals("q.qlogo.cn")||h.equals("thirdqq.qlogo.cn")||h.equals("thirdwx.qlogo.cn")||h.equals("wx.qlogo.cn"));}
    static HttpRequest imageRequest(URI uri){
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12))
                .header("Referer","https://y.qq.com/")
                .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Accept","image/png,image/jpeg,image/*;q=0.8")
                .header("Accept-Encoding","identity").GET().build();
    }
    private BufferedImage download(URI start)throws Exception{
        URI uri=start;
        for(int redirects=0;redirects<4;redirects++){
            HttpRequest request=imageRequest(uri);
            CompletableFuture<HttpResponse<byte[]>> pending=http.sendAsync(request,info->new QQAuthHttp.LimitedBody());
            HttpResponse<byte[]> response;try{response=pending.get(12,TimeUnit.SECONDS);}finally{pending.cancel(true);}
            if(response.statusCode()>=300&&response.statusCode()<400){uri=uri.resolve(response.headers().firstValue("location").orElseThrow());if(!allowed(uri))throw new IOException("QQ 图片跳转地址无效");continue;}
            if(response.statusCode()!=200){
                // 不记录查询参数、Cookie 或响应正文，保留实机定位需要的状态码。
                LOGGER.warn("[QQ封面] 下载失败，HTTP={}，host={}，path={}",response.statusCode(),uri.getHost(),uri.getPath());
                throw new HttpFailure(response.statusCode());
            }
            return decode(response.body());
        }
        throw new IOException("QQ 图片跳转过多");
    }
    static BufferedImage decode(byte[] bytes)throws IOException{
        if(bytes.length>2*1024*1024)throw new IOException("QQ 封面数据过大");
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("QQ 封面不是图片");
            var reader=readers.next();
            try{
                reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>2048||height>2048)throw new IOException("QQ 封面尺寸过大");
                BufferedImage image=reader.read(0);double scale=Math.min(1,384.0/Math.max(width,height));
                if(scale==1)return image;
                BufferedImage thumb=new BufferedImage(Math.max(1,(int)(width*scale)),Math.max(1,(int)(height*scale)),BufferedImage.TYPE_INT_ARGB);
                var graphics=thumb.createGraphics();try{graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);graphics.drawImage(image,0,0,thumb.getWidth(),thumb.getHeight(),null);}finally{graphics.dispose();}
                return thumb;
            }finally{reader.dispose();}
        }
    }
    public void clear(){generation++;images.clear();requested.clear();failed.clear();io.getQueue().clear();revision++;}
    @Override public void close(){closed=true;clear();io.shutdownNow();http.shutdownNow();}
}
