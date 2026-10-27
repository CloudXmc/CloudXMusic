package dev.lzasn.cloudxmusic.client.gui;

import dev.lzasn.cloudxmusic.client.gui.design.QrPainter;
import dev.lzasn.cloudxmusic.client.netease.QrLogin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Esc 取消轮询并返回父界面；调整窗口尺寸不会重新申请二维码。 */
public final class QrLoginScreen extends Screen implements QrLogin.Listener {
    private final Screen parent;
    private final UiTexture surface=new UiTexture();
    private boolean[][] modules;
    private String status="正在申请二维码…";
    private boolean success,failed,started,dirty=true,blurUnavailable;
    private long doneAt;
    private double scale,raster;
    public QrLoginScreen() { this(null); }
    public QrLoginScreen(Screen parent) { super(Component.literal("云朵音乐盒 · 扫码登录"));this.parent=parent; }

    @Override protected void init() {
        scale=Math.max(.05,Math.min((width-30.0)/(QrPainter.WIDTH+QrPainter.PAD*2),
                (height-40.0)/(QrPainter.HEIGHT+QrPainter.PAD*2)));
        raster=Math.max(.75,Math.min(2,scale*minecraft.getWindow().getGuiScale()));dirty=true;
        if(!started) { started=true;QrLogin.start(this); }
    }
    @Override public void onQrCode(boolean[][] modules) { this.modules=modules;dirty=true; }
    @Override public void onStatus(String text) { status=text;dirty=true; }
    @Override public void onSuccess(String nickname) { success=true;status="登录成功："+nickname;doneAt=System.currentTimeMillis();dirty=true; }
    @Override public void onFailure(String text) { failed=true;status=text;dirty=true; }
    @Override public void tick() { if(success&&System.currentTimeMillis()-doneAt>1200) onClose(); }

    @Override public void render(GuiGraphics graphics,int mx,int my,float tick) {
        if(!blurUnavailable&&minecraft.level!=null) {
            try { renderBlurredBackground(graphics); } catch(RuntimeException e) { blurUnavailable=true; }
        }
        graphics.fill(0,0,width,height,0x332C2430);
        if(dirty) { surface.update(QrPainter.paint(modules,status,success,failed,raster));dirty=false; }
        int w=(int)Math.round((QrPainter.WIDTH+2*QrPainter.PAD)*scale),h=(int)Math.round((QrPainter.HEIGHT+2*QrPainter.PAD)*scale);
        surface.draw(graphics,(width-w)/2,(height-h)/2,w,h);
    }
    @Override public void onClose() { QrLogin.cancel();minecraft.setScreen(parent); }
    @Override public void removed() { QrLogin.cancel();surface.close();super.removed(); }
    @Override public boolean isPauseScreen() { return false; }
}
