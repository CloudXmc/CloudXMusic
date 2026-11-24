package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class QQQualityFeedbackTest {
    @Test void onlyFallbackToastsAreShownAndThreeSecondBoundaryAllowsNextToast(){
        AtomicLong now=new AtomicLong();List<String> messages=new ArrayList<>();
        var feedback=new QQQualityFeedback(now::get,messages::add);
        feedback.fallback(QQQuality.SQ,QQQuality.SQ);assertTrue(messages.isEmpty());
        feedback.fallback(QQQuality.SQ,QQQuality.HQ);assertEquals(1,messages.size());
        assertEquals("当前歌曲暂无 SQ 无损音源，已自动为您降级至 HQ 高品质",messages.getFirst());
        for(int n=0;n<50;n++)feedback.fallback(QQQuality.SQ,QQQuality.STANDARD);assertEquals(1,messages.size());
        now.set(TimeUnit.SECONDS.toNanos(3)-1);feedback.fallback(QQQuality.SQ,QQQuality.STANDARD);assertEquals(1,messages.size());
        now.incrementAndGet();feedback.fallback(QQQuality.SQ,QQQuality.STANDARD);assertEquals(2,messages.size());assertTrue(messages.getLast().endsWith("标准品质"));
        now.addAndGet(TimeUnit.SECONDS.toNanos(3));feedback.fallback(QQQuality.HQ,QQQuality.STANDARD);assertEquals(3,messages.size());assertTrue(messages.getLast().contains("暂无 HQ 高品质音源"));
    }
    @Test void exactMenuBarLabelsAndSharedGeometry(){
        assertEquals(List.of("臻品母带 (FLAC)","SQ 无损 (24bit/48kHz)","HQ 高品质 (320kbps)","标准品质 (128kbps)"),Arrays.stream(QQQuality.values()).map(QQQuality::menuLabel).toList());
        assertEquals(List.of("臻品母带","SQ 无损","HQ 高品质","标准品质"),Arrays.stream(QQQuality.values()).map(QQQuality::barLabel).toList());
        assertEquals(332,QQQualityMenu.region().w());assertEquals(148,QQQualityMenu.region().h());
        for(int i=0;i<4;i++){var row=QQQualityMenu.row(i);assertTrue(row.contains(row.cx(),row.cy()));assertEquals(QQQualityMenu.region().x(),row.x());}
    }
}
