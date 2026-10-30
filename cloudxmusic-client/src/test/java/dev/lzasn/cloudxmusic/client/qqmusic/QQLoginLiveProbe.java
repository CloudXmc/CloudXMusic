package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** 手动执行的公网握手探针：不扫描、不载入已有账号、不记录 QR ID/票据/图像。 */
public final class QQLoginLiveProbe {
    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]); Path temporary = Files.createTempDirectory("cloudx-qq-probe-");
        long start = System.nanoTime();
        try (var accounts = new QQAccountManager(new QQCredentialStore(temporary.resolve("account.json")),new QQSessionVerifier());
             var login = new QQLoginService(accounts,Runnable::run)) {
            login.start();
            long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(50);
            while (login.view().state()==QQLoginService.State.LOADING && System.nanoTime()<deadline) Thread.sleep(50);
            QQLoginService.View view = login.view();
            JsonObject result = new JsonObject(); result.addProperty("state",view.state().name());
            result.addProperty("qrWidth",view.qr()==null?0:view.qr().getWidth());
            result.addProperty("durationMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
            result.addProperty("accountLoaded",false); result.addProperty("phoneScanned",false);
            Files.writeString(report,result.toString());
            System.out.println(result);
            if (view.state()!=QQLoginService.State.WAITING) throw new IllegalStateException("公网二维码申请/通道订阅未成功；无账号被修改");
        } finally { Files.deleteIfExists(temporary.resolve("account.json")); Files.deleteIfExists(temporary); }
    }
}
