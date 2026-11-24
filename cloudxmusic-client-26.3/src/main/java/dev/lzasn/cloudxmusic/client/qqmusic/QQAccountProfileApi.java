package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Duration;
import java.util.function.BooleanSupplier;

/** 已验证登录之后的可选只读资料请求，失败不撤销登录，不修改会话凭证。 */
final class QQAccountProfileApi {
    static QQAccountProfile load(QQCredentials account,BooleanSupplier active)throws IOException,InterruptedException{
        return load(account,active,QQAuthHttp::post);
    }
    static QQAccountProfile load(QQCredentials account,BooleanSupplier active,QQPlaybackApi.Transport transport)throws IOException,InterruptedException{
        QQPlaybackApi api=new QQPlaybackApi(account,transport,active,Duration.ofSeconds(20));
        QQAccountProfile profile=account.profile();
        if(profile.avatarUrl().isBlank()){
            try{
                JsonObject user=QQPlaybackApi.requireData(api.call("music.UserInfo.userInfoServer","GetLoginUserInfo",new JsonObject(),false));
                QQAccountProfile fresh=QQAccountProfile.login(user);
                if(!fresh.avatarUrl().isBlank())profile=new QQAccountProfile(fresh.avatarUrl(),profile.membership(),profile.level());
            }catch(InterruptedException e){throw e;}
            catch(IOException e){org.slf4j.LoggerFactory.getLogger(QQAccountProfileApi.class).debug("QQ 头像资料刷新暂不可用：{}",e.getClass().getSimpleName());}
        }
        JsonObject data=QQPlaybackApi.requireData(api.call("VipLogin.VipLoginInter","vip_login_base",new JsonObject(),true));
        if(!data.has("identity")||!data.get("identity").isJsonObject())throw new IOException("QQ 会员资料缺少 identity");
        return profile.withMembership(data);
    }
}
