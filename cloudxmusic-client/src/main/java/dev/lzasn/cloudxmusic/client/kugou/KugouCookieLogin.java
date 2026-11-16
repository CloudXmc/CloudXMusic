package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.gui.login.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 普通 token/userid 和官方 Web KuGoo 复合 Cookie 均可导入。 */
public final class KugouCookieLogin {
    private KugouCookieLogin(){}
    public static KugouAccount parse(String raw,String device){
        var cookies=LoginCookies.parse(raw);var web=new HashMap<String,String>();
        for(String part:cookies.getOrDefault("KuGoo","").split("&")){int p=part.indexOf('=');if(p>0)try{String key=part.substring(0,p),value=URLDecoder.decode(part.substring(p+1).replace("+","%2B"),StandardCharsets.UTF_8);String old=web.putIfAbsent(key,value);if(old!=null&&!old.equals(value))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new IllegalArgumentException("KuGoo Cookie 格式无效");}}
        String id=cookies.getOrDefault("userid",web.getOrDefault("KugooID","")),token=cookies.getOrDefault("token",web.getOrDefault("t",""));
        if(id.isBlank()||token.isBlank())throw new IllegalArgumentException("Cookie 缺少 token 或 userid，请复制完整 Cookie");
        if(web.containsKey("KugooID")&&!web.get("KugooID").equals(id)||web.containsKey("t")&&!web.get("t").equals(token))throw new IllegalArgumentException("Cookie 的账号凭证不一致，请重新复制");
        if(!id.matches("[1-9][0-9]{0,19}"))throw new IllegalArgumentException("Cookie 的 userid 无效");
        return new KugouAccount(id,token,device,"",cookies.entrySet().stream().map(e->new KugouAccount.Cookie(e.getKey(),e.getValue(),".kugou.com","/",true,0)).toList(),LoginMethod.COOKIE,"",0,KugouProfile.empty());
    }
}
