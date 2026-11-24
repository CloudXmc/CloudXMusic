package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.reply;
import static org.junit.jupiter.api.Assertions.*;

class QQAccountManagerTest {
    @TempDir Path directory;
    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(), "后台任务应在时限内完成");
    }
    private QQCredentials account(String key) { return new QQCredentials("123456", key, 2, "昵称", 0); }
    @Test void anOldMembershipResponseCannotOverwriteTheNextAccount()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        var oldProfile=new QQAccountProfile("https://q.qlogo.cn/old",QQAccountProfile.Membership.SVIP,6);
        var nextProfile=new QQAccountProfile("https://q.qlogo.cn/new",QQAccountProfile.Membership.VIP,2);
        try(var manager=new QQAccountManager(new QQCredentialStore(directory.resolve("switch-profile.json")),new QQSessionVerifier(),(account,active)->{
            if(account.musicKey().equals("old-key")){entered.countDown();release.await(2,TimeUnit.SECONDS);return oldProfile;}
            return nextProfile;
        })){
            manager.acceptVerified(account("old-key"));assertTrue(entered.await(2,TimeUnit.SECONDS));
            manager.acceptVerified(new QQCredentials("654321","new-key",2,"新账号",0));release.countDown();
            await(()->manager.view().profile().equals(nextProfile));
            assertEquals("新账号",manager.view().nickname());assertEquals("654321",manager.snapshot().credentials().userId());
            assertEquals("VIP2",manager.view().profile().badge());
        }finally{release.countDown();}
    }
    @Test void profileUpdateDoesNotInvalidatePlaybackAndLateProfileCannotUndoLogout()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        var profile=new QQAccountProfile("https://q.qlogo.cn/public",QQAccountProfile.Membership.SVIP,6);
        try(var manager=new QQAccountManager(new QQCredentialStore(directory.resolve("profile-account.json")),new QQSessionVerifier(),(account,active)->{
            entered.countDown();release.await(2,TimeUnit.SECONDS);return profile;
        })){
            manager.acceptVerified(account("key"));var snapshot=manager.snapshot();assertTrue(entered.await(2,TimeUnit.SECONDS));
            assertTrue(manager.isCurrent(snapshot));manager.logout();release.countDown();await(()->manager.view().status().contains("已退出"));
            assertFalse(manager.view().loggedIn());assertEquals(QQAccountProfile.EMPTY,manager.view().profile());
        }finally{release.countDown();}
        try(var manager=new QQAccountManager(new QQCredentialStore(directory.resolve("profile-good.json")),new QQSessionVerifier(),(account,active)->profile)){
            manager.acceptVerified(account("key"));var snapshot=manager.snapshot();await(()->manager.view().profile().level()==6);
            assertTrue(manager.isCurrent(snapshot));assertEquals("SVIP6",manager.view().profile().badge());
        }
    }
    @Test void membershipFailureDoesNotLogOutOrHideSaveErrors()throws Exception{
        try(var manager=new QQAccountManager(new QQCredentialStore(directory.resolve("profile-fail.json")),new QQSessionVerifier(),(account,active)->{throw new IOException("unavailable");})){
            manager.acceptVerified(account("key"));await(()->manager.view().status().contains("会员信息暂不可用"));
            assertTrue(manager.view().loggedIn());assertTrue(manager.view().status().contains("凭证已保存"));assertEquals("",manager.view().profile().badge());
        }
    }
    @Test void playbackSnapshotsAreReadOnlyRedactedAndInvalidatedBySwitchAndLogout() throws Exception {
        try(var manager=new QQAccountManager(new QQCredentialStore(directory.resolve("account.json")),new QQSessionVerifier())) {
            assertThrows(IOException.class,manager::snapshot);
            manager.acceptVerified(account("first-key"));var first=manager.snapshot();assertTrue(manager.isCurrent(first));
            assertFalse(first.toString().contains("first-key"));
            manager.acceptVerified(account("second-key"));assertFalse(manager.isCurrent(first));var second=manager.snapshot();
            assertTrue(manager.isCurrent(second));manager.logout();assertFalse(manager.isCurrent(second));assertThrows(IOException.class,manager::snapshot);
        }
    }
    @Test void missingAccountRestoresWithoutNetworkOrFileCreation() throws Exception {
        Path file = directory.resolve("qqmusic/account.json");
        try (var manager = new QQAccountManager(new QQCredentialStore(file), new QQSessionVerifier((r,c) -> fail("must not request")))) {
            manager.restore(); await(() -> manager.view().status().contains("匿名")); assertFalse(Files.exists(file));
        }
    }
    @Test void validAccountIsVerifiedOnRestoreAndNeverSentToOtherJar() throws Exception {
        Path file = directory.resolve("account.json"); QQCredentialStore store = new QQCredentialStore(file); store.save(account("key"));
        try (var manager = new QQAccountManager(store, new QQSessionVerifier((r,c) -> reply(0, "{\"musicid\":123456,\"nick\":\"恢复昵称\"}")))) {
            manager.restore(); await(() -> manager.view().loggedIn()); assertEquals("恢复昵称", manager.view().nickname());
        }
    }
    @Test void expiredCredentialFileRemainsUntouched() throws Exception {
        Path file = directory.resolve("account.json"); QQCredentialStore store = new QQCredentialStore(file);
        store.save(new QQCredentials("123456", "key", 2, "", 1)); byte[] before = Files.readAllBytes(file);
        try (var manager = new QQAccountManager(store, new QQSessionVerifier((r,c) -> fail("expired account must not request")))) {
            manager.restore(); await(() -> manager.view().status().contains("匿名")); assertFalse(manager.view().loggedIn());
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }
    @Test void rejectedAndOfflineRestorePreserveFileAndShowError() throws Exception {
        Path file = directory.resolve("account.json"); QQCredentialStore store = new QQCredentialStore(file); store.save(account("key"));
        byte[] before = Files.readAllBytes(file);
        for (boolean network : new boolean[]{false,true}) {
            try (var manager = new QQAccountManager(store, new QQSessionVerifier((r,c) -> {
                if (network) throw new IOException("offline"); return reply(104401, "{}");
            }))) {
                manager.restore(); await(() -> manager.view().error()); assertFalse(manager.view().loggedIn());
                assertArrayEquals(before, Files.readAllBytes(file));
            }
        }
    }
    @Test void acceptingNewAccountDiscardsLateRestoreResult() throws Exception {
        Path file = directory.resolve("account.json"); QQCredentialStore store = new QQCredentialStore(file); store.save(account("old-key"));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var manager = new QQAccountManager(store, new QQSessionVerifier((r,c) -> {
            entered.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); return reply(0, "{\"musicid\":123456,\"nick\":\"旧账号\"}");
        }))) {
            manager.restore(); assertTrue(entered.await(3, TimeUnit.SECONDS));
            manager.acceptVerified(account("new-key")); release.countDown();
            await(() -> manager.view().status().contains("凭证已保存"));
            assertEquals("new-key", store.load().orElseThrow().musicKey()); assertEquals("昵称", manager.view().nickname());
        } finally { release.countDown(); }
    }
    @Test void logoutClearsOnlyQqFile() throws Exception {
        Path file = directory.resolve("qqmusic/account.json"); QQCredentialStore store = new QQCredentialStore(file);
        Path netease = directory.resolve("cookies.json"); Files.writeString(netease, "untouched");
        try (var manager = new QQAccountManager(store, new QQSessionVerifier())) {
            manager.acceptVerified(account("key")); await(() -> manager.view().status().contains("凭证已保存"));
            manager.logout(); await(() -> manager.view().status().contains("已退出"));
            assertFalse(manager.view().loggedIn()); assertFalse(Files.exists(file)); assertEquals("untouched", Files.readString(netease));
        }
    }
    @Test void saveFailureRetainsMemorySessionAndShowsHonestStatus() throws Exception {
        Path blocked = directory.resolve("blocked"); Files.writeString(blocked, "file");
        try (var manager = new QQAccountManager(new QQCredentialStore(blocked.resolve("account.json")), new QQSessionVerifier())) {
            manager.acceptVerified(account("key")); await(() -> manager.view().error());
            assertTrue(manager.view().loggedIn()); assertTrue(manager.view().status().contains("保存失败"));
        }
    }
    @Test void closeIsIdempotentAndDoesNotDeletePersistedLogin() throws Exception {
        Path file = directory.resolve("account.json"); var manager = new QQAccountManager(new QQCredentialStore(file), new QQSessionVerifier());
        manager.acceptVerified(account("key")); await(() -> manager.view().status().contains("凭证已保存"));
        manager.close(); manager.close(); manager.restore(); manager.acceptVerified(account("different"));
        assertFalse(manager.view().loggedIn()); assertEquals("key", new QQCredentialStore(file).load().orElseThrow().musicKey());
    }
}
