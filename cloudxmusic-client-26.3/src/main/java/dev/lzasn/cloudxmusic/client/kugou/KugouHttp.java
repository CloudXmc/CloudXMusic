package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 只在工作线程调用；不安装 JVM 全局 CookieHandler，不向 CDN 发送账号。 */
final class KugouHttp implements KugouTransport,AutoCloseable {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("CloudXMusic/Kugou");
    static final String USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
    private static final Set<String> HOSTS=Set.of("msearchretry.kugou.com","login-user.kugou.com","loginservice.kugou.com","gateway.kugou.com","wwwapi.kugou.com","lyrics.kugou.com","kugouvip.kugou.com");
    private final ExecutorService reader=new ThreadPoolExecutor(3,3,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(8),
            r->{Thread t=new Thread(r,"CloudXMusic-Kugou-Http");t.setDaemon(true);return t;});
    private final Semaphore permits=new Semaphore(8);
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Set<CompletableFuture<?>> pending=ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.atomic.AtomicLong playbackRequests=new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean closed;
    @Override public JsonObject request(String endpoint,Map<String,String> params,Map<String,String> headers,String body,CookieManager cookies)throws Exception {
        if(closed)throw new IOException("酷狗 HTTP 已关闭");
        if(!permits.tryAcquire())throw new IOException("酷狗 API 请求繁忙，请稍后重试");
        try{return execute(endpoint,params,headers,body,cookies);}finally{permits.release();}
    }
    private JsonObject execute(String endpoint,Map<String,String> params,Map<String,String> headers,String body,CookieManager cookies)throws Exception {
        URI uri=URI.create(endpoint+(params.isEmpty()?"":"?"+form(params)));
        if(!"https".equals(uri.getScheme())||!HOSTS.contains(uri.getHost())||uri.getRawUserInfo()!=null||uri.getPort()!=-1)throw new IOException("酷狗 API 地址不受支持");
        HttpRequest.Builder request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent",USER_AGENT).header("Referer","https://www.kugou.com/").header("Accept","application/json").header("Accept-Encoding","identity");
        cookies.get(uri,Map.of()).forEach((name,values)->{if(name.equalsIgnoreCase("Cookie")&&!values.isEmpty())request.header(name,String.join("; ",values));});
        // 显式路由头需要覆盖默认浏览器头，避免同一请求出现两个 User-Agent。
        headers.forEach(request::setHeader);
        if(body==null)request.GET();else request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        HttpRequest outgoing=request.build();
        boolean playback=uri.getPath().equals("/play/songinfo")||uri.getPath().equals("/v5/url");
        long requestId=playback?playbackRequests.incrementAndGet():0;
        // 完整请求与回包只供排障；默认日志保留路由、错误码和实际音频，避免反复输出大段 JSON。
        if(playback&&LOG.isDebugEnabled())LOG.debug("[酷狗取链请求] id={} request={}",requestId,KugouPlaybackDiagnostics.request(endpoint,params,outgoing.headers().map(),body));
        CompletableFuture<HttpResponse<InputStream>> sent=client.sendAsync(outgoing,HttpResponse.BodyHandlers.ofInputStream());pending.add(sent);
        CompletableFuture<JsonObject> parsed=sent.thenApplyAsync(response->{
            try(InputStream stream=response.body()){
                byte[] bytes=stream.readNBytes(4*1024*1024+1);
                if(playback){
                    LOG.debug("[酷狗取链HTTP] id={} route={} http={}",requestId,uri.getPath(),response.statusCode());
                    // 在 HTTP/JSON 校验前记录脱敏回包，保留 4xx 的真实服务端错误。
                    if(LOG.isDebugEnabled())LOG.debug("[酷狗取链响应] id={} json={}",requestId,bytes.length<=4*1024*1024?
                            KugouPlaybackDiagnostics.response(bytes,params,outgoing.headers().map()):"{\"response_too_large\":true}");
                }
                if(response.statusCode()!=200)throw new ResponseFailure("酷狗 API HTTP 状态码："+response.statusCode());
                cookies.put(uri,response.headers().map());return parseResponse(response.statusCode(),bytes);
            }catch(IOException e){throw new CompletionException(e);}
            catch(RuntimeException e){throw new CompletionException(new ResponseFailure("酷狗 API JSON 响应无效"));}
        },reader);pending.add(parsed);
        try{return parsed.get(18,TimeUnit.SECONDS);}
        catch(ExecutionException e){
            Throwable cause=e.getCause();while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();
            if(cause instanceof ResponseFailure failure)throw failure;
            if(cause instanceof HttpTimeoutException)throw new HttpTimeoutException("酷狗 API 网络请求超时");
            throw new IOException("酷狗 API 网络或响应读取失败（"+cause.getClass().getSimpleName()+"）");
        }
        catch(TimeoutException e){throw new HttpTimeoutException("酷狗 API 响应超时");}
        finally{parsed.cancel(true);sent.cancel(true);pending.remove(parsed);pending.remove(sent);
            if(sent.isDone()&&!sent.isCompletedExceptionally()){var response=sent.getNow(null);if(response!=null)try{response.body().close();}catch(IOException ignored){}}}
    }
    private static final class ResponseFailure extends IOException{ResponseFailure(String message){super(message);}}
    static JsonObject parseResponse(int status,byte[] bytes)throws IOException{
        if(status!=200)throw new ResponseFailure("酷狗 API HTTP 状态码："+status);
        if(bytes.length>4*1024*1024)throw new ResponseFailure("酷狗 API 响应超过上限");
        try{return JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();}
        catch(RuntimeException e){throw new ResponseFailure("酷狗 API JSON 响应无效");}
    }
    static String form(Map<String,String> values){return values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
    @Override public void close(){closed=true;pending.forEach(f->f.cancel(true));pending.clear();client.shutdownNow();reader.shutdownNow();}
}
