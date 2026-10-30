package dev.lzasn.cloudxmusic.client.qqmusic;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
/** 主线程通知节流，同一场连续跳过最多每三秒显示一次。 */
final class QQPlaybackNotice {
    private final LongSupplier clock;private final Consumer<String> show;private boolean shown;private long last;
    QQPlaybackNotice(LongSupplier clock,Consumer<String> show){this.clock=clock;this.show=show;}
    void show(String message){long now=clock.getAsLong();if(shown&&now-last<TimeUnit.SECONDS.toNanos(3))return;shown=true;last=now;show.accept(message);}
}
