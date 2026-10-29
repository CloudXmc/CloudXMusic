package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Standalone painter for the first-run privacy, service and copyright notice. */
public final class FirstRunNoticePainter {
    public static final int WIDTH=780,HEIGHT=500,PAD=112;
    private FirstRunNoticePainter() {}

    public static BufferedImage paint(boolean checked,double scale) {
        BufferedImage image=new BufferedImage((int)Math.ceil((WIDTH+PAD*2)*scale),
                (int)Math.ceil((HEIGHT+PAD*2)*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)) {
            c.g.translate(PAD,PAD);c.glass(0,0,WIDTH,HEIGHT,34,false);
            c.round(32,28,42,42,21,new Color(248,222,233));UiIcons.draw(c,"note",53,49,23,FrostCanvas.DEEP);
            c.text("网易云音乐免责声明",90,48,23,true,FrostCanvas.INK,640);
            c.text("CloudXMusic · 云朵音乐盒首次使用须知",90,70,12,false,FrostCanvas.MUTED,640);
            c.g.setColor(new Color(135,90,115,35));c.g.drawLine(32,91,748,91);
            int y=123;
            y=paragraph(c,"1. CloudXMusic（云朵音乐盒）是非官方第三方 Fabric 模组，不隶属、未获网易云音乐运营方授权或背书。",y);
            y=paragraph(c,"2. 登录、搜索、歌单、歌词和播放会由客户端直接连接网易云音乐相关服务及音频 CDN；接口可能调整或不可用。",y+8);
            y=paragraph(c,"3. 登录凭据会发送给网易云音乐服务；本模组不会保存你的密码。",y+8);
            y=paragraph(c,"   登录 Cookie 以明文保存在 config/cloudxmusic/cookies.json，请勿分享。",y+3);
            paragraph(c,"4. 请遵守当地法律法规、网易云音乐服务条款及作品版权要求；不得传播或公开播放未经授权的内容。",y+8);
            c.g.setColor(new Color(135,90,115,35));c.g.drawLine(32,385,748,385);
            int bx=35,by=407;
            c.round(bx,by,21,21,6,checked?new Color(239,189,208):Color.WHITE);
            c.g.setColor(checked?FrostCanvas.DEEP:new Color(204,183,194));c.g.draw(FrostCanvas.shape(bx+.5,by+.5,20,20,6));
            if(checked) { c.g.setStroke(new BasicStroke(2));c.g.drawLine(bx+5,by+11,bx+9,by+15);c.g.drawLine(bx+9,by+15,bx+16,by+6); }
            c.text("我已阅读并理解上述账号、版权和服务风险",bx+31,by+16,12,true,FrostCanvas.INK,302);
            c.round(429,403,157,46,23,new Color(255,255,255,checked?220:118));
            c.g.setColor(new Color(255,255,255,200));c.g.draw(FrostCanvas.shape(429.5,403.5,156,45,23));
            c.center("退出",507,431,13,true,FrostCanvas.MUTED,120);
            c.shadow(604,403,143,46,23,10,4,new Color(158,65,103,checked?32:10));
            c.round(604,403,143,46,23,checked?new GradientPaint(604,403,new Color(242,170,194),604,449,FrostCanvas.PINK):new Color(211,198,204));
            c.center("我已知晓并继续",675.5f,431,12,true,Color.WHITE,132);
        }
        return image;
    }

    private static int paragraph(FrostCanvas c,String text,int top) {
        String remaining=text;int y=top;
        while(!remaining.isEmpty()) {
            int end=0;
            while(end<remaining.length()) {
                int next=end+Character.charCount(remaining.codePointAt(end));
                if(c.width(remaining.substring(0,next),13,false)>704)break;
                end=next;
            }
            if(end==0)end=Character.charCount(remaining.codePointAt(0));
            String line=remaining.substring(0,end);
            c.text(line,38,y,13,false,FrostCanvas.INK,704);
            remaining=remaining.substring(end);
            y+=22;
        }
        return y;
    }
}
