package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class QQQualityRegressionTest {
    @Test void hqOnlyFallsBackToStandardAndStandardNeverTriesHigherTiers()throws Exception{
        for(QQQuality preferred:List.of(QQQuality.HQ,QQQuality.STANDARD)){
            List<String> files=new ArrayList<>();
            var resolver=QQPlaybackResolverTest.resolver((r,c,t)->{
                if(QQPlaybackResolverTest.method(r).equals("get_song_detail_yqq"))return QQPlaybackResolverTest.metadata();
                String file=QQPlaybackResolverTest.filename(r);files.add(file);
                return file.startsWith("M500")?QQPlaybackResolverTest.link(file):QQPlaybackResolverTest.emptyLink();
            },(uri,flac,timeout)->uri);
            var link=resolver.resolveLink(QQPlaybackResolverTest.SONG,preferred,info->{});
            assertEquals(QQQuality.STANDARD,link.actualQuality());assertTrue(link.url().contains("M500Media456.mp3"));
            assertEquals(preferred==QQQuality.HQ?List.of("M800Media456.mp3","M800Media456.mp3","M500Media456.mp3"):List.of("M500Media456.mp3"),files);
        }
    }
    @Test void threeDistinctPreferencesAreAvailable() {
        assertEquals(List.of("SQ", "HQ", "STANDARD"), Arrays.stream(QQQuality.values()).map(Enum::name).toList());
    }
    @Test void missingSqAndHqReachStandardInsteadOfReturningNoResource() throws Exception {
        List<String> files = new ArrayList<>();
        var resolver = QQPlaybackResolverTest.resolver((request, cookies, timeout) -> {
            String method = QQPlaybackResolverTest.method(request);
            if (method.equals("get_song_detail_yqq")) return QQPlaybackResolverTest.metadata();
            if (method.equals("GetLoginUserInfo")) return QQSessionVerifierTest.reply(0,"{\"musicid\":123456}");
            String file = QQPlaybackResolverTest.filename(request); files.add(file);
            return file.startsWith("M500") ? QQPlaybackResolverTest.link(file) : QQPlaybackResolverTest.emptyLink();
        }, (uri, flac, timeout) -> uri);
        assertNotNull(resolver.resolve(QQPlaybackResolverTest.SONG));
        assertEquals(List.of("F000Media456.flac", "F000Media456.flac", "M800Media456.mp3", "M800Media456.mp3", "M500Media456.mp3"), files);
    }
}
