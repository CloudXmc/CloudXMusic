package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** QQ 的命中适配；所有控制坐标取自原版布局，不另造第二套控制条。 */
public final class QQPlaybackUi {
    private static final MusicLayout CONTROLS=new MusicLayout(MusicView.Page.NOW,0,0);
    private QQPlaybackUi(){}
    public static boolean visible(MusicView.Page page,boolean qq,QQPlaybackController.View playback){return qq;}
    public static int rowAt(double x,double y,int scroll,int count,boolean bar){
        int rows=bar?7:8;
        if(x<MAIN_X||x>=1050||y<ROW_TOP||y>=ROW_TOP+rows*ROW_H)return -1;
        int index=scroll+(int)((y-ROW_TOP)/ROW_H);return index<count?index:-1;
    }
    public static boolean nowPlayingAt(double x,double y){return MusicPainter.playbackInfoRegion().contains(x,y);}
    public static Action actionAt(double x,double y){
        Hit hit=CONTROLS.hit(x,y);if(hit==null)return null;
        return switch(hit.action()){
            case FAVORITE -> Action.STOP;
            case PREVIOUS,PLAY,NEXT,VOLUME,SEEK,QUALITY,PAGE,MODE_CYCLE -> hit.action();
            default -> null;
        };
    }
    public static Box volumeBox(){return CONTROLS.box("volume");}
    public static Box seekBox(){return CONTROLS.box("seek");}
    public static MusicPainter.PlaybackBarView barView(QQPlaybackController.View v){
        return new MusicPainter.PlaybackBarView(v.song().title(),v.song().artist(),(int)Math.ceil(v.duration()),v.position(),v.playing(),v.volume(),v.quality(),true);
    }
}
