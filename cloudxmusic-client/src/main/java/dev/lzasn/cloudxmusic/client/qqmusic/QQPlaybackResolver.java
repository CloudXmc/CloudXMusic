package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** 同步取链服务；调用方必须在后台执行。每档桌面/Web 路线均先于下一档。 */
public final class QQPlaybackResolver {
    @FunctionalInterface interface Sessions { QQAccountManager.Snapshot get() throws IOException; }
    @FunctionalInterface interface AudioCheck { URI verify(URI uri, boolean flac, Duration timeout) throws IOException, InterruptedException; }
    public record Link(String url,QQQuality actualQuality) {}
    private final Sessions sessions;
    private final Predicate<QQAccountManager.Snapshot> current;
    private final QQPlaybackApi.Transport transport;
    private final AudioCheck checkAudio;
    QQPlaybackResolver(QQAccountManager accounts) { this(accounts::snapshot, accounts::isCurrent, QQAuthHttp::post, QQAudioUrlVerifier::verify); }
    QQPlaybackResolver(Sessions sessions, Predicate<QQAccountManager.Snapshot> current, QQPlaybackApi.Transport transport, AudioCheck checkAudio) {
        this.sessions = sessions; this.current = current; this.transport = transport; this.checkAudio = checkAudio;
    }
    public String resolve(UnifiedSong song) throws IOException, InterruptedException {
        return resolve(song,duration -> {});
    }
    String resolve(UnifiedSong song,java.util.function.IntConsumer duration) throws IOException, InterruptedException {
        return resolve(song,QQQuality.SQ,info->duration.accept(info.durationSeconds()));
    }
    String resolve(UnifiedSong song,QQQuality preferred,java.util.function.Consumer<QQTrackMetadata.Info> metadata) throws IOException,InterruptedException{
        Link result=resolveLink(song,preferred,metadata);
        return result==null?null:result.url();
    }
    Link resolveLink(UnifiedSong song,QQQuality preferred,java.util.function.Consumer<QQTrackMetadata.Info> metadata) throws IOException,InterruptedException{
        Objects.requireNonNull(song);
        Objects.requireNonNull(preferred);
        if (!song.source().equals("qq") || !QQTrackMetadata.validMid(song.platformId())) throw new IllegalArgumentException("需要有效的 QQ 音乐歌曲 MID");
        QQAccountManager.Snapshot session = sessions.get();
        QQPlaybackApi api = new QQPlaybackApi(session.credentials(), transport, () -> current.test(session), Duration.ofSeconds(45));
        api.remaining();
        QQTrackMetadata.Info info;
        try {info=QQTrackMetadata.loadInfo(song.platformId(),api);}
        catch(QQPlaybackApi.BusinessFailure failure){
            if(!failure.songUnavailable())throw failure;
            verifySession(api,session);return null;
        }
        String mediaMid=info.mediaMid();metadata.accept(info);
        String guid = Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 10_000_000_000L));
        IOException failure = null;
        for (QQQuality quality : Arrays.asList(QQQuality.values()).subList(preferred.ordinal(),QQQuality.values().length)) {
            for (boolean desktop : new boolean[]{true, false}) {
                String filename = quality.prefix() + mediaMid + quality.extension();
                JsonObject params = new JsonObject(); params.addProperty("guid", guid); params.addProperty("uin", session.credentials().userId()); params.addProperty("ctx", 0);
                if (!desktop) { params.addProperty("loginflag", 1); params.addProperty("platform", "20"); }
                JsonArray files = new JsonArray(); files.add(filename); params.add("filename", files);
                JsonArray mids = new JsonArray(); mids.add(song.platformId()); params.add("songmid", mids);
                JsonArray types = new JsonArray(); types.add(0); params.add("songtype", types);
                QQPlaybackApi.Reply reply = api.call(desktop ? "music.vkey.GetVkey" : "vkey.GetVkeyServer",
                        desktop ? "UrlGetVkey" : "CgiGetVkey", params, desktop);
                if (reply.code() != 0) {
                    var rejected=new QQPlaybackApi.BusinessFailure(reply.code());
                    if(!rejected.songUnavailable())failure=rejected;
                    continue;
                }
                List<URI> candidates;
                try { candidates = candidates(reply.data(), filename, song.platformId(), quality.flac()); }
                catch (IOException e) { failure = e; continue; }
                for (URI candidate : candidates) {
                    URI verified;
                    try { verified = checkAudio.verify(candidate, quality.flac(), api.remaining()); }
                    catch (IOException e) { failure = e; api.remaining(); continue; }
                    api.remaining();
                    if (verified != null) {
                        if (!QQAudioUrlVerifier.allowed(verified) || !QQAudioUrlVerifier.extension(verified, quality.flac()))
                            throw new IOException("QQ CDN 验证返回了不受支持的音频地址");
                        return new Link(verified.toASCIIString(),quality);
                    }
                    failure = new IOException("QQ 返回的地址不可访问或文件头不是预期音频；已尝试降级");
                }
            }
        }
        verifySession(api,session);
        if (failure != null) throw failure;
        // 已穷尽各档位的空 purl / 22006，交给控制器跳过；不吞网络或格式错误。
        return null;
    }
    private static void verifySession(QQPlaybackApi api,QQAccountManager.Snapshot session)throws IOException,InterruptedException{
        // 空 purl 或歌曲限制不证明凭证失效，只读验证后再确认无资源。
        JsonObject profile = QQPlaybackApi.requireData(api.call("music.UserInfo.userInfoServer", "GetLoginUserInfo", new JsonObject(), true));
        if (profile.size() == 0) throw new IOException("QQ 账号只读验证返回空资料");
        String id = QQAuthHttp.text(profile, "musicid");
        if (id.isEmpty()) id = QQAuthHttp.text(profile, "str_musicid");
        if (!id.isEmpty() && !id.equals(session.credentials().userId())) throw new IOException("QQ 取链账号验证身份不一致");
    }
    static List<URI> candidates(JsonObject data, String filename, String songMid, boolean flac) throws IOException {
        JsonElement rows = data.get("midurlinfo");
        if (rows == null || !rows.isJsonArray() || rows.getAsJsonArray().size() > 16) throw new IOException("QQ VKey 响应缺少有效 midurlinfo 数组");
        List<String> hosts = new ArrayList<>();
        JsonElement sip = data.get("sip");
        if (sip != null && !sip.isJsonNull()) {
            if (!sip.isJsonArray() || sip.getAsJsonArray().size() > 16) throw new IOException("QQ VKey sip 必须是有界数组");
            for (JsonElement item : sip.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new IOException("QQ VKey sip 主机格式无效");
                hosts.add(item.getAsString());
            }
        }
        hosts.sort(Comparator.comparingInt(host -> host.startsWith("https://") ? 0 : 1));
        LinkedHashSet<URI> result = new LinkedHashSet<>();
        for (JsonElement item : rows.getAsJsonArray()) {
            if (!item.isJsonObject()) throw new IOException("QQ VKey 条目必须是对象");
            JsonObject row = item.getAsJsonObject();
            if (row.has("result") && QQAuthHttp.integer(row, "result") != 0) {
                var rejected=new QQPlaybackApi.BusinessFailure(QQAuthHttp.integer(row,"result"));
                if(!rejected.songUnavailable())throw rejected;
                continue;
            }
            if (row.has("filename") && !QQAuthHttp.string(row, "filename").equals(filename)) throw new IOException("QQ VKey 返回了其他文件");
            if (row.has("songmid") && !QQAuthHttp.string(row, "songmid").equals(songMid)) throw new IOException("QQ VKey 返回了其他歌曲");
            String purl = QQAuthHttp.string(row, "purl");
            if (purl.isBlank()) continue;
            if (purl.length() > 16384) throw new IOException("QQ VKey 音频地址过长");
            try {
                URI path = URI.create(purl);
                List<URI> urls = new ArrayList<>();
                if (path.isAbsolute()) urls.add(path);
                else for (String host : hosts) { URI base = URI.create(host); if (QQAudioUrlVerifier.allowed(base)) urls.add(base.resolve(path)); }
                boolean valid = false;
                for (URI url : urls) {
                    if (QQAudioUrlVerifier.allowed(url) && QQAudioUrlVerifier.extension(url, flac)
                            && url.getPath().substring(url.getPath().lastIndexOf('/') + 1).equals(filename)) {
                        result.add(url); valid = true;
                        if (result.size() > 8) throw new IOException("QQ VKey 候选 CDN 地址过多");
                    }
                }
                if (!valid) throw new IOException("QQ VKey 未返回可验证的对应音频地址");
            } catch (IllegalArgumentException e) { throw new IOException("QQ VKey 音频地址语法无效"); }
        }
        return List.copyOf(result);
    }
}
