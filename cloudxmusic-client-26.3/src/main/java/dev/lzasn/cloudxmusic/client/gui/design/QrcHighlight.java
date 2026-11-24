package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.omni.TimedLyrics;
import java.awt.image.BufferedImage;

/** 使用与实际栅格化相同的字体测宽；每帧只计算时间插值，不重新生成字形纹理。 */
public final class QrcHighlight {
    private final TimedLyrics.Line line;
    private final float[] widths;
    public QrcHighlight(TimedLyrics.Line line,float size,boolean bold){
        this.line=line;widths=new float[line.words().size()];
        try(FrostCanvas c=new FrostCanvas(new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB),1)){
            for(int i=0;i<widths.length;i++)widths[i]=c.width(line.words().get(i).text(),size,bold);
        }
    }
    public float widthAt(double milliseconds){
        float width=0;for(int i=0;i<widths.length;i++){var word=line.words().get(i);width+=widths[i]*word.progress(milliseconds);if(milliseconds<(long)word.startMs()+word.durationMs())break;}return width;
    }
}
