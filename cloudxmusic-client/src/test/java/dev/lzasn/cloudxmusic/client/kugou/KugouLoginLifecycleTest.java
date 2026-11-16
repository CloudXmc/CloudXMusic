package dev.lzasn.cloudxmusic.client.kugou;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import dev.lzasn.cloudxmusic.client.gui.login.LoginMethod;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.kugou.KugouLoginServiceTest.json;

class KugouLoginLifecycleTest {
    @TempDir Path dir;
    static void await(java.util.function.BooleanSupplier done)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!done.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);assertTrue(done.getAsBoolean());}
    @Test void cookieLoginValidatesProfileAndNeverExchangesEvenWhenOld()throws Exception{
        var store=new KugouAccountStore(dir.resolve("kugou/account.json"));var exchanges=new AtomicInteger();
        KugouTransport transport=(url,p,h,b,c)->{if(url.contains("login_by_token"))exchanges.incrementAndGet();assertFalse(url.contains("login_by_token"));return json("{\"status\":1,\"data\":{\"userid\":123,\"nickname\":\"真实用户\",\"pic\":\"https://imge.kugou.com/avatar.png\",\"vip_type\":6}}");};
        try(var login=new KugouLoginController(store,Runnable::run,()->new KugouLoginService(store.account().deviceId(),new KugouApi(transport)),()->System.currentTimeMillis()+TimeUnit.DAYS.toMillis(2),s->{})){
            login.importCookie("userid=123; token=long-lived; kg_dfid=device");await(()->login.view().state()==KugouLoginController.State.SUCCESS);assertEquals("真实用户",store.account().nickname());assertEquals("VIP",store.account().profile().badge());assertTrue(store.account().cookieLogin());long revision=store.revision();login.maintain();assertEquals(revision,store.revision());assertEquals(0,exchanges.get());
        }
    }
    @Test void cookieSessionRestoresFromDiskAndStartupValidationDoesNotExchangeToken()throws Exception{
        Path file=dir.resolve("kugou/account.json");var initial=new KugouAccountStore(file);var parsed=KugouCookieLogin.parse("userid=123; token=persisted-cookie; kg_dfid=device",initial.account().deviceId());
        var saved=new KugouAccount(parsed.userId(),parsed.token(),parsed.deviceId(),"已保存用户",parsed.cookies(),LoginMethod.COOKIE,"",0,KugouProfile.empty());
        assertTrue(initial.commit(initial.revision(),saved));

        var restored=new KugouAccountStore(file);var validations=new AtomicInteger();var exchanges=new AtomicInteger();
        KugouTransport transport=(url,p,h,b,c)->{
            if(url.contains("login_by_token")){exchanges.incrementAndGet();return json("{\"status\":0,\"error_code\":20017}");}
            validations.incrementAndGet();assertEquals("persisted-cookie",p.get("token"));
            assertTrue(c.getCookieStore().getCookies().stream().anyMatch(cookie->cookie.getName().equals("token")&&cookie.getValue().equals("persisted-cookie")));
            return json("{\"status\":1,\"data\":{\"userid\":123,\"nickname\":\"恢复用户\"}}");
        };
        try(var login=new KugouLoginController(restored,Runnable::run,()->new KugouLoginService(restored.account().deviceId(),new KugouApi(transport)),System::currentTimeMillis,s->{})){
            login.maintain();
            assertTrue(restored.account().loggedIn());assertTrue(restored.account().cookieLogin());assertEquals("persisted-cookie",restored.account().token());
            assertEquals(2,validations.get(),"启动验证读取账号资料与会员资料");assertEquals(0,exchanges.get());
        }
    }
    @Test void qrRenewalIsDebouncedPreservesRefreshAndPublishesOnlyValidatedNewToken()throws Exception{
        var store=new KugouAccountStore(dir.resolve("renew/account.json"));var now=new AtomicLong(System.currentTimeMillis());
        store.commit(0,new KugouAccount("123","old-token",store.account().deviceId(),"用户",List.of(),LoginMethod.QR,"saved-refresh",now.get()-TimeUnit.HOURS.toMillis(13),KugouProfile.empty()));
        var exchanges=new AtomicInteger();KugouTransport transport=(url,p,h,b,c)->{
            if(url.contains("login_by_token")){exchanges.incrementAndGet();return json("{\"status\":1,\"data\":{\"userid\":123,\"token\":\"new-token\"}}");}
            assertEquals("new-token",p.get("token"));return json("{\"status\":1,\"data\":{\"userid\":123,\"nickname\":\"用户\"}}");
        };
        try(var login=new KugouLoginController(store,Runnable::run,()->new KugouLoginService(store.account().deviceId(),new KugouApi(transport)),now::get,s->{})){
            login.maintain();assertEquals("new-token",store.account().token());assertEquals("saved-refresh",store.account().refreshToken());assertEquals(1,exchanges.get());login.maintain();assertEquals(1,exchanges.get());
        }
    }
    @Test void cancellationCannotCommitValidatedCookieAndExpiredSessionRetainsDisk()throws Exception{
        var store=new KugouAccountStore(dir.resolve("cancel/account.json"));byte[] before=Files.readAllBytes(dir.resolve("cancel/account.json"));var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        KugouTransport blocked=(u,p,h,b,c)->{entered.countDown();try{release.await(2,TimeUnit.SECONDS);}catch(InterruptedException ignored){}return json("{\"status\":1,\"data\":{\"userid\":123,\"nickname\":\"用户\"}}");};
        try(var login=new KugouLoginController(store,Runnable::run,()->new KugouLoginService(store.account().deviceId(),new KugouApi(blocked)),System::currentTimeMillis,s->{})){
            login.importCookie("userid=123; token=late");assertTrue(entered.await(1,TimeUnit.SECONDS));login.cancel();release.countDown();login.logout();await(()->login.view().status().contains("退出"));assertFalse(store.account().loggedIn());assertArrayEquals(before,Files.readAllBytes(dir.resolve("cancel/account.json")));
        }finally{release.countDown();}
        store.commit(store.revision(),KugouCookieLogin.parse("userid=123; token=expired",store.account().deviceId()));var notices=new ArrayList<String>();byte[] saved=Files.readAllBytes(dir.resolve("cancel/account.json"));
        try(var login=new KugouLoginController(store,Runnable::run,()->new KugouLoginService(store.account().deviceId(),new KugouApi((u,p,h,b,c)->json("{\"status\":0,\"error_code\":20017}"))),System::currentTimeMillis,notices::add)){
            login.maintain();assertFalse(store.account().loggedIn());assertTrue(login.view().status().contains("重新粘贴 Cookie"));assertEquals(1,notices.size());assertArrayEquals(saved,Files.readAllBytes(dir.resolve("cancel/account.json")));login.maintain();assertEquals(1,notices.size());
        }
    }
}
