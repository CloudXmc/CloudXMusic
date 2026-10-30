package dev.lzasn.cloudxmusic.client.qqmusic;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

/** QQ 图片缓存独立于网易云；最多 64 张、2 MiB/张、2048x2048，后台下载及解码。 */
public final class QQArtworkCache implements AutoCloseable {
    @FunctionalInterface interface Loader {BufferedImage load(URI uri)throws Exception;}
    private final Loader loader;
    private final Map<String,BufferedImage> images=new LinkedHashMap<>();
    private final Set<String> requested=new HashSet<>();
    private final Map<String,Long> failed=new LinkedHashMap<>();
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"CloudXMusic-QQ-Artwork");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Consumer<Runnable> main;
    private int revision;
    private long generation;
    private boolean closed;
    QQArtworkCache(Consumer<Runnable> main){this.main=main;this.loader=this::download;}
    QQArtworkCache(Consumer<Runnable> main,Loader loader){this.main=main;this.loader=loader;}
    public int revision(){return revision;}
    boolean prefetch(String url){
        image(url);return images.containsKey(url)||requested.contains(url);
    }
    public BufferedImage image(String url){
        if(url==null||url.isBlank()||closed)return null;
        BufferedImage image=images.get(url);if(image!=null||requested.contains(url))return image;
        Long retry=failed.get(url);if(retry!=null&&System.nanoTime()<retry)return null;
        URI uri;try{uri=URI.create(url);if(!allowed(uri))return null;}catch(IllegalArgumentException e){return null;}
        requested.add(url);long token=generation;
        try{io.execute(()->{
            BufferedImage loaded=null;
            try{loaded=loader.load(uri);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception error){org.slf4j.LoggerFactory.getLogger(QQArtworkCache.class).debug("QQ 图片加载失败，主机={}，原因={}",uri.getHost(),error.getClass().getSimpleName());}
            BufferedImage result=loaded;
            main.accept(()->{
                if(closed||generation!=token)return;requested.remove(url);
                if(result!=null){failed.remove(url);if(images.size()>=64)images.remove(images.keySet().iterator().next());images.put(url,result);revision++;}
                else{if(failed.size()>=64)failed.remove(failed.keySet().iterator().next());failed.put(url,System.nanoTime()+TimeUnit.SECONDS.toNanos(30));}
            });
        });}catch(RejectedExecutionException e){requested.remove(url);}
        return null;
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
            if(response.statusCode()!=200)throw new IOException("QQ 图片 HTTP "+response.statusCode());
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
