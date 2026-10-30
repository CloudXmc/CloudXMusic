package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.state.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class QQLoginPainterTest {
    @TempDir Path directory;
    static MusicView view() {
        return new MusicView(MusicView.Page.SEARCH, new Track("原网易云曲目", "歌手", 200, "123", "专辑", ""),
                List.of(), List.of(), List.of(), 0, true, false, "", "周杰伦", false, false, "", "标准", "原文", true,
                PlaybackMode.values()[0], false, "test", "", 0, -1, .5f, "", FrostCanvas.dark, 1f,1f,.5f,.5f,"H");
    }
    @Test void entryDoesNotOverlapTabSearchAndRows() {
        assertTrue(QQLoginPainter.ENTRY.contains(100,474));
        assertFalse(QQLoginPainter.ENTRY.contains(972,174));
        assertFalse(QQLoginPainter.ENTRY.contains(918,46)); assertFalse(QQLoginPainter.ENTRY.contains(990,134));
        assertFalse(QQLoginPainter.ENTRY.contains(990,220));
    }
    @Test void rendersQrAndAccountStatusInBothThemes() throws Exception {
        Path out = Path.of("../outputs/qq-login-step1").toAbsolutePath().normalize(); Files.createDirectories(out);
        BitMatrix bits = new QRCodeWriter().encode("CloudXMusic offline preview - NOT a login QR", BarcodeFormat.QR_CODE,224,224);
        BufferedImage qr = new BufferedImage(224,224,BufferedImage.TYPE_INT_RGB);
        for (int y=0;y<224;y++) for (int x=0;x<224;x++) qr.setRGB(x,y,bits.get(x,y)?0xff000000:0xffffffff);
        for (boolean dark : new boolean[]{false,true}) {
            FrostCanvas.setDark(dark); MusicView view = view();
            BufferedImage base = MusicPainter.paint(view, new MusicLayout(MusicView.Page.SEARCH,0,0),1,key -> null);
            base = QQSearchPainter.decorate(base,1,view,new QQSearchController.View(QQSearchController.Source.QQ,List.of(),0,false,false,"可匿名搜索"));
            var account = new QQAccountManager.View(false,"","未登录 · 可匿名搜索",false);
            var waiting = new QQLoginService.View(QQLoginService.State.WAITING,qr,"请使用手机 QQ 音乐 App 扫码并确认（有效期约 3 分钟）");
            BufferedImage image = QQLoginPainter.decorate(base,1,true,account,waiting);
            assertEquals(base.getWidth(),image.getWidth()); assertEquals(0xffffffff,image.getRGB(620,275));
            ImageIO.write(image,"png",out.resolve(dark?"login-dark.png":"login-light.png").toFile());
            var saved = new QQAccountManager.View(true,"测试昵称","QQ 音乐已登录 · 凭证已保存",false);
            ImageIO.write(QQLoginPainter.decorate(base,1,true,saved,new QQLoginService.View(QQLoginService.State.SUCCESS,null,"QQ 音乐登录成功")),
                    "png",out.resolve(dark?"account-dark.png":"account-light.png").toFile());
            BufferedImage header = QQLoginPainter.decorate(base,1,false,saved,waiting);
            for (int y=100;y<700;y+=17) for (int x=100;x<350;x+=19) assertEquals(base.getRGB(x,y),header.getRGB(x,y),"关闭浮层时不改变网易云侧栏");
            ImageIO.write(header,"png",out.resolve(dark?"entry-dark.png":"entry-light.png").toFile());
        }
        FrostCanvas.setDark(false);
    }
    @Test void modalConsumesOutsideClicksAndDismissKeepsAccount() throws Exception {
        var store = new QQCredentialStore(directory.resolve("account.json"));
        try (var accounts = new QQAccountManager(store,new QQSessionVerifier());
             var login = new QQLoginService(accounts,Runnable::run,(r,c)->{fail("logged in entry must not create QR");return null;},
                     id -> {fail("must not connect");return null;},new QQSessionVerifier());
             var consent=new QQTermsConsent(new QQMusicConfig(directory.resolve("config.json")),Runnable::run,()->{})) {
            accounts.acceptVerified(new QQCredentials("123","key",2,"昵称",0));
            QQLoginUi ui = new QQLoginUi(accounts,login,url->null,consent); assertFalse(ui.click(100,100));
            assertTrue(ui.click(100,474)); assertTrue(ui.isOpen()); assertTrue(ui.click(10,10)); assertTrue(ui.isOpen());
            ui.dismiss(); assertFalse(ui.isOpen()); assertTrue(accounts.view().loggedIn());
            BufferedImage base = new BufferedImage(10,10,BufferedImage.TYPE_INT_ARGB);
            assertSame(base,ui.decorate(base,1,view(),false));
            ui.close(); assertTrue(accounts.view().loggedIn());
        }
    }
}
