package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;

/** QQ 目录独立的布局、绘制和命中；内容区止于共享底栏上沿，不依赖网易云列表几何。 */
public final class QQCatalogPainter {
    public static final Box REFRESH=new Box(970,104,80,28),BACK=new Box(292,104,68,28),PLAY=new Box(866,104,92,28),
            MORE=new Box(292,552,758,26);
    private QQCatalogPainter(){}
    public static void paint(FrostCanvas c,QQCatalogController.View view,Function<String,BufferedImage> images,UnifiedSong current){
        Shape clip=c.g.getClip();c.g.clip(new Rectangle2D.Double(282,98,778,482));
        try{
            button(c,REFRESH,view.loading()?"加载中":"刷新");
            if(view.mode()==QQCatalogController.Mode.HOME)home(c,view,images);
            else detail(c,view,images,current);
        }finally{c.g.setClip(clip);}
    }
    private static void home(FrostCanvas c,QQCatalogController.View v,Function<String,BufferedImage> images){
        c.text(v.status(),292,123,11,false,v.error()?QQSearchPainter.errorColor():MUTED,588);
        dailyEntry(c);
        Shape saved=c.g.getClip();c.text("为你推荐歌单",292,308,15,true,INK,400);
        if(v.playlists().isEmpty()&&!v.loading())c.text("暂无推荐歌单，请登录或点击刷新",292,362,13,false,MUTED,700);
        c.g.clip(new Rectangle2D.Double(282,314,778,242));
        for(int n=v.scroll()*QQCatalogLayout.COLUMNS;n<Math.min(v.playlists().size(),(v.scroll()+QQCatalogLayout.VISIBLE_ROWS)*QQCatalogLayout.COLUMNS);n++){
            Box tile=QQCatalogLayout.tile(n-v.scroll()*QQCatalogLayout.COLUMNS);int x=tile.x(),y=tile.y();var p=v.playlists().get(n);
            c.shadow(x,y,178,224,14,7,0,3,new Color(24,16,24,dark?48:18));c.round(x,y,178,224,14,CARD_BG);
            BufferedImage image=images.apply(p.cover());cover(c,image,x,y,178,178,14);
            if(image==null)UiIcons.draw(c,"note",x+89,y+89,30,PINK);
            c.text(p.title(),x+10,y+199,12,true,INK,158);
            c.text(p.count()>0?p.count()+" 首歌曲":"精选歌单",x+10,y+216,10,false,MUTED,158);
            c.round(x+134,y+134,32,32,16,new Color(255,255,255,185));UiIcons.draw(c,"play",x+151,y+150,14,new Color(55,37,50,230));
        }
        c.g.setClip(saved);c.text(v.loading()?"正在加载推荐…":v.hasMore()?"向下滚动加载更多歌单":"已显示全部推荐",292,574,10,false,MUTED,758);
    }
    private static void dailyEntry(FrostCanvas c){
        Box box=QQCatalogLayout.dailyCard();int x=box.x(),y=box.y(),w=box.w(),h=box.h();
        Color left=dark?new Color(46,65,83):new Color(215,233,253);
        Color right=dark?new Color(59,85,110):new Color(161,198,240);
        c.shadow(x,y,w,h,14,8,0,3,new Color(20,12,24,dark?48:22));
        c.round(x,y,w,h,14,new GradientPaint(x,y,left,x+w,y+h,right));
        Shape clip=c.g.getClip();c.g.clip(shape(x,y,w,h,14));
        try{
            int artX=x+w-132,artY=y+14,side=104;
            c.g.setColor(new Color(15,20,27,225));c.g.fillOval(artX+24,artY+1,108,108);
            for(int r:new int[]{42,35,27}){c.g.setColor(new Color(220,230,240,28));c.g.drawOval(artX+78-r,artY+55-r,r*2,r*2);}
            c.shadow(artX,artY,side,side,5,9,0,4,new Color(10,20,30,60));
            dailyArtwork(c,artX,artY,side);
            c.g.setColor(new Color(255,255,255,100));c.g.draw(shape(artX+.5,artY+.5,side-1,side-1,5));
            Color text=dark?new Color(238,246,250):new Color(25,48,61);
            Color secondary=dark?new Color(207,225,231):new Color(56,88,103);
            c.text("DAILY 30",x+18,y+20,9,true,secondary,202);
            c.text("每日30首",x+18,y+47,23,true,text,202);
            c.text("今日私享，每天遇见新旋律",x+18,y+66,11,false,secondary,300);
            c.text("每天更新 · 你的专属精选",x+18,y+83,10,false,secondary,300);
            Box play=QQCatalogLayout.dailyPlay();c.round(play.x(),play.y(),play.w(),play.h(),13,new Color(PINK.getRed(),PINK.getGreen(),PINK.getBlue(),235));
            UiIcons.draw(c,"play",play.x()+16,play.cy(),12,Color.WHITE);c.text("立即播放",play.x()+29,play.y()+17,10,true,Color.WHITE,52);
        }finally{c.g.setClip(clip);}
    }
    private static void dailyArtwork(FrostCanvas c,int x,int y,int side){
        // 固定默认唱片封面，离线绘制；不触发图片下载或借用推荐歌单的人像封面。
        Color a=new Color(255,242,221),b=new Color(244,201,202);
        c.round(x,y,side,side,5,new GradientPaint(x,y,a,x+side,y+side,b));
        Color ink=new Color(105,68,82);
        c.g.setColor(new Color(255,255,255,105));c.g.fillOval(x+22,y+20,64,64);
        c.center("30",x+side/2,y+70,47,true,ink,side-12);
        c.center("DAILY SELECTION",x+side/2,y+92,8,true,ink,side-8);
    }
    private static void detail(FrostCanvas c,QQCatalogController.View v,Function<String,BufferedImage> images,UnifiedSong current){
        if(v.mode()!=QQCatalogController.Mode.LIKED)button(c,BACK,"‹ 返回");
        c.text(v.title(),v.mode()==QQCatalogController.Mode.LIKED?292:374,124,15,true,INK,v.mode()==QQCatalogController.Mode.LIKED?552:474);
        button(c,PLAY,"播放全部");c.text(v.status(),292,157,11,false,v.error()?QQSearchPainter.errorColor():MUTED,758);
        for(int n=v.scroll();n<Math.min(v.tracks().size(),v.scroll()+7);n++){
            var track=v.tracks().get(n);var song=track.display();int y=182+(n-v.scroll())*52;
            if(current!=null&&current.platformId().equals(song.platformId()))c.round(292,y,758,50,10,SOFT);
            c.g.setColor(LINE);c.g.drawLine(292,y+51,1050,y+51);
            cover(c,images.apply(song.coverUrl()),304,y+7,36,36,7);
            if(song.coverUrl().isBlank())UiIcons.draw(c,"note",322,y+25,19,PINK);
            QQVipBadge.title(c,song.title(),352,y+22,13,true,INK,372,song.vip());
            c.text(song.artist(),352,y+40,10,false,MUTED,372);c.text(song.album(),748,y+29,11,false,MUTED,225);
            c.text(song.durationText(),1002,y+29,11,false,MUTED,48);
        }
        if(v.tracks().isEmpty()&&!v.loading())c.text(v.error()?"请检查账号后刷新重试":"歌单暂无歌曲",292,242,13,false,MUTED,758);
        c.center(v.loading()?"正在加载…":v.hasMore()?"下拉或点击加载更多":"已显示全部歌曲",671,570,10,false,MUTED,758);
    }
    private static void button(FrostCanvas c,Box box,String label){c.round(box.x(),box.y(),box.w(),box.h(),box.h()/2f,SOFT);c.center(label,box.cx(),box.cy()+4,11,true,DEEP,box.w()-8);}
    private static void cover(FrostCanvas c,BufferedImage image,int x,int y,int width,int height,int radius){
        QQCoverArt.paint(c,image,x,y,width,height,radius);
    }
    public static boolean click(QQCatalogController controller,double x,double y){
        if(x<282||x>1060||y<98||y>=582)return false;var v=controller.view();
        if(REFRESH.contains(x,y)){controller.refresh();return true;}
        if(v.mode()==QQCatalogController.Mode.HOME){
            if(QQCatalogLayout.dailyCard().contains(x,y))controller.daily(QQCatalogLayout.dailyPlay().contains(x,y));
            else{
                int n=QQCatalogLayout.playlistAt(x,y,v.scroll(),v.playlists().size());
                if(n>=0){Box tile=QQCatalogLayout.tile(n-v.scroll()*QQCatalogLayout.COLUMNS);controller.open(v.playlists().get(n),new Box(tile.x()+134,tile.y()+134,32,32).contains(x,y));}
            }
        }else{
            if(BACK.contains(x,y)&&v.mode()!=QQCatalogController.Mode.LIKED)controller.home();
            else if(PLAY.contains(x,y))controller.play(0);
            else if(MORE.contains(x,y))controller.loadMore();
            else if(x>=292&&x<1050&&y>=182&&y<546)controller.play(v.scroll()+(int)((y-182)/52));
        }
        return true;
    }
    public static boolean scroll(QQCatalogController controller,double x,double y,double horizontal,double vertical){
        if(x<282||x>1060||y<98||y>=582)return false;
        if(controller.view().mode()==QQCatalogController.Mode.HOME&&y>=138&&y<279)return true;
        else if(vertical!=0)controller.scroll(vertical>0?-1:1);return true;
    }
}
