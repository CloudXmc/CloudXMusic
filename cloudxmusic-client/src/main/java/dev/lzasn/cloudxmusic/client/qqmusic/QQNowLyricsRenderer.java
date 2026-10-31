package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.BilingualTextRenderer;
import dev.lzasn.cloudxmusic.client.gui.design.*;
import net.minecraft.client.gui.GuiGraphics;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 正在播放页当前行：与 HUD 共用原文/译文独立滚动，不重新栅格化整句来制造动画。 */
public final class QQNowLyricsRenderer implements AutoCloseable {
    private final BilingualTextRenderer text=new BilingualTextRenderer();

    public void draw(GuiGraphics graphics,MusicView view,double raster,dev.lzasn.cloudxmusic.client.omni.PlaybackSnapshot playback){
        var current=view.lyrics().stream().filter(MusicView.LyricRow::active).findFirst();
        if(current.isEmpty()){close();return;}
        var line=current.get();var region=MusicLayout.lyricRegion();int baseline=region.y()+region.h()/2;
        var active=playback.lineAt((int)(playback.position()*1000));double elapsed=Math.max(0,playback.position()-(active==null?0:active.timeMs()/1000.0));
        text.draw(graphics,line.text(),line.translation(),19,11,INK,new java.awt.Color(MUTED.getRed(),MUTED.getGreen(),MUTED.getBlue(),205),region.x()+24,baseline,baseline+20,
                region.w()-32,baseline-30,baseline+28,false,raster,elapsed,playback.timedLine(),playback.position()*1000);
    }
    @Override public void close(){text.close();}
}
