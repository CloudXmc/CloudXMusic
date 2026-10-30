package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.BilingualTextRenderer;
import dev.lzasn.cloudxmusic.client.gui.design.*;
import net.minecraft.client.gui.GuiGraphics;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 正在播放页当前行：与 HUD 共用双语整体滚动，不重新栅格化整句来制造动画。 */
public final class QQNowLyricsRenderer implements AutoCloseable {
    private final BilingualTextRenderer text=new BilingualTextRenderer();
    private final MarqueeClock clock=new MarqueeClock();
    public void draw(GuiGraphics graphics,MusicView view,double raster,long now,int lyricIndex){
        var current=view.lyrics().stream().filter(MusicView.LyricRow::active).findFirst();
        if(current.isEmpty()){close();return;}
        var line=current.get();var region=MusicLayout.lyricRegion();int baseline=region.y()+region.h()/2;
        String key=view.current().platformId()+"|"+lyricIndex+"|"+line.text()+"|"+line.translation();
        text.draw(graphics,line.text(),line.translation(),19,11,INK,new java.awt.Color(MUTED.getRed(),MUTED.getGreen(),MUTED.getBlue(),205),region.x()+24,baseline,baseline+20,
                region.w()-32,baseline-30,baseline+28,false,raster,clock.elapsed(key,now),QQMusicIntegration.snapshot().timedLine(),QQMusicIntegration.snapshot().position()*1000);
    }
    @Override public void close(){text.close();clock.reset();}
}
