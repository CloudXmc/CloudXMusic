package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;

final class KugouJson {
    private KugouJson(){}
    static String text(JsonObject object,String... keys){
        if(object==null)return "";
        for(String key:keys){JsonElement value=object.get(key);if(value!=null&&value.isJsonPrimitive())return value.getAsString();}
        return "";
    }
    static long number(JsonObject object,String... keys){try{return Long.parseLong(text(object,keys));}catch(NumberFormatException e){return 0;}}
    static JsonObject object(JsonObject object,String key){JsonElement value=object==null?null:object.get(key);return value!=null&&value.isJsonObject()?value.getAsJsonObject():new JsonObject();}
    static JsonArray array(JsonObject object,String key){JsonElement value=object==null?null:object.get(key);return value!=null&&value.isJsonArray()?value.getAsJsonArray():new JsonArray();}
}
