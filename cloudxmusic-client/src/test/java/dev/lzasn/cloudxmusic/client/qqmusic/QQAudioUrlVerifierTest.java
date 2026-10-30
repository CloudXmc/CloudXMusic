package dev.lzasn.cloudxmusic.client.qqmusic;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class QQAudioUrlVerifierTest {
    @Test void acceptsFlacMp3AndId3ButNotErrorPages() {
        assertTrue(QQAudioUrlVerifier.audioHeader("fLaC123".getBytes(StandardCharsets.US_ASCII),true));
        assertTrue(QQAudioUrlVerifier.audioHeader("ID3data".getBytes(StandardCharsets.US_ASCII),false));
        assertTrue(QQAudioUrlVerifier.audioHeader(new byte[]{(byte)0xff,(byte)0xfb,(byte)0x90,0},false));
        for(byte[] b:new byte[][]{"<html>error".getBytes(),"{\"code\":1}".getBytes(),{}, {(byte)0xff,(byte)0xe0,0,0}}) {
            assertFalse(QQAudioUrlVerifier.audioHeader(b,true)); assertFalse(QQAudioUrlVerifier.audioHeader(b,false));
        }
    }
    @Test void productionAddressRulesRejectLocalUserInfoAndNonQqHosts() {
        assertTrue(QQAudioUrlVerifier.allowed(URI.create("https://isure.stream.qqmusic.qq.com/F000a.flac?vkey=x")));
        for(String uri:List.of("//qq.com/a.flac","http://localhost/a.flac","file:///a.flac","https://qq.com.evil.example/a.flac","https://evilqq.com/a.flac", "https://user:secret@qq.com/a.flac","https://qq.com:3000/a.flac","https://qq.com/a.flac#frag"))
            assertFalse(QQAudioUrlVerifier.allowed(URI.create(uri)),uri);
    }
    @Test void rangeProbeNeverSendsCredentialsAndHandlesRedirectsAndNonAudio() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); ExecutorService pool=Executors.newCachedThreadPool();server.setExecutor(pool);
        List<String> failures=new CopyOnWriteArrayList<>();
        server.createContext("/ok.flac",e->{
            if(!"bytes=0-15".equals(e.getRequestHeaders().getFirst("Range"))) failures.add("range");
            if(e.getRequestHeaders().getFirst("Cookie")!=null||e.getRequestHeaders().getFirst("Authorization")!=null)failures.add("credentials");
            byte[] b=Arrays.copyOf("fLaC".getBytes(StandardCharsets.US_ASCII),16);e.getResponseHeaders().add("Content-Range","bytes 0-15/10000000");e.sendResponseHeaders(206,b.length);
            try(var out=e.getResponseBody()){out.write(b);}
        });
        server.createContext("/bad.flac",e->{byte[] b="<html>no access</html>".getBytes();e.sendResponseHeaders(200,b.length);try(var o=e.getResponseBody()){o.write(b);}});
        server.createContext("/redirect.flac",e->{e.getResponseHeaders().add("Location","/ok.flac");e.sendResponseHeaders(302,-1);e.close();});
        server.createContext("/forbidden.flac",e->{e.sendResponseHeaders(403,-1);e.close();});
        server.createContext("/slow.flac",e->{e.sendResponseHeaders(200,0);e.getResponseBody().flush();try{Thread.sleep(1500);}catch(InterruptedException ex){Thread.currentThread().interrupt();}e.close();});
        server.start();
        try{
            URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            java.util.function.Predicate<URI> allow=u->u.getHost().equals("127.0.0.1");
            assertEquals(base.resolve("/ok.flac"),QQAudioUrlVerifier.verify(base.resolve("/ok.flac"),true,Duration.ofSeconds(3),allow));
            assertEquals(base.resolve("/ok.flac"),QQAudioUrlVerifier.verify(base.resolve("/redirect.flac"),true,Duration.ofSeconds(3),allow));
            assertNull(QQAudioUrlVerifier.verify(base.resolve("/bad.flac"),true,Duration.ofSeconds(3),allow));
            assertNull(QQAudioUrlVerifier.verify(base.resolve("/forbidden.flac"),true,Duration.ofSeconds(3),allow));
            assertThrows(HttpTimeoutException.class,()->QQAudioUrlVerifier.verify(base.resolve("/slow.flac"),true,Duration.ofMillis(200),allow));
            assertTrue(failures.isEmpty(),failures.toString());
        }finally{server.stop(0);pool.shutdownNow();}
    }
    @Test void ignoredRangeResponseIsNotDownloadedInFull() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);ExecutorService pool=Executors.newCachedThreadPool();server.setExecutor(pool);
        server.createContext("/large.mp3",e->{e.sendResponseHeaders(200,0);try(var out=e.getResponseBody()){
            out.write("ID3".getBytes());out.write(new byte[100]);out.flush();
            try{Thread.sleep(2000);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            out.write(new byte[2*1024*1024]);
        }catch(java.io.IOException ignored){}});server.start();
        try{
            URI uri=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/large.mp3");
            assertEquals(uri,QQAudioUrlVerifier.verify(uri,false,Duration.ofMillis(700),u->true));
        }finally{server.stop(0);pool.shutdownNow();}
    }
}
