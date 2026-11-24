package dev.lzasn.cloudxmusic.client.kugou;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** 无账号的 CDN 小包检查：42 字节足够检查 FLAC STREAMINFO，不预下载整首。 */
final class KugouAudioUrlVerifier {
    private static final int PREFIX_BYTES=8192;
    private KugouAudioUrlVerifier() {}
    static boolean allowed(URI uri) {
        String host = uri.getHost(); if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                && (uri.getPort() == -1 || uri.getPort() == 80 || uri.getPort() == 443)
                && (host.equals("kugou.com") || host.endsWith(".kugou.com") || host.equals("kugou.net") || host.endsWith(".kugou.net"));
    }
    static URI verify(URI uri, boolean flac, Duration timeout) throws IOException, InterruptedException {
        return verify(uri, flac, timeout, KugouAudioUrlVerifier::allowed);
    }
    /** 仅测试允许注入回环地址；正式调用固定使用 Kugou CDN 域名检查。 */
    static URI verify(URI uri, boolean flac, Duration timeout, java.util.function.Predicate<URI> allowed) throws IOException, InterruptedException {
        Prefix result=inspect(uri,timeout,allowed);
        return result==null||audioHeader(result.bytes(),true)!=flac?null:result.uri();
    }
    private record Prefix(URI uri,byte[] bytes){}
    static KugouPlaybackResolver.Verified probe(URI uri,KugouQuality requested,long bitrate)throws IOException,InterruptedException {
        return probe(uri,requested,bitrate,Duration.ofSeconds(8),KugouAudioUrlVerifier::allowed);
    }
    static KugouPlaybackResolver.Verified probe(URI uri,KugouQuality requested,long bitrate,Duration timeout,
                                                java.util.function.Predicate<URI> allowed)throws IOException,InterruptedException {
        Prefix result=inspect(uri,timeout,allowed);
        if(result==null)return null;
        KugouQuality actual;
        if(audioHeader(result.bytes(),true)){
            var info=new io.nayuki.flac.common.StreamInfo(Arrays.copyOfRange(result.bytes(),8,42));
            actual=info.sampleDepth>16||info.sampleRate>48000?KugouQuality.HIGH:KugouQuality.FLAC;
        }else {
            long bitsPerSecond=bitrate>0?(bitrate<=1000?bitrate*1000:bitrate):mp3BitrateKbps(result.bytes())*1000L;
            actual=bitsPerSecond>=320000?KugouQuality.HQ:KugouQuality.STANDARD;
        }
        return new KugouPlaybackResolver.Verified(result.uri(),actual);
    }
    private static Prefix inspect(URI uri,Duration timeout,java.util.function.Predicate<URI> allowed)throws IOException,InterruptedException{
        long deadline = System.nanoTime() + timeout.toNanos();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofNanos(Math.min(timeout.toNanos(), Duration.ofSeconds(5).toNanos())))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        try {
            for (int redirects = 0; redirects <= 3; redirects++) {
                if (!allowed.test(uri)) throw new IOException("Kugou 音频地址的域名不受支持");
                long left = deadline - System.nanoTime(); if (left <= 0) throw new HttpTimeoutException("Kugou 音频地址检查超时");
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(left))
                        .header("Range", "bytes=0-"+(PREFIX_BYTES-1)).header("Accept-Encoding", "identity")
                        .header("Referer", "https://www.kugou.com/").header("User-Agent", KugouHttp.USER_AGENT).header("Accept","audio/flac,audio/mpeg,audio/ogg,application/octet-stream;q=0.9,*/*;q=0.5").GET().build();
                CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new PrefixBody());
                HttpResponse<byte[]> response;
                try { response = pending.get(left, TimeUnit.NANOSECONDS); }
                catch (TimeoutException e) { throw new HttpTimeoutException("Kugou 音频地址检查超时"); }
                catch (ExecutionException e) { throw new IOException("Kugou 音频 CDN 请求失败"); }
                finally { pending.cancel(true); }
                int status = response.statusCode();
                
                org.slf4j.LoggerFactory.getLogger("CloudXMusic/Kugou").info("[酷狗音频探测] host={} http={} bytes={}",uri.getHost(),status,response.body().length);
                if(status==401||status==403)throw new IOException("Kugou CDN HTTP 状态码："+status);
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = response.headers().firstValue("Location").orElseThrow(() -> new IOException("Kugou CDN 重定向缺少目标"));
                    try { uri = uri.resolve(location); } catch (IllegalArgumentException e) { throw new IOException("Kugou CDN 重定向目标无效"); }
                    continue;
                }
                if (status != 200 && status != 206) return null;
                if (status == 206 && !response.headers().firstValue("Content-Range").orElse("").matches("(?i)bytes 0-[0-9]+/(?:[0-9]+|\\*)"))
                    throw new IOException("Kugou CDN 返回的 Range 起点无效");
                if (!response.headers().firstValue("Content-Encoding").orElse("identity").equalsIgnoreCase("identity")) return null;
                byte[] bytes=response.body();
                if(audioHeader(bytes,true))return decodableFlac(bytes)?new Prefix(uri,bytes):null;
                return audioHeader(bytes,false)||vorbisHeader(bytes)?new Prefix(uri,bytes):null;
            }
            throw new IOException("Kugou 音频地址重定向次数超限");
        } finally { client.shutdownNow(); }
    }
    static boolean extension(URI uri, boolean flac) {
        return uri.getPath() != null && uri.getPath().toLowerCase(Locale.ROOT).endsWith(flac ? ".flac" : ".mp3");
    }
    static long mp3BitrateKbps(byte[] bytes){
        int start=0;
        if(bytes.length>=10&&bytes[0]=='I'&&bytes[1]=='D'&&bytes[2]=='3'){
            int tagSize=(bytes[6]&127)<<21|(bytes[7]&127)<<14|(bytes[8]&127)<<7|(bytes[9]&127);
            start=Math.min(bytes.length,10+tagSize);
        }
        int[] mpeg1Layer3={0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0};
        int[] mpeg2Layer3={0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0};
        for(int offset=start;offset+3<bytes.length;offset++){
            int first=bytes[offset]&255,second=bytes[offset+1]&255,third=bytes[offset+2]&255;
            if(first!=255||(second&224)!=224)continue;
            int version=(second>>>3)&3,layer=(second>>>1)&3,index=(third>>>4)&15;
            if(version==1||layer!=1||index==0||index==15)continue;
            return (version==3?mpeg1Layer3:mpeg2Layer3)[index];
        }
        return 0;
    }
    static boolean audioHeader(byte[] bytes, boolean flac) {
        if (flac) return bytes.length >= 4 && bytes[0] == 'f' && bytes[1] == 'L' && bytes[2] == 'a' && bytes[3] == 'C';
        if (bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') return true;
        if (bytes.length < 4) return false;
        int b1 = bytes[1] & 255, b2 = bytes[2] & 255;
        return (bytes[0] & 255) == 255 && (b1 & 224) == 224 && (b1 & 24) != 8 && (b1 & 6) != 0
                && (b2 & 240) != 0 && (b2 & 240) != 240 && (b2 & 12) != 12;
    }
    static boolean vorbisHeader(byte[] bytes){
        // Ogg 首包必须是 Vorbis identification；Opus 等其他 Ogg 编码不送入解码器。
        if(bytes.length<58||bytes[0]!='O'||bytes[1]!='g'||bytes[2]!='g'||bytes[3]!='S'||bytes[4]!=0||(bytes[5]&2)==0)return false;
        int segments=bytes[26]&255,offset=27+segments;
        if(segments==0||bytes.length<offset+30||(bytes[27]&255)<30||bytes[offset]!=1)return false;
        byte[] signature={'v','o','r','b','i','s'};
        for(int i=0;i<signature.length;i++)if(bytes[offset+1+i]!=signature[i])return false;
        int channels=bytes[offset+11]&255;
        int rate=ByteBuffer.wrap(bytes,offset+12,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
        return channels>=1&&channels<=2&&rate>=8000&&rate<=192000;
    }
    static boolean decodableFlac(byte[] bytes){
        if(!audioHeader(bytes,true)||bytes.length<42||(bytes[4]&127)!=0||bytes[5]!=0||bytes[6]!=0||bytes[7]!=34)return false;
        try{
            var info=new io.nayuki.flac.common.StreamInfo(Arrays.copyOfRange(bytes,8,42));info.checkValues();
            return info.numChannels>=1&&info.numChannels<=2&&info.sampleDepth>=8&&info.sampleDepth<=32&&info.sampleRate>=8000&&info.sampleRate<=192000;
        }catch(RuntimeException e){return false;}
    }
    private static final class PrefixBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(PREFIX_BYTES);
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return body; }
        public void onSubscribe(Flow.Subscription value) { subscription = value; value.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                byte[] part = new byte[Math.min(PREFIX_BYTES - bytes.size(), buffer.remaining())]; buffer.get(part); bytes.writeBytes(part);
                if (bytes.size() == PREFIX_BYTES) { body.complete(bytes.toByteArray()); subscription.cancel(); return; }
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { body.completeExceptionally(error); }
        public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
