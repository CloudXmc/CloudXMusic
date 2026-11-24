package dev.lzasn.cloudxmusic.client.kugou;

import java.net.*;
import java.util.*;
import dev.lzasn.cloudxmusic.client.gui.login.LoginMethod;

/** 不可变的酷狗会话快照；日志输出永不展开 token 和 cookies。 */
public record KugouAccount(String userId,String token,String deviceId,String nickname,List<Cookie> cookies,LoginMethod method,String refreshToken,long refreshedAt,KugouProfile profile) {
    public KugouAccount(String userId,String token,String deviceId,String nickname,List<Cookie> cookies){this(userId,token,deviceId,nickname,cookies,LoginMethod.QR,"",0,KugouProfile.empty());}
    public record Cookie(String name,String value,String domain,String path,boolean secure,long expiresAt) {
        @Override public String toString(){return "KugouCookie[redacted]";}
    }
    public KugouAccount {
        Objects.requireNonNull(userId);Objects.requireNonNull(token);Objects.requireNonNull(nickname);
        if(deviceId==null||!deviceId.matches("[a-fA-F0-9]{32}"))throw new IllegalArgumentException("酷狗设备身份无效");
        if(!userId.matches("[0-9]{1,20}"))throw new IllegalArgumentException("酷狗用户身份无效");
        if(token.length()>8192||token.chars().anyMatch(c->c<=32||c>=127||c==';'||c=='\"'||c=='\\'))throw new IllegalArgumentException("酷狗凭证格式无效");
        method=method==null?LoginMethod.QR:method;refreshToken=refreshToken==null||method==LoginMethod.COOKIE?"":refreshToken;profile=profile==null?KugouProfile.empty():profile;
        if(refreshToken.length()>8192||refreshToken.chars().anyMatch(c->c<=32||c>=127)||refreshedAt<0)throw new IllegalArgumentException("酷狗续期凭证无效");
        cookies=List.copyOf(cookies);
    }
    public boolean loggedIn(){return !userId.equals("0")&&!token.isBlank();}
    public boolean cookieLogin(){return method==LoginMethod.COOKIE;}
    public static KugouAccount guest(String deviceId){return new KugouAccount("0","",deviceId,"",List.of());}
    public CookieManager cookieManager(){
        CookieManager manager=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        for(Cookie saved:cookies){
            if(!cookieDomain(saved.domain())||(saved.expiresAt()>0&&saved.expiresAt()<=System.currentTimeMillis()))continue;
            HttpCookie cookie=new HttpCookie(saved.name(),saved.value());cookie.setVersion(0);cookie.setDomain(saved.domain());
            cookie.setPath(saved.path());cookie.setSecure(saved.secure());
            if(saved.expiresAt()>0)cookie.setMaxAge(Math.max(0,(saved.expiresAt()-System.currentTimeMillis())/1000));
            manager.getCookieStore().add(URI.create("https://"+saved.domain().replaceFirst("^\\.","")+"/"),cookie);
        }
        return manager;
    }
    static boolean cookieDomain(String domain){String normalized=domain==null?"":domain.toLowerCase(Locale.ROOT).replaceFirst("^\\.","");return normalized.equals("kugou.com")||normalized.endsWith(".kugou.com");}
    static List<Cookie> capture(CookieManager manager){
        return manager.getCookieStore().getCookies().stream().filter(c->!c.hasExpired()&&cookieDomain(c.getDomain())).limit(128)
                .map(c->new Cookie(c.getName(),c.getValue(),c.getDomain(),c.getPath()==null?"/":c.getPath(),c.getSecure(),
                        c.getMaxAge()<0?0:System.currentTimeMillis()+Math.min(c.getMaxAge(),315360000L)*1000)).toList();
    }
    @Override public String toString(){return "KugouAccount[loggedIn="+loggedIn()+", credentials=redacted]";}
}
