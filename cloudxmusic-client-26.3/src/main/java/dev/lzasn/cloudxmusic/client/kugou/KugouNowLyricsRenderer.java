package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.gui.BilingualTextRenderer;
import dev.lzasn.cloudxmusic.client.gui.design.*;
import dev.lzasn.cloudxmusic.client.omni.PlaybackSnapshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 酷狗 KRC 逐字时间轴的播放页渲染适配。 */
public final class KugouNowLyricsRenderer implements AutoCloseable {
    private final BilingualTextRenderer renderer=new BilingualTextRenderer();
    public void draw(GuiGraphicsExtractor graphics,MusicView view,double raster,PlaybackSnapshot playback){
        var active=view.lyrics().stream().filter(MusicView.LyricRow::active).findFirst();if(active.isEmpty()){close();return;}
        var region=MusicLayout.lyricRegion();var line=playback.lineAt((int)(playback.position()*1000));double elapsed=Math.max(0,playback.position()-(line==null?0:line.timeMs()/1000.0));int baseline=region.y()+region.h()/2;
        renderer.draw(graphics,active.get().text(),active.get().translation(),19,11,INK,new java.awt.Color(MUTED.getRed(),MUTED.getGreen(),MUTED.getBlue(),205),region.x()+24,baseline,baseline+20,region.w()-32,baseline-30,baseline+28,false,raster,elapsed,playback.timedLine(),playback.position()*1000);
    }
    @Override public void close(){renderer.close();}
}
