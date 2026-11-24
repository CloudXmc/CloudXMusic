package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.PAD;

/** QQ 播放页的显式反馈和“译”按钮。普通交接成功文字不进入任何页面图层。 */
public final class QQNowPainter {
    private QQNowPainter(){}
    public static BufferedImage decorate(BufferedImage base,double scale,MusicView view,QQPlaybackController.View playback){
        BufferedImage image=new BufferedImage(base.getWidth(),base.getHeight(),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)){
            // FrostCanvas 构造时会清空目标图，先复制底图，不能直接在 base 上构造。
            var transform=c.g.getTransform();c.g.setTransform(new java.awt.geom.AffineTransform());c.g.drawImage(base,0,0,null);c.g.setTransform(transform);
            c.g.translate(PAD,PAD);
            if(playback.loading()||playback.error())c.text(playback.status(),292,126,11,false,playback.error()?QQSearchPainter.errorColor():MUTED,758);
            var b=QQTranslationControl.BUTTON;
            boolean enabled=view.lyricMode().equals(dev.lzasn.cloudxmusic.client.state.LyricMode.BILINGUAL.label);
            c.round(b.x(),b.y(),b.w(),b.h(),9,enabled?SOFT:CARD_BG);
            c.center("译",b.cx(),b.cy()+5,13,true,enabled?PINK:MUTED,b.w()-8);
        }
        return image;
    }
}
