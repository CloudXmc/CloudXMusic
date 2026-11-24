package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.kugou.KugouPlaybackControllerTest.pump;

class KugouFavoritesTest {
    static final KugouAccount ACCOUNT=new KugouAccount("123","token","a".repeat(32),"用户",List.of());
    static final KugouTrack TRACK=KugouPlaybackResolverTest.track();
    static KugouFavoritesService.Snapshot empty(){return new KugouFavoritesService.Snapshot(9,List.of());}
    static KugouFavoritesService.Snapshot liked(){return new KugouFavoritesService.Snapshot(9,List.of(new KugouFavoritesService.Entry(TRACK,71)));}
    @Test void writeFailureRollsBackAndDoubleClickCannotSubmitAgain()throws Exception{
        var main=new ConcurrentLinkedQueue<Runnable>();var notices=new ArrayList<String>();var calls=new AtomicInteger();var release=new CountDownLatch(1);
        var remote=new KugouFavoritesService.Remote(){public KugouFavoritesService.Snapshot load(KugouAccount a,BooleanSupplier active){return empty();}public KugouFavoritesService.Snapshot set(KugouAccount a,KugouTrack t,boolean liked,BooleanSupplier active)throws Exception{calls.incrementAndGet();release.await();throw new java.io.IOException();}};
        try(var c=new KugouFavoritesController(remote,()->ACCOUNT,()->1,main::add,notices::add)){
            c.toggle(TRACK);assertTrue(c.contains(TRACK.song()));c.toggle(TRACK);pump(main,()->calls.get()==1);release.countDown();pump(main,()->!c.busy());assertFalse(c.contains(TRACK.song()));assertEquals(1,calls.get());assertTrue(notices.getLast().contains("恢复原状态"));
        }finally{release.countDown();}
    }
    @Test void staleAccountResultsAreDiscardedAndBusyTrackChangeRefreshesAfterward()throws Exception{
        var main=new ConcurrentLinkedQueue<Runnable>();var revision=new AtomicLong(1);var calls=new AtomicInteger();var release=new CountDownLatch(1);
        var remote=new KugouFavoritesService.Remote(){public KugouFavoritesService.Snapshot load(KugouAccount a,BooleanSupplier active)throws Exception{if(calls.incrementAndGet()==1)release.await();return liked();}public KugouFavoritesService.Snapshot set(KugouAccount a,KugouTrack t,boolean liked,BooleanSupplier active){return empty();}};
        try(var c=new KugouFavoritesController(remote,()->ACCOUNT,revision::get,main::add,s->{})){
            c.update(null,true);pump(main,()->calls.get()==1);c.update(TRACK.song(),true);release.countDown();pump(main,()->!c.busy());c.update(TRACK.song(),true);pump(main,()->calls.get()==2&&!c.busy());assertTrue(c.contains(TRACK.song()));
            revision.incrementAndGet();c.update(null,false);assertFalse(c.contains(TRACK.song()));assertTrue(c.tracks().isEmpty());
        }finally{release.countDown();}
    }
    @Test void cloudWriteUsesTokenRealFileIdAndServerConfirmation()throws Exception{
        var liked=new AtomicBoolean(true);var mutations=new AtomicInteger();
        var api=new KugouApi((url,params,headers,body,cookies)->{
            assertEquals("token",params.get("token"));assertEquals("cloudlist.service.kugou.com",headers.get("x-router"));
            var json=KugouLoginServiceTest.json(body);assertEquals("123",json.get("userid").getAsString());assertEquals("token",json.get("token").getAsString());
            if(url.endsWith("get_all_list"))return KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"info\":[{\"listid\":9,\"is_def\":2}]}}");
            if(url.endsWith("get_list_all_file"))return KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"info\":"+(liked.get()?"[{\"hash\":\""+TRACK.song().platformId()+"\",\"fileid\":71,\"name\":\"歌手 - 测试\",\"album_id\":123}]":"[]")+"}}");
            if(url.endsWith("delete_songs")){assertEquals(71,json.getAsJsonArray("data").get(0).getAsJsonObject().get("fileid").getAsLong());liked.set(false);}else{assertTrue(url.endsWith("add_song"));assertEquals(TRACK.song().platformId(),json.getAsJsonArray("data").get(0).getAsJsonObject().get("hash").getAsString());liked.set(true);}mutations.incrementAndGet();return KugouLoginServiceTest.json("{\"status\":1}");
        });
        var service=new KugouFavoritesService(api);assertFalse(service.set(ACCOUNT,TRACK,false,()->true).contains(TRACK.song().platformId()));assertTrue(service.set(ACCOUNT,TRACK,true,()->true).contains(TRACK.song().platformId()));assertEquals(2,mutations.get());
    }
    @Test void cloudFavoriteKeepsAlbumMetadataAndUsesWebPlaybackRoute()throws Exception{
        String hash="abcdef0123456789abcdef0123456789";
        var api=new KugouApi((url,params,headers,body,cookies)->url.endsWith("/v7/get_all_list")
                ?KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"info\":[{\"listid\":9,\"is_def\":2}]}}")
                :KugouLoginServiceTest.json("{\"status\":1,\"data\":{\"count\":1,\"info\":[{\"filename\":\"Papa Tin - Una Mattina.mp3\",\"timelen\":177000,\"fileid\":71,\"audio_info\":{\"hash_128\":\""+hash+"\"},\"album_info\":{\"album_id\":42,\"album_name\":\"Piano Covers\"},\"base\":{\"album_audio_id\":73},\"privilege\":1}]}}"));
        try{
            var snapshot=new KugouFavoritesService(api).load(ACCOUNT,()->true);
            var favorite=snapshot.entries().getFirst().track();var webCalls=new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
            var resolver=new KugouPlaybackResolver((song,account,quality,metadata)->KugouLoginServiceTest.json("{\"status\":0}"),
                    (song,account,metadata)->{webCalls.add(metadata);if(metadata){assertEquals("42",song.albumId());assertEquals("73",song.albumAudioId());}return KugouLoginServiceTest.json("{\"status\":1,\"url\":\"https://fs.kugou.com/vip.flac\",\"extName\":\"flac\",\"timelength\":177000}");},
                    (uri,quality,bitrate)->new KugouPlaybackResolver.Verified(uri,KugouQuality.FLAC));
            var link=resolver.resolve(favorite,ACCOUNT,KugouQuality.HIGH);
            assertEquals(KugouQuality.FLAC,link.actualQuality());assertEquals(List.of(true),webCalls);
        }finally{api.close();}
    }
}
