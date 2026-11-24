package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.util.function.Consumer;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** 独立酷狗扫码浮层，仅提交动作给本地登录控制器。 */
public final class KugouLoginUi {
    public static final Box ENTRY=new Box(32,442,210,64), CLOSE=new Box(951,102,28,28), REFRESH=new Box(416,528,132,34), LOGOUT=new Box(558,528,132,34), DONE=new Box(700,528,132,34);
    private boolean open;
    private final dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog cookie=new dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog();
    public long revision(){return cookie.revision();}
    public boolean isOpen(){return open;}
    public void open(){open=true;if(!KugouIntegration.module().account().loggedIn()&&!KugouIntegration.login().view().state().equals(KugouLoginController.State.WAITING))KugouIntegration.login().start();}
    public void dismiss(){cookie.close();if(open)KugouIntegration.login().cancel();open=false;}
    public void click(double x,double y){
        if(cookie.isOpen()){
            if(dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog.CLOSE.contains(x,y)){dismiss();return;}
            if(dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog.BACK.contains(x,y)){cookie.close();KugouIntegration.login().start();return;}
            cookie.click(x,y,busy(),KugouIntegration.login()::importCookie);return;
        }
        if(dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog.ENTRY.contains(x,y)){KugouIntegration.login().cancel();cookie.open();return;}
        if(CLOSE.contains(x,y)||DONE.contains(x,y)){dismiss();return;}
        if(REFRESH.contains(x,y)){KugouIntegration.login().start();return;}
        if(LOGOUT.contains(x,y)){KugouIntegration.login().logout();return;}
        if(x<295||x>995||y<86||y>598)dismiss();
    }
    private boolean busy(){return KugouIntegration.login().view().state()==KugouLoginController.State.LOADING;}
    public void key(net.minecraft.client.input.KeyEvent event,String pasted){if(cookie.isOpen())cookie.key(event,pasted,busy(),KugouIntegration.login()::importCookie);}
    public void text(String text){if(cookie.isOpen())cookie.append(text);}
    public boolean changed(){return open;}
    public Consumer<FrostCanvas> sidebar(MusicView view,boolean active){
        if(!active)return null;
        return c->{
            if(view.hover().equals("account"))c.round(ENTRY.x(),ENTRY.y(),ENTRY.w(),ENTRY.h(),19,CARD_BG);
            var account=KugouIntegration.module().account();var profile=KugouIntegration.module().details().profile();var avatar=account.loggedIn()?KugouIntegration.artwork().image(profile.avatarUrl()):null;
            c.round(34,461,36,36,18,SOFT);if(avatar==null)UiIcons.draw(c,"user",52,479,20,DEEP);else c.roundImage(avatar,34,461,36,18);
            String name=account.loggedIn()?(!profile.nickname().isBlank()?profile.nickname():account.nickname().isBlank()?"酷狗用户":account.nickname()):"登录酷狗音乐";
            String badge=account.loggedIn()?profile.badge():"";float nameWidth=151-(badge.isEmpty()?0:KugouBadges.width(c,badge)+5);String fitted=c.fit(name,14,true,nameWidth);
            c.text(fitted,82,474,14,true,INK,nameWidth);KugouBadges.draw(c,badge,82+c.width(fitted,14,true)+5,462,16);
            SidebarTextLayout.draw(c,account.loggedIn()?"酷狗音乐 · 切换音源 / 管理":"酷狗音乐 · 切换 / 登录",82,495,11,false,MUTED,151);
        };
    }
    public BufferedImage decorate(BufferedImage base,double scale){
        if(!open)return base;
        var login=KugouIntegration.login().view();var account=KugouIntegration.module().account();
        if(cookie.isOpen())return cookie.decorate(base,scale,"酷狗音乐",login.status(),login.state()==KugouLoginController.State.ERROR||login.state()==KugouLoginController.State.EXPIRED,busy());
        BufferedImage image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)){
            var transform=c.g.getTransform();c.g.setTransform(new AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(transform);c.g.translate(PAD,PAD);
            c.round(0,0,WIDTH,HEIGHT,38,new Color(0,0,0,115));c.round(295,86,700,512,24,dark?new Color(38,46,51):new Color(255,248,252));
            c.text("酷狗音乐扫码登录",325,127,20,true,INK,560);c.center("×",CLOSE.cx(),CLOSE.cy()+5,21,false,MUTED,28);
            button(c,dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog.ENTRY,"Cookie 登录");
            c.center(account.loggedIn()?"当前账号："+(account.nickname().isBlank()?"酷狗用户":account.nickname()):"请使用酷狗音乐 App 扫码",645,160,14,true,INK,340);
            c.round(521,174,248,248,12,Color.WHITE);
            BufferedImage qr=null;if(login.png().length>0)try{qr=ImageIO.read(new ByteArrayInputStream(login.png()));}catch(Exception ignored){}
            if(qr!=null){c.g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);c.g.drawImage(qr,533,186,224,224,null);}
            else c.center(switch(login.state()){case LOADING->"正在连接…";case SUCCESS->"登录成功";case EXPIRED->"二维码已过期";case ERROR->"请刷新二维码";case SCANNED->"已扫码，请在手机确认";default->account.loggedIn()?"酷狗音乐已登录":"点击刷新获取二维码";},645,304,15,true,INK,225);
            c.center(login.status(),645,447,13,true,login.state()==KugouLoginController.State.ERROR?new Color(0xB94F6A):INK,640);
            c.center("酷狗 token 与用户信息仅保存于本机 kugou 账号文件",645,473,11,false,MUTED,640);
            button(c,REFRESH,account.loggedIn()?"切换账号 / 刷新":"刷新二维码");if(account.loggedIn())button(c,LOGOUT,"退出登录");button(c,DONE,"关闭");
        }
        return image;
    }
    private static void button(FrostCanvas c,Box b,String label){c.round(b.x(),b.y(),b.w(),b.h(),12,SOFT);c.center(label,b.cx(),b.cy()+4,12,true,DEEP,b.w()-12);}
}
