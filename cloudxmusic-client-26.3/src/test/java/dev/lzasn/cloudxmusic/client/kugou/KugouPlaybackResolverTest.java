package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouPlaybackResolverTest {
    @Test void explicitWholeSongLockSkipsEvenAtHighQualityButQualityDenialStillFallsBack() throws Exception {
        var locked=new KugouPlaybackResolver((s,a,q,m)->KugouLoginServiceTest.json("{\"status\":0,\"error_code\":403,\"song_locked\":1,\"message\":\"无试听资格\"}"),(u,q,b)->null);
        assertEquals(KugouPlaybackAccessException.Reason.SONG_LOCKED,assertThrows(KugouPlaybackAccessException.class,()->locked.resolve(track(),KugouAccount.guest("a".repeat(32)),KugouQuality.HIGH)).reason());
        var fallback=new KugouPlaybackResolver((s,a,q,m)->KugouLoginServiceTest.json(q==KugouQuality.STANDARD?"{\"status\":1,\"url\":\"https://fs.kugou.com/a.mp3\",\"privilege\":0}":"{\"status\":0,\"error_code\":403,\"privilege\":10}"),(u,q,b)->new KugouPlaybackResolver.Verified(u,q));
        var result=fallback.resolve(track(),KugouAccount.guest("a".repeat(32)),KugouQuality.HIGH);
        assertEquals(KugouQuality.STANDARD,result.actualQuality());assertTrue(result.permissionFallback());
    }
    @Test void genericErrorsKeepTheirCodeAndDoNotBecomeVipLocks() {
        var denied=new KugouPlaybackResolver((s,a,q,m)->KugouLoginServiceTest.json("{\"status\":0,\"error_code\":403}"),(u,q,b)->null);
        assertTrue(assertThrows(IOException.class,()->denied.resolve(track(),KugouAccount.guest("a".repeat(32)),KugouQuality.STANDARD)).getMessage().contains("code=403"));
        var missing=new KugouPlaybackResolver((s,a,q,m)->KugouLoginServiceTest.json("{\"status\":0}"),(u,q,b)->null);
        assertTrue(assertThrows(IOException.class,()->missing.resolve(track(),KugouAccount.guest("a".repeat(32)),KugouQuality.STANDARD)).getMessage().contains("status=0"));
    }
    @Test void appCode20018DoesNotDiscardVerifiedWebAudioOrInventVipDenial() throws Exception {
        var resolver=new KugouPlaybackResolver((s,a,q,m)->KugouLoginServiceTest.json("{\"status\":2,\"error_code\":20018,\"privilege\":8,\"require_vip_type\":\"vip\"}"),
                (s,a,m)->KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"url\":\"https://fs.kugou.com/standard.mp3\",\"bitrate\":128000,\"privilege\":8}}"),
                (u,q,b)->new KugouPlaybackResolver.Verified(u,KugouQuality.STANDARD));

        var link=resolver.resolve(track(),new KugouAccount("123","web-token","a".repeat(32),"VIP",List.of()),KugouQuality.FLAC);
        assertEquals(KugouQuality.STANDARD,link.actualQuality());assertFalse(link.permissionFallback());
        assertFalse(KugouPrivileges.explicitPermissionDenied(KugouLoginServiceTest.json("{\"status\":2,\"error_code\":20018,\"privilege\":8,\"require_vip_type\":\"vip\"}")));
    }
    @Test void explicitHigherTierPermissionDenialMarksTheActualLowerTier() throws Exception {
        var resolver=new KugouPlaybackResolver(
                (s,a,q,m)->KugouLoginServiceTest.json(q==KugouQuality.HQ
                        ?"{\"status\":0,\"error_code\":403,\"privilege\":10}"
                        :"{\"status\":1,\"url\":\"https://fs.kugou.com/standard.mp3\",\"bitrate\":128000}"),
                (u,q,b)->new KugouPlaybackResolver.Verified(u,KugouQuality.STANDARD));

        var result=resolver.resolve(track(),KugouAccount.guest("a".repeat(32)),KugouQuality.HIGH);

        assertEquals(KugouQuality.STANDARD,result.actualQuality());
        assertTrue(result.permissionFallback());
    }
    @Test void webTimeoutCanRecoverThroughAppWithoutInventingVipDenial() throws Exception {
        var appCalls=new ArrayList<KugouQuality>();
        var failing=new KugouPlaybackResolver((s,a,q,m)->{appCalls.add(q);return KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"url\":\"https://fs.kugou.com/standard.mp3\",\"bitrate\":128000}}");},
                (s,a,m)->{throw new java.net.SocketTimeoutException("timeout");},
                (u,q,b)->new KugouPlaybackResolver.Verified(u,KugouQuality.STANDARD));
        var link=failing.resolve(track(),new KugouAccount("123","web-token","a".repeat(32),"VIP",List.of()),KugouQuality.HIGH);
        assertEquals(KugouQuality.STANDARD,link.actualQuality());assertFalse(link.permissionFallback());assertFalse(appCalls.isEmpty());
    }
    @Test void explicitPermissionDenialCannotBeOverriddenByAUrlInTheSameResponse() {
        var denied=new KugouPlaybackResolver((s,a,q,m)->KugouLoginServiceTest.json("{\"status\":1,\"error_code\":403,\"message\":\"permission denied\",\"url\":\"https://fs.kugou.com/forbidden.mp3\"}"),(u,q,b)->{fail("拒绝权限的直链不能交给探测器");return null;});
        assertEquals(KugouPlaybackAccessException.Reason.SONG_LOCKED,assertThrows(KugouPlaybackAccessException.class,()->denied.resolve(track(),KugouAccount.guest("a".repeat(32)),KugouQuality.STANDARD)).reason());
    }
    @Test void ordinaryVipWebSessionCanSupplyLosslessBeforeAppQualityFallback() throws Exception {
        var appCalls=new ArrayList<KugouQuality>();var webCalls=new ArrayList<Boolean>();
        var resolver=new KugouPlaybackResolver((song,account,quality,metadata)->{
            appCalls.add(quality);return KugouLoginServiceTest.json("{\"status\":0}");
        },(song,account,metadata)->{
            webCalls.add(metadata);return KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"url\":\"https://fs.kugou.com/vip.flac\",\"extName\":\"flac\",\"timelength\":240000}}");
        },(uri,quality,bitrate)->new KugouPlaybackResolver.Verified(uri,KugouQuality.FLAC));

        var link=resolver.resolve(track(),new KugouAccount("123","web-token","d".repeat(32),"VIP",List.of()),KugouQuality.HIGH);

        assertEquals(KugouQuality.FLAC,link.actualQuality());
        assertFalse(link.permissionFallback(),"Web 回包没有明确权限拒绝，不能伪报 VIP 降级");
        assertTrue(appCalls.isEmpty(),"Web 返回 FLAC 后不应再向 App 请求未支持的 high/flac 档");
        assertEquals(List.of(true),webCalls);
    }
    @Test void legacyStandardPreferenceDoesNotDiscardServerAssignedFlac() throws Exception {
        var appCalls=new ArrayList<KugouQuality>();
        var resolver=new KugouPlaybackResolver((song,account,quality,metadata)->{
            appCalls.add(quality);return KugouLoginServiceTest.json("{\"status\":1,\"url\":\"https://fs.kugou.com/standard.mp3\",\"bitrate\":128000}");
        },(song,account,metadata)->KugouLoginServiceTest.json("{\"status\":1,\"url\":\"https://fs.kugou.com/vip.flac\",\"extName\":\"flac\"}"),
                (uri,quality,bitrate)->new KugouPlaybackResolver.Verified(uri,uri.getPath().endsWith(".flac")?KugouQuality.FLAC:KugouQuality.STANDARD));

        var link=resolver.resolve(track(),new KugouAccount("123","web-token","e".repeat(32),"VIP",List.of()),KugouQuality.STANDARD);

        assertEquals(KugouQuality.FLAC,link.actualQuality());
        assertTrue(appCalls.isEmpty());
    }
    @Test void playableWebAudioReturnsImmediatelyWithoutAppQualityProbing()throws Exception{
        var attempts=new ArrayList<String>();
        var resolver=new KugouPlaybackResolver((song,account,quality,metadata)->{
            attempts.add("app:"+quality.apiValue()+":"+metadata);
            return KugouLoginServiceTest.json("{\"status\":1,\"url\":\"https://fs.kugou.com/320.mp3\",\"bitrate\":320000}");
        },(song,account,metadata)->{
            attempts.add("web:"+metadata);
            return KugouLoginServiceTest.json("{\"status\":1,\"url\":\"https://fs.kugou.com/standard.mp3\",\"bitrate\":128000}");
        },(uri,quality,bitrate)->new KugouPlaybackResolver.Verified(uri,bitrate>=320000?KugouQuality.HQ:KugouQuality.STANDARD));

        var link=resolver.resolve(track(),new KugouAccount("123","web-token","f".repeat(32),"VIP",List.of()),KugouQuality.HIGH);

        assertEquals(KugouQuality.STANDARD,link.actualQuality());
        assertEquals(List.of("web:true"),attempts);
        assertEquals("https://fs.kugou.com/standard.mp3",link.url());
    }
    @Test void unavailableWebTries320Then128EvenWithLegacyStandardPreference()throws Exception{
        var attempts=new ArrayList<String>();
        var resolver=new KugouPlaybackResolver((song,account,quality,metadata)->{
            attempts.add("app:"+quality.apiValue()+":"+metadata);
            return KugouLoginServiceTest.json(quality==KugouQuality.HQ?"{\"status\":1}":
                    "{\"status\":1,\"url\":\"https://fs.kugou.com/128.mp3\",\"bitrate\":128000}");
        },(song,account,metadata)->{
            attempts.add("web:"+metadata);return KugouLoginServiceTest.json("{\"status\":1}");
        },(uri,quality,bitrate)->new KugouPlaybackResolver.Verified(uri,KugouQuality.STANDARD));
        var link=resolver.resolve(track(),new KugouAccount("123","token","f".repeat(32),"",List.of()),KugouQuality.STANDARD);
        assertEquals(List.of("web:true","web:false","app:320:true","app:320:false","app:128:true"),attempts);
        assertEquals(KugouQuality.STANDARD,link.actualQuality());assertFalse(link.permissionFallback());
    }
    static final String HASH="0123456789ABCDEF0123456789ABCDEF";
    static KugouTrack track(){return KugouTrack.parse(JsonParser.parseString("{\"hash\":\""+HASH+"\",\"album_id\":\"123\",\"songname\":\"测试\",\"singername\":\"歌手\",\"duration\":240}").getAsJsonObject());}
    @Test void appFallbackTries320Before128AndRetainsAlbumId() throws Exception {
        List<String> attempts=new ArrayList<>();
        var resolver=new KugouPlaybackResolver((song,account,q,metadata)->{
            assertEquals("123",song.albumId());attempts.add(q.apiValue());
            return JsonParser.parseString(q==KugouQuality.STANDARD?"{\"status\":1,\"url\":\"https://fs.kugou.com/test.mp3\",\"bitrate\":128000}":"{\"status\":0,\"error_code\":403,\"privilege\":10}").getAsJsonObject();
        },(uri,q,bitrate)->new KugouPlaybackResolver.Verified(uri,q));
        var link=resolver.resolve(track(),KugouAccount.guest("a".repeat(32)),KugouQuality.HIGH);
        assertEquals(KugouQuality.STANDARD,link.actualQuality());
        assertEquals(List.of("320","320","128"),attempts);
    }
    @Test void actualProbeControlsAutomaticQualityAndTrialIsRejected() throws Exception {
        List<KugouQuality> attempts=new ArrayList<>();
        var resolver=new KugouPlaybackResolver((song,a,q,m)->{attempts.add(q);return JsonParser.parseString("{\"status\":1,\"url\":\"https://fs.kugou.com/test.mp3\",\"timelength\":240000}").getAsJsonObject();},
                (uri,q,b)->new KugouPlaybackResolver.Verified(uri,KugouQuality.STANDARD));
        assertEquals(KugouQuality.STANDARD,resolver.resolve(track(),KugouAccount.guest("b".repeat(32)),KugouQuality.HQ).actualQuality());
        assertEquals(List.of(KugouQuality.HQ),attempts);
        var trial=new KugouPlaybackResolver((s,a,q,m)->JsonParser.parseString("{\"status\":1,\"url\":\"https://fs.kugou.com/trial.mp3\",\"timelength\":30000}").getAsJsonObject(),(uri,q,b)->new KugouPlaybackResolver.Verified(uri,q));
        assertThrows(KugouPlaybackAccessException.class,()->trial.resolve(track(),KugouAccount.guest("b".repeat(32)),KugouQuality.STANDARD));
    }
    @Test void unusableWebResultsStillReachFullAppAudio()throws Exception{
        for(String response:List.of(
                "{\"status\":1}",
                "{\"status\":1,\"data\":{\"play_url\":\"https://fs.kugou.com/trial.mp3\",\"timelength\":30000}}",
                "{\"status\":1,\"url\":\"https://fs.kugou.com/trial.mp3\",\"is_free_part\":1}",
                "{\"status\":1,\"url\":\"https://fs.kugou.com/opus.ogg\",\"extName\":\"opus\"}",
                "{\"status\":1,\"url\":\"https://evil.example/not-a-kugou-cdn.mp3\"}",
                "{\"status\":1,\"url\":\"https://fs.kugou.com/unplayable.mp3\"}",
                "{\"status\":2,\"error_code\":20018}")){
            var calls=new ArrayList<KugouQuality>();
            var resolver=new KugouPlaybackResolver((s,a,q,m)->{
                calls.add(q);return KugouLoginServiceTest.json("{\"status\":1,\"url\":\"https://fs.kugou.com/full.mp3\",\"bitrate\":320000}");
            },(s,a,m)->KugouLoginServiceTest.json(response),(uri,q,b)->{
                if(!uri.getPath().equals("/full.mp3"))throw new IOException("CDN HTTP 403");
                return new KugouPlaybackResolver.Verified(uri,KugouQuality.HQ);
            });
            var link=resolver.resolve(track(),new KugouAccount("123","token","a".repeat(32),"",List.of()),KugouQuality.STANDARD);
            assertEquals(KugouQuality.HQ,link.actualQuality());assertEquals(List.of(KugouQuality.HQ),calls);
        }
    }
    @Test void automaticRoutingStopsOnWholeSongLockAndPreservesNetworkFailure(){
        var account=new KugouAccount("123","token","a".repeat(32),"",List.of());
        var locked=new KugouPlaybackResolver((s,a,q,m)->{fail("整首锁定不应继续请求");return null;},
                (s,a,m)->KugouLoginServiceTest.json("{\"status\":0,\"song_locked\":1}"),(u,q,b)->null);
        assertEquals(KugouPlaybackAccessException.Reason.SONG_LOCKED,
                assertThrows(KugouPlaybackAccessException.class,()->locked.resolve(track(),account,KugouQuality.STANDARD)).reason());
        var unavailable=new KugouPlaybackResolver((s,a,q,m)->{throw new java.net.SocketTimeoutException("app timeout");},
                (s,a,m)->{throw new java.net.SocketTimeoutException("web timeout");},(u,q,b)->null);
        assertInstanceOf(java.net.SocketTimeoutException.class,
                assertThrows(IOException.class,()->unavailable.resolve(track(),account,KugouQuality.HIGH)));
    }
    @Test void untrustedCdnAndSecurityChallengeAreNeverSentToDecoder() {
        var resolver=new KugouPlaybackResolver((s,a,q,m)->JsonParser.parseString("{\"status\":1,\"url\":\"https://evil.example/a.mp3\"}").getAsJsonObject(),(uri,q,b)->{fail("不应探测非酷狗地址");return null;});
        assertThrows(KugouPlaybackAccessException.class,()->resolver.resolve(track(),KugouAccount.guest("c".repeat(32)),KugouQuality.STANDARD));
        var blocked=new KugouPlaybackResolver((s,a,q,m)->JsonParser.parseString("{\"status\":0,\"error_code\":30020}").getAsJsonObject(),(uri,q,b)->null);
        assertEquals(KugouPlaybackAccessException.Reason.VERIFICATION_REQUIRED,
                assertThrows(KugouPlaybackAccessException.class,()->blocked.resolve(track(),KugouAccount.guest("c".repeat(32)),KugouQuality.HIGH)).reason());
    }
}
