package dev.lzasn.cloudxmusic.client.gui.terms;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import java.awt.*;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.terms.AgreementReader.*;

/** 首次使用页和 QQ 登录浮层共用的主题面板；阅读区裁剪不影响固定操作栏。 */
public final class AgreementPainter {
    public static final int WIDTH=800,HEIGHT=590,PAD=112;
    public static final Box TEXT=new Box(30,96,740,380),AGREE=new Box(568,523,202,42),REJECT=new Box(376,523,172,42);
    private AgreementPainter(){}
    public static BufferedImage paint(AgreementReader reader,boolean ready,String error,double scale){
        var image=new BufferedImage((int)Math.ceil((WIDTH+2*PAD)*scale),(int)Math.ceil((HEIGHT+2*PAD)*scale),BufferedImage.TYPE_INT_ARGB);
        try(var c=new FrostCanvas(image,scale)){c.g.translate(PAD,PAD);panel(c,reader,ready,false,error);}
        return image;
    }
    public static void panel(FrostCanvas c,AgreementReader reader,boolean loaded,boolean saving,String error){
        c.shadow(0,0,WIDTH,HEIGHT,24,12,0,4,new Color(0,0,0,60));
        c.round(0,0,WIDTH,HEIGHT,24,dark?new Color(38,46,51):new Color(255,248,252));
        c.text(UserAgreement.label("title"),30,48,21,true,INK,740);
        c.text(UserAgreement.label("subtitle"),30,76,12,false,MUTED,740);
        c.round(TEXT.x(),TEXT.y(),TEXT.w(),TEXT.h(),14,SOFT);
        Shape clip=c.g.getClip();
        try{
            c.g.clip(new Rectangle(TEXT.x()+16,TEXT.y()+16,TEXT_WIDTH,CONTENT_HEIGHT));
            for(int i=0;i<reader.lines().size();i++){
                double baseline=TEXT.y()+30+i*LINE_HEIGHT-reader.offset();
                if(baseline>=TEXT.y()+4&&baseline<TEXT.y()+TEXT.h())c.text(reader.lines().get(i),TEXT.x()+16,(float)baseline,13,false,INK,TEXT_WIDTH);
            }
        }finally{c.g.setClip(clip);}
        double thumb=Math.min(CONTENT_HEIGHT,Math.max(30,CONTENT_HEIGHT*CONTENT_HEIGHT/(double)(reader.lines().size()*LINE_HEIGHT)));
        double top=TEXT.y()+16+(reader.maxScroll()==0?0:reader.offset()/reader.maxScroll()*(CONTENT_HEIGHT-thumb));
        c.round(756,TEXT.y()+16,4,CONTENT_HEIGHT,2,TRACK);c.round(756,top,4,thumb,2,PINK);
        boolean canAgree=loaded&&!saving&&reader.atEnd();
        String message=!error.isBlank()?error:UserAgreement.label(saving?"saving":!loaded?"loading":canAgree?"ready":"reading");
        c.text(message,30,501,11,false,error.isBlank()?MUTED:new Color(0xB94F6A),740);
        c.text(UserAgreement.label("hint"),30,555,10,false,MUTED,325);
        c.round(REJECT.x(),REJECT.y(),REJECT.w(),REJECT.h(),14,SOFT);
        c.center(UserAgreement.label("reject"),REJECT.cx(),REJECT.cy()+4,12,true,DEEP,REJECT.w()-16);
        c.round(AGREE.x(),AGREE.y(),AGREE.w(),AGREE.h(),14,canAgree?PINK:TRACK);
        c.center(UserAgreement.label(saving?"saving":"agree"),AGREE.cx(),AGREE.cy()+4,12,true,canAgree?Color.WHITE:MUTED,AGREE.w()-16);
    }
}
