package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 固定向量用独立 MD5 实现按协议排序字符串计算，不从被测签名器反算期望值。 */
class KugouPlaybackSigningContractTest {
    private static final KugouTrack TRACK=new KugouTrack(new UnifiedSong("kugou",
            "ABCDEF0123456789ABCDEF0123456789","fixture","fixture","fixture",true),"123","456",240,"");
    private static final KugouAccount ACCOUNT=new KugouAccount("123456","fixture-token","a".repeat(32),"",
            List.of(new KugouAccount.Cookie("kg_dfid","fixture-dfid",".kugou.com","/",true,0)));

    @Test void webRequestMatchesCompleteGoldenVectorIncludingUppercaseSignature()throws Exception{
        var api=new KugouApi((endpoint,params,headers,body,cookies)->{
            assertEquals("https://wwwapi.kugou.com/play/songinfo",endpoint);assertNull(body);
            assertEquals(Map.ofEntries(Map.entry("appid","1014"),Map.entry("srcappid","2919"),
                    Map.entry("clientver","20000"),Map.entry("clienttime","1700000000123"),
                    Map.entry("mid","a".repeat(32)),Map.entry("uuid","a".repeat(32)),Map.entry("dfid","fixture-dfid"),
                    Map.entry("hash",TRACK.song().platformId()),Map.entry("album_id","123"),Map.entry("platid","4"),
                    Map.entry("userid","123456"),Map.entry("token","fixture-token"),
                    Map.entry("signature","CC6DD7AA7D723D441EAADCE9E1DA1EDF")),params);
            assertEquals(Map.of("User-Agent","Mozilla/5.0"),headers);
            assertTrue(cookies.get(java.net.URI.create(endpoint),Map.of()).toString().contains("kg_dfid=fixture-dfid"));
            return KugouLoginServiceTest.json("{\"status\":1}");
        },()->1700000000123L);
        api.webSongInfo(TRACK,ACCOUNT,true);
    }

    @Test void appRequestMatchesCompleteGoldenVectorIncludingKeyAndEmptyBodySignature()throws Exception{
        var api=new KugouApi((endpoint,params,headers,body,cookies)->{
            assertEquals("https://gateway.kugou.com/v5/url",endpoint);assertNull(body);
            assertEquals(Map.ofEntries(Map.entry("appid","1005"),Map.entry("clientver","11430"),
                    Map.entry("clienttime","1700000000"),Map.entry("mid","125999436132575330361201394184494153683"),
                    Map.entry("uuid","a".repeat(32)),Map.entry("dfid","fixture-dfid"),Map.entry("userid","123456"),
                    Map.entry("token","fixture-token"),Map.entry("hash",TRACK.song().platformId().toLowerCase(Locale.ROOT)),
                    Map.entry("quality","320"),Map.entry("album_id","123"),Map.entry("album_audio_id","456"),
                    Map.entry("area_code","1"),Map.entry("ssa_flag","is_fromtrack"),Map.entry("version","11430"),
                    Map.entry("page_id","151369488"),Map.entry("ppage_id","463467626,350369493,788954147"),
                    Map.entry("behavior","play"),Map.entry("pid","2"),Map.entry("cmd","26"),Map.entry("pidversion","3001"),
                    Map.entry("IsFreePart","0"),Map.entry("cdnBackup","1"),Map.entry("module",""),
                    Map.entry("key","0df423227691478581873bec61b21268"),Map.entry("signature","244510f2c6a6f438681cb2f7ee9e7140")),params);
            assertEquals(Map.of("User-Agent","Mozilla/5.0","x-router","trackercdn.kugou.com",
                    "mid",params.get("mid"),"dfid","fixture-dfid","clienttime","1700000000","kg-rc","1","kg-rec","1",
                    "kg-thash","5d816a0","kg-rf","B9EDA08A64250DEFFBCADDEE00F8F25F"),headers);
            return KugouLoginServiceTest.json("{\"status\":1}");
        },()->1700000000123L);
        api.quality(TRACK,ACCOUNT,KugouQuality.HQ,true);
    }

    @Test void missingDfidAndMetadataUseProtocolSentinelsWithoutInventingFingerprints()throws Exception{
        var api=new KugouApi((endpoint,params,headers,body,cookies)->{
            assertEquals("-",params.get("dfid"));
            if(endpoint.contains("wwwapi"))assertEquals("",params.get("album_id"));
            else {assertEquals("0",params.get("album_id"));assertEquals("0",params.get("album_audio_id"));}
            return KugouLoginServiceTest.json("{\"status\":1}");
        });
        var guest=KugouAccount.guest("a".repeat(32));
        api.webSongInfo(TRACK,guest,false);api.quality(TRACK,guest,KugouQuality.STANDARD,false);
    }
}
