package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class KugouAccountStoreTest {
    @TempDir Path directory;
    @Test void accountPersistsIndependentlyLogoutRetainsDeviceAndStaleLoginCannotCommit() throws Exception {
        Path old=directory.resolve("qqmusic/account.json");Files.createDirectories(old.getParent());Files.writeString(old,"existing-session");
        var store=new KugouAccountStore(directory.resolve("kugou/account.json"));
        String device=store.account().deviceId();long revision=store.revision();
        assertTrue(store.commit(revision,new KugouAccount("123","secret",device,"昵称",List.of())));
        var reloaded=new KugouAccountStore(directory.resolve("kugou/account.json"));
        assertEquals("secret",reloaded.account().token());assertFalse(reloaded.account().toString().contains("secret"));
        store.logout();assertFalse(store.commit(revision,new KugouAccount("123","stale",device,"",List.of())));
        assertFalse(store.account().loggedIn());assertEquals(device,store.account().deviceId());
        assertEquals("existing-session",Files.readString(old));
        assertEquals(device,new KugouAccountStore(directory.resolve("kugou/account.json")).account().deviceId());
    }
    @Test void metadataUsesCanonicalHashAndAlbumIdAndVipFlag() {
        var track=KugouPlaybackResolverTest.track();
        assertEquals(KugouPlaybackResolverTest.HASH,track.song().platformId());assertEquals("123",track.albumId());
        assertEquals("kugou",track.song().source());
    }
}
