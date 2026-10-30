package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QQPlaybackResolverTest {
    @Test void restriction22006FallsBackThroughEachQualityAndReturnsNoResourceOnlyAfterSessionCheck()throws Exception{
        for(String playable:List.of("M800","M500","none")){
            List<String> files=new ArrayList<>();List<String> methods=new ArrayList<>();
            var resolver=resolver((r,c,t)->{
                methods.add(method(r));
                if(method(r).equals("get_song_detail_yqq"))return metadata();
                if(method(r).equals("GetLoginUserInfo"))return reply(0,"{\"musicid\":123456}");
                String file=filename(r);files.add(file);
                return file.startsWith(playable)?link(file):reply(22006,"{}");
            },(u,f,t)->u);
            var result=resolver.resolveLink(SONG,QQQuality.SQ,info->{});
            if(playable.equals("none")){
                assertNull(result);assertEquals(6,files.size());assertEquals("GetLoginUserInfo",methods.getLast());
            }else{
                assertNotNull(result);assertTrue(result.url().contains(playable));
                assertEquals(playable.equals("M800")?QQQuality.HQ:QQQuality.STANDARD,result.actualQuality());
                assertEquals(playable.equals("M800")?3:5,files.size());
            }
            assertEquals(List.of("F000Media456.flac","F000Media456.flac"),files.subList(0,2));
        }
    }
    @Test void restrictionsCannotHideUnknownBusinessErrorsOrBrokenRows() {
        for(boolean row:new boolean[]{false,true}){
            var resolver=resolver((r,c,t)->{
                if(method(r).equals("get_song_detail_yqq"))return metadata();
                if(method(r).equals("GetLoginUserInfo"))return reply(0,"{\"musicid\":123456}");
                if(filename(r).startsWith("F000"))return row?reply(0,"{\"midurlinfo\":[{\"result\":54321}]}"):reply(54321,"{}");
                return reply(22006,"{}");
            },(u,f,t)->u);
            assertTrue(assertThrows(IOException.class,()->resolver.resolve(SONG)).getMessage().contains("54321"));
        }
    }
    @Test void metadataRestrictionIsNoResourceButExpiredVerificationStillFails()throws Exception{
        var resolver=resolver((r,c,t)->method(r).equals("get_song_detail_yqq")?reply(22006,"{}"):reply(0,"{\"musicid\":123456}"),(u,f,t)->fail("no media identity"));
        assertNull(resolver.resolve(SONG));
        var expired=resolver((r,c,t)->method(r).equals("get_song_detail_yqq")?reply(22006,"{}"):reply(104401,"{}"),(u,f,t)->u);
        assertThrows(QQSessionVerifier.Rejected.class,()->expired.resolve(SONG));
    }
    @Test void manualMp3NeverRequestsSqAndPreservesMediaIdentity()throws Exception{
        List<String> calls=new ArrayList<>();
        var resolver=resolver((r,c,t)->{if(method(r).equals("get_song_detail_yqq"))return metadata();calls.add(filename(r));return link(filename(r));},(u,f,t)->{assertFalse(f);return u;});
        assertTrue(resolver.resolve(SONG,QQQuality.HQ,info->assertEquals("Media456",info.mediaMid())).contains("M800Media456.mp3"));
        assertEquals(List.of("M800Media456.mp3"),calls);
    }
    static final UnifiedSong SONG = new UnifiedSong("qq","Song123","测试曲目","歌手","专辑");
    static final String HOST = "https://isure.stream.qqmusic.qq.com/";
    static QQAccountManager.Snapshot session() { return new QQAccountManager.Snapshot(new QQCredentials("123456","fake-key",2,"昵称",0),7); }
    static String method(JsonObject r) { return r.getAsJsonObject("req_0").get("method").getAsString(); }
    static String filename(JsonObject r) { return r.getAsJsonObject("req_0").getAsJsonObject("param").getAsJsonArray("filename").get(0).getAsString(); }
    static JsonObject metadata() { return reply(0,"{\"track_info\":{\"mid\":\"Song123\",\"file\":{\"media_mid\":\"Media456\"}}}"); }
    static JsonObject link(String file) { return reply(0,"{\"sip\":[\""+HOST+"\"],\"midurlinfo\":[{\"filename\":\""+file+"\",\"songmid\":\"Song123\",\"result\":0,\"purl\":\""+file+"?vkey=opaque\"}]}"); }
    static JsonObject emptyLink() { return reply(0,"{\"sip\":[],\"midurlinfo\":[{\"purl\":\"\",\"result\":0}]}"); }
    static QQPlaybackResolver resolver(QQPlaybackApi.Transport t, QQPlaybackResolver.AudioCheck audio) {
        var s=session(); return new QQPlaybackResolver(()->s,x->true,t,audio);
    }
    @Test void sqUsesActualMediaMidAndRetainsOriginalSongMid() throws Exception {
        List<String> calls=new ArrayList<>();
        var resolver=resolver((r,c,timeout)->{
            calls.add(method(r)); assertTrue(timeout.compareTo(Duration.ofSeconds(15))<=0);
            assertEquals("fake-key",r.getAsJsonObject("comm").get("authst").getAsString());
            assertEquals(3,c.getCookieStore().getCookies().size());
            if (method(r).equals("get_song_detail_yqq")) return metadata();
            assertEquals("F000Media456.flac",filename(r));
            assertEquals("Song123",r.getAsJsonObject("req_0").getAsJsonObject("param").getAsJsonArray("songmid").get(0).getAsString());
            return link(filename(r));
        },(uri,flac,timeout)->{assertTrue(flac);return uri;});
        assertEquals(HOST+"F000Media456.flac?vkey=opaque",new QQProvider(resolver).getPlaybackUrl(SONG));
        assertEquals(List.of("get_song_detail_yqq","UrlGetVkey"),calls);
    }
    @Test void sqWebCompatibilityIsTriedBeforeMp3() throws Exception {
        List<String> calls=new ArrayList<>();
        var resolver=resolver((r,c,t)->{
            if (method(r).equals("get_song_detail_yqq")) return metadata();
            calls.add(filename(r)+":"+method(r));
            if (method(r).equals("UrlGetVkey")) return emptyLink();
            assertEquals(24,r.getAsJsonObject("comm").get("ct").getAsInt());
            assertEquals("20",r.getAsJsonObject("req_0").getAsJsonObject("param").get("platform").getAsString());
            return link(filename(r));
        },(u,f,t)->u);
        assertTrue(resolver.resolve(SONG).contains("F000Media456.flac"));
        assertEquals(List.of("F000Media456.flac:UrlGetVkey","F000Media456.flac:CgiGetVkey"),calls);
    }
    @Test void fallbackOrderIsF000DesktopF000WebThenM800() throws Exception {
        List<String> calls=new ArrayList<>();
        var resolver=resolver((r,c,t)->{
            if (method(r).equals("get_song_detail_yqq")) return metadata();
            calls.add(filename(r)+":"+method(r)); return filename(r).startsWith("F000")?emptyLink():link(filename(r));
        },(u,f,t)->{assertFalse(f);return u;});
        assertTrue(resolver.resolve(SONG).contains("M800Media456.mp3"));
        assertEquals(List.of("F000Media456.flac:UrlGetVkey","F000Media456.flac:CgiGetVkey","M800Media456.mp3:UrlGetVkey"),calls);
    }
    @Test void inaccessibleOrNonAudioSqFallsBackToActualMp3() throws Exception {
        var resolver=resolver((r,c,t)->method(r).equals("get_song_detail_yqq")?metadata():link(filename(r)),(u,f,t)->f?null:u);
        assertTrue(resolver.resolve(SONG).contains("M800Media456.mp3"));
    }
    @Test void sqCdnNetworkFailureCanFallBackWithoutHidingFinalFailures() throws Exception {
        var resolver=resolver((r,c,t)->method(r).equals("get_song_detail_yqq")?metadata():link(filename(r)),(u,f,t)->{
            if(f) throw new IOException("SQ CDN unavailable"); return u;
        });
        assertTrue(resolver.resolve(SONG).contains("M800Media456.mp3"));
    }
    @Test void emptyPurlUsesReadOnlySessionCheckAndDoesNotMutateLogin() throws Exception {
        List<String> calls=new ArrayList<>();
        var resolver=resolver((r,c,t)->{
            calls.add(method(r));
            return switch(method(r)) {case "get_song_detail_yqq"->metadata();case "GetLoginUserInfo"->reply(0,"{\"musicid\":123456}");default->emptyLink();};
        },(u,f,t)->fail("no CDN request without purl"));
        assertNull(resolver.resolve(SONG)); assertEquals(8,calls.size()); assertEquals("GetLoginUserInfo",calls.getLast()); assertFalse(calls.contains("Login"));
    }
    @Test void expiredSessionIsNotTreatedAsNoResource() {
        var resolver=resolver((r,c,t)->method(r).equals("get_song_detail_yqq")?metadata():reply(104401,"{}"),(u,f,t)->u);
        assertThrows(QQSessionVerifier.Rejected.class,()->resolver.resolve(SONG));
    }
    @Test void emptyLinksWithExpiredProfileAreExplicit() {
        var resolver=resolver((r,c,t)->switch(method(r)){case "get_song_detail_yqq"->metadata();case "GetLoginUserInfo"->reply(1000,"{}");default->emptyLink();},(u,f,t)->u);
        assertThrows(QQSessionVerifier.Rejected.class,()->resolver.resolve(SONG));
    }
    @Test void missingMediaCannotReachVkeyOrCdn() {
        var resolver=resolver((r,c,t)->{assertEquals("get_song_detail_yqq",method(r));return reply(0,"{\"track_info\":{\"file\":{}}}");},(u,f,t)->fail("no CDN"));
        assertThrows(IOException.class,()->resolver.resolve(SONG));
    }
    @Test void networkAndMalformedJsonShapeNeverBecomeNull() {
        assertThrows(IOException.class,()->resolver((r,c,t)->{throw new IOException("offline");},(u,f,t)->u).resolve(SONG));
        assertThrows(IOException.class,()->resolver((r,c,t)->json("{}"),(u,f,t)->u).resolve(SONG));
    }
    @Test void accountChangeOrCancellationInvalidatesInFlightResponse() {
        AtomicBoolean active=new AtomicBoolean(true); var s=session();
        var resolver=new QQPlaybackResolver(()->s,x->active.get(),(r,c,t)->{active.set(false);return metadata();},(u,f,t)->u);
        assertThrows(IOException.class,()->resolver.resolve(SONG));
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedException.class,()->resolver((r,c,t)->fail("no request"),(u,f,t)->u).resolve(SONG)); }
        finally {Thread.interrupted();}
    }
    @Test void invalidSourceIsRejectedBeforeReadingCredentials() {
        var resolver=new QQPlaybackResolver(()->fail("no account access"),x->true,(r,c,t)->fail("no request"),(u,f,t)->u);
        assertThrows(IllegalArgumentException.class,()->resolver.resolve(new UnifiedSong("netease","123","","","")));
        assertThrows(IllegalArgumentException.class,()->resolver.resolve(new UnifiedSong("qq","../bad","","","")));
    }
    @Test void parserPrefersHttpsAndPreservesSignedQuery() throws Exception {
        var data=json("{\"sip\":[\"http://dl.stream.qqmusic.qq.com/\",\"https://isure.stream.qqmusic.qq.com/\"],\"midurlinfo\":[{\"purl\":\"F000Media456.flac?vkey=A%2BB&guid=1\"}]}");
        var urls=QQPlaybackResolver.candidates(data,"F000Media456.flac","Song123",true);
        assertEquals("https",urls.getFirst().getScheme()); assertEquals("vkey=A%2BB&guid=1",urls.getFirst().getRawQuery()); assertEquals(2,urls.size());
    }
    @Test void absolutePurlDoesNotNeedSipAndEncryptedOrWrongFilesAreRejected() throws Exception {
        assertEquals(1,QQPlaybackResolver.candidates(json("{\"midurlinfo\":[{\"purl\":\""+HOST+"F000Media456.flac?vkey=1\"}]}"),"F000Media456.flac","Song123",true).size());
        for (String path:List.of(HOST+"F000Media456.mflac",HOST+"Other.flac","https://evil.example/F000Media456.flac","http://127.0.0.1/F000Media456.flac"))
            assertThrows(IOException.class,()->QQPlaybackResolver.candidates(json("{\"midurlinfo\":[{\"purl\":\""+path+"\"}]}"),"F000Media456.flac","Song123",true));
    }
    @Test void malformedRowsAndMismatchedIdentityAreExplicit() {
        for(String data:List.of("{}","{\"midurlinfo\":{}}","{\"midurlinfo\":[1]}","{\"midurlinfo\":[{\"purl\":null}]}",
                "{\"midurlinfo\":[{\"filename\":\"other\",\"purl\":\"\"}]}","{\"midurlinfo\":[{\"songmid\":\"other\",\"purl\":\"\"}]}"))
            assertThrows(IOException.class,()->QQPlaybackResolver.candidates(json(data),"F000Media456.flac","Song123",true));
    }
    @Test void accountReadOnlyVerificationMustMatchCurrentIdentity() {
        var resolver=resolver((r,c,t)->switch(method(r)){case "get_song_detail_yqq"->metadata();case "GetLoginUserInfo"->reply(0,"{\"musicid\":999}");default->emptyLink();},(u,f,t)->u);
        assertThrows(IOException.class,()->resolver.resolve(SONG));
    }
}
