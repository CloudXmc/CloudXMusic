package dev.lzasn.cloudxmusic.client.hud;

import dev.lzasn.cloudxmusic.client.gui.BilingualTextRenderer;
import dev.lzasn.cloudxmusic.client.gui.design.IslandPainter;
import net.minecraft.client.gui.GuiGraphics;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 原文/译文独立滚动，短行保持居中；裁剪保留字形边缘，不覆盖封面和时长。 */
final class IslandLyricsRenderer implements AutoCloseable {
    private final BilingualTextRenderer text=new BilingualTextRenderer(true);
    private String time="";private float available;
    void draw(GuiGraphics graphics,IslandPainter.View view,double raster,double seconds){
        if(!time.equals(view.time())){time=view.time();available=IslandPainter.availableTextWidth(view);}
        var playback=dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.snapshot();
        text.draw(graphics,view.title(),view.subtitle(),13,11,INK,MUTED,57,26,44,available,5,51,true,raster,seconds,playback.timedLine(),playback.position()*1000);
    }
    @Override public void close(){text.close();time="";}
}
