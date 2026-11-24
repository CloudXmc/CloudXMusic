package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.JsonObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.lzasn.cloudxmusic.client.kugou.KugouJson.*;

/** 普通版扫码协议；每次扫码持有自己的 CookieManager，一次性 token 换成可持久 Web token。 */
final class KugouLoginService implements AutoCloseable {
    record Qr(String key,byte[] png){Qr{png=png.clone();}public byte[] png(){return png.clone();}}
    record Poll(int status,KugouAccount account){}
    private final KugouApi api;
    private final String device;
    private final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    KugouLoginService(String device){this(device,new KugouApi());}
    KugouLoginService(String device,KugouApi api){this.device=device;this.api=api;}
    Qr create()throws Exception {
        JsonObject root=api.web("https://login-user.kugou.com/v2/qrcode",Map.of("clientver","1000","type","1","plat","4","qrcode_txt","https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=1014&"),device,cookies,null);
        JsonObject data=object(root,"data");String key=text(data,"qrcode"),image=text(data,"qrcode_img");
        if(number(root,"status")!=1||key.isBlank()||!image.startsWith("data:image/png;base64,")||image.length()>1400000)throw new IOException("酷狗二维码响应无效");
        byte[] png;try{png=Base64.getDecoder().decode(image.substring(image.indexOf(',')+1));}catch(IllegalArgumentException e){throw new IOException("酷狗二维码编码无效");}
        if(png.length<8||png.length>1024*1024||png[0]!=(byte)137||png[1]!='P'||png[2]!='N'||png[3]!='G')throw new IOException("酷狗二维码图像无效");
        return new Qr(key,png);
    }
    Poll poll(String key)throws Exception {
        JsonObject root=api.web("https://login-user.kugou.com/v2/get_userinfo_qrcode",Map.of("clientver","1000","plat","4","qrcode",key),device,cookies,null);
        if(number(root,"status")!=1)throw new IOException("酷狗扫码状态读取失败");
        JsonObject state=object(root,"data");int status=(int)number(state,"status");if(status!=4)return new Poll(status,null);
        JsonObject info=state.has("userinfo")?object(state,"userinfo"):state.has("user_info")?object(state,"user_info"):state;
        String id=text(info,"userid"),token=text(info,"token");if(id.isBlank())id=text(state,"userid");if(token.isBlank())token=text(state,"token");
        if(id.isBlank()||token.isBlank())throw new IOException("酷狗扫码凭证不完整");
        long now=System.currentTimeMillis();JsonObject exchanged=exchange(id,token,cookies);
        if(number(exchanged,"status")!=1)throw new IOException("酷狗登录会话交换失败，请刷新二维码");
        JsonObject data=object(exchanged,"data");String webToken=cookiePart("t");if(webToken.isBlank())webToken=text(data,"token");String webId=cookiePart("KugooID");if(webId.isBlank())webId=id;
        if(webToken.isBlank())throw new IOException("酷狗未返回可保存的登录会话");
        String nickname=text(data,"nickname");if(nickname.isBlank())nickname=text(info,"nickname");
        if(!webId.equals(id))throw new IOException("酷狗换票账号不一致");
        String refresh=text(data,"refresh_token","refreshToken");if(refresh.isEmpty())refresh=text(info,"refresh_token","refreshToken");if(refresh.isEmpty())refresh=text(state,"refresh_token","refreshToken");
        return new Poll(4,new KugouAccount(webId,webToken,device,nickname,KugouAccount.capture(cookies),dev.lzasn.cloudxmusic.client.gui.login.LoginMethod.QR,refresh,now,KugouProfile.parse(data)));
    }
    KugouAccount validate(KugouAccount account)throws Exception{
        var profile=api.profile(account);if(profile.nickname().isBlank()&&profile.avatarUrl().isBlank())throw new IOException("酷狗账号返回空资料，未接受登录");
        return new KugouAccount(account.userId(),account.token(),account.deviceId(),profile.nickname(),account.cookies(),account.method(),account.refreshToken(),account.refreshedAt(),profile);
    }
    /** Web 协议用当前 token 换票，不能把可选 refresh_token 当作另一类登录票据。 */
    KugouAccount renew(KugouAccount account)throws Exception{
        if(account.cookieLogin())return account;var jar=account.cookieManager();String previousCookie=cookiePart(jar,"t");var root=KugouApi.checked(exchange(account.userId(),account.token(),jar));var data=object(root,"data");
        String id=cookiePart(jar,"KugooID");if(id.isBlank())id=text(data,"userid");if(id.isBlank())id=account.userId();if(!id.equals(account.userId()))throw new IOException("酷狗续期账号不一致");
        String returnedCookie=cookiePart(jar,"t");String token=!returnedCookie.equals(previousCookie)?returnedCookie:text(data,"token");if(token.isBlank())throw new IOException("酷狗续期未返回会话");
        // 返回体换票时 CookieManager 仍可能持有旧 t；保存前同步，避免下一次请求带回旧登录态。
        if(!token.equals(returnedCookie))replaceTokenCookie(jar,id,token);
        String refresh=text(data,"refresh_token","refreshToken");if(refresh.isBlank())refresh=account.refreshToken();
        return validate(new KugouAccount(id,token,device,account.nickname(),KugouAccount.capture(jar),account.method(),refresh,System.currentTimeMillis(),account.profile()));
    }
    private JsonObject exchange(String id,String token,CookieManager jar)throws Exception{
        long now=System.currentTimeMillis();var params=new LinkedHashMap<>(KugouSigning.token(token,now));params.putAll(Map.of("clientver","20000","clienttime",Long.toString(now/1000),"dev","web","userid",id,"plat","4","clienttime_ms",Long.toString(now)));
        return api.web("https://loginservice.kugou.com/v1/login_by_token_get",params,device,jar,"");
    }
    private String cookiePart(String key){return cookiePart(cookies,key);}
    private static void replaceTokenCookie(CookieManager jar,String id,String token){
        String current=KugouApi.cookie(jar,"KuGoo");var parts=new ArrayList<String>();
        for(String part:current.split("&"))if(!part.isBlank()&&!part.startsWith("t=")&&!part.startsWith("KugooID="))parts.add(part);
        parts.add("t="+URLEncoder.encode(token,StandardCharsets.UTF_8));parts.add("KugooID="+id);
        for(var old:List.copyOf(jar.getCookieStore().getCookies()))if(old.getName().equals("KuGoo"))jar.getCookieStore().remove(null,old);
        var cookie=new HttpCookie("KuGoo",String.join("&",parts));cookie.setVersion(0);cookie.setDomain(".kugou.com");cookie.setPath("/");cookie.setSecure(true);jar.getCookieStore().add(URI.create("https://loginservice.kugou.com/"),cookie);
    }
    private static String cookiePart(CookieManager cookies,String key){for(String part:KugouApi.cookie(cookies,"KuGoo").split("&"))if(part.startsWith(key+"="))try{return URLDecoder.decode(part.substring(key.length()+1).replace("+","%2B"),StandardCharsets.UTF_8);}catch(IllegalArgumentException ignored){}return "";}
    @Override public void close(){api.close();cookies.getCookieStore().removeAll();}
}
