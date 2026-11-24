package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.util.*;

/** LoginServer 实际返回的续期字段；缺少 refresh_key 时不能续期。来源及许可见 THIRD_PARTY_NOTICES.md。 */
public record QQRenewal(String refreshKey,String refreshToken,String openId,String unionId,String musicId,long issuedAt){
    public QQRenewal{refreshKey=refreshKey==null?"":refreshKey;refreshToken=refreshToken==null?"":refreshToken;openId=openId==null?"":openId;unionId=unionId==null?"":unionId;musicId=musicId==null?"":musicId;for(var value:List.of(refreshKey,refreshToken,openId,unionId,musicId))if(value.length()>8192||value.chars().anyMatch(c->c<=32||c>=127))throw new IllegalArgumentException("QQ 续期字段无效");if(issuedAt<0)throw new IllegalArgumentException("QQ 续期时间无效");}
    public static QQRenewal empty(){return new QQRenewal("","","","","",0);}
    static QQRenewal from(JsonObject data){return new QQRenewal(QQAuthHttp.text(data,"refresh_key"),QQAuthHttp.text(data,"refresh_token"),QQAuthHttp.text(data,"openid"),QQAuthHttp.text(data,"unionid"),QQAuthHttp.text(data,"str_musicid"),System.currentTimeMillis());}
    QQRenewal merge(QQRenewal old){return new QQRenewal(or(refreshKey,old.refreshKey),or(refreshToken,old.refreshToken),or(openId,old.openId),or(unionId,old.unionId),or(musicId,old.musicId),issuedAt);}
    private static String or(String value,String fallback){return value.isEmpty()?fallback:value;}
    public boolean available(){return !refreshKey.isEmpty();}
    JsonObject params(QQCredentials account){var p=new JsonObject();p.addProperty("openid",openId);p.addProperty("refresh_token",refreshToken);p.addProperty("str_musicid",musicId.isEmpty()?account.userId():musicId);p.addProperty("musickey",account.musicKey());p.addProperty("unionid",unionId);p.addProperty("refresh_key",refreshKey);p.addProperty("loginMode",2);return p;}
    @Override public String toString(){return "QQRenewal[redacted]";}
}
