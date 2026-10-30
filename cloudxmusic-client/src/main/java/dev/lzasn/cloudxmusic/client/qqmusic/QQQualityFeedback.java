package dev.lzasn.cloudxmusic.client.qqmusic;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** 客户端线程调用；使用单调时钟限制降级提示频率。 */
final class QQQualityFeedback {
    private final LongSupplier clock;
    private final Consumer<String> show;
    private long last;
    private boolean shown;
    QQQualityFeedback(LongSupplier clock,Consumer<String> show){this.clock=clock;this.show=show;}
    void fallback(QQQuality requested,QQQuality actual){
        if(actual.ordinal()<=requested.ordinal())return;
        long now=clock.getAsLong();
        if(shown&&now-last<TimeUnit.SECONDS.toNanos(3))return;
        shown=true;last=now;
        show.accept("当前歌曲暂无 "+requested.label()+"音源，已自动为您降级至 "+actual.label());
    }
}
