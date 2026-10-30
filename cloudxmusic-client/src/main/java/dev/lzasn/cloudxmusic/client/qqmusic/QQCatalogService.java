package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** 单个目录任务的网络上下文，绑定凭证快照；公网资料请求不携带 Cookie。 */
final class QQCatalogService implements QQCatalogController.DataSource {
    private final QQCredentials credentials;
    private final BooleanSupplier active;
    private final QQPlaybackApi authenticated;
    private String encrypted="";
    QQCatalogService(QQCredentials credentials,BooleanSupplier active){
        this.credentials=credentials;this.active=active;
        authenticated=credentials==null?null:new QQPlaybackApi(credentials,QQAuthHttp::post,active,Duration.ofSeconds(45));
    }
    public QQCatalogApi api(){return new QQCatalogApi(this::call,credentials==null?"":credentials.userId(),this::profileId);}
    private JsonObject call(String module,String method,JsonObject p)throws IOException,InterruptedException{
        check();if(authenticated!=null)return QQPlaybackApi.requireData(authenticated.call(module,method,p,false));
        JsonObject root=QQAuthHttp.post(QQAuthHttp.request(module,method,p),QQAuthHttp.emptyCookies(),Duration.ofSeconds(15));check();
        if(QQAuthHttp.integer(root,"code")!=0)throw new IOException("QQ 推荐服务拒绝请求");
        JsonObject block=QQAuthHttp.object(root,"req_0");if(QQAuthHttp.integer(block,"code")!=0)throw new IOException("QQ 推荐业务请求失败");
        return QQAuthHttp.object(block,"data");
    }
    public QQCatalogApi.Playlist daily()throws IOException,InterruptedException{
        if(credentials==null)throw new IOException("请先登录 QQ 音乐后读取每日推荐");
        return QQDailyDiscovery.parse(get(URI.create("https://c.y.qq.com/node/musicmac/v6/index.html"),true));
    }
    private String profileId()throws IOException,InterruptedException{
        if(!encrypted.isBlank())return encrypted;
        JsonObject data=call("music.UserInfo.userInfoServer","GetLoginUserInfo",new JsonObject());encrypted=findProfileId(data);
        if(encrypted.isBlank()){
            JsonObject root=QQAuthHttp.parse(get(URI.create("https://c6.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg?format=json&cid=205360838&ct=20&cv=4747474&userid="+credentials.userId()),false).getBytes(StandardCharsets.UTF_8));
            encrypted=findProfileId(root);
        }
        if(encrypted.isBlank())throw new IOException("QQ 私人歌单缺少加密用户标识，请重新登录后重试");return encrypted;
    }
    static String findProfileId(JsonObject data){
        String direct=QQCatalogApi.text(data,"encrypt_uin","encryptUin","encryptedUin");if(!direct.isBlank()&&direct.length()<=256)return direct;
        for(String key:new String[]{"info","base","data","creator"})if(data.has(key)&&data.get(key).isJsonObject()){String child=findProfileId(data.getAsJsonObject(key));if(!child.isBlank())return child;}return "";
    }
    private String get(URI uri,boolean cookie)throws IOException,InterruptedException{
        check();HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try{
            var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36").header("Referer","https://y.qq.com/").header("Accept","text/html,application/json;q=0.9").header("Accept-Encoding","identity");
            if(cookie)request.header("Cookie","qqmusic_uin="+credentials.userId()+"; qqmusic_key="+credentials.musicKey()+"; login_type="+(credentials.loginType()==1?"2":"1"));
            pending=http.sendAsync(request.GET().build(),info->new QQAuthHttp.LimitedBody());var response=pending.get(15,TimeUnit.SECONDS);check();
            if(response.statusCode()!=200)throw new IOException("QQ 推荐页面 HTTP "+response.statusCode());
            return decodeBody(response.body(),response.headers().firstValue("Content-Type").orElse(""),response.headers().firstValue("Content-Encoding").orElse(""));
        }catch(TimeoutException e){throw new HttpTimeoutException("QQ 推荐页面请求超时");}
        catch(ExecutionException e){throw new IOException("QQ 推荐页面网络失败",e.getCause());}
        finally{if(pending!=null)pending.cancel(true);http.shutdownNow();}
    }
    static String decodeBody(byte[] bytes,String contentType,String encoding)throws IOException{
        if(encoding.equalsIgnoreCase("gzip")||(bytes.length>2&&(bytes[0]&255)==0x1f&&(bytes[1]&255)==0x8b)){
            try(var input=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(bytes))){bytes=input.readNBytes(2*1024*1024+1);if(bytes.length>2*1024*1024)throw new IOException("QQ 推荐页面解压过大");}
        }else if(!encoding.isBlank()&&!encoding.equalsIgnoreCase("identity"))throw new IOException("QQ 推荐页面压缩格式不支持");
        return decodePage(bytes,contentType);
    }
    static String decodePage(byte[] bytes,String contentType)throws IOException{
        // musicmac 仍有 GB2312/GBK 页面；优先 HTTP 和 HTML 声明，不能直接按 UTF-8 丢字。
        String header=contentType+" "+new String(bytes,0,Math.min(bytes.length,8192),StandardCharsets.ISO_8859_1);
        var matcher=java.util.regex.Pattern.compile("(?i)charset\\s*=\\s*['\"]?([a-zA-Z0-9_-]+)").matcher(header);
        java.nio.charset.Charset charset=StandardCharsets.UTF_8;
        if(matcher.find()){
            String name=matcher.group(1).toLowerCase(java.util.Locale.ROOT);
            charset=switch(name){case "gb2312","gbk","gb18030"->java.nio.charset.Charset.forName("GB18030");case "utf-8","utf8"->StandardCharsets.UTF_8;default->throw new IOException("QQ 页面字符集不支持");};
        }
        return charset.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }
    private void check()throws IOException,InterruptedException{if(Thread.currentThread().isInterrupted())throw new InterruptedException();if(!active.getAsBoolean())throw new IOException("QQ 页面或账号已变化");}
}
