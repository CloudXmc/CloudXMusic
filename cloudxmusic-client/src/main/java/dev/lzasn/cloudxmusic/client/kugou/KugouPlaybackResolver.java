package dev.lzasn.cloudxmusic.client.kugou;

import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.util.*;

/** 自动取链：先接受 Web 分配的可播完整音频，无可用结果时再尝试 App 320 / 128。 */
public final class KugouPlaybackResolver {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("CloudXMusic/Kugou");
    public record Link(String url,int durationSeconds,KugouQuality actualQuality,boolean permissionFallback){
        public Link(String url,int durationSeconds,KugouQuality actualQuality){this(url,durationSeconds,actualQuality,false);}
        public Link{Objects.requireNonNull(url);Objects.requireNonNull(actualQuality);if(durationSeconds<0)throw new IllegalArgumentException("酷狗音频时长无效");}
        @Override public String toString(){return "KugouLink[quality="+actualQuality+", url=redacted]";}
    }
    record Verified(URI uri,KugouQuality quality){}
    @FunctionalInterface interface Request {JsonObject get(KugouTrack song,KugouAccount account,KugouQuality quality,boolean metadata)throws Exception;}
    @FunctionalInterface interface WebRequest {JsonObject get(KugouTrack song,KugouAccount account,boolean metadata)throws Exception;}
    @FunctionalInterface interface Verifier {Verified verify(URI uri,KugouQuality quality,long bitrate)throws Exception;}
    private final Request request;
    private final WebRequest web;
    private final Verifier verifier;
    private final java.util.function.BiConsumer<KugouQuality,JsonObject> observation;

    KugouPlaybackResolver(Request request,Verifier verifier){this(request,null,verifier,(q,r)->{});}
    KugouPlaybackResolver(Request request,Verifier verifier,java.util.function.BiConsumer<KugouQuality,JsonObject> observation){this(request,null,verifier,observation);}
    KugouPlaybackResolver(Request request,WebRequest web,Verifier verifier){this(request,web,verifier,(q,r)->{});}
    KugouPlaybackResolver(Request request,WebRequest web,Verifier verifier,java.util.function.BiConsumer<KugouQuality,JsonObject> observation){
        this.request=Objects.requireNonNull(request);this.web=web;this.verifier=Objects.requireNonNull(verifier);this.observation=Objects.requireNonNull(observation);
    }
    KugouPlaybackResolver(KugouApi api){this(api::quality,api::webSongInfo,KugouAudioUrlVerifier::probe);}

    /** 保留旧调用签名兼容已保存的偏好；旧档位不再影响自动取链顺序。 */
    public Link resolve(KugouTrack song,KugouAccount account,KugouQuality legacyPreference)throws Exception {
        Objects.requireNonNull(legacyPreference);
        LOG.info("[酷狗取链] mode=auto strategy=web-first,app-320,app-128 authenticated={}",account.loggedIn());
        boolean metadata=!song.albumId().isBlank()||!song.albumAudioId().isBlank();
        Attempt state=new Attempt();
        if(web!=null&&account.loggedIn())for(boolean withMetadata:metadata?List.of(true,false):List.of(false)){
            if(Thread.currentThread().isInterrupted())throw new InterruptedException("酷狗取链已取消");
            try{
                // Web 没有 quality 参数，不能因实际音质低于旧偏好而丢弃已可播的结果。
                Link link=state.accept(web.get(song,account,withMetadata),song,KugouQuality.HIGH,"web");
                if(link!=null)return link;
            }catch(InterruptedException e){throw e;}
            catch(KugouPlaybackAccessException e){throw e;}
            catch(IOException e){state.routeFailed("web",e);if(timeout(e))break;}
        }
        for(KugouQuality quality:List.of(KugouQuality.HQ,KugouQuality.STANDARD)){
            // App 只请求已支持的兜底档位，不向 /v5/url 发送 high / flac。
            for(boolean withMetadata:metadata?List.of(true,false):List.of(false)){
                if(Thread.currentThread().isInterrupted())throw new InterruptedException("酷狗取链已取消");
                try{
                    Link link=state.accept(request.get(song,account,quality,withMetadata),song,quality,"app-"+quality.apiValue());
                    if(link!=null)return link;
                }catch(InterruptedException e){throw e;}
                catch(KugouPlaybackAccessException e){throw e;}
                catch(IOException e){state.routeFailed("app-"+quality.apiValue(),e);if(timeout(e))break;}
            }
        }
        throw state.failure();
    }

    private final class Attempt {
        boolean standardDenied;
        final EnumSet<KugouQuality> permissionDenied=EnumSet.noneOf(KugouQuality.class);
        IOException lastFailure;
        final Set<URI> inspected=new HashSet<>();

        Attempt(){}

        Link accept(JsonObject root,KugouTrack song,KugouQuality requested,String route)throws Exception{
            if(root==null)return null;
            JsonObject data=root.has("data")&&root.get("data").isJsonObject()?root.getAsJsonObject("data"):root;
            long code=KugouApi.code(root);if(code==0)code=KugouApi.code(data);
            if(code==30020)throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.VERIFICATION_REQUIRED);
            if(code==20017)throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.LOGIN_REQUIRED);
            observation.accept(requested,root);
            if(KugouPrivileges.wholeSongDenied(root))throw new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.SONG_LOCKED);
            boolean permission=KugouPrivileges.permissionDenied(root);if(permission)permissionDenied.add(requested);
            if(requested==KugouQuality.STANDARD)standardDenied|=permission;
            if(permission)return null;

            long status=KugouJson.number(root,"status");if(status==0)status=KugouJson.number(data,"status");
            if(status!=1){
                LOG.warn("Kugou playback request failed route={} status={} code={}",route,status,code);
                throw new PlaybackApiFailure(route,status,code);
            }
            long milliseconds=durationMillis(data,song,route);
            if(KugouJson.number(data,"is_free_part","isFreePart","is_trial")>0||song.durationSeconds()>5&&milliseconds>0&&milliseconds<song.durationSeconds()*1000L-5000){
                LOG.info("[酷狗取链] rejected route={} reason=trial durationMs={} expectedSeconds={}",route,milliseconds,song.durationSeconds());return null;
            }
            String format=KugouJson.text(data,"extName","extname");if(!format.isEmpty()&&!Set.of("mp3","flac","ogg","vorbis").contains(format.toLowerCase(Locale.ROOT))){
                LOG.warn("[酷狗取链] rejected route={} reason=unsupported-format",route);return null;
            }
            for(URI uri:candidates(data))try{
                if(!inspected.add(uri))continue;
                Verified verified=verifier.verify(uri,requested,KugouJson.number(data,"bitrate","bitRate"));
                if(verified==null){LOG.warn("[酷狗取链] rejected route={} host={} reason=unsupported-audio-header",route,uri.getHost());continue;}
                observation.accept(verified.quality(),root);
                boolean permissionFallback=permissionCausedFallback(verified.quality());
                Link link=new Link(verified.uri().toString(),milliseconds>0?(int)Math.min(86400,milliseconds/1000):song.durationSeconds(),verified.quality(),permissionFallback);
                LOG.info("[酷狗取链] verified route={} host={} actual={} durationSeconds={}",route,verified.uri().getHost(),verified.quality().apiValue(),link.durationSeconds());
                return link;
            }catch(InterruptedException e){throw e;}
            // 单个 CDN 的 403/超时不代表账号失效；继续检查同一回包中的备用地址。
            catch(IOException e){routeFailed(route+"-cdn",e);}
            return null;
        }

        private boolean permissionCausedFallback(KugouQuality actual){return permissionDenied.stream().anyMatch(denied->denied.ordinal()<actual.ordinal());}
        IOException failure(){
            if(standardDenied)return new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.SONG_LOCKED);
            if(lastFailure!=null)return lastFailure;
            return new KugouPlaybackAccessException(KugouPlaybackAccessException.Reason.RESTRICTED);
        }
        void routeFailed(String route,IOException error){
            lastFailure=error;
            LOG.warn("[酷狗取链] route={} failed={} detail={}; trying remaining routes",route,error.getClass().getSimpleName(),KugouAudioFailure.safe(error).getMessage());
        }
    }

    private static boolean timeout(IOException error){return error instanceof java.net.SocketTimeoutException||error instanceof java.net.http.HttpTimeoutException;}
    static long durationMillis(JsonObject data,KugouTrack song,String route){
        long raw=KugouJson.number(data,"timelength","timelen");
        // /v5/url 的 timelength 实测为秒，Web 为毫秒；兼容旧回包的毫秒值并保留试听长度检查。
        if(raw>0&&route.startsWith("app")&&raw<=86400&&raw<=(song.durationSeconds()>0?song.durationSeconds()+5:3600))return raw*1000;
        return raw>0?raw:KugouJson.number(data,"timeLength")*1000;
    }

    private static final class PlaybackApiFailure extends IOException {
        PlaybackApiFailure(String route,long status,long code){super("酷狗取链接口失败 route="+route+" status="+status+" code="+code);}
    }

    private static List<URI> candidates(JsonObject data){
        LinkedHashSet<URI> result=new LinkedHashSet<>();
        for(String key:List.of("play_url","url","backup_url","play_backup_url")){
            JsonElement raw=data.get(key);if(raw==null)continue;
            Iterable<JsonElement> values=raw.isJsonArray()?raw.getAsJsonArray():List.of(raw);
            for(var value:values)if(value.isJsonPrimitive())for(String text:value.getAsString().split(",\\s*(?=https?://)"))try{
                URI uri=URI.create(text.trim());if(KugouAudioUrlVerifier.allowed(uri)){result.add(uri);if(result.size()==3)return List.copyOf(result);}
            }catch(IllegalArgumentException ignored){}
        }
        return List.copyOf(result);
    }
}
