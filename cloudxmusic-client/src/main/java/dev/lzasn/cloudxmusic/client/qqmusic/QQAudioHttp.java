package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.regex.*;

/** 无凭证 CDN 顺序流：首包有超时，正文由播放器空闲监控管理；断流按字节有界续传。 */
final class QQAudioHttp implements AutoCloseable {
    private static final String USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    private static final Pattern CONTENT_RANGE=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)",Pattern.CASE_INSENSITIVE);
    private final HttpClient client;
    private final Duration headerTimeout;
    private final Predicate<URI> allowed;
    QQAudioHttp(){this(Duration.ofSeconds(12),QQAudioUrlVerifier::allowed);}
    // 仅测试允许替换地址检查和首包期限；正式实例始终限制 QQ CDN。
    QQAudioHttp(Duration timeout,Predicate<URI> allowed){
        if(timeout.isZero()||timeout.isNegative())throw new IllegalArgumentException("首包超时必须为正数");
        this.headerTimeout=timeout;this.allowed=Objects.requireNonNull(allowed);
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }
    static final class Failure extends IOException {
        Failure(String message,Throwable cause){super(message,cause);}
        Failure(String message){super(message);}
    }
    InputStream open(URI uri,BooleanSupplier cancelled,Consumer<CompletableFuture<?>> pending,LongConsumer progress)throws IOException,InterruptedException{
        Stream stream=new Stream(uri,cancelled,pending,progress);
        stream.connect();return new BufferedInputStream(stream,64*1024);
    }
    private final class Stream extends InputStream {
        private URI uri;
        private final BooleanSupplier cancelled;
        private final Consumer<CompletableFuture<?>> pending;
        private final LongConsumer progress;
        private volatile boolean closed;
        private volatile InputStream body;
        private long offset,total=-1;
        private String validator;
        private int retries;
        Stream(URI uri,BooleanSupplier cancelled,Consumer<CompletableFuture<?>> pending,LongConsumer progress){
            this.uri=uri;this.cancelled=cancelled;this.pending=pending;this.progress=progress;
        }
        private boolean stopped(){return closed||cancelled.getAsBoolean()||Thread.currentThread().isInterrupted();}
        private void connect()throws IOException,InterruptedException{
            for(int redirects=0;redirects<=3;redirects++){
                if(stopped())throw new InterruptedException("QQ 音频连接已取消");
                if(!allowed.test(uri)||(!QQAudioUrlVerifier.extension(uri,true)&&!QQAudioUrlVerifier.extension(uri,false)))
                    throw new Failure("QQ 音频地址不受支持");
                // 不给整段音频设置总下载期限；只等待响应头，正文可连续播放数分钟。
                HttpRequest.Builder builder=HttpRequest.newBuilder(uri).header("User-Agent",USER_AGENT)
                        .header("Referer","https://y.qq.com/").header("Accept-Encoding","identity")
                        .header("Accept","audio/flac, audio/mpeg, application/octet-stream;q=0.9, */*;q=0.8")
                        .header("Range","bytes="+offset+"-");
                if(offset>0&&validator!=null)builder.header("If-Range",validator);
                CompletableFuture<HttpResponse<InputStream>> request=client.sendAsync(builder.GET().build(),HttpResponse.BodyHandlers.ofInputStream());
                pending.accept(request);HttpResponse<InputStream> response;
                try{response=request.get(headerTimeout.toNanos(),TimeUnit.NANOSECONDS);}
                catch(InterruptedException e){request.cancel(true);Thread.currentThread().interrupt();throw e;}
                catch(ExecutionException|TimeoutException e){request.cancel(true);throw new Failure("QQ 网络连接失败或首包超时",e);}
                InputStream received=response.body();boolean accepted=false;
                try{
                    if(stopped())throw new InterruptedException("QQ 音频连接已取消");
                    int code=response.statusCode();
                    if(code==301||code==302||code==303||code==307||code==308){
                        try{uri=uri.resolve(response.headers().firstValue("Location").orElseThrow(()->new Failure("QQ CDN 重定向缺少地址")));}
                        catch(IllegalArgumentException e){throw new Failure("QQ CDN 重定向地址无效",e);}
                        continue;
                    }
                    if(code!=200&&code!=206)throw new Failure("QQ 网络音频 HTTP 状态码："+code);
                    if(!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity"))
                        throw new Failure("QQ 网络音频返回了不支持的压缩编码");
                    String type=response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                    if(type.startsWith("text/")||type.contains("json")||type.contains("xml"))throw new Failure("QQ 网络音频返回了错误页，未送入解码器");
                    long length=response.headers().firstValueAsLong("Content-Length").orElse(-1),nextTotal=length;
                    if(code==206){
                        Matcher range=CONTENT_RANGE.matcher(response.headers().firstValue("Content-Range").orElse(""));
                        if(!range.matches())throw new Failure("QQ CDN 续传缺少有效 Content-Range");
                        long start=Long.parseLong(range.group(1)),end=Long.parseLong(range.group(2));nextTotal=Long.parseLong(range.group(3));
                        if(start!=offset||nextTotal<=start||end!=nextTotal-1||(length>=0&&length!=nextTotal-start))
                            throw new Failure("QQ CDN 续传字节范围不匹配，已停止以避免音频错位");
                    }else if(offset>0)throw new Failure("QQ CDN 未接受断点续传，请重新点歌");
                    if(total>=0&&nextTotal!=total)throw new Failure("QQ CDN 续传文件长度发生变化");
                    if(nextTotal==0)throw new Failure("QQ 网络音频为空");
                    String nextValidator=response.headers().firstValue("ETag").filter(v->!v.startsWith("W/"))
                            .orElseGet(()->response.headers().firstValue("Last-Modified").orElse(null));
                    if(offset>0&&validator!=null&&!validator.equals(nextValidator))throw new Failure("QQ CDN 续传文件标识发生变化");
                    total=nextTotal;validator=nextValidator;body=received;accepted=true;
                    if(stopped()){close();throw new InterruptedException("QQ 音频连接已取消");}
                    return;
                }catch(NumberFormatException e){throw new Failure("QQ CDN 音频长度字段无效",e);}
                finally{if(!accepted)received.close();}
            }
            throw new Failure("QQ CDN 重定向次数过多");
        }
        @Override public int read()throws IOException{byte[] single=new byte[1];return read(single,0,1)<0?-1:single[0]&255;}
        @Override public int read(byte[] bytes,int start,int length)throws IOException{
            Objects.checkFromIndexSize(start,length,bytes.length);if(length==0)return 0;
            while(true){
                if(stopped())throw new InterruptedIOException("QQ 网络音频读取已取消");
                if(total>=0&&offset==total)return -1;
                try{
                    int count=body.read(bytes,start,length);
                    if(count<0&&total>=0&&offset<total)throw new EOFException("QQ 网络音频未达到声明长度："+offset+"/"+total);
                    if(count>0){offset+=count;if(total>=0&&offset>total)throw new Failure("QQ 网络音频超过声明长度");progress.accept(count);}
                    return count;
                }catch(IOException e){
                    if(stopped())throw new InterruptedIOException("QQ 网络音频读取已取消");
                    if(e instanceof Failure)throw e;
                    if(retries++>=2)throw new Failure("QQ 网络音频流中断，续传两次仍失败，请重新点歌",e);
                    try{body.close();}catch(IOException ignored){}
                    try{connect();}
                    catch(InterruptedException interrupted){Thread.currentThread().interrupt();InterruptedIOException stopped=new InterruptedIOException("QQ 网络续传已取消");stopped.initCause(interrupted);throw stopped;}
                    catch(IOException failure){failure.addSuppressed(e);throw failure;}
                }
            }
        }
        @Override public void close()throws IOException{closed=true;InputStream input=body;if(input!=null)input.close();}
    }
    @Override public void close(){client.shutdownNow();}
}
