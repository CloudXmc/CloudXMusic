package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class QQArtworkCacheTest {
    @Test void slowOrTimedOutCoverNeverBlocksUiAndKeepsDefaultUntilRetrySucceeds()throws Exception{
        var main=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var cache=new QQArtworkCache(main::add,uri->{
            calls.incrementAndGet();entered.countDown();release.await(2,java.util.concurrent.TimeUnit.SECONDS);
            throw new java.net.http.HttpTimeoutException("cover timeout");
        },attempt->{})){
            assertNull(cache.image("https://y.gtimg.cn/timeout.jpg"));
            assertTrue(entered.await(1,java.util.concurrent.TimeUnit.SECONDS));
            for(int i=0;i<100;i++)assertNull(cache.image("https://y.gtimg.cn/timeout.jpg"));
            assertEquals(1,calls.get());assertEquals(0,cache.revision());
            release.countDown();QQCatalogControllerTest.awaitCallback(main);main.remove().run();
            assertEquals(3,calls.get());assertEquals(0,cache.revision());assertNull(cache.image("https://y.gtimg.cn/timeout.jpg"));
        }finally{release.countDown();}
    }
    @Test void staticPageRecoversAfterBackoffWithoutAnotherPaintRequest()throws Exception{
        var main=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var calls=new java.util.concurrent.atomic.AtomicInteger();var time=new java.util.concurrent.atomic.AtomicLong();
        var bitmap=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);String url="https://y.gtimg.cn/recover.png";
        try(var cache=new QQArtworkCache(main::add,uri->{if(calls.incrementAndGet()<=3)throw new QQArtworkCache.HttpFailure(503);return bitmap;},attempt->{},time::get)){
            cache.image(url);QQCatalogControllerTest.awaitCallback(main);main.remove().run();assertEquals(3,calls.get());assertEquals(0,cache.revision());
            cache.tick();assertEquals(3,calls.get());time.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(31));cache.tick();
            QQCatalogControllerTest.awaitCallback(main);main.remove().run();assertEquals(4,calls.get());assertEquals(1,cache.revision());assertSame(bitmap,cache.image(url));
        }
    }
    @Test void retriesAreBoundedAndOnlyKnownAlbumUrlsUseAllowedFallbacks()throws Exception{
        var main=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var seen=new java.util.concurrent.CopyOnWriteArrayList<java.net.URI>();
        String url="https://y.gtimg.cn/music/photo_new/T002R300x300M000ABC123.jpg?token=private";
        var bitmap=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);
        try(var cache=new QQArtworkCache(main::add,uri->{seen.add(uri);if(seen.size()<3)throw new QQArtworkCache.HttpFailure(403);return bitmap;},attempt->{})){
            cache.image(url);QQCatalogControllerTest.awaitCallback(main);main.remove().run();
            assertSame(bitmap,cache.image(url));assertEquals(3,seen.size());assertTrue(seen.stream().allMatch(QQArtworkCache::allowed));
            assertTrue(seen.get(1).getPath().contains("150x150"));assertNull(seen.get(2).getRawQuery());assertEquals(1,cache.revision());
        }
        seen.clear();
        try(var cache=new QQArtworkCache(main::add,uri->{seen.add(uri);throw new QQArtworkCache.HttpFailure(429);},attempt->{})){
            cache.image(url);QQCatalogControllerTest.awaitCallback(main);main.remove().run();
            for(int i=0;i<20;i++)assertNull(cache.image(url));assertEquals(3,seen.size());assertEquals(0,cache.revision());
        }
        assertTrue(QQArtworkCache.candidates(java.net.URI.create("https://evil.test/music/photo_new/T002R300x300M000ABC.jpg")).isEmpty());
    }
    @Test void permanentAvatarHttpFailureDoesNotHammerTheSameEndpoint()throws Exception{
        var main=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var calls=new java.util.concurrent.atomic.AtomicInteger();
        String url="https://q1.qlogo.cn/g?b=qq&nk=12345678&s=100";
        try(var cache=new QQArtworkCache(main::add,uri->{calls.incrementAndGet();throw new QQArtworkCache.HttpFailure(404);},attempt->{})){
            cache.image(url);QQCatalogControllerTest.awaitCallback(main);main.remove().run();assertEquals(1,calls.get());
            assertNull(cache.image(url));assertEquals(1,calls.get());assertEquals(0,cache.revision());
        }
    }
    @Test void temporaryNetworkFailureRetriesInBackgroundAndPublishesRepaintRevision()throws Exception{
        var main=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var calls=new java.util.concurrent.atomic.AtomicInteger();
        var bitmap=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);String url="https://y.gtimg.cn/test.png";
        try(var cache=new QQArtworkCache(main::add,uri->{if(calls.incrementAndGet()<2)throw new IOException("temporary");return bitmap;})){
            assertNull(cache.image(url));QQCatalogControllerTest.awaitCallback(main);
            main.remove().run();assertSame(bitmap,cache.image(url));assertEquals(2,calls.get());assertEquals(1,cache.revision());
        }
    }
    @Test void downloadedAvatarPublishesRevisionOnlyOnClientThreadAndOldAccountCannotPublish()throws Exception{
        var main=new java.util.concurrent.ConcurrentLinkedQueue<Runnable>();var loaded=new java.util.concurrent.CountDownLatch(1);
        var image=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);String url="https://q1.qlogo.cn/g?b=qq&nk=12345678&s=100";
        try(var cache=new QQArtworkCache(main::add,uri->{loaded.countDown();return image;})){
            assertNull(cache.image(url));assertTrue(loaded.await(1,java.util.concurrent.TimeUnit.SECONDS));
            QQCatalogControllerTest.awaitCallback(main);assertEquals(0,cache.revision());
            main.remove().run();assertEquals(1,cache.revision());assertSame(image,cache.image(url));
            cache.clear();cache.image(url);QQCatalogControllerTest.awaitCallback(main);int before=cache.revision();cache.clear();
            while(!main.isEmpty())main.remove().run();assertEquals(before+1,cache.revision());
        }
    }
    @Test void actualQqProfileAndPlaylistImageHostsAreAllowedWithoutSuffixSpoofing(){
        for(String host:new String[]{"q1.qlogo.cn","pic6.y.qq.com","music-file.y.qq.com","qpic.y.qq.com"}){
            assertTrue(QQArtworkCache.allowed(java.net.URI.create("https://"+host+"/image")));
            assertFalse(QQArtworkCache.allowed(java.net.URI.create("https://"+host+".evil.test/image")));
        }
    }
    @Test void avatarRequestIncludesBrowserAndAntiHotlinkHeadersWithoutCredentials(){
        var request=QQArtworkCache.imageRequest(java.net.URI.create("https://q1.qlogo.cn/g?b=qq&nk=12345678&s=100"));
        assertEquals("https://y.qq.com/",request.headers().firstValue("Referer").orElseThrow());
        assertTrue(request.headers().firstValue("User-Agent").orElseThrow().contains("AppleWebKit"));
        assertTrue(request.headers().firstValue("Accept").orElseThrow().contains("image/png"));
        assertTrue(request.headers().firstValue("Cookie").isEmpty());
    }
    static byte[] png(int width,int height)throws IOException{
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB),"png",bytes);return bytes.toByteArray();
    }
    @Test void decodedCacheImagesHaveBoundedMemoryAndKeepAspectRatio()throws Exception{
        var image=QQArtworkCache.decode(png(1024,512));assertEquals(384,image.getWidth());assertEquals(192,image.getHeight());
        assertEquals(300,QQArtworkCache.decode(png(300,300)).getWidth());
    }
    @Test void rejectsHtmlOversizedDimensionsAndOversizedCompressedResponse()throws Exception{
        assertThrows(IOException.class,()->QQArtworkCache.decode("<html>error</html>".getBytes()));
        assertThrows(IOException.class,()->QQArtworkCache.decode(png(2049,1)));
        assertThrows(IOException.class,()->QQArtworkCache.decode(new byte[2*1024*1024+1]));
    }
}
