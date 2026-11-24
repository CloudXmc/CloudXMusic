package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import java.io.IOException;

/** 只判断播放付费标志，不用下载付费或账号会员等级推断歌曲 VIP。 */
final class QQVip {
    private QQVip(){}
    static boolean parse(JsonObject song)throws IOException{
        boolean vip=flag(song.get("vip"));
        JsonElement pay=song.get("pay");
        if(pay==null||pay.isJsonNull())return vip;
        if(!pay.isJsonObject())throw new IOException("QQ 歌曲 pay 必须是对象");
        return vip|flag(pay.getAsJsonObject().get("play"))|flag(pay.getAsJsonObject().get("pay_play"));
    }
    private static boolean flag(JsonElement value)throws IOException{
        if(value==null||value.isJsonNull())return false;
        if(!value.isJsonPrimitive())throw new IOException("QQ 歌曲 VIP 标记格式无效");
        JsonPrimitive p=value.getAsJsonPrimitive();
        if(p.isBoolean())return p.getAsBoolean();
        try{long n=p.getAsBigDecimal().longValueExact();if(n<0)throw new ArithmeticException();return n>0;}
        catch(NumberFormatException|ArithmeticException e){throw new IOException("QQ 歌曲 VIP 标记不是有效非负整数",e);}
    }
}
