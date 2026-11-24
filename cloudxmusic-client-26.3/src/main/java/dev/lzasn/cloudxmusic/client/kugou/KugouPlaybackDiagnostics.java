package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 保留回包结构和错误文本；按请求上下文脱敏，防止服务端回显凭证。仅供后台取链诊断。 */
final class KugouPlaybackDiagnostics {
    private static final String REDACTED="[redacted]";
    private static final int MAX_LOG=16384, MAX_DEPTH=8, MAX_NODES=192;
    private static final Set<String> PRIVATE=Set.of("userid","kugooid","uid","uin","t","token","accesstoken","refreshtoken",
            "signature","key","accesskey","dfid","kgdfid","mid","kgmid","uuid","deviceid","cookie","setcookie",
            "authorization","password","passwd","secret","session","sessionid","openid","nickname","username");
    private KugouPlaybackDiagnostics(){}

    static String snapshot(JsonObject root){return snapshot(root,List.of());}
    static String snapshot(JsonObject root,Collection<String> requestSecrets){
        var secrets=new HashSet<>(requestSecrets);
        collect(root,secrets,0,new int[]{MAX_NODES});
        return bounded(new Redactor(secrets).copy(root,0));
    }

    static String request(String endpoint,Map<String,String> params,Map<String,List<String>> headers,String body){
        var redactor=new Redactor(secrets(params,headers));
        var safeParams=new LinkedHashMap<String,String>();
        params.forEach((key,value)->safeParams.put(key,privateField(key)?REDACTED:redactor.text(value)));
        var safe=new JsonObject();safe.addProperty("method",body==null?"GET":"POST");
        safe.addProperty("url",endpoint+(params.isEmpty()?"":"?"+KugouHttp.form(safeParams)));
        var safeHeaders=new JsonObject();
        headers.forEach((name,values)->{
            if(name.equalsIgnoreCase("Cookie")){
                var names=new JsonArray();
                values.stream().flatMap(value->Arrays.stream(value.split(";"))).limit(64)
                        .map(value->value.split("=",2)[0].trim()).filter(value->value.matches("[A-Za-z0-9_.-]{1,48}"))
                        .distinct().forEach(names::add);
                safeHeaders.add("Cookie-names-only",names);
            }else if(name.equalsIgnoreCase("Referer")&&values.equals(List.of("https://www.kugou.com/")))safeHeaders.addProperty(name,values.getFirst());
            else safeHeaders.addProperty(name,privateField(name)?REDACTED:redactor.text(String.join(",",values)));
        });
        safe.add("headers",safeHeaders);
        if(body==null)safe.add("payload",JsonNull.INSTANCE);
        else try{safe.add("payload",redactor.copy(JsonParser.parseString(body),0));}
        catch(RuntimeException ignored){safe.addProperty("payload","[non-json payload redacted]");}
        return bounded(safe);
    }

    static String response(byte[] bytes,Map<String,String> params,Map<String,List<String>> headers){
        try{return snapshot(JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject(),secrets(params,headers));}
        catch(RuntimeException ignored){return "{\"non_json_response\":true,\"bytes\":"+bytes.length+"}";}
    }

    private static Set<String> secrets(Map<String,String> params,Map<String,List<String>> headers){
        var secrets=new HashSet<String>();
        params.forEach((key,value)->{if(privateField(key))addSecret(secrets,value);});
        headers.forEach((name,values)->{
            if(!privateField(name))return;
            for(String value:values){
                addSecret(secrets,value);
                if(name.equalsIgnoreCase("Cookie"))for(String part:value.split("[;&]")){
                    int equals=part.indexOf('=');if(equals>=0){
                        String field=part.substring(0,equals).trim();
                        // a_id 是公开客户端标识；布尔标记不能当秘密匹配，否则会抹掉 status=1。
                        if(field.equalsIgnoreCase("a_id"))continue;
                        String raw=part.substring(equals+1).trim();addSecret(secrets,raw);
                        try{addSecret(secrets,URLDecoder.decode(raw,StandardCharsets.UTF_8));}catch(IllegalArgumentException ignored){}
                    }
                }
            }
        });
        return secrets;
    }

    private static boolean privateField(String key){
        String normalized=key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
        return PRIVATE.contains(normalized)||normalized.endsWith("token")||normalized.endsWith("signature")
                ||normalized.endsWith("cookie")||normalized.endsWith("password");
    }
    private static void addSecret(Set<String> secrets,String value){
        if(value!=null&&value.length()>=4&&!value.isBlank()&&value.length()<=16384)secrets.add(value);
    }
    private static void collect(JsonElement value,Set<String> secrets,int depth,int[] nodes){
        if(depth>MAX_DEPTH||nodes[0]--<=0||value==null||value.isJsonNull())return;
        if(value.isJsonObject())for(var entry:value.getAsJsonObject().entrySet()){
            if(privateField(entry.getKey())&&entry.getValue().isJsonPrimitive())addSecret(secrets,entry.getValue().getAsString());
            else collect(entry.getValue(),secrets,depth+1,nodes);
            if(nodes[0]<=0)break;
        }else if(value.isJsonArray())for(var child:value.getAsJsonArray()){
            collect(child,secrets,depth+1,nodes);if(nodes[0]<=0)break;
        }
    }
    private static String bounded(JsonElement value){
        String json=value.toString();
        if(json.length()<=MAX_LOG)return json;
        // 极端转义膨胀也不能产生无界或半截 JSON 日志。
        return "{\"truncated\":true,\"reason\":\"diagnostic-size-limit\"}";
    }

    private static final class Redactor {
        private final List<String> secrets;
        private int nodes=MAX_NODES,characters=6000;
        Redactor(Collection<String> secrets){this.secrets=secrets.stream().filter(Objects::nonNull).filter(s->!s.isEmpty())
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();}
        String text(String value){
            String safe=value;
            for(String secret:secrets)if(secret.length()>=4)safe=safe.replace(secret,REDACTED);
            else if(safe.equals(secret))safe=REDACTED;
            safe=safe.replaceAll("(?i)https?://[^\\s\"<>]+","[redacted-url]")
                    .replaceAll("(?i)(token|userid|dfid|signature|cookie|authorization)\\s*[=:]\\s*[^\\s,;]+","$1="+REDACTED)
                    .replaceAll("[\\p{Cntrl}]"," ");
            int limit=Math.max(0,Math.min(512,characters));
            if(safe.length()>limit)safe=safe.substring(0,limit)+"[truncated]";
            characters-=safe.length();return safe;
        }
        JsonElement copy(JsonElement value,int depth){
            if(value==null||value.isJsonNull())return JsonNull.INSTANCE;
            if(depth>MAX_DEPTH||nodes--<=0||characters<=0)return new JsonPrimitive("[truncated]");
            if(value.isJsonObject()){
                var result=new JsonObject();int fields=0;
                for(var entry:value.getAsJsonObject().entrySet()){
                    if(fields++>=48||nodes<=0||characters<=0){result.addProperty("_truncated",true);break;}
                    String key=text(entry.getKey());
                    result.add(key,privateField(entry.getKey())?new JsonPrimitive(REDACTED):copy(entry.getValue(),depth+1));
                }
                return result;
            }
            if(value.isJsonArray()){
                var result=new JsonArray();int items=0;
                for(var child:value.getAsJsonArray()){
                    if(items++>=48||nodes<=0||characters<=0){result.add("[truncated]");break;}
                    result.add(copy(child,depth+1));
                }
                return result;
            }
            var primitive=value.getAsJsonPrimitive();
            return secrets.contains(primitive.getAsString())?new JsonPrimitive(REDACTED):
                    primitive.isString()?new JsonPrimitive(text(primitive.getAsString())):
                    primitive.toString().length()<=32?primitive:new JsonPrimitive("[truncated-number]");
        }
    }
}
