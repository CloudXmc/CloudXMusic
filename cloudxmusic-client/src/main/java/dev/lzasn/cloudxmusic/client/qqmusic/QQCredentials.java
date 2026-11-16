package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.gui.login.LoginMethod;

/** 仅保存已验证的 QQ 音乐会话；日志中不得展开账号或密钥。 */
public record QQCredentials(String userId, String musicKey, int loginType, String nickname,
                            long expiresAtMillis, QQAccountProfile profile,LoginMethod method,QQRenewal renewal) {
    public QQCredentials(String userId,String musicKey,int loginType,String nickname,long expiresAtMillis,QQAccountProfile profile){this(userId,musicKey,loginType,nickname,expiresAtMillis,profile,LoginMethod.QR,QQRenewal.empty());}
    public QQCredentials(String userId,String musicKey,int loginType,String nickname,long expiresAtMillis){this(userId,musicKey,loginType,nickname,expiresAtMillis,QQAccountProfile.EMPTY);}
    public QQCredentials {
        profile=profile==null?QQAccountProfile.EMPTY:profile;
        method=method==null?LoginMethod.QR:method;renewal=renewal==null||method==LoginMethod.COOKIE?QQRenewal.empty():renewal;
        if (userId == null || !userId.matches("[1-9][0-9]{0,18}"))
            throw new IllegalArgumentException("QQ 音乐账号格式无效");
        try { Long.parseLong(userId); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("QQ 音乐账号超出范围"); }
        if (musicKey == null || musicKey.isBlank() || musicKey.length() > 8192
                || musicKey.chars().anyMatch(c -> c <= 32 || c >= 127 || c == ';' || c == ',' || c == '"' || c == '\\'))
            throw new IllegalArgumentException("QQ 音乐凭证格式无效");
        if (loginType < 1 || loginType > 16) throw new IllegalArgumentException("QQ 音乐登录类型无效");
        nickname = nickname == null ? "" : nickname.replaceAll("[\\p{Cntrl}]", "").strip();
        if (nickname.length() > 256 || expiresAtMillis < 0) throw new IllegalArgumentException("QQ 音乐账号资料无效");
    }
    public boolean expired() { return expiresAtMillis > 0 && expiresAtMillis <= System.currentTimeMillis(); }
    public boolean cookieLogin(){return method==LoginMethod.COOKIE;}
    @Override public String toString() { return "QQCredentials[redacted]"; }
}
