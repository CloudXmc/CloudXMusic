package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas;
import dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.Box;
import dev.lzasn.cloudxmusic.client.gui.design.UiIcons;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import static dev.lzasn.cloudxmusic.client.gui.design.FrostCanvas.*;
import static dev.lzasn.cloudxmusic.client.gui.design.MusicLayout.*;

/** QQ 专属登录浮层，图像直接合成到现有界面纹理，不占用额外 GPU 纹理。 */
public final class QQLoginPainter {
    static final Box ENTRY = new Box(32, 442, 210, 64);
    static final Box CLOSE = new Box(951, 102, 28, 28);
    static final Box REFRESH = new Box(425, 528, 120, 34);
    static final Box LOGOUT = new Box(565, 528, 120, 34);
    static final Box DONE = new Box(705, 528, 120, 34);
    private QQLoginPainter() {}
    public static void sidebar(FrostCanvas c,QQAccountManager.View account,boolean hover){sidebar(c,account,hover,null);}
    public static void sidebar(FrostCanvas c,QQAccountManager.View account,boolean hover,BufferedImage avatar){
        if (hover) c.round(ENTRY.x(), ENTRY.y(), ENTRY.w(), ENTRY.h(), 20, CARD_BG);
        c.round(34, 461, 36, 36, 18, SOFT);
        if(avatar==null||!account.loggedIn())UiIcons.draw(c,"user",52,479,21,DEEP);
        else {
            Shape clip=c.g.getClip();c.g.clip(shape(34,461,36,36,18));
            double zoom=36.0/Math.min(avatar.getWidth(),avatar.getHeight());
            int w=(int)Math.ceil(avatar.getWidth()*zoom),h=(int)Math.ceil(avatar.getHeight()*zoom);
            c.g.drawImage(avatar,34+(36-w)/2,461+(36-h)/2,w,h,null);c.g.setClip(clip);
        }
        String name=account.loggedIn()?(account.nickname().isBlank()?"QQ 音乐用户":account.nickname()):"登录 QQ 音乐";
        String badge=account.loggedIn()?account.profile().badge():"";
        float badgeWidth=badge.isEmpty()?0:c.width(badge,9,true)+12;
        float nameWidth=151-(badge.isEmpty()?0:badgeWidth+5);
        String fitted=c.fit(name,14,true,nameWidth);c.text(fitted,82,474,14,true,INK,nameWidth);
        if(!badge.isEmpty()){
            float bx=82+c.width(fitted,14,true)+5;
            Color color=account.profile().membership()==QQAccountProfile.Membership.SVIP?
                    (dark?new Color(0xF5CD76):new Color(0xA57316)):(dark?new Color(0x70D8AE):new Color(0x26815D));
            c.round(bx,462,badgeWidth,16,5,new Color(color.getRed(),color.getGreen(),color.getBlue(),28));
            c.g.setColor(color);c.g.draw(shape(bx,462,badgeWidth,16,5));
            c.center(badge,bx+badgeWidth/2,474,9,true,color,badgeWidth-6);
        }
        c.text(account.loggedIn() ? "QQ音乐 · 切换音源 / 管理" : account.error() ? "QQ音乐 · 登录异常" : "QQ音乐 · 切换 / 登录",
                82, 495, 11, false, account.error() ? QQSearchPainter.errorColor() : MUTED, 151);
    }
    public static BufferedImage decorate(BufferedImage base, double scale, boolean open,
            QQAccountManager.View account, QQLoginService.View login) {
        if (!open) return base;
        BufferedImage image = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        try (FrostCanvas c = new FrostCanvas(image, scale)) {
            AffineTransform transform = c.g.getTransform();
            c.g.setTransform(new AffineTransform()); c.g.drawImage(base, 0, 0, null); c.g.setTransform(transform);
            c.g.translate(PAD, PAD);
            c.round(0, 0, WIDTH, HEIGHT, 38, new Color(0, 0, 0, 115));
            c.round(295, 86, 700, 512, 24, dark ? new Color(38, 46, 51) : new Color(255, 248, 252));
            c.text("QQ 音乐扫码登录", 325, 127, 20, true, INK, 560);
            c.center("×", CLOSE.cx(), CLOSE.cy() + 6, 22, false, MUTED, 28);
            c.center(account.loggedIn() ? "当前账号：" + (account.nickname().isBlank() ? "QQ 音乐用户" : account.nickname())
                    : "请使用手机 QQ 音乐 App 扫码", 645, 160, 14, true, INK, 630);
            c.round(521, 174, 248, 248, 12, Color.WHITE);
            if (login.qr() != null) {
                BufferedImage qr = login.qr();
                c.g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                c.g.drawImage(qr, 533, 186, 224, 224, null);
            } else {
                c.center(switch (login.state()) {
                    case LOADING -> "正在连接…"; case VERIFYING -> "正在验证…";
                    case SUCCESS -> "登录成功"; case EXPIRED -> "二维码已过期";
                    case ERROR, CANCELED -> "请刷新二维码";
                    default -> account.loggedIn() ? "QQ 音乐已登录" : "点击刷新获取二维码";
                }, 645, 304, 16, true, new Color(0x40323C), 225);
            }
            c.center(account.loggedIn() && login.state() == QQLoginService.State.IDLE ? "QQ 音乐已登录" : login.message(),
                    645, 447, 13, true, login.error() ? QQSearchPainter.errorColor() : INK, 640);
            c.center(account.status(), 645, 473, 12, false, account.error() ? QQSearchPainter.errorColor() : MUTED, 640);
            c.center("凭证仅存于本机 QQ 账号目录；不会修改网易云账号", 645, 503, 11, false, MUTED, 640);
            button(c, REFRESH, account.loggedIn() ? "切换账号 / 刷新" : "刷新二维码", DEEP);
            if (account.loggedIn() || account.error()) button(c, LOGOUT, "退出 / 清除凭证", QQSearchPainter.errorColor());
            button(c, DONE, "关闭", DEEP);
        }
        return image;
    }
    private static void button(FrostCanvas c, Box box, String label, Color color) {
        c.round(box.x(), box.y(), box.w(), box.h(), 13, SOFT);
        c.center(label, box.cx(), box.cy() + 4, 12, true, color, box.w() - 14);
    }
}
