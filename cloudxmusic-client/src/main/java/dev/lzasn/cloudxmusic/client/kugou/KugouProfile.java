package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.util.*;
import static dev.lzasn.cloudxmusic.client.kugou.KugouJson.*;

/** 会员仅用于账号展示，绝不作为音质取链的门禁。 */
public record KugouProfile(String nickname,String avatarUrl,String badge) {
    public static KugouProfile empty(){return new KugouProfile("","","");}
    public KugouProfile merge(KugouProfile other){return new KugouProfile(other.nickname.isBlank()?nickname:other.nickname,other.avatarUrl.isBlank()?avatarUrl:other.avatarUrl,other.badge);}
    public static KugouProfile parse(JsonObject root){
        var data=root.has("data")&&root.get("data").isJsonObject()?object(root,"data"):root;
        var info=data.has("userinfo")?object(data,"userinfo"):data.has("user_info")?object(data,"user_info"):data;
        String name=text(info,"nickname","nick_name","nickName"),avatar=text(info,"pic","avatar","avatar_url","headurl","head_pic").replace("{size}","300");
        if(avatar.startsWith("//"))avatar="https:"+avatar;
        if(!avatar.startsWith("https://")&&!avatar.startsWith("http://"))avatar="";
        String badge=membership(info);if(badge.isEmpty())badge=membership(data);
        if(data.has("busi_vip"))badge=memberships(data.get("busi_vip"),badge,0);
        return new KugouProfile(name,avatar,badge);
    }
    private static String memberships(JsonElement value,String badge,int depth){
        if(depth>4||value==null)return badge;
        if(value.isJsonArray())for(var child:value.getAsJsonArray())badge=memberships(child,badge,depth+1);
        if(value.isJsonObject()){String found=membership(value.getAsJsonObject());if(found.equals("SVIP")||badge.isEmpty())badge=found;for(var child:value.getAsJsonObject().entrySet())if(child.getValue().isJsonObject()||child.getValue().isJsonArray())badge=memberships(child.getValue(),badge,depth+1);}
        return badge;
    }
    private static String membership(JsonObject row){
        if(row.has("is_vip")&&number(row,"is_vip")==0)return "";
        long expiry=number(row,"expire_time","vip_end_time","vip_expire_time");if(expiry>0&&(expiry>100000000000L?expiry:expiry*1000)<=System.currentTimeMillis())return "";
        String type=text(row,"vip_type","product_type").toLowerCase(Locale.ROOT);
        if(Set.of("svip","suvip","super_vip").contains(type)||number(row,"is_svip","is_super_vip")>0)return "SVIP";
        if(number(row,"vip_type","is_vip","is_dvip")>0||Set.of("vip","dvip","svip").contains(type))return "VIP";
        return "";
    }
}
