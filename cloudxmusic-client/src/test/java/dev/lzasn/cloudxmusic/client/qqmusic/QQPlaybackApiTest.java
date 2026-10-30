package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QQPlaybackApiTest {
    private QQPlaybackApi api(QQPlaybackApi.Transport transport,Duration budget) {
        return new QQPlaybackApi(QQPlaybackResolverTest.session().credentials(),transport,()->true,budget);
    }
    @Test void expiredTotalBudgetNeverStartsRequest() throws Exception {
        var api=api((r,c,t)->fail("no request after deadline"),Duration.ofMillis(1));Thread.sleep(5);
        assertThrows(HttpTimeoutException.class,()->api.call("module","method",json("{}"),true));
    }
    @Test void totalBudgetIsCheckedAgainAfterResponse() {
        var api=api((r,c,t)->{Thread.sleep(20);return reply(0,"{}");},Duration.ofMillis(5));
        assertThrows(HttpTimeoutException.class,()->api.call("module","method",json("{}"),true));
    }
    @Test void nestedBusinessCodesAndRateLimitAreExplicit() {
        assertThrows(QQSessionVerifier.Rejected.class,()->api((r,c,t)->reply(0,"{\"code\":104401}"),Duration.ofSeconds(1)).call("m","m",json("{}"),true));
        assertThrows(QQSessionVerifier.Rejected.class,()->api((r,c,t)->reply(104604,"{}"),Duration.ofSeconds(1)).call("m","m",json("{}"),true));
        assertThrows(IOException.class,()->api((r,c,t)->reply(0,"[]"),Duration.ofSeconds(1)).call("m","m",json("{}"),true));
    }
}
