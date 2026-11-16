package dev.lzasn.cloudxmusic.client.gui.login;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class CookieLoginDialogTest {
    @Test void submittedSecretsAreClearedAndPendingSubmitCannotRepeat(){
        var dialog=new CookieLoginDialog();var values=new ArrayList<String>();dialog.open();dialog.append("uin=123; qm_keyst=secret");
        dialog.click(CookieLoginDialog.SUBMIT.cx(),CookieLoginDialog.SUBMIT.cy(),true,values::add);assertTrue(values.isEmpty());
        dialog.click(CookieLoginDialog.SUBMIT.cx(),CookieLoginDialog.SUBMIT.cy(),false,values::add);assertEquals(List.of("uin=123; qm_keyst=secret"),values);
        dialog.click(CookieLoginDialog.SUBMIT.cx(),CookieLoginDialog.SUBMIT.cy(),false,values::add);assertEquals(1,values.size());dialog.close();assertFalse(dialog.toString().contains("secret"));
    }
    @Test void cookieFormMasksSecretsAndRendersBothThemesAndProviders()throws Exception{
        var out=Path.of("../outputs/login-cookie").toAbsolutePath().normalize();Files.createDirectories(out);
        try{
            for(boolean dark:List.of(false,true))for(String source:List.of("QQ 音乐","酷狗音乐")){
                FrostCanvas.setDark(dark);var base=new BufferedImage(MusicLayout.WIDTH+MusicLayout.PAD*2,MusicLayout.HEIGHT+MusicLayout.PAD*2,BufferedImage.TYPE_INT_ARGB);
                var first=new CookieLoginDialog();first.open();first.append("uin=123; qm_keyst=secret-a");first.click(500,480,false,s->{});
                var second=new CookieLoginDialog();second.open();second.append("uin=123; qm_keyst=secret-b");second.click(500,480,false,s->{});
                var image=first.decorate(base,1,source,"请粘贴完整 Cookie 后验证登录",false,false);var other=second.decorate(base,1,source,"请粘贴完整 Cookie 后验证登录",false,false);
                for(int y=0;y<image.getHeight();y+=2)for(int x=0;x<image.getWidth();x+=2)assertEquals(image.getRGB(x,y),other.getRGB(x,y),"Cookie 内容应遮蔽");
                ImageIO.write(image,"png",out.resolve((source.startsWith("QQ")?"qq":"kugou")+(dark?"-dark":"-light")+".png").toFile());
            }
        }finally{FrostCanvas.setDark(false);}
    }
}
