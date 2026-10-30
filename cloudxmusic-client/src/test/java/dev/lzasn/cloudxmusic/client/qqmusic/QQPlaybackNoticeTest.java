package dev.lzasn.cloudxmusic.client.qqmusic;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class QQPlaybackNoticeTest {
    @Test void repeatedSkipNoticesAreThrottled(){
        AtomicLong clock=new AtomicLong();List<String> messages=new ArrayList<>();var notice=new QQPlaybackNotice(clock::get,messages::add);
        notice.show("skip");notice.show("skip");clock.set(2_999_000_000L);notice.show("skip");assertEquals(1,messages.size());
        clock.set(3_000_000_000L);notice.show("skip");assertEquals(2,messages.size());
    }
}
