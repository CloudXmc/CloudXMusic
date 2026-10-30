package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** 非敏感的只读账号展示资料；会员等级只能来自明确的会员身份字段。 */
public record QQAccountProfile(String avatarUrl,Membership membership,int level) {
    public enum Membership {NONE,VIP,SVIP}
    public static final QQAccountProfile EMPTY=new QQAccountProfile("",Membership.NONE,0);
    public QQAccountProfile {
        avatarUrl=safeAvatar(avatarUrl);Objects.requireNonNull(membership);
        level=Math.max(0,Math.min(99,level));if(membership==Membership.NONE)level=0;
    }
    public String badge(){return membership==Membership.NONE?"":membership.name()+(level>0?level:"");}
    static QQAccountProfile login(JsonObject data){
        String avatar="";
        for(JsonObject block:blocks(data))for(String name:new String[]{"headurl","headUrl","headpic","headPic","avatar_url","logo","avatarUrl","avatar"}){
            String value=QQAuthHttp.text(block,name);if(avatar.isEmpty())avatar=safeAvatar(value);
        }
        if(avatar.isEmpty())for(JsonObject block:blocks(data))for(String name:new String[]{"uin","qq_uin","qq","qqnum"}){
            String uin=QQAuthHttp.text(block,name);
            // musicid 是音乐账号身份，不能无条件当成 QQ 号。
            if(avatar.isEmpty()&&uin.matches("[1-9][0-9]{4,11}"))avatar="https://q1.qlogo.cn/g?b=qq&nk="+uin+"&s=100";
        }
        return new QQAccountProfile(avatar,Membership.NONE,0);
    }
    QQAccountProfile withMembership(JsonObject data){
        JsonObject identity=data.has("identity")&&data.get("identity").isJsonObject()?data.getAsJsonObject("identity"):new JsonObject();
        boolean svip=positive(data,"svip"),vip=positive(identity,"vip")||positive(identity,"HugeVip");
        // score、music_level、yearflag 不代表当前有效会员，更不能直接当作会员等级。
        int level=number(identity,"level");
        return new QQAccountProfile(avatarUrl,svip?Membership.SVIP:vip?Membership.VIP:Membership.NONE,level);
    }
    private static boolean positive(JsonObject o,String key){return number(o,key)>0;}
    private static int number(JsonObject o,String key){
        try{JsonElement v=o.get(key);if(v==null||!v.isJsonPrimitive()||v.getAsJsonPrimitive().isBoolean())return 0;return v.getAsBigDecimal().intValueExact();}
        catch(RuntimeException e){return 0;}
    }
    private static List<JsonObject> blocks(JsonObject data){
        List<JsonObject> result=new ArrayList<>();if(data==null)return result;result.add(data);
        for(String key:new String[]{"info","userInfo","userinfo","base"})if(data.has(key)&&data.get(key).isJsonObject()){
            JsonObject child=data.getAsJsonObject(key);result.add(child);
            if(child.has("base")&&child.get("base").isJsonObject())result.add(child.getAsJsonObject("base"));
        }
        return result;
    }
    static String safeAvatar(String value){
        if(value==null||value.length()>4096)return "";
        try{
            value=value.strip();if(value.startsWith("//"))value="https:"+value;
            if(value.startsWith("http://"))value="https:"+value.substring(5);
            URI uri=URI.create(value);return QQArtworkCache.allowed(uri)?uri.toASCIIString():"";
        }catch(IllegalArgumentException e){return "";}
    }
}
