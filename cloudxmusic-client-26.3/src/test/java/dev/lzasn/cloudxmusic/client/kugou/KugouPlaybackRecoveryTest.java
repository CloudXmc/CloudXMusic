package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouPlaybackRecoveryTest {
    static final KugouAccount ACCOUNT=new KugouAccount("123","web-token","a".repeat(32),"",List.of());
    static com.google.gson.JsonObject json(String text){return KugouLoginServiceTest.json(text);}

    @Test void appSecondsAreNotMistakenForAMillisecondTrial()throws Exception{
        var resolver=new KugouPlaybackResolver((s,a,q,m)->json("{\"status\":1,\"timelength\":240,\"url\":\"https://fs.kugou.com/full.mp3\"}"),
                (u,q,b)->new KugouPlaybackResolver.Verified(u,q));
        assertEquals(240,resolver.resolve(KugouPlaybackResolverTest.track(),ACCOUNT,KugouQuality.HQ).durationSeconds());
    }
    @Test void backupCdnSurvivesPrimary403WithoutMisreportingAVipLock()throws Exception{
        var resolver=new KugouPlaybackResolver((s,a,q,m)->json("{\"status\":1,\"url\":\"https://fs.kugou.com/bad.mp3\",\"backup_url\":[\"https://fs.kugou.com/good.mp3\"]}"),
                (u,q,b)->{if(u.getPath().contains("bad"))throw new IOException("CDN HTTP 403");return new KugouPlaybackResolver.Verified(u,q);});
        var result=resolver.resolve(KugouPlaybackResolverTest.track(),ACCOUNT,KugouQuality.STANDARD);
        assertTrue(result.url().contains("good"));assertFalse(result.permissionFallback());
    }
    @Test void webBackupFieldAndOggVorbisAreAccepted()throws Exception{
        var resolver=new KugouPlaybackResolver((s,a,q,m)->json("{\"status\":2,\"error_code\":20018}"),
                (s,a,m)->json("{\"status\":1,\"data\":{\"extName\":\"ogg\",\"timelength\":240000,\"play_backup_url\":\"https://webfs.kugou.com/full.ogg\"}}"),
                (u,q,b)->new KugouPlaybackResolver.Verified(u,KugouQuality.STANDARD));
        assertEquals(KugouQuality.STANDARD,resolver.resolve(KugouPlaybackResolverTest.track(),ACCOUNT,KugouQuality.HIGH).actualQuality());
    }
    @Test void nestedSecurityChallengeStopsAllOtherRoutes(){
        var resolver=new KugouPlaybackResolver((s,a,q,m)->{fail("安全验证不能绕行");return null;},
                (s,a,m)->json("{\"status\":0,\"data\":{\"err_code\":30020}}"),(u,q,b)->null);
        assertEquals(KugouPlaybackAccessException.Reason.VERIFICATION_REQUIRED,
                assertThrows(KugouPlaybackAccessException.class,()->resolver.resolve(KugouPlaybackResolverTest.track(),ACCOUNT,KugouQuality.HIGH)).reason());
    }
    @Test void opaqueCdnStreamReachesTheDecoderWithCorrectHeaders()throws Exception{
        byte[] bytes;try(var in=getClass().getResourceAsStream("/qq-audio/tone16.flac")){bytes=in.readAllBytes();}
        AtomicInteger calls=new AtomicInteger();
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/stream",e->{
            calls.incrementAndGet();assertEquals("https://www.kugou.com/",e.getRequestHeaders().getFirst("Referer"));
            assertTrue(e.getRequestHeaders().getFirst("User-Agent").contains("Windows"));
            try(e){e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}
        });server.start();
        try(var http=KugouAudioHttpTest.client(Duration.ofSeconds(2));
            var in=KugouAudioHttpTest.open(http,URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/stream?id=opaque"))){
            assertArrayEquals(bytes,in.readAllBytes());assertEquals(1,calls.get());
        }finally{server.stop(0);}
    }
    @Test void playerDetectsFlacAndVorbisByContentInsteadOfUrlSuffix()throws Exception{
        for(String fixture:List.of("/qq-audio/tone16.flac","/kugou-audio/tone.ogg")){
            var device=new KugouAudioPlayerTest.Device(new AtomicInteger(),new AtomicInteger());
            CountDownLatch finished=new CountDownLatch(1);
            try(var player=new KugouAudioPlayer(f->device.line(),(u,s)->getClass().getResourceAsStream(fixture))){
                player.playFrom(URI.create("https://webfs.kugou.com/stream?id=opaque"),1,0,false,()->true,finished::countDown).get(1,TimeUnit.SECONDS);
                assertTrue(finished.await(3,TimeUnit.SECONDS),player.view().toString());
                assertEquals(KugouPlaybackAudio.Status.FINISHED,player.view().status());assertTrue(device.frames>0);
            }
        }
    }
    @Test void actualVorbisBytesAreDecodedIncrementallyAndClosed()throws Exception{
        AtomicBoolean closed=new AtomicBoolean();long[] frames={0};
        try(var source=new FilterInputStream(getClass().getResourceAsStream("/kugou-audio/tone.ogg")){
            public int read(byte[] b,int off,int len)throws IOException{return super.read(b,off,Math.min(73,len));}
            public void close()throws IOException{closed.set(true);super.close();}
        }){
            KugouAudioDecoder.decode(source,false,new KugouAudioDecoder.Sink(){
                public void open(int rate,int channels,long count){assertEquals(44100,rate);assertEquals(2,channels);}
                public void write(short[] samples,int count,int channels){frames[0]+=count;}
            });
        }
        assertEquals(11025,frames[0]);assertTrue(closed.get());
    }
    @Test void cdnProbeRecognizesVorbisContentAndRejectsOpus()throws Exception{
        byte[] bytes;try(var in=getClass().getResourceAsStream("/kugou-audio/tone.ogg")){bytes=in.readAllBytes();}
        try(var server=new KugouAudioHttpTest.Server(e->{
            e.getResponseHeaders().add("Content-Range","bytes 0-"+(bytes.length-1)+"/"+bytes.length);
            try(e){e.sendResponseHeaders(206,bytes.length);e.getResponseBody().write(bytes);}
        })){
            var result=KugouAudioUrlVerifier.probe(server.uri(),KugouQuality.HIGH,96,Duration.ofSeconds(2),u->u.getHost().equals("127.0.0.1"));
            assertNotNull(result);assertEquals(KugouQuality.STANDARD,result.quality());
        }
        byte[] opus=bytes.clone();System.arraycopy("OpusHead".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,opus,28,8);
        assertFalse(KugouAudioUrlVerifier.vorbisHeader(opus));
    }
    @Test void appSecondsStillRejectThirtySecondTrials(){
        var resolver=new KugouPlaybackResolver((s,a,q,m)->json("{\"status\":1,\"timelength\":30,\"url\":\"https://fs.kugou.com/trial.mp3\"}"),
                (u,q,b)->{fail("试听不能作为全曲播放");return null;});
        assertThrows(KugouPlaybackAccessException.class,()->resolver.resolve(KugouPlaybackResolverTest.track(),ACCOUNT,KugouQuality.STANDARD));
    }
    @Test void vorbisSeekSkipsExactPcmFramesBeforeOutput()throws Exception{
        long[] output={0};int[] rate={0};
        var skip=new KugouSampleSkip(new KugouAudioDecoder.Sink(){
            public void open(int r,int channels,long count){rate[0]=r;}
            public void write(short[] samples,int count,int channels){output[0]+=count;}
        },.1,()->false);
        try(var in=getClass().getResourceAsStream("/kugou-audio/tone.ogg")){
            KugouAudioDecoder.decode(in,false,skip);
        }
        assertEquals(44100,rate[0]);assertEquals(11025-4410,output[0]);
    }
}
