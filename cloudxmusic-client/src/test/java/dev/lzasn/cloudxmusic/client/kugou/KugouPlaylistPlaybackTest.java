package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static dev.lzasn.cloudxmusic.client.kugou.KugouPlaybackControllerTest.pump;
import static org.junit.jupiter.api.Assertions.*;

class KugouPlaylistPlaybackTest {
    @TempDir Path directory;

    @Test void likedPageKeepsSelectedHashAndAlbumIdsThroughThePlaybackController() throws Exception {
        String firstHash="0123456789abcdef0123456789abcdef";
        String selectedHash="abcdef0123456789abcdef0123456789";
        var accounts=new KugouAccountStore(directory.resolve("kugou/account.json"));
        var guest=accounts.account();
        assertTrue(accounts.commit(accounts.revision(),new KugouAccount("123","test-token",guest.deviceId(),"测试",List.of())));
        var api=new KugouApi((endpoint,params,headers,body,cookies)->{
            if(endpoint.endsWith("/v7/get_all_list"))return KugouLoginServiceTest.json(
                    "{\"status\":1,\"data\":{\"info\":[{\"listid\":9,\"type\":0,\"is_def\":2,\"name\":\"我喜欢的音乐\",\"count\":2}],\"list_count\":1,\"collect_count\":0}}");
            if(endpoint.endsWith("/v4/get_list_all_file"))return KugouLoginServiceTest.json(
                    "{\"status\":1,\"data\":{\"count\":2,\"info\":["
                            +"{\"hash\":\""+firstHash+"\",\"album_id\":41,\"mixsongid\":71,\"filename\":\"甲 - 第一首\",\"timelen\":120000,\"sort\":1},"
                            +"{\"hash\":\""+selectedHash+"\",\"album_id\":42,\"mixsongid\":73,\"filename\":\"乙 - 被点击的歌\",\"timelen\":177000,\"sort\":2}]}}");
            fail("unexpected Kugou endpoint: "+endpoint);return null;
        });
        var provider=new KugouProvider(accounts,api);
        var main=new ConcurrentLinkedQueue<Runnable>();
        var audio=new KugouPlaybackControllerTest.Audio();
        var resolved=new AtomicReference<KugouTrack>();
        try(provider;var playback=new KugouPlaybackController(audio,
                ()->java.util.concurrent.CompletableFuture.completedFuture(null),main::add,
                (song,quality)->{
                    resolved.set(provider.track(song));
                    return new KugouPlaybackResolver.Link("https://fs.kugou.com/selected.mp3",177,KugouQuality.STANDARD);
                },accounts::revision)){
            var source=new KugouPlaylistSource(provider.playlists(accounts.account(),()->true),provider,playback,()->true);
            var liked=source.liked(0);
            assertEquals(2,liked.items().size());
            assertEquals(selectedHash.toUpperCase(),liked.items().get(1).song().platformId());
            assertEquals("42",liked.items().get(1).albumId());
            assertEquals("73",liked.items().get(1).albumAudioId());

            source.play(liked.items(),1);
            pump(main,()->audio.plays==1);

            assertNotNull(resolved.get());
            assertEquals(selectedHash.toUpperCase(),resolved.get().song().platformId());
            assertEquals("42",resolved.get().albumId());
            assertEquals("73",resolved.get().albumAudioId());
            assertEquals(selectedHash.toUpperCase(),playback.view().song().platformId());
        }
    }
}
