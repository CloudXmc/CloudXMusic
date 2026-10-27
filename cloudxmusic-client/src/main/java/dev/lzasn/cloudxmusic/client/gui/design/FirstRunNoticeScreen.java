package dev.lzasn.cloudxmusic.client.gui.design;

import dev.lzasn.cloudxmusic.client.gui.FirstRunNotice;
import dev.lzasn.cloudxmusic.client.gui.UiTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.awt.image.BufferedImage;

/** 首次使用确认页；未勾选前不能继续进入音乐界面或发起网易云请求。 */
public final class FirstRunNoticeScreen extends Screen {
    private final Screen parent,destination;
    private final UiTexture surface=new UiTexture();
    private double scale,rasterScale;
    private boolean checked,blurUnavailable,dirty=true;
    private static final int W=FirstRunNoticePainter.WIDTH,H=FirstRunNoticePainter.HEIGHT,P=FirstRunNoticePainter.PAD;

    public FirstRunNoticeScreen(Screen parent,Screen destination) {
        super(Component.literal("云朵音乐盒 · 首次使用说明"));this.parent=parent;this.destination=destination;
    }
    @Override protected void init() {
        scale=Math.max(.1,Math.min((width-36.0)/(W+P*2),(height-36.0)/(H+P*2)));
        rasterScale=Math.max(1,Math.min(3,scale*minecraft.getWindow().getGuiScale()));
        dirty=true;
    }

    private BufferedImage renderNotice() {
        return FirstRunNoticePainter.paint(checked,rasterScale);
    }

    @Override public void render(GuiGraphics graphics,int mx,int my,float tick) {
        if(!blurUnavailable&&minecraft.level!=null) try { renderBlurredBackground(graphics); } catch(RuntimeException e) { blurUnavailable=true; }
        graphics.fill(0,0,width,height,0x55332A35);
        if(dirty) { surface.update(renderNotice());dirty=false; }
        int w=(int)Math.round((W+P*2)*scale),h=(int)Math.round((H+P*2)*scale);
        surface.draw(graphics,(width-w)/2,(height-h)/2,w,h);
    }

    @Override public boolean mouseClicked(MouseButtonEvent e,boolean handled) {
        if(e.button()!=0)return super.mouseClicked(e,handled);
        double factor=scale;
        double x=(e.x()-(width-W*scale)/2)/factor,y=(e.y()-(height-H*scale)/2)/factor;
        if(x>=35&&x<371&&y>=407&&y<445){checked=!checked;dirty=true;return true;}
        if(x>=429&&x<587&&y>=403&&y<449){minecraft.setScreen(parent);return true;}
        if(x>=604&&x<747&&y>=403&&y<449&&checked){
            try { FirstRunNotice.accept();minecraft.setScreen(destination); }
            catch(IllegalStateException e1) { checked=false;dirty=true; }
            return true;
        }
        return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if(event.isEscape()){minecraft.setScreen(parent);return true;}
        if(event.isConfirmation()&&checked){try{FirstRunNotice.accept();minecraft.setScreen(destination);}catch(IllegalStateException ignored){checked=false;dirty=true;}return true;}
        return true;
    }
    @Override public void removed(){surface.close();super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
