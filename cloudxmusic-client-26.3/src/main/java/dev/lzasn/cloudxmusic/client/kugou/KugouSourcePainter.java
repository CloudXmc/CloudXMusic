package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** 三音源总入口画布；点击和绘制共享坐标，平台登录仍由各自控制器负责。 */
public final class KugouSourcePainter {
    public static final Box NETEASE=new Box(360,213,560,52), QQ=new Box(360,274,560,52), KUGOU=new Box(360,335,560,52);
    public static final Box MANAGE=new Box(360,410,260,40), CLOSE=new Box(660,410,260,40);
    private KugouSourcePainter(){}
    public static BufferedImage menu(double scale,MusicSourceCoordinator.View route,String netease,String qq,String kugou){
        BufferedImage image=new BufferedImage((int)Math.ceil((WIDTH+2*PAD)*scale),(int)Math.ceil((HEIGHT+2*PAD)*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)){
            c.g.translate(PAD,PAD);c.round(0,0,WIDTH,HEIGHT,38,new Color(0,0,0,115));c.round(324,126,632,372,24,dark?new Color(38,46,51):new Color(255,248,252));
            c.text("账号与全局音源",360,169,22,true,INK,550);
            row(c,NETEASE,"网易云音乐 · "+netease,route.source()==MusicSource.NETEASE);
            row(c,QQ,"QQ 音乐 · "+qq,route.source()==MusicSource.QQ);
            row(c,KUGOU,"酷狗音乐 · "+kugou,route.source()==MusicSource.KUGOU);
            button(c,MANAGE,"管理当前音源账号");button(c,CLOSE,"关闭");
            c.center(route.switching()||route.error()?route.status():"切换音源会暂停当前播放；各音源的账号和队列独立保存",640,482,12,false,route.error()?new Color(0xB94F6A):MUTED,580);
        }
        return image;
    }
    private static void row(FrostCanvas c,Box b,String text,boolean active){c.round(b.x(),b.y(),b.w(),b.h(),13,active?SOFT:CARD_BG);c.text((active?"✓  ":"    ")+text,b.x()+20,b.cy()+5,14,true,active?DEEP:INK,b.w()-40);}
    private static void button(FrostCanvas c,Box b,String text){c.round(b.x(),b.y(),b.w(),b.h(),13,SOFT);c.center(text,b.cx(),b.cy()+4,12,true,DEEP,b.w()-20);}
}
