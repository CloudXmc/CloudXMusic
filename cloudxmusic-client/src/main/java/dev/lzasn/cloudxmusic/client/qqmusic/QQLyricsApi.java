package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;

/** QQ 公网逐行歌词。请求和整段响应均有期限，不携带账号 Cookie。 */
public final class QQLyricsApi {
    private final QQAuthHttp.Transport transport;
    public QQLyricsApi(){this(QQAuthHttp::post);}
    QQLyricsApi(QQAuthHttp.Transport transport){this.transport=transport;}
    static JsonObject qrcRequest(String mid){
        JsonObject body=request(mid),param=body.getAsJsonObject("req").getAsJsonObject("param");
        param.addProperty("qrc",1);param.addProperty("crypt",1);return body;
    }
    public QQTimedLyrics loadDetailed(UnifiedSong song)throws IOException,InterruptedException{
        if(song==null||!song.source().equals("qq")||!QQTrackMetadata.validMid(song.platformId()))throw new IllegalArgumentException("QQ 歌曲 MID 无效");
        IOException qrcFailure=null;
        try{
            JsonObject root=transport.post(qrcRequest(song.platformId()),new java.net.CookieManager());
            if(QQAuthHttp.integer(root,"code")!=0)throw new IOException("QQ QRC 服务拒绝请求");
            JsonObject reply=QQAuthHttp.object(root,"req");
            if(QQAuthHttp.integer(reply,"code")!=0)throw new IOException("QQ QRC 业务请求失败");
            QQTimedLyrics result=parseDetailed(QQAuthHttp.object(reply,"data"));
            if(!result.lyric().isEmpty())return result;
        }catch(IOException e){qrcFailure=e;}
        try{return new QQTimedLyrics(load(song),java.util.List.of());}
        catch(IOException e){if(qrcFailure!=null)e.addSuppressed(qrcFailure);throw e;}
    }
    static QQTimedLyrics parseDetailed(JsonObject data)throws IOException{
        if(!data.has("lyric"))throw new IOException("QQ QRC 响应缺少 lyric 字段");
        String raw=QQAuthHttp.text(data,"qrc");if(raw.isBlank()||raw.equals("0")||raw.equals("1"))raw=QQAuthHttp.string(data,"lyric");
        String original=wireText(raw);var timed=QQQrcParser.parse(original);
        String translation=QQAuthHttp.text(data,"trans");if(translation.isBlank())translation=QQAuthHttp.text(data,"translyric");
        translation=wireText(translation);var translatedTimed=QQQrcParser.parse(translation);
        if(!translatedTimed.isEmpty())translation=lrc(translatedTimed);
        if(!timed.isEmpty())original=lrc(timed);
        return new QQTimedLyrics(merge(original,translation),timed);
    }
    private static String wireText(String raw)throws IOException{
        if(raw.isBlank())return "";
        if(raw.stripLeading().startsWith("<"))return raw;
        String value=entities(raw);
        if(value.stripLeading().startsWith("<")||value.stripLeading().startsWith("[")||value.matches("[0-9a-fA-F]+"))return QQQrcCodec.plaintext(value);
        return decode(value);
    }
    private static String lrc(java.util.List<dev.lzasn.cloudxmusic.client.omni.TimedLyrics.Line> lines){
        StringBuilder out=new StringBuilder();for(var line:lines){int ms=line.startMs();out.append(String.format(java.util.Locale.ROOT,"[%02d:%02d.%03d]",ms/60000,(ms/1000)%60,ms%1000)).append(line.text()).append('\n');}return out.toString();
    }
    static JsonObject request(String mid){
        JsonObject param=new JsonObject();param.addProperty("songMid",mid);param.addProperty("type",1);
        for(String key:new String[]{"lrc_t","qrc_t","roma_t","trans_t","crypt","qrc"})param.addProperty(key,0);
        // trans 是请求开关；0 会直接令接口返回空翻译。qrc=0/crypt=0 请求普通时间轴格式。
        param.addProperty("trans",1);param.addProperty("roma",0);
        JsonObject req=new JsonObject();req.addProperty("module","music.musichallSong.PlayLyricInfo");
        req.addProperty("method","GetPlayLyricInfo");req.add("param",param);
        JsonObject body=new JsonObject(),comm=new JsonObject();comm.addProperty("ct",24);comm.addProperty("cv",0);
        body.add("comm",comm);body.add("req",req);return body;
    }
    static Lyric parseModern(JsonObject root)throws IOException{
        if(QQAuthHttp.integer(root,"code")!=0)throw new IOException("QQ 歌词服务拒绝请求");
        JsonObject req=QQAuthHttp.object(root,"req");
        if(QQAuthHttp.integer(req,"code")!=0)throw new IOException("QQ 歌词业务请求失败");
        JsonObject data=QQAuthHttp.object(req,"data");
        JsonObject legacy=data.deepCopy();legacy.addProperty("retcode",0);return parse(legacy);
    }
    public Lyric load(UnifiedSong song) throws IOException, InterruptedException {
        if (song==null || !song.source().equals("qq") || !QQTrackMetadata.validMid(song.platformId()))
            throw new IllegalArgumentException("QQ 歌曲 MID 无效");
        IOException modernFailure=null;
        try{
            Lyric result=parseModern(transport.post(request(song.platformId()),new java.net.CookieManager()));
            if(!result.isEmpty())return result;
        }catch(IOException e){modernFailure=e;}
        try{return load(URI.create("https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?format=json&nobase64=1&trans=1&songmid="+song.platformId()));}
        catch(IOException e){if(modernFailure!=null)e.addSuppressed(modernFailure);throw e;}
    }
    Lyric load(URI endpoint) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        CompletableFuture<HttpResponse<byte[]>> pending = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                    .header("Referer", "https://y.qq.com/").header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json").header("Accept-Encoding", "identity").GET().build();
            pending = client.sendAsync(request, info -> new QQAuthHttp.LimitedBody());
            HttpResponse<byte[]> response = pending.get(15, TimeUnit.SECONDS);
            if (response.statusCode() != 200) throw new IOException("QQ 歌词 HTTP 状态码：" + response.statusCode());
            return parse(QQAuthHttp.parse(response.body()));
        } catch (TimeoutException e) { throw new HttpTimeoutException("QQ 歌词请求超时"); }
        catch (ExecutionException e) { throw new IOException("QQ 歌词网络请求失败", e.getCause()); }
        finally { if (pending != null) pending.cancel(true); client.shutdownNow(); }
    }
    static Lyric parse(JsonObject root) throws IOException {
        long code = QQAuthHttp.integer(root, "retcode");
        if (code != 0) throw new IOException("QQ 歌词接口拒绝请求，状态码：" + code);
        if (!root.has("lyric")) throw new IOException("QQ 歌词响应缺少 lyric 字段");
        String original = QQAuthHttp.string(root, "lyric");
        String translated = root.has("trans")&&!root.get("trans").isJsonNull() ? QQAuthHttp.string(root, "trans") : "";
        if(translated.isBlank()&&root.has("translyric")&&!root.get("translyric").isJsonNull())translated=QQAuthHttp.string(root,"translyric");
        return merge(decode(original), decode(translated));
    }
    static Lyric merge(String original,String translation){
        Lyric base=Lyric.parse(original,"");
        var translated=Lyric.parse(translation,"").lines();
        var aligned=new StringBuilder();
        // 只对 QQ 做 80ms 内一对一匹配；保留原文时间轴，绝不按行号错配整首歌。
        var remaining=new java.util.TreeMap<Integer,String>();
        for(var line:translated)if(!line.text().equals("//"))remaining.put(line.timeMs(),line.text());
        for(var line:base.lines()){
            var low=remaining.floorEntry(line.timeMs());var high=remaining.ceilingEntry(line.timeMs());
            var best=low==null?high:high==null?low:line.timeMs()-low.getKey()<=high.getKey()-line.timeMs()?low:high;
            if(best!=null&&Math.abs((long)best.getKey()-line.timeMs())<=80){
                int ms=line.timeMs();
                aligned.append(String.format(java.util.Locale.ROOT,"[%02d:%02d.%03d]",ms/60000,(ms/1000)%60,ms%1000)).append(best.getValue()).append('\n');
                remaining.remove(best.getKey());
            }
        }
        return Lyric.parse(original,aligned.toString());
    }
    private static String decode(String value) throws IOException {
        if (value.isBlank()) return "";
        value=entities(value);
        if (!value.contains("[")) {
            try { value = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Base64.getDecoder().decode(value))).toString(); }
            catch (IllegalArgumentException e) { throw new IOException("QQ 歌词编码无效", e); }
        }
        return entities(value);
    }
    static String entities(String value)throws IOException{
        var matcher=java.util.regex.Pattern.compile("&#(x[0-9a-fA-F]{1,6}|[0-9]{1,7});").matcher(value);
        StringBuilder result=new StringBuilder();
        while(matcher.find()){
            String number=matcher.group(1);int code=Integer.parseInt(number.startsWith("x")?number.substring(1):number,number.startsWith("x")?16:10);
            if(!Character.isValidCodePoint(code)||(code>=0xD800&&code<=0xDFFF))throw new IOException("QQ 歌词字符实体无效");
            matcher.appendReplacement(result,java.util.regex.Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        matcher.appendTail(result);
        return result.toString().replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
