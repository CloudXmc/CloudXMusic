package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.*;
import javax.sound.sampled.LineUnavailableException;
import static org.junit.jupiter.api.Assertions.*;

class QQAudioFailureTest {
    @Test void preservesUsefulCauseAndStackButRedactsSignedUrls(){
        var root=new IOException("closed https://a.qq.com/a.flac?vkey=SECRET&uin=123 cookie=TOKEN");
        var failure=new IOException("network",root);Throwable safe=QQAudioFailure.safe(failure);
        StringWriter text=new StringWriter();safe.printStackTrace(new PrintWriter(text));String printed=text.toString();
        assertTrue(printed.contains("closed"));assertTrue(printed.contains("java.io.IOException"));
        assertFalse(printed.contains("SECRET"));assertFalse(printed.contains("TOKEN"));assertArrayEquals(root.getStackTrace(),safe.getCause().getStackTrace());
    }
    @Test void distinguishesNetworkDeviceAndDecoderFailures(){
        assertTrue(QQAudioFailure.message(new QQAudioHttp.Failure("QQ 网络音频流中断",new IOException("closed"))).contains("网络"));
        assertTrue(QQAudioFailure.message(new IOException("wrapper",new LineUnavailableException("busy"))).contains("声卡"));
        assertTrue(QQAudioFailure.message(new IOException("invalid FLAC" )).contains("解码"));
    }
}
