package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 歌单网格与单曲/歌单标签共用绘制、点击几何，始终避开底栏。 */
public final class QQPlaylistSearchPainter {
    /** Tab 绘制和点击共用此坐标，避免命中区域与画面错位。 */
    public static final int TAB_TOP = 162;
    private static final int TAB_HEIGHT = 26;
    private static final int TAB_BASELINE = TAB_TOP + TAB_HEIGHT / 2 + 4;
    public static final Box SONGS=new Box(292,TAB_TOP,68,26),PLAYLISTS=new Box(368,TAB_TOP,68,26);
    private QQPlaylistSearchPainter(){}
    public static Box tile(int visibleIndex){return new Box(292+(visibleIndex%4)*192,208+(visibleIndex/4)*178,178,170);}
    public static int itemAt(double x,double y,int scroll,int count){
        for(int i=0;i<8&&scroll+i<count;i++)if(tile(i).contains(x,y))return scroll+i;return -1;
    }
    public static BufferedImage decorate(BufferedImage base,double scale,MusicView music,boolean playlists,QQPlaylistSearchController.View view,Function<String,BufferedImage> images){
        if(playlists)return QQSearchPainter.panel(base,scale,c->{field(c,music,view.loading());tabs(c,true);c.text(view.status(),464,175,11,false,view.error()?QQSearchPainter.errorColor():MUTED,586);grid(c,view,images);});
        var image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(var c=new FrostCanvas(image,scale)){var t=c.g.getTransform();c.g.setTransform(new java.awt.geom.AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(t);c.g.translate(MusicLayout.PAD,MusicLayout.PAD);tabs(c,false);}return image;
    }
    private static void tabs(FrostCanvas c,boolean playlists){
        for(int i=0;i<2;i++){Box box=i==0?SONGS:PLAYLISTS;boolean chosen=playlists==(i==1);if(chosen)c.round(box.x(),box.y(),box.w(),box.h(),13,SOFT);c.center(i==0?"单曲":"歌单",box.cx(),TAB_BASELINE,12,chosen,chosen?DEEP:MUTED,60);}
    }
    private static void field(FrostCanvas c,MusicView music,boolean loading){
        c.card(292,116,634,36,24);UiIcons.draw(c,"search",316,134,19,PINK);
        String label=music.search().isEmpty()?"搜索 QQ 音乐歌单":music.search();c.text(label,338,139,14,false,music.search().isEmpty()?MUTED:INK,564);
        if(music.focused()&&music.cursor()){double x=338+c.width(c.fit(music.search(),14,false,564),14,false);c.g.setColor(PINK);c.g.draw(new java.awt.geom.Line2D.Double(x,125,x,143));}
        c.round(950,116,100,36,18,loading?TRACK:SOFT);c.center(loading?"搜索中…":"搜索",1000,139,13,true,DEEP,90);
    }
    private static void grid(FrostCanvas c,QQPlaylistSearchController.View view,Function<String,BufferedImage> images){
        for(int i=0;i<8&&view.scroll()+i<view.items().size();i++){var item=view.items().get(view.scroll()+i);var p=item.playlist();Box b=tile(i);
            c.round(b.x(),b.y(),b.w(),b.h(),12,CARD_BG);var image=images.apply(p.cover());QQCoverArt.paint(c,image,b.x(),b.y(),b.w(),110,12);
            if(image==null)UiIcons.draw(c,"list",b.cx(),b.y()+55,30,PINK);
            c.text(p.title(),b.x()+9,b.y()+130,12,true,INK,160);c.text(item.creator().isBlank()?"QQ 音乐歌单":item.creator(),b.x()+9,b.y()+146,10,false,MUTED,160);
            c.text(plays(item.plays())+" 次播放 · "+p.count()+" 首",b.x()+9,b.y()+161,9,false,MUTED,160);
        }
        if(view.items().isEmpty())c.center(view.loading()?"正在搜索歌单…":view.error()?"搜索失败，请重新搜索":"输入关键词，发现喜欢的歌单",671,348,15,true,INK,700);
        c.center(view.loading()?"正在加载…":"向下滚动加载更多 · 点击歌单查看歌曲",671,579,10,false,MUTED,740);
    }
    private static String plays(long value){return value>=100000000?String.format(java.util.Locale.ROOT,"%.1f亿",value/100000000d):value>=10000?String.format(java.util.Locale.ROOT,"%.1f万",value/10000d):Long.toString(value);}
}
