package dev.lzasn.cloudxmusic.client.omni;

import dev.lzasn.cloudxmusic.client.gui.CoverCache;
import dev.lzasn.cloudxmusic.client.kugou.KugouIntegration;
import dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import java.util.List;
import java.awt.image.BufferedImage;

/** GUI、HUD 与世界歌词共用的客户端路由入口。 */
public final class MusicPlayback {
    private static final PlaybackRouter ROUTER=new PlaybackRouter(()->QQMusicIntegration.coordinator().source(),QQMusicIntegration::snapshot,()->KugouIntegration.module().snapshot());
    private MusicPlayback(){}
    public static PlaybackSnapshot snapshot(){
        if(isKugou()&&!KugouIntegration.ready())return new PlaybackSnapshot(MusicSource.KUGOU,SongView.empty(MusicSource.KUGOU),List.of(),List.of(),false,false,0,0,0,"","Hi-Res音质",LyricMode.ORIGINAL,PlaybackMode.REPEAT_LIST,false,"酷狗模块正在初始化",false);
        return ROUTER.snapshot();
    }
    public static boolean isKugou(){return QQMusicIntegration.coordinator()!=null&&QQMusicIntegration.coordinator().source()==MusicSource.KUGOU;}
    public static boolean roomsSupported(){return !isKugou();}
    public static int artworkRevision(){return isKugou()?KugouIntegration.artwork().revision():QQMusicIntegration.isQq()?QQMusicIntegration.artwork().revision():CoverCache.revision();}
    public static BufferedImage artwork(String url){return isKugou()?KugouIntegration.artwork().image(url):QQMusicIntegration.isQq()?QQMusicIntegration.artwork().image(url):CoverCache.image(url);}
}
