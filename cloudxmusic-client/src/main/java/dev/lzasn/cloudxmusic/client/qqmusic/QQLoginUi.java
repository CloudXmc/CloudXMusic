package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.MusicView;
import java.awt.image.BufferedImage;

/** Screen 的薄 QQ 事件适配器；不接触 MusicState、网易云账号或播放控制。 */
public final class QQLoginUi implements AutoCloseable {
    private final QQAccountManager accounts;
    private final QQLoginService login;
    private final java.util.function.Function<String,BufferedImage> avatars;
    private final QQTermsConsent consent;
    private final QQTermsDialog terms;
    private boolean offered,dirty;
    private QQTermsConsent.View lastConsent;
    private long lastTermsRevision=-1;
    private boolean open;
    private QQAccountManager.View lastAccount;
    private QQLoginService.View lastLogin;
    QQLoginUi(QQAccountManager accounts,QQLoginService login,java.util.function.Function<String,BufferedImage> avatars,QQTermsConsent consent){this.accounts=accounts;this.login=login;this.avatars=avatars;this.consent=consent;terms=new QQTermsDialog(consent);}
    public boolean isOpen() { return open||terms.isOpen(); }
    public void checkTerms(boolean qq){
        if(!qq){if(isOpen())dismiss();return;}
        if(!offered&&consent.view().loaded()&&!consent.view().agreed()){offered=true;terms.open(this::continueLogin);dirty=true;}
    }
    public java.util.function.Consumer<dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas> sidebarPainter(MusicView view, boolean qq) {
        QQAccountManager.View account = accounts.view();
        return qq
                ? canvas -> QQLoginPainter.sidebar(canvas,account,view.hover().equals("account"),avatars.apply(account.profile().avatarUrl())) : null;
    }
    public boolean changed() {
        QQAccountManager.View account = accounts.view(); QQLoginService.View session = login.view();
        terms.update();
        boolean changed = dirty||account != lastAccount || session != lastLogin||lastConsent!=consent.view()||lastTermsRevision!=terms.revision();
        dirty=false;lastConsent=consent.view();lastTermsRevision=terms.revision();
        lastAccount = account; lastLogin = session; return changed;
    }
    public BufferedImage decorate(BufferedImage base, double scale, MusicView view, boolean qq) {
        return !qq?base:terms.isOpen()?terms.decorate(base,scale):QQLoginPainter.decorate(base, scale, open, accounts.view(), login.view());
    }
    public boolean click(double x, double y) {
        if(terms.isOpen())return terms.click(x,y);
        if (!open) {
            if (!QQLoginPainter.ENTRY.contains(x, y)) return false;
            open();
            return true;
        }
        if (QQLoginPainter.CLOSE.contains(x, y) || QQLoginPainter.DONE.contains(x, y)) dismiss();
        else if (QQLoginPainter.REFRESH.contains(x, y)) {if(consent.view().agreed())login.start();else open();}
        else if (QQLoginPainter.LOGOUT.contains(x, y) && (accounts.view().loggedIn() || accounts.view().error())) {
            login.cancel(); accounts.logout();
        }
        return true; // 浮层范围外的点击也不能穿透到原界面。
    }
    public boolean scroll(double x,double y,double vertical){return terms.scroll(x,y,vertical);}
    public void open(){
        offered=true;dirty=true;
        if(!consent.view().agreed()){open=false;terms.open(this::continueLogin);return;}
        continueLogin();
    }
    private void continueLogin(){open=true;dirty=true;if(!accounts.view().loggedIn())login.start();}
    public void dismiss() {terms.dismiss();dirty=true;if(open){open=false;login.cancel();}}
    @Override public void close() { dismiss(); }
}
