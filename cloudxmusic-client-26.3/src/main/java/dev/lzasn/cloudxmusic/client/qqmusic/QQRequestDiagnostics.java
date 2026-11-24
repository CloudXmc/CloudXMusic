package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import java.net.URI;
import java.util.*;

/** 仅记录必要的 QQ 音频请求诊断；凭证、用户身份、签名 URL 不进入日志。 */
final class QQRequestDiagnostics {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("CloudXMusic-QQ-Diagnostics");
    private static final Set<String> PRIVATE=Set.of("authst","musickey","music_key","qqmusic_key","qm_keyst","token","cookie","skey","p_skey","vkey","uin","qqmusic_uin","musicid","str_musicid","encrypt_uin","g_tk","g_tk_new_20200303");
    static boolean enabled(JsonObject request){
        var block=request.getAsJsonObject("req_0");if(block==null)return false;
        String method=QQAuthHttp.text(block,"method");
        var p=block.getAsJsonObject("param");return p!=null&&p.has("filename")&&p.get("filename").toString().contains("AI00");
    }
    static void response(URI endpoint,JsonObject request,int status,byte[] bytes){
        if(!enabled(request))return;String body;
        try{body=json(QQAuthHttp.parse(bytes));}catch(Exception e){body="<non-JSON body omitted; bytes="+bytes.length+">";}
        LOG.info("[QQ请求] URL={} HTTP={} request={} response={}",url(endpoint.toASCIIString()),status,json(request),body);
    }
    static void audio(URI uri,int status,byte[] bytes){
        if(uri.getPath()!=null&&uri.getPath().contains("AI00"))LOG.info("[QQ母带] URL={} HTTP={} prefixHex={}",url(uri.toASCIIString()),status,HexFormat.of().formatHex(Arrays.copyOf(bytes,Math.min(bytes.length,42))));
    }
    static String json(JsonElement value){String text=clean(value).toString();return text.length()>16384?text.substring(0,16384)+"…<truncated>":text;}
    private static JsonElement clean(JsonElement value){
        if(value.isJsonObject()){var out=new JsonObject();value.getAsJsonObject().entrySet().forEach(e->out.add(e.getKey(),PRIVATE.contains(e.getKey().toLowerCase(Locale.ROOT))||Set.of("ekey","login_key","servercheck","openid").contains(e.getKey().toLowerCase(Locale.ROOT))?new JsonPrimitive("<redacted>"):clean(e.getValue())));return out;}
        if(value.isJsonArray()){var out=new JsonArray();value.getAsJsonArray().forEach(v->out.add(clean(v)));return out;}
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString())return new JsonPrimitive(url(value.getAsString()));
        return value.deepCopy();
    }
    private static String url(String text){int q=text.indexOf('?');return (q>=0?text.substring(0,q)+"?<redacted-query>":text).replaceAll("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b","<redacted-ip>");}
}
