package dev.lzasn.cloudxmusic.client.gui.playlist;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.qqmusic.QQVipBadge;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Function;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistLayout.*;
import static dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.*;

/** 沿用搜索/发现页的字体、圆角卡片、封面遮罩和歌曲 VIP 标签。 */
public final class PlaylistPainter {
    private PlaylistPainter(){}
    public static void paint(FrostCanvas c,PlaylistView view,Function<String,BufferedImage> images,UnifiedSong current){
        Shape old=c.g.getClip();c.g.clip(new Rectangle2D.Double(CONTENT.x(),CONTENT.y(),CONTENT.w(),CONTENT.h()));
        try{
            for(var section:Section.values()){
                var box=tab(section);boolean selected=section==view.section();
                c.round(box.x(),box.y(),box.w(),box.h(),16,selected?SOFT:CARD_BG);
                c.center(switch(section){case LIKED->"我喜欢的音乐";case OWNED->"我的歌单";case COLLECTED->"收藏歌单";},box.cx(),box.cy()+4,12,selected,selected?DEEP:MUTED,box.w()-12);
            }
            button(c,REFRESH,view.loading()?"加载中":"刷新",!view.loading()&&!view.playingAll());
            if(view.detail())detail(c,view,images,current);else grid(c,view,images);
        }finally{c.g.setClip(old);}
    }
    private static void grid(FrostCanvas c,PlaylistView view,Function<String,BufferedImage> images){
        c.text(view.status(),292,169,11,false,view.error()?new Color(0xB94F6A):MUTED,660);
        for(int n=0;n<8;n++){
            int index=view.scroll()*4+n;if(index>=view.playlists().size())break;
            var e=view.playlists().get(index);var box=card(n);
            c.shadow(box.x(),box.y(),box.w(),box.h(),12,7,0,3,new Color(24,16,24,dark?48:18));c.round(box.x(),box.y(),box.w(),box.h(),12,CARD_BG);
            cover(c,images.apply(e.cover()),new MusicLayout.Box(box.x(),box.y(),box.w(),126),12);
            c.text(e.title(),box.x()+10,box.y()+148,12,true,INK,158);c.text(e.count()+" 首歌曲",box.x()+10,box.y()+167,10,false,MUTED,158);
        }
        if(view.playlists().isEmpty()&&!view.loading())c.center(view.error()?"读取失败，请刷新重试":"暂无歌单",671,340,15,true,MUTED,700);
        c.center(view.loading()?"正在加载歌单…":view.hasMore()?"点击加载更多歌单":"已显示全部歌单",671,575,10,false,MUTED,758);
    }
    private static void detail(FrostCanvas c,PlaylistView view,Function<String,BufferedImage> images,UnifiedSong current){
        if(view.selected()!=null)button(c,BACK,"‹ 返回",!view.playingAll());
        button(c,PLAY,view.playingAll()?view.status():"播放全部",!view.loading()&&!view.playingAll()&&!view.tracks().isEmpty());
        String title=view.selected()==null?"我喜欢的音乐":view.selected().title();String image=view.selected()==null?"":view.selected().cover();
        cover(c,images.apply(image),new MusicLayout.Box(292,187,36,36),7);c.text(title,340,203,14,true,INK,700);
        c.text(view.status(),340,222,10,false,view.error()?new Color(0xB94F6A):MUTED,700);
        for(int local=0;local<7;local++){
            int index=view.scroll()+local;if(index>=view.tracks().size())break;var t=view.tracks().get(index);var s=t.display();var box=row(local);
            if(current!=null&&current.source().equals(t.song().source())&&current.platformId().equals(t.song().platformId()))c.round(box.x(),box.y(),box.w(),box.h(),9,SOFT);
            c.g.setColor(LINE);c.g.drawLine(box.x(),box.y()+45,box.x()+box.w(),box.y()+45);
            cover(c,images.apply(s.coverUrl()),new MusicLayout.Box(304,box.y()+5,34,34),7);
            QQVipBadge.title(c,s.title(),350,box.y()+19,12,true,INK,420,s.vip());c.text(s.artist(),350,box.y()+36,10,false,MUTED,420);
            c.text(s.album(),790,box.y()+27,10,false,MUTED,116);c.right(s.durationText(),930,box.y()+27,11,false,MUTED);
            if(t.song().source().equals("qq")){var share=share(local);UiIcons.draw(c,"share",share.cx(),share.cy(),15,MUTED);}
            var add=add(local);UiIcons.draw(c,"plus",add.cx(),add.cy(),16,MUTED);
        }
        if(view.tracks().isEmpty()&&!view.loading())c.center(view.error()?"读取失败，请刷新重试":"歌单暂无歌曲",671,340,15,true,MUTED,700);
        c.center(view.loading()?"正在加载…":view.playingAll()?view.status():view.hasMore()?"点击或下拉加载更多":"已显示全部歌曲",671,575,10,false,MUTED,758);
    }
    static void cover(FrostCanvas c,BufferedImage image,MusicLayout.Box box,int radius){
        if(image!=null)c.roundImage(image,box.x(),box.y(),box.w(),box.h(),radius);
        else{c.round(box.x(),box.y(),box.w(),box.h(),radius,SOFT);UiIcons.draw(c,"note",box.cx(),box.cy(),Math.min(28,box.h()*.6f),PINK);}
    }
    private static void button(FrostCanvas c,MusicLayout.Box box,String label,boolean enabled){c.round(box.x(),box.y(),box.w(),box.h(),14,enabled?SOFT:CARD_BG);c.center(label,box.cx(),box.cy()+4,11,true,enabled?DEEP:MUTED,box.w()-12);}
    public static boolean click(PlaylistController controller,double x,double y){
        if(!CONTENT.contains(x,y))return false;var section=tabAt(x,y);if(section!=null){controller.select(section);return true;}
        var view=controller.view();if(view.playingAll())return true;
        if(REFRESH.contains(x,y)){controller.refresh();return true;}
        if(view.detail()){
            if(view.selected()!=null&&BACK.contains(x,y))controller.back();else if(PLAY.contains(x,y))controller.playAll();
            else if(MORE.contains(x,y))controller.loadMore();else if(addAt(x,y,view.scroll(),view.tracks().size())<0&&shareAt(x,y,view.scroll(),view.tracks().size())<0){int row=rowAt(x,y,view.scroll(),view.tracks().size());if(row>=0)controller.playFrom(row);}
        }else if(MORE.contains(x,y))controller.loadMore();else{int index=cardAt(x,y,view.scroll(),view.playlists().size());if(index>=0)controller.open(view.playlists().get(index));}
        return true;
    }
}
