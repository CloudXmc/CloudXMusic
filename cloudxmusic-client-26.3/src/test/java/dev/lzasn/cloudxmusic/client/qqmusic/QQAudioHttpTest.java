package dev.lzasn.cloudxmusic.client.qqmusic;

import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class QQAudioHttpTest {
    static final class Server implements AutoCloseable {
        final HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        final ExecutorService pool=Executors.newCachedThreadPool();
        Server(HttpHandler handler)throws IOException{server.setExecutor(pool);server.createContext("/audio.flac",handler);server.start();}
        URI uri(){return URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/audio.flac");}
        public void close(){server.stop(0);pool.shutdownNow();}
    }
    static QQAudioHttp client(Duration timeout){return new QQAudioHttp(timeout,u->"127.0.0.1".equals(u.getHost()));}
    static InputStream open(QQAudioHttp http,URI uri)throws Exception{return http.open(uri,()->false,p->{},n->{});}
    @Test void sendsCdnHeadersAndDoesNotLimitTotalBodyLifetime()throws Exception{
        List<String> failures=new CopyOnWriteArrayList<>();
        try(Server server=new Server(e->{
            if(!"bytes=0-".equals(e.getRequestHeaders().getFirst("Range")))failures.add("Range");
            if(!"https://y.qq.com/".equals(e.getRequestHeaders().getFirst("Referer")))failures.add("Referer");
            if(!e.getRequestHeaders().getFirst("User-Agent").contains("Windows"))failures.add("User-Agent");
            if(!"identity".equals(e.getRequestHeaders().getFirst("Accept-Encoding")))failures.add("encoding");
            if(e.getRequestHeaders().getFirst("Accept")==null)failures.add("Accept");
            if(e.getRequestHeaders().getFirst("Cookie")!=null||e.getRequestHeaders().getFirst("Authorization")!=null)failures.add("credentials");
            if(!"HTTP/1.1".equals(e.getProtocol()))failures.add("HTTP version");
            try(e){e.sendResponseHeaders(200,12*1024);for(int i=0;i<12;i++){
                e.getResponseBody().write(new byte[1024]);e.getResponseBody().flush();Thread.sleep(70);
            }}catch(InterruptedException ex){Thread.currentThread().interrupt();}
        });QQAudioHttp http=client(Duration.ofMillis(500));InputStream in=open(http,server.uri())) {
            assertEquals(12*1024,in.readAllBytes().length);assertTrue(failures.isEmpty(),failures.toString());
        }
    }
    @Test void interruptedBodyResumesAtExactByteAndStillDecodesValidFlac()throws Exception{
        byte[] bytes;try(var in=getClass().getResourceAsStream("/qq-audio/tone16.flac")){bytes=in.readAllBytes();}
        int cut=bytes.length/2;List<Long> offsets=new CopyOnWriteArrayList<>();CountDownLatch consumed=new CountDownLatch(1);
        try(Server server=new Server(e->{
            long offset=Long.parseLong(e.getRequestHeaders().getFirst("Range").replace("bytes=","").replace("-",""));offsets.add(offset);
            e.getResponseHeaders().add("ETag","\"same-audio\"");e.getResponseHeaders().add("Content-Type","audio/flac");
            if(offsets.size()==1){
                try(e){e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes,0,cut);e.getResponseBody().flush();consumed.await(2,TimeUnit.SECONDS);}
                catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(IOException ignored){}
            }else{
                assertEquals("\"same-audio\"",e.getRequestHeaders().getFirst("If-Range"));
                e.getResponseHeaders().add("Content-Range","bytes "+offset+"-"+(bytes.length-1)+"/"+bytes.length);
                try(e){e.sendResponseHeaders(206,bytes.length-offset);e.getResponseBody().write(bytes,(int)offset,bytes.length-(int)offset);}
            }
        });QQAudioHttp http=client(Duration.ofSeconds(2));InputStream in=open(http,server.uri())) {
            byte[] first=in.readNBytes(100);consumed.countDown();ByteArrayOutputStream all=new ByteArrayOutputStream();all.write(first);in.transferTo(all);
            assertArrayEquals(bytes,all.toByteArray());assertEquals(List.of(0L,(long)cut),offsets);
            long[] frames={0};QQAudioDecoder.decode(new ByteArrayInputStream(all.toByteArray()),true,new QQAudioDecoder.Sink(){
                public void open(int rate,int channels,long count){assertEquals(44100,rate);assertEquals(2,channels);}
                public void write(short[] samples,int count,int channels){frames[0]+=count;}
            });assertEquals(11025,frames[0]);
        }finally{consumed.countDown();}
    }
    @Test void neverAppendsWholeFileWhenServerIgnoresResumeRange()throws Exception{rejectBadResume(false);}
    @Test void rejectsWrongResumeOffset()throws Exception{rejectBadResume(true);}
    static void rejectBadResume(boolean wrongOffset)throws Exception{
        AtomicInteger requests=new AtomicInteger();
        try(Server server=new Server(e->{
            try(e){int n=requests.incrementAndGet();if(n==1){e.sendResponseHeaders(200,10000);e.getResponseBody().write(new byte[100]);e.getResponseBody().flush();}
                else {if(wrongOffset)e.getResponseHeaders().add("Content-Range","bytes 0-9999/10000");e.sendResponseHeaders(wrongOffset?206:200,10000);e.getResponseBody().write(new byte[10000]);}}
            catch(IOException ignored){}
        });QQAudioHttp http=client(Duration.ofSeconds(2));InputStream in=open(http,server.uri())){
            assertThrows(QQAudioHttp.Failure.class,in::readAllBytes);assertTrue(requests.get()<=3);
        }
    }
    @Test void rejectsHtml403AndCompressionBeforeDecoder()throws Exception{
        for(int mode=0;mode<3;mode++){final int type=mode;
            try(Server server=new Server(e->{try(e){e.getResponseHeaders().add(type==2?"Content-Encoding":"Content-Type",type==2?"gzip":"text/html");e.sendResponseHeaders(type==1?403:200,16);e.getResponseBody().write(new byte[16]);}});
                QQAudioHttp http=client(Duration.ofSeconds(2))) {
                assertThrows(QQAudioHttp.Failure.class,()->open(http,server.uri()));
            }
        }
    }
    @Test void headersHaveBoundedWait()throws Exception{
        try(Server server=new Server(e->{try(e){try{Thread.sleep(800);}catch(InterruptedException ex){Thread.currentThread().interrupt();}e.sendResponseHeaders(200,1);e.getResponseBody().write(0);}catch(IOException ignored){}});
            QQAudioHttp http=client(Duration.ofMillis(150))){
            assertThrows(QQAudioHttp.Failure.class,()->open(http,server.uri()));
        }
    }
    @Test void changedFileValidatorCannotBeSplicedIntoOldAudio()throws Exception{
        AtomicInteger requests=new AtomicInteger();CountDownLatch consumed=new CountDownLatch(1);
        try(Server server=new Server(e->{
            int attempt=requests.incrementAndGet();long offset=Long.parseLong(e.getRequestHeaders().getFirst("Range").replace("bytes=","").replace("-",""));
            try(e){e.getResponseHeaders().add("ETag",attempt==1?"\"original\"":"\"changed\"");
                if(attempt>1)e.getResponseHeaders().add("Content-Range","bytes "+offset+"-9999/10000");
                e.sendResponseHeaders(attempt==1?200:206,10000-offset);e.getResponseBody().write(new byte[100]);e.getResponseBody().flush();
                if(attempt==1)consumed.await(2,TimeUnit.SECONDS);
            }catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(IOException ignored){}
        });QQAudioHttp http=client(Duration.ofSeconds(2));InputStream in=open(http,server.uri())){
            assertEquals(10,in.readNBytes(10).length);consumed.countDown();
            assertThrows(QQAudioHttp.Failure.class,in::readAllBytes);assertEquals(2,requests.get());
        }finally{consumed.countDown();}
    }
    @Test void closingBlockedReadCancelsWithoutRetrying()throws Exception{
        CountDownLatch sent=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger requests=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();
        try(Server server=new Server(e->{requests.incrementAndGet();try(e){e.sendResponseHeaders(200,10000);e.getResponseBody().write(new byte[100]);e.getResponseBody().flush();sent.countDown();release.await(3,TimeUnit.SECONDS);}
            catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(IOException ignored){}});
            QQAudioHttp http=client(Duration.ofSeconds(2));InputStream in=http.open(server.uri(),cancelled::get,p->{},n->{})){
            assertTrue(sent.await(1,TimeUnit.SECONDS));CompletableFuture<Void> read=CompletableFuture.runAsync(()->{assertThrows(IOException.class,in::readAllBytes);});
            cancelled.set(true);in.close();read.get(2,TimeUnit.SECONDS);assertEquals(1,requests.get());
        }finally{release.countDown();}
    }
    @Test void retriesAreBoundedAndCancellationDoesNotReconnect()throws Exception{
        AtomicInteger requests=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();CountDownLatch consumed=new CountDownLatch(1);
        try(Server server=new Server(e->{int attempt=requests.incrementAndGet();try(e){
            long offset=Long.parseLong(e.getRequestHeaders().getFirst("Range").replace("bytes=","").replace("-",""));
            if(attempt>1)e.getResponseHeaders().add("Content-Range","bytes "+offset+"-9999/10000");
            e.sendResponseHeaders(attempt==1?200:206,10000-offset);e.getResponseBody().write(new byte[100]);e.getResponseBody().flush();
            if(attempt==1)consumed.await(2,TimeUnit.SECONDS);
        }catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(IOException ignored){}});
            QQAudioHttp http=client(Duration.ofSeconds(2));InputStream in=http.open(server.uri(),cancelled::get,p->{},n->{})){
            assertEquals(10,in.readNBytes(10).length);consumed.countDown();
            assertThrows(QQAudioHttp.Failure.class,in::readAllBytes);assertEquals(3,requests.get());
        }finally{consumed.countDown();}
        requests.set(0);cancelled.set(true);
        try(Server server=new Server(e->{requests.incrementAndGet();e.close();});QQAudioHttp http=client(Duration.ofSeconds(2))){
            assertThrows(InterruptedException.class,()->http.open(server.uri(),cancelled::get,p->{},n->{}));assertEquals(0,requests.get());
        }
    }
}
