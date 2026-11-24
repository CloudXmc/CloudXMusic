package dev.lzasn.cloudxmusic.client.gui.login;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;


/** 模态 Cookie 表单；键盘事件由 Screen 转发，关闭与提交均释放输入引用。 */
public final class CookieLoginDialog {
    public static final String HINT="请登录网页版，按 F12 在 Network 中复制完整 Cookie 粘贴至此。";
    public static final Box ENTRY=new Box(325,144,130,30),BACK=new Box(325,150,140,30),FIELD=new Box(345,234,600,52),SUBMIT=new Box(345,350,600,52),CLOSE=new Box(951,102,28,28);
    private String value="",error="";private boolean open,focused,selected;private long version;
    public boolean isOpen(){return open;}
    public long revision(){return version*2+(open&&focused&&System.currentTimeMillis()/500%2==0?1:0);}
    public void open(){clear();open=true;focused=true;version++;}
    public void clear(){value="";error="";selected=false;version++;}
    public void close(){clear();open=false;focused=false;version++;}
    public void append(String text){if(!open||!focused||text==null)return;if(selected){value="";selected=false;}if(value.length()+text.length()>LoginCookies.MAX_LENGTH){error="Cookie 超过长度上限，请复制请求中的 Cookie 字段";version++;return;}value+=text;error="";version++;}
    public void click(double x,double y,boolean busy,Consumer<String> submit){if(FIELD.contains(x,y)){focused=true;selected=false;}else if(SUBMIT.contains(x,y)){send(busy,submit);}else focused=false;version++;}
    private void send(boolean busy,Consumer<String> submit){if(busy)return;if(value.isBlank()){error="请先粘贴完整 Cookie";version++;return;}String raw=value;clear();submit.accept(raw);}
    public void key(KeyEvent event,String pasted,boolean busy,Consumer<String> submit){
        if(event.isConfirmation()){send(busy,submit);return;}if(event.key()==InputConstants.KEY_TAB){focused=true;version++;return;}if(!focused)return;
        if(event.isSelectAll()){selected=true;version++;return;}if(event.isPaste()){append(pasted);return;}if(event.isCopy()||event.isCut())return;
        if(event.key()==InputConstants.KEY_BACKSPACE||event.key()==InputConstants.KEY_DELETE){if(selected||event.key()==InputConstants.KEY_DELETE)value="";else if(!value.isEmpty())value=value.substring(0,value.offsetByCodePoints(value.length(),-1));selected=false;version++;}
    }
    public BufferedImage decorate(BufferedImage base,double scale,String source,String status,boolean failed,boolean busy){
        if(!open)return base;var image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(var c=new FrostCanvas(image,scale)){
            var t=c.g.getTransform();c.g.setTransform(new AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(t);c.g.translate(PAD,PAD);
            c.round(0,0,WIDTH,HEIGHT,38,new Color(0,0,0,115));c.round(295,86,700,512,24,dark?new Color(38,46,51):new Color(255,248,252));
            c.text(source+" · Cookie 登录",325,127,20,true,INK,560);c.center("×",CLOSE.cx(),CLOSE.cy()+5,21,false,MUTED,28);c.text("< 返回扫码登录",BACK.x(),BACK.cy()+4,13,false,MUTED,BACK.w());
            c.text("完整 Cookie",345,216,18,true,INK,600);LoginFieldPainter.paint(c,FIELD,value,"粘贴完整 Cookie",true,focused,focused&&System.currentTimeMillis()/500%2==0);
            c.text(HINT,345,309,11,false,MUTED,620);MusicPainter.loginButton(c,SUBMIT,busy?"正在验证…":"验证并登录");
            Color errorInk=new Color(0xB94F6A);c.text(error.isBlank()?status:error,345,438,12,false,!error.isBlank()||failed?errorInk:MUTED,600);
            c.text("验证成功后保存至本机独立账号文件",345,500,11,false,MUTED,600);
        }
        return image;
    }
    @Override public String toString(){return "CookieLoginDialog[redacted]";}
}
