package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** 账号区域总开关与未接入页面的绘制，无账号或播放器业务。 */
public final class QQSourcePainter {
    public static final Box NETEASE=new Box(360,224,560,62), QQ=new Box(360,300,560,62);
    public static final Box MANAGE=new Box(360,398,260,40), CLOSE=new Box(660,398,260,40);
    private QQSourcePainter(){}
    public static BufferedImage menu(double scale,MusicSourceCoordinator.View route,String netease,String qq){
        BufferedImage image=new BufferedImage((int)Math.ceil((WIDTH+2*PAD)*scale),(int)Math.ceil((HEIGHT+2*PAD)*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)){
            c.g.translate(PAD,PAD);c.round(0,0,WIDTH,HEIGHT,38,new Color(0,0,0,115));
            c.round(324,132,632,370,24,dark?new Color(38,46,51):new Color(255,248,252));
            c.text("账号与全局音源",360,178,22,true,INK,550);
            row(c,NETEASE,"网易云 · "+netease,route.source()==MusicSource.NETEASE);
            row(c,QQ,"QQ音乐 · "+qq,route.source()==MusicSource.QQ);
            button(c,MANAGE,"管理当前音源账号");button(c,CLOSE,"关闭");
            c.center(route.switching()||route.error()?route.status():"切换会暂停播放，搜索与队列各自保留",640,475,12,false,route.error()?QQSearchPainter.errorColor():MUTED,570);
        }
        return image;
    }
    private static void row(FrostCanvas c,Box box,String text,boolean active){c.round(box.x(),box.y(),box.w(),box.h(),14,active?SOFT:CARD_BG);c.text((active?"✓  ":"    ")+text,box.x()+20,box.cy()+5,15,true,active?DEEP:INK,box.w()-40);}
    private static void button(FrostCanvas c,Box box,String text){c.round(box.x(),box.y(),box.w(),box.h(),14,SOFT);c.center(text,box.cx(),box.cy()+5,13,true,DEEP,box.w()-20);}
    public static void placeholder(FrostCanvas c,MusicView.Page page){
        UiIcons.draw(c,"note",671,315,52,PINK);
        c.center(page==MusicView.Page.DISCOVER?"QQ音乐推荐模块待接入":"QQ音乐收藏模块待接入",671,390,20,true,INK,700);
        c.center("当前为 QQ音乐音源，可前往搜索或播放队列",671,424,13,false,MUTED,700);
    }
}
