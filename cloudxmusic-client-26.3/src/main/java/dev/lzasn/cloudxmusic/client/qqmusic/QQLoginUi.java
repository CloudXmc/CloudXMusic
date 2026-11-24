package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.MusicView;
import java.awt.image.BufferedImage;

/** Screen 的薄 QQ 事件适配器；不接触 MusicState、网易云账号或播放控制。 */
public final class QQLoginUi implements AutoCloseable {
    private final QQAccountManager accounts;
    private final QQLoginService login;
    private final java.util.function.Function<String,BufferedImage> avatars;
    private boolean dirty;
    private boolean open;
    private QQAccountManager.View lastAccount;
    private QQLoginService.View lastLogin;
    private final dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog cookie=new dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog();
    private long lastCookieRevision=-1;
    QQLoginUi(QQAccountManager accounts,QQLoginService login,java.util.function.Function<String,BufferedImage> avatars){this.accounts=accounts;this.login=login;this.avatars=avatars;}
    public boolean isOpen() { return open||cookie.isOpen(); }
    public java.util.function.Consumer<dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas> sidebarPainter(MusicView view, boolean qq) {
        QQAccountManager.View account = accounts.view();
        return qq
                ? canvas -> QQLoginPainter.sidebar(canvas,account,view.hover().equals("account"),avatars.apply(account.profile().avatarUrl())) : null;
    }
    public boolean changed() {
        QQAccountManager.View account = accounts.view(); QQLoginService.View session = login.view();
        boolean changed = dirty||account != lastAccount || session != lastLogin||lastCookieRevision!=cookie.revision();
        lastCookieRevision=cookie.revision();
        dirty=false;
        lastAccount = account; lastLogin = session; return changed;
    }
    public BufferedImage decorate(BufferedImage base, double scale, MusicView view, boolean qq) {
        return !qq?base:cookie.isOpen()?cookie.decorate(base,scale,"QQ 音乐",accounts.view().status(),accounts.view().error(),accounts.cookieBusy()):QQLoginPainter.decorate(base, scale, open, accounts.view(), login.view());
    }
    public boolean click(double x, double y) {
        if(cookie.isOpen()){
            if(dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog.CLOSE.contains(x,y)){dismiss();return true;}
            if(dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog.BACK.contains(x,y)){cookie.close();accounts.cancelCookieImport();login.start();return true;}
            cookie.click(x,y,accounts.cookieBusy(),accounts::importCookie);return true;
        }
        if (!open) {
            if (!QQLoginPainter.ENTRY.contains(x, y)) return false;
            open();
            return true;
        }
        if(dev.lzasn.cloudxmusic.client.gui.login.CookieLoginDialog.ENTRY.contains(x,y)){login.cancel();cookie.open();dirty=true;}
        else if (QQLoginPainter.CLOSE.contains(x, y) || QQLoginPainter.DONE.contains(x, y)) dismiss();
        else if (QQLoginPainter.REFRESH.contains(x, y)) login.start();
        else if (QQLoginPainter.LOGOUT.contains(x, y) && (accounts.view().loggedIn() || accounts.view().error())) {
            login.cancel(); accounts.logout();
        }
        return true; // 浮层范围外的点击也不能穿透到原界面。
    }
    public boolean scroll(double x,double y,double vertical){return cookie.isOpen();}
    public void open(){
        continueLogin();
    }
    private void continueLogin(){open=true;dirty=true;if(!accounts.view().loggedIn())login.start();}
    public void key(net.minecraft.client.input.KeyEvent event,String pasted){if(cookie.isOpen())cookie.key(event,pasted,accounts.cookieBusy(),accounts::importCookie);}
    public void text(String text){if(cookie.isOpen())cookie.append(text);}
    public void dismiss() {cookie.close();accounts.cancelCookieImport();dirty=true;if(open){open=false;login.cancel();}}
    @Override public void close() { dismiss(); }
}
