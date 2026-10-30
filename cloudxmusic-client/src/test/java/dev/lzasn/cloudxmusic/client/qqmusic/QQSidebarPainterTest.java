package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import javax.imageio.ImageIO;
import static dev.lzasn.cloudxmusic.client.qqmusic.QQLoginPainterTest.view;
import static org.junit.jupiter.api.Assertions.*;

class QQSidebarPainterTest {
    @Test void originalNeteaseBranchIsPixelIdenticalAndQqOnlyChangesAccountArea() throws Exception {
        Path out=Path.of("../outputs/qq-playback-step2").toAbsolutePath().normalize();Files.createDirectories(out);
        for(boolean dark:new boolean[]{false,true}) {
            FrostCanvas.setDark(dark);MusicView view=view();MusicLayout layout=new MusicLayout(view.page(),0,0);
            BufferedImage original=MusicPainter.paint(view,layout,1,key->null);
            BufferedImage explicit=MusicPainter.paint(view,layout,1,key->null,null);
            assertArrayEquals(pixels(original),pixels(explicit));
            var account=new QQAccountManager.View(true,"测试QQ昵称","QQ 音乐已登录 · 凭证已保存",false);
            BufferedImage qq=MusicPainter.paint(view,layout,1,key->null,c->QQLoginPainter.sidebar(c,account,false));
            int differences=0;
            for(int y=0;y<qq.getHeight();y++) for(int x=0;x<qq.getWidth();x++) {
                if(original.getRGB(x,y)!=qq.getRGB(x,y)) {
                    differences++;assertTrue(QQLoginPainter.ENTRY.contains(x-MusicLayout.PAD,y-MusicLayout.PAD),"只允许替换账号块");
                }
            }
            assertTrue(differences>0);
            qq=QQSearchPainter.decorate(qq,1,view,new QQSearchController.View(QQSearchController.Source.QQ,List.of(),0,false,false,"匿名搜索仍可使用 · Ctrl+Shift+L 测试首条可见歌曲取链"));
            assertSame(qq,QQLoginPainter.decorate(qq,1,false,account,new QQLoginService.View(QQLoginService.State.IDLE,null,"")),"关闭浮层时右侧不再绘制登录按钮");
            ImageIO.write(qq,"png",out.resolve(dark?"qq-sidebar-dark.png":"qq-sidebar-light.png").toFile());
            ImageIO.write(original,"png",out.resolve(dark?"netease-sidebar-dark.png":"netease-sidebar-light.png").toFile());
            var guest=new QQAccountManager.View(false,"","未登录 · 可匿名搜索",false);
            BufferedImage loggedOut=MusicPainter.paint(view,layout,1,key->null,c->QQLoginPainter.sidebar(c,guest,false));
            loggedOut=QQSearchPainter.decorate(loggedOut,1,view,new QQSearchController.View(QQSearchController.Source.QQ,List.of(),0,false,false,"可匿名搜索"));
            ImageIO.write(loggedOut,"png",out.resolve(dark?"qq-guest-dark.png":"qq-guest-light.png").toFile());
            BufferedImage failed=QQSearchPainter.decorate(qq,1,view,new QQSearchController.View(QQSearchController.Source.QQ,List.of(),0,false,false,""),
                    new QQPlaybackProbeController.View(false,true,"QQ 取链失败：测试错误提示（原账号状态保持）"));
            ImageIO.write(failed,"png",out.resolve(dark?"link-error-dark.png":"link-error-light.png").toFile());
        }
        FrostCanvas.setDark(false);
    }
    private static int[] pixels(BufferedImage image) { return image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth()); }
}
