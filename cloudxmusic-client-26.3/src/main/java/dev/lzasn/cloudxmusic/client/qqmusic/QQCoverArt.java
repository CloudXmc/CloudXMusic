package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/** 等比居中填充与抗锯齿圆角蒙版，只在静态页面重绘时生成，不持有图片缓存。 */
final class QQCoverArt {
    private QQCoverArt(){}
    static void paint(FrostCanvas canvas,BufferedImage image,int x,int y,int width,int height,int radius){
        canvas.round(x,y,width,height,radius,FrostCanvas.SOFT);
        if(image==null)return;
        double scale=Math.clamp(Math.abs(canvas.g.getTransform().getScaleX()),1,4);
        BufferedImage rounded=crop(image,(int)Math.ceil(width*scale),(int)Math.ceil(height*scale),radius*scale);
        canvas.g.drawImage(rounded,x,y,width,height,null);
    }
    static BufferedImage crop(BufferedImage image,int width,int height,double radius){
        if(width<1||height<1)throw new IllegalArgumentException("封面尺寸必须为正数");
        BufferedImage result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g=result.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            double scale=Math.max((double)width/image.getWidth(),(double)height/image.getHeight());
            AffineTransform transform=AffineTransform.getTranslateInstance((width-image.getWidth()*scale)/2,(height-image.getHeight()*scale)/2);
            transform.scale(scale,scale);g.drawImage(image,transform,null);
            // Graphics2D.clip 的形状边缘不做抗锯齿；用独立透明蒙版保留平滑圆角。
            BufferedImage mask=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D mg=mask.createGraphics();
            try{mg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);mg.setColor(Color.WHITE);mg.fill(FrostCanvas.shape(0,0,width,height,radius));}
            finally{mg.dispose();}
            g.setComposite(AlphaComposite.DstIn);g.drawImage(mask,0,0,null);
        }finally{g.dispose();}
        return result;
    }
}
