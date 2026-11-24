package dev.lzasn.cloudxmusic.client.gui.playlist;

import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import static dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistSource.Section;

/** 收藏页三分类与歌单内容的唯一几何来源，主内容严格止于共享播放条之前。 */
public final class PlaylistLayout {
    public static final Box CONTENT=new Box(282,98,778,482),BACK=new Box(292,150,68,28),
            PLAY=new Box(790,150,172,28),REFRESH=new Box(974,150,76,28),MORE=new Box(292,560,758,20);
    private PlaylistLayout(){}
    public static Box tab(Section section){return new Box(292+section.ordinal()*172,104,160,32);}
    public static Section tabAt(double x,double y){for(var section:Section.values())if(tab(section).contains(x,y))return section;return null;}
    public static Box card(int local){return new Box(292+local%4*193,184+local/4*188,178,178);}
    public static int cardAt(double x,double y,int scroll,int count){for(int n=0;n<8;n++)if(card(n).contains(x,y)){int row=scroll*4+n;return row<count?row:-1;}return -1;}
    public static Box row(int local){return new Box(292,234+local*46,758,44);}
    public static Box add(int local){var b=row(local);return new Box(1002,b.y()+3,40,38);}
    public static Box share(int local){var b=row(local);return new Box(942,b.y()+4,32,36);}
    public static int addAt(double x,double y,int scroll,int count){for(int n=0;n<7;n++)if(add(n).contains(x,y))return scroll+n<count?scroll+n:-1;return -1;}
    public static int shareAt(double x,double y,int scroll,int count){for(int n=0;n<7;n++)if(share(n).contains(x,y))return scroll+n<count?scroll+n:-1;return -1;}
    public static int rowAt(double x,double y,int scroll,int count){for(int n=0;n<7;n++)if(row(n).contains(x,y)){int row=scroll+n;return row<count?row:-1;}return -1;}
}
