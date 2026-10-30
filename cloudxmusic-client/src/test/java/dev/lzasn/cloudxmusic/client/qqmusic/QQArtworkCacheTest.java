package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class QQArtworkCacheTest {
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
