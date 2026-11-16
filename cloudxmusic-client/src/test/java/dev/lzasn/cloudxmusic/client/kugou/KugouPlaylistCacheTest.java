package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouPlaylistCacheTest {
    @TempDir Path temp;
    @Test void fullPlaylistMetadataSurvivesSearchLruAndClearsOnAccountChange()throws Exception{
        var store=new KugouAccountStore(temp.resolve("account.json"));
        try(var provider=new KugouProvider(store,new KugouApi((u,p,h,b,c)->{fail("只测缓存不能联网");return null;}))){
            var songs=new ArrayList<KugouTrack>();for(int i=0;i<1200;i++){String hash=String.format("%032x",i);songs.add(new KugouTrack(new UnifiedSong("kugou",hash,"歌曲","歌手","专辑"),"42","73",180,""));}
            provider.rememberPlaylist(songs);for(var t:songs)provider.remember(t);
            assertEquals("42",provider.track(songs.getFirst().song()).albumId());assertEquals("73",provider.track(songs.getFirst().song()).albumAudioId());
            store.logout();assertEquals("",provider.track(songs.getFirst().song()).albumId());
            assertThrows(IllegalArgumentException.class,()->provider.rememberPlaylist(Collections.nCopies(20001,songs.getFirst())));
        }
    }
    @Test void realCloudAdapterSignsRequestsWithIsolatedCredentials()throws Exception{
        var store=new KugouAccountStore(temp.resolve("account.json"));var original=store.account();
        var account=new KugouAccount("123","test-token",original.deviceId(),"用户",List.of());store.commit(store.revision(),account);
        try(var provider=new KugouProvider(store,new KugouApi((url,params,headers,body,cookies)->{
            assertTrue(url.endsWith("/v7/get_all_list"));assertEquals("test-token",params.get("token"));assertEquals("1014",params.get("appid"));assertEquals("cloudlist.service.kugou.com",headers.get("x-router"));assertNotNull(params.get("signature"));
            var json=KugouPlaylistApiTest.json(body);assertEquals("test-token",json.get("token").getAsString());
            return KugouPlaylistApiTest.json("{\"status\":1,\"data\":{\"info\":[],\"list_count\":0,\"collect_count\":0}}");
        }))){assertTrue(provider.playlists(account,()->true).playlists(dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.Section.OWNED,0).items().isEmpty());}
    }
    @Test void completePlaylistMetadataOverridesSparseSearchCache()throws Exception{
        var store=new KugouAccountStore(temp.resolve("account.json"));
        try(var provider=new KugouProvider(store,new KugouApi((u,p,h,b,c)->{fail("缓存不应联网");return null;}))){
            var song=new UnifiedSong("kugou","a".repeat(32),"歌曲","歌手","专辑");
            provider.remember(new KugouTrack(song,"","",0,""));
            provider.rememberPlaylist(List.of(new KugouTrack(song,"42","73",180,"")));
            assertEquals("42",provider.track(song).albumId());assertEquals("73",provider.track(song).albumAudioId());
        }
    }
}
