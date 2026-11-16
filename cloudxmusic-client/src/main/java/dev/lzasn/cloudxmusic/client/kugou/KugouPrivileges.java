package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.util.*;
import static dev.lzasn.cloudxmusic.client.kugou.KugouJson.*;

/** 仅解释服务返回的权益，不按音质或本地会员状态推导播放权。未知档位不画标签。 */
public record KugouPrivileges(Map<KugouQuality,String> badges) {
    public KugouPrivileges{badges=Map.copyOf(badges);}
    public static KugouPrivileges empty(){return new KugouPrivileges(Map.of());}
    public String badge(KugouQuality quality){return badges.getOrDefault(quality,"");}
    public KugouPrivileges merge(KugouPrivileges newer){var copy=new EnumMap<KugouQuality,String>(KugouQuality.class);copy.putAll(badges);copy.putAll(newer.badges);return new KugouPrivileges(copy);}
    public static KugouPrivileges parse(JsonObject root){
        var result=new EnumMap<KugouQuality,String>(KugouQuality.class);collect(root,result,0);return new KugouPrivileges(result);
    }
    private static void collect(JsonElement element,Map<KugouQuality,String> result,int depth){
        if(element==null||depth>5)return;
        if(element.isJsonArray()){for(var value:element.getAsJsonArray())collect(value,result,depth+1);return;}
        if(!element.isJsonObject())return;var row=element.getAsJsonObject();String quality=text(row,"quality");
        for(var q:KugouQuality.values())if(q.apiValue().equals(quality)){String badge=requirement(row);if(badge!=null)result.put(q,badge);}
        for(String field:List.of("data","relate_goods","qualities","resource","privileges","quality_vip"))if(row.has(field))collect(row.get(field),result,depth+1);
    }
    static KugouPrivileges observation(JsonObject root,KugouQuality quality){
        var values=parse(root);JsonObject data=root.has("data")&&root.get("data").isJsonObject()?object(root,"data"):root;
        String badge=requirement(data);if(badge==null)badge=requirement(root);
        return badge==null?values:values.merge(new KugouPrivileges(Map.of(quality,badge)));
    }
    private static String requirement(JsonObject row){
        String type=text(row,"require_vip_type","required_vip_type","need_vip_type").toLowerCase(Locale.ROOT);
        if(Set.of("svip","suvip","super_vip","6").contains(type))return "SVIP";
        if(Set.of("vip","dvip","concept","1").contains(type))return "VIP";
        if(flag(row,"need_svip","need_super_vip"))return "SVIP";
        if(flag(row,"need_vip"))return "VIP";
        if(row.has("privilege")&&row.get("privilege").isJsonPrimitive()){long p=number(row,"privilege");return p<0?null:p==0?"":"VIP";}
        if(row.has("need_vip")||row.has("need_svip"))return "";
        return null;
    }
    static boolean permissionDenied(JsonObject root){
        // privilege 是资源权益/标签字段，不能单独证明当前请求被拒绝。
        // 只有服务端明确返回拒绝码、拒绝标志或拒绝文案时，才允许触发 VIP 降级提示。
        if(explicitPermissionDenied(root))return true;
        JsonObject data=root.has("data")&&root.get("data").isJsonObject()?object(root,"data"):root;
        // 403 + 当前资源的正 privilege 值表示该音质被权限挡住；没有资源权益字段
        // 的裸 403 仍按普通受限/风控错误处理，避免误报 VIP。
        return (KugouApi.code(root)==403||KugouApi.code(data)==403)&&data.has("privilege")&&number(data,"privilege")>0;
    }
    static boolean explicitPermissionDenied(JsonObject root){
        JsonObject data=root.has("data")&&root.get("data").isJsonObject()?object(root,"data"):root;
        String message=(text(root,"message","msg","error","error_msg")+text(data,"message","msg","error","error_msg")).toLowerCase(Locale.ROOT);
        // 403 单独出现时也可能是路由、设备或临时风控错误；必须同时有明确的
        // 权限字段或人类可读拒绝文案，才能把它归类为 VIP 权限拒绝。
        return flag(data,"no_privilege","no_permission")||message.contains("权限不足")||message.contains("无播放权限")||message.contains("暂无播放资格")||message.contains("无试听资格")||message.contains("需要vip")||message.contains("需要会员")||message.contains("permission denied");
    }
    static boolean wholeSongDenied(JsonObject root){
        var data=root.has("data")&&root.get("data").isJsonObject()?object(root,"data"):root;
        String message=text(root,"message","msg","error","error_msg")+text(data,"message","msg","error","error_msg");
        return flag(data,"song_locked","no_trial_privilege","no_listen_privilege")||flag(root,"song_locked","no_trial_privilege","no_listen_privilege")||message.contains("无试听资格")||message.contains("整首歌无权限");
    }
    /**
     * 只返回固定的、脱敏的分类名称供诊断日志使用；不把响应正文、凭证或播放地址写入日志。
     */
    static String denialReason(JsonObject root){
        if(root==null)return "";
        if(wholeSongDenied(root))return "whole_song_locked";
        if(!explicitPermissionDenied(root)){
            long code=KugouApi.code(root);
            return code==0?"":"server_code_"+code;
        }
        JsonObject data=root.has("data")&&root.get("data").isJsonObject()?object(root,"data"):root;
        if(KugouApi.code(root)==403||KugouApi.code(data)==403)return "permission_code_403";
        if(flag(data,"no_privilege","no_permission"))return "permission_flag";
        return "permission_message";
    }
    private static boolean flag(JsonObject row,String... keys){for(String key:keys)if("true".equalsIgnoreCase(text(row,key))||number(row,key)>0)return true;return false;}
}
