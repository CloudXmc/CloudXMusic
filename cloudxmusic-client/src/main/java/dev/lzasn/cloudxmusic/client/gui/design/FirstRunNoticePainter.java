package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.gui.terms.*;
import java.awt.image.BufferedImage;

/** 首次使用页沿用统一三平台协议的正文、滚动区与主题按钮。 */
public final class FirstRunNoticePainter {
    public static final int WIDTH=AgreementPainter.WIDTH,HEIGHT=AgreementPainter.HEIGHT,PAD=AgreementPainter.PAD;
    private FirstRunNoticePainter(){}
    public static BufferedImage paint(AgreementReader reader,String error,double scale){
        return AgreementPainter.paint(reader,true,error,scale);
    }
    /** 保留已有离线预览调用；布尔值现在表示是否已读至底部。 */
    public static BufferedImage paint(boolean readToEnd,double scale){
        var reader=new AgreementReader();if(readToEnd)reader.scroll(-100000);
        return paint(reader,"",scale);
    }
}
