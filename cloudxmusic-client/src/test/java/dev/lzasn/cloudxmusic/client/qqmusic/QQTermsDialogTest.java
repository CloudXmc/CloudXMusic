package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackControllerTest.pump;

class QQTermsDialogTest {
    @TempDir Path root;
    @Test void onlyBottomEnablesConsentAndPersistenceMustFinishBeforeLogin()throws Exception{
        var config=new QQMusicConfig(root.resolve("config.json"));var document=config.load();document.addProperty("custom","保留");config.save(document);
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger restores=new AtomicInteger(),starts=new AtomicInteger();
        try(var consent=new QQTermsConsent(config,main::add,restores::incrementAndGet)){
            pump(main,()->consent.view().loaded());assertEquals(0,restores.get());
            var dialog=new QQTermsDialog(consent);dialog.open(starts::incrementAndGet);assertTrue(dialog.maxScroll()>300);assertFalse(dialog.canAgree());
            dialog.click(QQTermsDialog.AGREE.cx(),QQTermsDialog.AGREE.cy());assertEquals(0,starts.get());assertFalse(consent.view().saving());
            assertTrue(dialog.scroll(10,10,-10000));assertEquals(0,dialog.scrollOffset());
            dialog.scroll(QQTermsDialog.TEXT.cx(),QQTermsDialog.TEXT.cy(),Double.NaN);assertEquals(0,dialog.scrollOffset());
            dialog.scroll(QQTermsDialog.TEXT.cx(),QQTermsDialog.TEXT.cy(),-1);assertFalse(dialog.canAgree());
            dialog.scroll(QQTermsDialog.TEXT.cx(),QQTermsDialog.TEXT.cy(),-10000);assertTrue(dialog.canAgree());assertEquals(dialog.maxScroll(),dialog.scrollOffset());
            dialog.scroll(QQTermsDialog.TEXT.cx(),QQTermsDialog.TEXT.cy(),1);assertFalse(dialog.canAgree());
            dialog.scroll(QQTermsDialog.TEXT.cx(),QQTermsDialog.TEXT.cy(),-10000);dialog.click(QQTermsDialog.AGREE.cx(),QQTermsDialog.AGREE.cy());
            assertTrue(consent.view().saving());assertEquals(0,starts.get());assertFalse(dialog.canAgree());
            dialog.click(QQTermsDialog.AGREE.cx(),QQTermsDialog.AGREE.cy());pump(main,()->!dialog.isOpen());
            assertEquals(1,starts.get());assertEquals(1,restores.get());assertTrue(config.load().get("agreedTerms").getAsBoolean());assertEquals("保留",config.load().get("custom").getAsString());
        }
        try(var consent=new QQTermsConsent(config,main::add,restores::incrementAndGet)){
            pump(main,()->consent.view().loaded());assertTrue(consent.view().agreed());assertEquals(2,restores.get());
        }
    }
    @Test void rejectOrCloseCancelsPendingLoginAndNoConsentIsWritten()throws Exception{
        var config=new QQMusicConfig(root.resolve("config.json"));Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger starts=new AtomicInteger();
        try(var consent=new QQTermsConsent(config,main::add,starts::incrementAndGet)){
            pump(main,()->consent.view().loaded());var dialog=new QQTermsDialog(consent);dialog.open(starts::incrementAndGet);
            assertTrue(dialog.click(2,2));assertTrue(dialog.isOpen());dialog.click(QQTermsDialog.REJECT.cx(),QQTermsDialog.REJECT.cy());
            assertFalse(dialog.isOpen());assertEquals(0,starts.get());assertFalse(config.load().has("agreedTerms"));
            dialog.open(starts::incrementAndGet);dialog.dismiss();assertFalse(dialog.isOpen());assertFalse(dialog.scroll(200,200,-1));assertEquals(0,starts.get());
        }
    }
    @Test void damagedConfigCannotBeOverwrittenAndFailedSaveCannotBeginLogin()throws Exception{
        Path file=root.resolve("config.json");Files.writeString(file,"{bad");Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger starts=new AtomicInteger();
        try(var consent=new QQTermsConsent(new QQMusicConfig(file),main::add,starts::incrementAndGet)){
            pump(main,()->consent.view().loaded());assertFalse(consent.view().agreed());assertFalse(consent.view().error().isBlank());
            var dialog=new QQTermsDialog(consent);dialog.open(starts::incrementAndGet);dialog.scroll(300,200,-10000);dialog.click(QQTermsDialog.AGREE.cx(),QQTermsDialog.AGREE.cy());
            pump(main,()->!consent.view().saving());assertFalse(consent.view().agreed());assertTrue(dialog.isOpen());assertEquals(0,starts.get());assertEquals("{bad",Files.readString(file));
        }
    }
    @Test void alreadyAcceptedStartupBypassesPopupButFirstLoginCannotBypassOrRequestQr()throws Exception{
        var config=new QQMusicConfig(root.resolve("config.json"));Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger requests=new AtomicInteger();
        try(var consent=new QQTermsConsent(config,main::add,()->{});
            var accounts=new QQAccountManager(new QQCredentialStore(root.resolve("account.json")),new QQSessionVerifier());
            var login=new QQLoginService(accounts,main::add,(r,c)->{requests.incrementAndGet();return QQLoginServiceTest.qrReply();},id->new QQLoginServiceTest.FakeChannel(),new QQSessionVerifier());
            var ui=new QQLoginUi(accounts,login,url->null,consent)){
            pump(main,()->consent.view().loaded());ui.checkTerms(true);assertTrue(ui.isOpen());ui.click(QQTermsDialog.AGREE.cx(),QQTermsDialog.AGREE.cy());
            assertEquals(0,requests.get());ui.click(QQTermsDialog.REJECT.cx(),QQTermsDialog.REJECT.cy());assertFalse(ui.isOpen());ui.checkTerms(true);assertFalse(ui.isOpen(),"拒绝后本次界面不反复强制弹出");
            ui.open();assertTrue(ui.isOpen());ui.scroll(300,200,-10000);ui.click(QQTermsDialog.AGREE.cx(),QQTermsDialog.AGREE.cy());
            pump(main,()->requests.get()==1);assertTrue(consent.view().agreed());
            ui.dismiss();ui.open();pump(main,()->requests.get()==2);assertTrue(ui.isOpen());
        }
    }
    @Test void queuedSuccessCannotStartLoginAfterDialogIsDismissed()throws Exception{
        Queue<Runnable> main=new ConcurrentLinkedQueue<>();AtomicInteger starts=new AtomicInteger();
        try(var consent=new QQTermsConsent(new QQMusicConfig(root.resolve("config.json")),main::add,()->{})){
            pump(main,()->consent.view().loaded());var dialog=new QQTermsDialog(consent);dialog.open(starts::incrementAndGet);dialog.scroll(300,200,-10000);dialog.click(QQTermsDialog.AGREE.cx(),QQTermsDialog.AGREE.cy());
            QQCatalogControllerTest.awaitCallback(main);dialog.dismiss();while(!main.isEmpty())main.remove().run();assertEquals(0,starts.get());assertFalse(dialog.isOpen());
        }
    }
    @Test void unicodeWrappingAndBothThemePreviewsAtTopAndBottom()throws Exception{
        var config=new QQMusicConfig(root.resolve("config.json"));Queue<Runnable> main=new ConcurrentLinkedQueue<>();Path out=Path.of("../outputs/qq-upgrade-2.13.1/previews");Files.createDirectories(out);
        try(var consent=new QQTermsConsent(config,main::add,()->{})){
            pump(main,()->consent.view().loaded());var dialog=new QQTermsDialog(consent);dialog.open(()->{});
            try(var c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){
                String text="日韩 한국어 𠮷野家 🎵".repeat(8);var lines=QQTermsDialog.wrap(c,text,80);assertEquals(text,String.join("",lines));
                for(String line:lines){assertFalse(Character.isHighSurrogate(line.charAt(line.length()-1)));assertTrue(c.width(line,13,false)<=80.01);}
            }
            for(boolean dark:new boolean[]{false,true}){
                FrostCanvas.setDark(dark);var base=MusicPainter.paint(QQLoginPainterTest.view(),new MusicLayout(MusicView.Page.SEARCH,0,0),1,u->null);
                dialog.scroll(300,200,10000);ImageIO.write(dialog.decorate(base,1),"png",out.resolve((dark?"dark":"light")+"-terms-top.png").toFile());
                dialog.scroll(300,200,-10000);ImageIO.write(dialog.decorate(base,1),"png",out.resolve((dark?"dark":"light")+"-terms-bottom.png").toFile());
            }
        }finally{FrostCanvas.setDark(false);}
    }
}
