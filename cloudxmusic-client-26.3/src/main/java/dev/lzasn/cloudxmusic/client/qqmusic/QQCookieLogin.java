package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.login.*;

/** Cookie 仅产生待验证会话；不调用扫码换票或刷新接口。 */
public final class QQCookieLogin {
    private QQCookieLogin(){}
    public static QQCredentials parse(String raw){
        var cookies=LoginCookies.parse(raw);String id=LoginCookies.required(cookies,"uin");
        if(id.startsWith("o"))id=id.substring(1);id=id.replaceFirst("^0+(?!$)","");
        String key=LoginCookies.required(cookies,"qm_keyst");int type="2".equals(cookies.get("login_type"))||key.startsWith("W_X")?1:2;
        return new QQCredentials(id,key,type,"",0,QQAccountProfile.EMPTY,LoginMethod.COOKIE,QQRenewal.empty());
    }
}
