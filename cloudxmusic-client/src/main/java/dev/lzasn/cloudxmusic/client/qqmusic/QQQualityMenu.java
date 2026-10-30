package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import dev.lzasn.cloudxmusic.client.gui.design.MusicPainter;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** 绘制、定位和点击共用尺寸，完整显示三档音质名称。 */
public final class QQQualityMenu {
    private static final int WIDTH=260;
    private QQQualityMenu(){}
    public static Box region(){return MusicPainter.qualityMenuRegion(QQQuality.values().length,WIDTH);}
    public static Box row(int index){return MusicPainter.qualityRow(index,QQQuality.values().length,WIDTH);}
    public static int selected(QQPlaybackController.View playback,QQQuality preferred){
        // 对勾只表示当前流的真实档位；未取到链接时不把偏好伪装成播放音质。
        if(playback.actualQuality()!=null)return playback.actualQuality().ordinal();
        return playback.song()==null?preferred.ordinal():-1;
    }
    public static BufferedImage paint(int selected,int hovered,double scale){
        return MusicPainter.paintQualityMenu(selected,hovered,scale,Arrays.stream(QQQuality.values()).map(QQQuality::menuLabel).toArray(String[]::new),WIDTH);
    }
}
