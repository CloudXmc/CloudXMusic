package dev.lzasn.cloudxmusic.client.qqmusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import dev.lzasn.cloudxmusic.client.gui.login.LoginMethod;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQAccountManagerTest.await;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQSessionVerifierTest.reply;

class QQLoginLifecycleTest {
    @TempDir Path dir;
    private static QQCredentials qr(long expires,long issued){return new QQCredentials("123456","old-key",2,"用户",expires,QQAccountProfile.EMPTY,LoginMethod.QR,new QQRenewal("refresh-key","refresh-token","open","union","123456",issued));}
    @Test void restoringExpiredQrPersistsRotatedKeyAndDoesNotRepeatRejectedRenewal()throws Exception{
        var store=new QQCredentialStore(dir.resolve("restore.json"));store.save(qr(1,1));var renewals=new AtomicInteger();
        try(var manager=new QQAccountManager(store,new QQSessionVerifier((r,c)->{
            if(r.getAsJsonObject("req_0").get("method").getAsString().equals("Login")){renewals.incrementAndGet();return reply(0,"{\"musicid\":123456,\"musickey\":\"restored-key\",\"refresh_key\":\"rotated\"}");}
            return reply(0,"{\"musicid\":123456}");
        }))){manager.restore();await(()->manager.view().loggedIn());assertEquals("restored-key",store.load().orElseThrow().musicKey());assertEquals("rotated",store.load().orElseThrow().renewal().refreshKey());assertEquals(1,renewals.get());}
        store.save(qr(1,1));renewals.set(0);
        try(var manager=new QQAccountManager(store,new QQSessionVerifier((r,c)->{renewals.incrementAndGet();return reply(104401,"{}");}))){manager.restore();await(()->manager.view().error());assertEquals(1,renewals.get());}
    }
    @Test void qrMaintenanceRenewsAtMostOncePerTwelveHours()throws Exception{
        var now=new AtomicLong(System.currentTimeMillis());var renewals=new AtomicInteger();var store=new QQCredentialStore(dir.resolve("timer.json"));
        try(var manager=new QQAccountManager(store,new QQSessionVerifier((r,c)->{
            if(r.getAsJsonObject("req_0").get("method").getAsString().equals("Login")){renewals.incrementAndGet();return reply(0,"{\"musicid\":123456,\"musickey\":\"next-key\"}");}
            return reply(0,"{\"musicid\":123456}");
        }))){manager.clock(now::get);manager.acceptVerified(qr(0,now.get()));await(()->manager.view().status().contains("凭证已保存"));manager.maintain();await(()->manager.view().status().contains("已验证"));assertEquals(0,renewals.get());now.addAndGet(TimeUnit.HOURS.toMillis(13));manager.maintain();await(()->manager.view().status().contains("续期"));assertEquals(1,renewals.get());manager.maintain();await(()->manager.view().status().contains("已验证"));assertEquals(1,renewals.get());}
    }
    @Test void importedCookieIsValidatedSavedAndNeverSentToRenewalEvenAfterTwelveHours()throws Exception{
        var now=new AtomicLong(System.currentTimeMillis());var requests=new AtomicInteger();var store=new QQCredentialStore(dir.resolve("account.json"));
        try(var manager=new QQAccountManager(store,new QQSessionVerifier((r,c)->{requests.incrementAndGet();assertEquals("GetLoginUserInfo",r.getAsJsonObject("req_0").get("method").getAsString());return reply(0,"{\"musicid\":123456,\"nickname\":\"网页用户\"}");}))){
            manager.clock(now::get);manager.importCookie("uin=123456; qm_keyst=web-key");await(()->manager.view().status().contains("已验证并保存"));assertTrue(manager.view().loggedIn());assertEquals("网页用户",manager.view().nickname());assertTrue(store.load().orElseThrow().cookieLogin());
            now.addAndGet(TimeUnit.HOURS.toMillis(13));manager.maintain();await(()->requests.get()==2);assertEquals("web-key",manager.snapshot().credentials().musicKey());
        }
    }
    @Test void cookieLoginMethodAndSessionSurviveFreshManagerRestore()throws Exception{
        var store=new QQCredentialStore(dir.resolve("cookie-restore.json"));
        var cookie=new QQCredentials("123456","web-key",2,"网页用户",0,QQAccountProfile.EMPTY,LoginMethod.COOKIE,QQRenewal.empty());
        store.save(cookie);
        try(var manager=new QQAccountManager(store,new QQSessionVerifier((request,cookies)->{
            assertNotNull(cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals("qm_keyst")).findFirst().orElse(null));
            return reply(0,"{\"musicid\":123456,\"nickname\":\"网页用户\"}");
        }))){
            manager.restore();await(()->manager.view().loggedIn());
            assertTrue(manager.snapshot().credentials().cookieLogin());
            assertEquals("网页用户",manager.view().nickname());
            assertTrue(store.load().orElseThrow().cookieLogin());
        }
    }
    @Test void renewalUsesActualFieldsAndRetainsMissingRotationFields()throws Exception{
        var calls=new AtomicInteger();var verifier=new QQSessionVerifier((r,c)->{
            if(calls.incrementAndGet()==1){var p=r.getAsJsonObject("req_0").getAsJsonObject("param");assertEquals(2,p.get("loginMode").getAsInt());assertEquals("refresh-key",p.get("refresh_key").getAsString());assertEquals("refresh-token",p.get("refresh_token").getAsString());return reply(0,"{\"musicid\":123456,\"musickey\":\"fresh-key\",\"refresh_key\":\"next-refresh\"}");}
            assertEquals("fresh-key",r.getAsJsonObject("comm").get("authst").getAsString());return reply(0,"{\"musicid\":123456}");
        });
        var old=new QQCredentials("123456","old-key",2,"用户",0,QQAccountProfile.EMPTY,LoginMethod.QR,new QQRenewal("refresh-key","refresh-token","open","union","123456",1));
        var next=verifier.renew(old);assertEquals("fresh-key",next.musicKey());assertEquals("next-refresh",next.renewal().refreshKey());assertEquals("refresh-token",next.renewal().refreshToken());
        var store=new QQCredentialStore(dir.resolve("renew.json"));store.save(next);assertEquals(next.renewal(),store.load().orElseThrow().renewal());
    }
    @Test void cancelledCookieValidationCannotReplaceOrSaveExistingSession()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var store=new QQCredentialStore(dir.resolve("cancel.json"));
        try(var manager=new QQAccountManager(store,new QQSessionVerifier((r,c)->{entered.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){}return reply(0,"{\"musicid\":123456}");}))){
            manager.acceptVerified(new QQCredentials("123456","original",2,"原用户",0));await(()->manager.view().status().contains("凭证已保存"));
            manager.importCookie("uin=123456; qm_keyst=late-key");assertTrue(entered.await(1,TimeUnit.SECONDS));manager.cancelCookieImport();assertTrue(manager.view().loggedIn());assertFalse(manager.view().status().contains("正在验证"));release.countDown();manager.logout();await(()->manager.view().status().contains("已退出"));assertFalse(Files.exists(dir.resolve("cancel.json")));
        }finally{release.countDown();}
    }
    @Test void onlyExplicitExpiredSessionLogsOutAndNotifiesOnce()throws Exception{
        var expired=new AtomicBoolean();var notices=new CopyOnWriteArrayList<String>();var store=new QQCredentialStore(dir.resolve("expire.json"));
        try(var manager=new QQAccountManager(store,new QQSessionVerifier((r,c)->{if(!expired.get())throw new java.io.IOException("offline");return reply(104401,"{}");}))){
            manager.notices(notices::add);manager.acceptVerified(new QQCredentials("123456","key",2,"用户",0));await(()->manager.view().status().contains("凭证已保存"));manager.maintain();await(()->manager.view().status().contains("暂时无法"));assertTrue(manager.view().loggedIn());
            expired.set(true);manager.maintain();await(()->!manager.view().loggedIn()&&notices.size()==1);assertTrue(manager.view().status().contains("重新粘贴 Cookie"));assertTrue(Files.exists(dir.resolve("expire.json")));manager.maintain();assertEquals(1,notices.size());
        }
    }
}
