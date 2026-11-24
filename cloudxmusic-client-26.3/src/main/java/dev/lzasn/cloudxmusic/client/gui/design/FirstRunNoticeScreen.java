package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.gui.FirstRunNotice;
import dev.lzasn.cloudxmusic.client.gui.UiTexture;
import dev.lzasn.cloudxmusic.client.gui.terms.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.awt.image.BufferedImage;


/** 首次使用确认页；阅读至底部且本地保存成功后才进入原目标界面。 */
public final class FirstRunNoticeScreen extends Screen {
    private final Screen parent,destination;
    private final UiTexture surface=new UiTexture();
    private double scale,rasterScale;
    private boolean blurUnavailable,dirty=true,paintedDark;
    private final AgreementReader reader=new AgreementReader();
    private String error="";
    private static final int W=FirstRunNoticePainter.WIDTH,H=FirstRunNoticePainter.HEIGHT,P=FirstRunNoticePainter.PAD;

    public FirstRunNoticeScreen(Screen parent,Screen destination) {
        super(Component.literal(UserAgreement.label("title")));this.parent=parent;this.destination=destination;
    }
    @Override protected void init() {
        scale=Math.max(.1,Math.min((width-36.0)/(W+P*2),(height-36.0)/(H+P*2)));
        rasterScale=Math.max(1,Math.min(3,scale*minecraft.getWindow().getGuiScale()));
        dirty=true;
    }

    private BufferedImage renderNotice() {
        return FirstRunNoticePainter.paint(reader,error,rasterScale);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mx,int my,float tick) {
        if(!blurUnavailable&&minecraft.level!=null) try { extractBlurredBackground(graphics); } catch(RuntimeException e) { blurUnavailable=true; }
        graphics.fill(0,0,width,height,0x55332A35);
        if(dirty||paintedDark!=FrostCanvas.dark) { surface.update(renderNotice());dirty=false;paintedDark=FrostCanvas.dark; }
        int w=(int)Math.round((W+P*2)*scale),h=(int)Math.round((H+P*2)*scale);
        surface.draw(graphics,(width-w)/2,(height-h)/2,w,h);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e,boolean handled) {
        if(e.button()!=1)return true; // SDL 左键 = 1（GLFW 时代是 0）
        double factor=scale;
        double x=(e.x()-(width-W*scale)/2)/factor,y=(e.y()-(height-H*scale)/2)/factor;
        if(AgreementPainter.REJECT.contains(x,y))onClose();
        else if(AgreementPainter.AGREE.contains(x,y)&&reader.atEnd())accept();
        return true;
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        double x=(mx-(width-W*scale)/2)/scale,y=(my-(height-H*scale)/2)/scale;
        if(AgreementPainter.TEXT.contains(x,y)&&reader.scroll(sy))dirty=true;
        return true;
    }
    private void accept(){
        try{FirstRunNotice.accept();minecraft.gui.setScreen(destination);}
        catch(IllegalStateException e){error=UserAgreement.label("saveError");dirty=true;}
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.isEscape()){onClose();return true;}
        if(event.isConfirmation()&&reader.atEnd()){accept();return true;}
        double scroll=switch(event.key()){
            case InputConstants.KEY_DOWN->-1;case InputConstants.KEY_UP->1;
            case InputConstants.KEY_PAGEDOWN->-9;case InputConstants.KEY_PAGEUP->9;
            case InputConstants.KEY_END->-100000;case InputConstants.KEY_HOME->100000;default->0;
        };
        if(reader.scroll(scroll))dirty=true;
        return true;
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){surface.close();super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
