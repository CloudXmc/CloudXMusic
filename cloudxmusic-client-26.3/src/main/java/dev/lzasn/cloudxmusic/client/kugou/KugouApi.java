package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.math.BigInteger;
import java.net.CookieManager;
import java.util.*;

/** 固定 API 与签名装配，不持有全局账号；所有方法由调用方后台执行。 */
final class KugouApi implements AutoCloseable {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("CloudXMusic/Kugou");
    // 普通版协议按路由区分浏览器 Web 会话和 Android 网关客户端。
    static final String WEB_USER_AGENT="Mozilla/5.0";
    // /v5/url 使用浏览器头；仅资料/权益路由显式使用 Android UA，不能混用客户端身份。
    static final String PLAYBACK_USER_AGENT="Mozilla/5.0";
    static final String GATEWAY_USER_AGENT="Android15-AndroidPhone-20489-46-0-wifi";
    private final KugouTransport transport;
    private final java.util.function.LongSupplier clock;
    KugouApi(){this(new KugouHttp());}
    KugouApi(KugouTransport transport){this(transport,System::currentTimeMillis);}
    KugouApi(KugouTransport transport,java.util.function.LongSupplier clock){this.transport=Objects.requireNonNull(transport);this.clock=Objects.requireNonNull(clock);}
    JsonObject search(String keyword,int page,int size)throws Exception {
        return transport.request("https://msearchretry.kugou.com/api/v3/search/song",Map.of("format","json","page",Integer.toString(page),"pagesize",Integer.toString(size),"showtype","1","keyword",keyword),Map.of(),null,new CookieManager());
    }
    JsonObject web(String endpoint,Map<String,String> values,String device,CookieManager cookies,String body)throws Exception {
        return transport.request(endpoint,KugouSigning.web(values,device,clock.getAsLong()),Map.of("User-Agent",WEB_USER_AGENT),body,cookies);
    }
    JsonObject quality(KugouTrack song,KugouAccount account,KugouQuality quality,boolean metadata)throws Exception {
        if(quality!=KugouQuality.HQ&&quality!=KugouQuality.STANDARD)throw new IllegalArgumentException("酷狗 App 取链只支持 320 和 128 档位");
        String hash=song.song().platformId().toLowerCase(Locale.ROOT),mid=new BigInteger(KugouSigning.md5(account.deviceId()),16).toString();
        CookieManager cookies=account.cookieManager();
        String now=Long.toString(clock.getAsLong()/1000),dfid=accountCookie(cookies,"kg_dfid","dfid");if(dfid.isBlank()||dfid.equals("-"))dfid="-";
        Map<String,String> params=new LinkedHashMap<>();
        params.putAll(Map.of("appid","1005","clientver","11430","clienttime",now,"mid",mid,"uuid",account.deviceId(),"dfid",dfid,"userid",account.userId(),"token",account.token(),"hash",hash,"quality",quality.apiValue()));
        params.put("album_id",metadata&&!song.albumId().isBlank()?song.albumId():"0");
        params.put("album_audio_id",metadata&&!song.albumAudioId().isBlank()?song.albumAudioId():"0");
        params.putAll(Map.of("area_code","1","ssa_flag","is_fromtrack","version","11430","page_id","151369488","ppage_id","463467626,350369493,788954147","behavior","play","pid","2","cmd","26","pidversion","3001"));
        params.putAll(Map.of("IsFreePart","0","cdnBackup","1","module",""));
        params.put("key",KugouSigning.md5(hash+"57ae12eb6890223e355ccfcb74edf70d"+"1005"+mid+account.userId()));
        params.put("signature",KugouSigning.signature(params,false));
        JsonObject response=transport.request("https://gateway.kugou.com/v5/url",params,Map.of("User-Agent",PLAYBACK_USER_AGENT,"x-router","trackercdn.kugou.com","mid",mid,"dfid",dfid,"clienttime",now,"kg-rc","1","kg-rec","1","kg-thash","5d816a0","kg-rf","B9EDA08A64250DEFFBCADDEE00F8F25F"),null,cookies);
        logPlayback("app",quality.apiValue(),response,song,account.loggedIn(),hasDfid(dfid));return response;
    }
    JsonObject webSongInfo(KugouTrack song,KugouAccount account,boolean metadata)throws Exception {
        CookieManager cookies=account.cookieManager();String dfid=accountCookie(cookies,"kg_dfid","dfid");if(dfid.isBlank()||dfid.equals("-"))dfid="-";
        var values=new LinkedHashMap<String,String>();
        // 与 KugouWeb.songInfo 一致：Web 签名覆盖原始规范 hash；只有 App 路由转小写。
        values.put("hash",song.song().platformId());
        values.put("album_id",metadata?song.albumId():"");
        values.put("platid","4");values.put("userid",account.userId());values.put("token",account.token());values.put("dfid",dfid);
        JsonObject response=web("https://wwwapi.kugou.com/play/songinfo",values,
                account.deviceId(),cookies,null);
        logPlayback("web","account",response,song,account.loggedIn(),hasDfid(dfid));return response;
    }
    private static void logPlayback(String route,String quality,JsonObject root,KugouTrack song,boolean authenticated,boolean dfidPresent){
        JsonObject data=root.has("data")&&root.get("data").isJsonObject()?root.getAsJsonObject("data"):root;
        long status=KugouJson.number(root,"status");if(status==0)status=KugouJson.number(data,"status");
        long error=code(root);if(error==0)error=code(data);
        int urlCount=urlCount(data);
        LOG.info("Kugou playback route={} quality={} status={} code={} hasUrl={} urlCount={} authenticated={} dfidPresent={} hashPresent={} albumIdPresent={} albumAudioIdPresent={} ext={} bitrate={} durationMs={} privilege={} needVip={} needSvip={} required={} reason={}",
                route,quality,status,error,urlCount>0,urlCount,authenticated,dfidPresent,!song.song().platformId().isBlank(),!song.albumId().isBlank(),!song.albumAudioId().isBlank(),safeFormat(KugouJson.text(data,"extName","extname")),KugouJson.number(data,"bitrate","bitRate"),KugouPlaybackResolver.durationMillis(data,song,route),KugouJson.number(data,"privilege","Privilege"),
                flag(data,"need_vip","needVip"),flag(data,"need_svip","needSvip"),
                safeRequired(required(data)),KugouPrivileges.denialReason(root));
    }
    private static int urlCount(JsonObject row){
        int count=0;
        for(String key:List.of("url","play_url","backup_url","play_backup_url")){
            JsonElement value=row.get(key);if(value==null||value.isJsonNull())continue;
            if(value.isJsonArray()){
                for(JsonElement item:value.getAsJsonArray())if(item.isJsonPrimitive()&&!item.getAsString().isBlank())count++;
            }else if(value.isJsonPrimitive()&&!value.getAsString().isBlank())count+=value.getAsString().split(",\\s*(?=https?://)").length;
        }
        return Math.min(16,count);
    }
    private static String safeFormat(String value){
        String format=value.toLowerCase(Locale.ROOT);
        return Set.of("mp3","flac","ogg","vorbis","wav","aac").contains(format)?format:"unknown";
    }
    private static String safeRequired(String value){
        String requirement=value.toLowerCase(Locale.ROOT);
        return Set.of("vip","dvip","svip","suvip","super_vip","concept","1","2","6").contains(requirement)?requirement:"unknown";
    }
    JsonObject lyricSearch(KugouTrack track)throws Exception {
        return transport.request("https://lyrics.kugou.com/search",Map.of("ver","1","man","yes","client","pc","hash",track.song().platformId(),"duration",Long.toString(track.durationSeconds()*1000L)),Map.of(),null,new CookieManager());
    }
    /** Web token 对云歌单使用匹配的 H5 签名；POST 签名必须包含原始 JSON。 */
    JsonObject cloud(String path,Map<String,String> values,JsonObject body,KugouAccount account)throws Exception{
        var params=new LinkedHashMap<>(Map.of("appid","1014","srcappid","2919","clientver","20000","clienttime",Long.toString(System.currentTimeMillis()),"mid",account.deviceId(),"uuid",account.deviceId(),"dfid","-","userid",account.userId(),"token",account.token()));
        params.putAll(values);String text=body==null?"":body.toString();params.put("signature",KugouSigning.signature(params,text,true));
        return transport.request("https://gateway.kugou.com"+path,params,Map.of("User-Agent",WEB_USER_AGENT,"x-router","cloudlist.service.kugou.com"),body==null?null:text,account.cookieManager());
    }
    JsonObject app(String endpoint,String router,Map<String,String> values,JsonObject body,KugouAccount account)throws Exception{
        String mid=new BigInteger(KugouSigning.md5(account.deviceId()),16).toString(),now=Long.toString(System.currentTimeMillis()/1000);
        var params=new LinkedHashMap<>(Map.of("appid","1005","clientver","20489","clienttime",now,"mid",mid,"uuid",account.deviceId(),"dfid","-","userid",account.userId(),"token",account.token()));params.putAll(values);
        String text=body==null?"":body.toString();params.put("signature",KugouSigning.signature(params,text,false));
        var headers=new LinkedHashMap<>(Map.of("User-Agent",GATEWAY_USER_AGENT,"mid",mid,"clienttime",now));if(!router.isBlank())headers.put("x-router",router);
        return transport.request(endpoint,params,headers,body==null?null:text,account.cookieManager());
    }
    KugouProfile profile(KugouAccount account)throws Exception{
        var body=new JsonObject();long now=System.currentTimeMillis()/1000;body.addProperty("visit_time",now);body.addProperty("usertype",1);body.addProperty("userid",new BigInteger(account.userId()));body.addProperty("p",KugouSigning.profileToken(account.token(),now).toUpperCase(Locale.ROOT));
        var identity=checked(app("https://gateway.kugou.com/v3/get_my_info","usercenter.kugou.com",Map.of("plat","1"),body,account));
        var info=KugouJson.object(identity,"data");if(info.has("userinfo"))info=KugouJson.object(info,"userinfo");else if(info.has("user_info"))info=KugouJson.object(info,"user_info");String id=KugouJson.text(info,"userid","user_id");if(!id.isBlank()&&!id.equals(account.userId()))throw new java.io.IOException("酷狗账号资料身份不一致");
        var profile=KugouProfile.parse(identity);
        try{var vip=checked(app("https://kugouvip.kugou.com/v1/get_union_vip","",Map.of("busi_type","concept"),null,account));profile=profile.merge(KugouProfile.parse(vip));}catch(InterruptedException e){throw e;}catch(Exception ignored){}
        return profile;
    }
    KugouPrivileges privileges(KugouTrack track,KugouAccount account)throws Exception{
        var resource=new JsonObject();resource.addProperty("type","audio");resource.addProperty("hash",track.song().platformId().toLowerCase(Locale.ROOT));resource.addProperty("album_id",track.albumId().isBlank()?"0":track.albumId());resource.addProperty("page_id",0);
        var body=new JsonObject();body.addProperty("appid",1005);body.addProperty("clientver",20489);body.addProperty("area_code",1);body.addProperty("behavior","play");body.addProperty("relate",1);body.addProperty("need_hash_offset",1);body.addProperty("support_verify",1);var resources=new JsonArray();resources.add(resource);body.add("resource",resources);var qualities=new JsonArray();for(var q:KugouQuality.values())qualities.add(q.apiValue());body.add("qualities",qualities);
        return KugouPrivileges.parse(checked(app("https://gateway.kugou.com/v2/get_res_privilege/lite","media.store.kugou.com",Map.of(),body,account)));
    }
    static JsonObject checked(JsonObject root)throws java.io.IOException{
        if(KugouJson.number(root,"status")!=1){long code=code(root);if(code==30020)throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.VERIFICATION_REQUIRED);if(code==20017)throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.LOGIN_REQUIRED);throw new java.io.IOException("酷狗账号接口请求失败");}return root;
    }
    JsonObject lyricDownload(String id,String key,String format)throws Exception {
        return transport.request("https://lyrics.kugou.com/download",Map.of("ver","1","client","pc","fmt",format,"charset","utf8","id",id,"accesskey",key),Map.of(),null,new CookieManager());
    }
    static String cookie(CookieManager cookies,String name){return cookies.getCookieStore().getCookies().stream().filter(c->c.getName().equals(name)&&!c.hasExpired()).map(java.net.HttpCookie::getValue).findFirst().orElse("");}
    private static String accountCookie(CookieManager cookies,String... names){for(String name:names){String value=cookie(cookies,name);if(!value.isBlank())return value;}return "";}
    private static boolean hasDfid(String value){return value!=null&&!value.isBlank()&&!value.equals("-");}
    private static boolean flag(JsonObject row,String... names){for(String name:names){if("true".equalsIgnoreCase(KugouJson.text(row,name))||KugouJson.number(row,name)>0)return true;}return false;}
    private static String required(JsonObject row){String value=KugouJson.text(row,"require_vip_type","required_vip_type","need_vip_type");return value.isBlank()?"":value.length()>32?value.substring(0,32):value;}
    static long code(JsonObject root){for(String key:List.of("error_code","err_code","errcode")){long value=KugouJson.number(root,key);if(value!=0)return value;}return 0;}
    @Override public void close(){if(transport instanceof AutoCloseable closeable)try{closeable.close();}catch(Exception ignored){}}
}
