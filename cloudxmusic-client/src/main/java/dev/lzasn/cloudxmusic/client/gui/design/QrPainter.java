package dev.lzasn.cloudxmusic.client.gui.design;

import java.awt.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;

/** 登录弹窗和主界面使用相同字体及玻璃材质；二维码保留四模块静区。 */
public final class QrPainter {
    public static final int WIDTH=420, HEIGHT=474, PAD=112;
    private QrPainter() {}

    public static BufferedImage paint(boolean[][] modules,String status,boolean success,boolean failed,double scale) {
        BufferedImage image=new BufferedImage((int)Math.ceil((WIDTH+PAD*2)*scale),(int)Math.ceil((HEIGHT+PAD*2)*scale),BufferedImage.TYPE_INT_ARGB);
        try(FrostCanvas c=new FrostCanvas(image,scale)) {
            c.g.translate(PAD,PAD);c.glass(0,0,WIDTH,HEIGHT,34,false);
            c.center("连接网易云音乐",210,47,21,true,INK,370);
            c.center("用手机上的网易云音乐 App 扫码",210,75,12,false,MUTED,370);
            c.card(74,100,272,272,26);
            int count=modules==null?29:modules.length;
            int cell=Math.max(1,240/(count+8));
            int total=(count+8)*cell;
            int left=(WIDTH-total)/2,top=112+(248-total)/2;
            c.round(left,top,total,total,0,Color.WHITE);
            if(modules!=null) {
                Object previous=c.g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
                c.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_OFF);
                c.g.setColor(new Color(0x24202A));
                for(int y=0;y<count;y++) for(int x=0;x<count;x++) if(modules[y][x])
                    c.g.fillRect(left+(x+4)*cell,top+(y+4)*cell,cell,cell);
                c.g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,previous);
            } else c.center("正在申请二维码…",210,240,14,false,MUTED,240);
            c.center(status,210,405,13,true,failed?new Color(176,58,81):success?DEEP:INK,374);
            c.center("无法扫描？登录链接已复制到剪贴板",210,434,11,false,MUTED,374);
            c.center("Esc 返回音乐盒",210,455,11,false,MUTED,374);
        }
        return image;
    }
}
