package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.lyric.Lyric;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 酷狗阻塞数据接口，供独立后台控制器调用；账号与目录缓存均不触及其他音源。 */
public final class KugouProvider implements OnlineMusicProvider,AutoCloseable {
    private final KugouApi api;
    private final KugouAccountStore accounts;
    private record Observed(String hash,long revision,long time,KugouPrivileges privileges){}
    private volatile Observed observed=new Observed("",-1,0,KugouPrivileges.empty());
    private record PlaylistCache(long account,Map<String,KugouTrack> tracks){}
    private volatile PlaylistCache playlistCache=new PlaylistCache(-1,Map.of());
    private final Map<String,KugouTrack> tracks=Collections.synchronizedMap(new LinkedHashMap<>(128,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,KugouTrack> entry){return size()>1024;}
    });
    public KugouProvider(KugouAccountStore accounts){this(accounts,new KugouApi());}
    KugouProvider(KugouAccountStore accounts,KugouApi api){this.accounts=accounts;this.api=api;}
    @Override public String sourceId(){return "kugou";}
    public List<KugouTrack> searchTracks(String keyword,int offset,int limit)throws Exception {
        if(keyword==null||keyword.isBlank()||offset<0||limit<1)return List.of();
        if(keyword.length()>256||offset>10000||limit>100)throw new IllegalArgumentException("酷狗搜索范围无效");
        // API 按页分页，先读取覆盖 offset 的页，再截取，避免任意 offset 漏歌。
        int page=offset/limit+1,skip=offset%limit;List<KugouTrack> result=new ArrayList<>();
        for(int n=0;n<(skip==0?1:2);n++){
            var root=api.search(keyword,page+n,limit);if(KugouJson.number(root,"status")!=1)throw new IOException("酷狗搜索失败，请稍后重试");
            for(var item:KugouJson.array(KugouJson.object(root,"data"),"info"))if(item.isJsonObject())try{result.add(KugouTrack.parse(item.getAsJsonObject()));}catch(IllegalArgumentException ignored){}
        }
        List<KugouTrack> selected=result.stream().skip(skip).limit(limit).toList();selected.forEach(this::remember);return selected;
    }
    @Override public List<UnifiedSong> searchSongs(String keyword,int offset,int limit)throws Exception{return searchTracks(keyword,offset,limit).stream().map(KugouTrack::song).toList();}
    public void remember(KugouTrack track){tracks.put(track.song().platformId(),track);}
    public void rememberPlaylist(List<KugouTrack> list){
        if(list.size()>20000)throw new IllegalArgumentException("酷狗歌单超出读取上限");
        var complete=new LinkedHashMap<String,KugouTrack>();list.forEach(t->complete.put(t.song().platformId(),t));
        playlistCache=new PlaylistCache(accounts.revision(),Map.copyOf(complete));
    }
    public KugouTrack track(UnifiedSong song){
        if(!song.source().equals(sourceId()))throw new IllegalArgumentException("酷狗不能处理其他音源的歌曲");
        var list=playlistCache;if(list.account()!=accounts.revision()){playlistCache=new PlaylistCache(-1,Map.of());list=playlistCache;}
        // 完整队列保留专辑身份，避免先前的精简搜索结果覆盖歌单元数据。
        KugouTrack cached=list.tracks().get(song.platformId());
        if(cached==null)cached=tracks.get(song.platformId());
        return cached!=null?cached:new KugouTrack(song,"","",0,"");
    }
    public KugouPlaybackResolver.Link playbackLink(UnifiedSong song,KugouQuality preferred)throws Exception{
        long epoch=accounts.revision();var account=accounts.account();observed=new Observed(song.platformId(),epoch,System.nanoTime(),KugouPrivileges.empty());
        var resolver=new KugouPlaybackResolver(api::quality,api::webSongInfo,KugouAudioUrlVerifier::probe,(q,root)->{
            if(accounts.revision()!=epoch)return;var old=observed;var value=KugouPrivileges.observation(root,q);observed=new Observed(song.platformId(),epoch,System.nanoTime(),old.hash().equals(song.platformId())&&old.revision()==epoch?old.privileges().merge(value):value);
        });
        return resolver.resolve(track(song),account,Objects.requireNonNull(preferred));
    }
    KugouProfile profile(KugouAccount account)throws Exception{return api.profile(account);}
    KugouPrivileges privileges(UnifiedSong song,KugouAccount account)throws Exception{return api.privileges(track(song),account);}
    KugouFavoritesService.Remote favorites(){return new KugouFavoritesService(api).remote();}
    KugouPlaylistApi playlists(KugouAccount account,java.util.function.BooleanSupplier active){
        return new KugouPlaylistApi((path,values,body,session)->{
            var root=KugouApi.checked(api.cloud(path,values,body,session));
            if(!root.has("data")||!root.get("data").isJsonObject())throw new IOException("酷狗歌单响应不完整");
            var data=root.getAsJsonObject("data");
            if(KugouApi.code(data)!=0||data.has("status")&&KugouJson.number(data,"status")==0)throw new IOException("酷狗歌单读取失败");return data;
        },account,active);
    }
    KugouPrivileges observed(String hash,long revision,long since){var value=observed;return value.hash().equals(hash)&&value.revision()==revision&&value.time()>since&&System.nanoTime()-value.time()<java.util.concurrent.TimeUnit.MINUTES.toNanos(1)?value.privileges():KugouPrivileges.empty();}
    @Override public String getPlaybackUrl(UnifiedSong song)throws Exception{return playbackLink(song,KugouQuality.HIGH).url();}
    public KugouKrc.Lyrics lyrics(UnifiedSong song)throws Exception {
        var search=api.lyricSearch(track(song));
        var candidates=KugouJson.array(search,"candidates");
        for(int i=0;i<Math.min(3,candidates.size());i++)if(candidates.get(i).isJsonObject()){
            var candidate=candidates.get(i).getAsJsonObject();String id=KugouJson.text(candidate,"id"),key=KugouJson.text(candidate,"accesskey");
            if(id.isBlank()||key.isBlank())continue;
            try{
                var root=api.lyricDownload(id,key,"krc");var parsed=KugouKrc.decode(KugouJson.text(root,"content"));if(!parsed.timed().isEmpty())return parsed;
            }catch(IOException|IllegalArgumentException ignored){/* 逐字歌词不存在时尝试同候选的 LRC。 */}
            try{
                String encoded=KugouJson.text(api.lyricDownload(id,key,"lrc"),"content");if(encoded.length()>3*1024*1024)continue;
                Lyric lyric=Lyric.parse(new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8),null);if(!lyric.isEmpty())return new KugouKrc.Lyrics(lyric,List.of());
            }catch(IOException|IllegalArgumentException ignored){}
        }
        return KugouKrc.EMPTY;
    }
    @Override public Lyric getLyrics(UnifiedSong song)throws Exception{return lyrics(song).lyric();}
    @Override public void close(){tracks.clear();playlistCache=new PlaylistCache(-1,Map.of());api.close();}
}
