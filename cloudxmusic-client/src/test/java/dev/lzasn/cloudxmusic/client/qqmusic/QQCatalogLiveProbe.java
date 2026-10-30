package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** 手动只读联调工具，不属于自动测试；只输出计数与错误类型，绝不记录用户、URL、Cookie 或密钥。 */
public final class QQCatalogLiveProbe {
    @FunctionalInterface interface Probe {JsonObject run()throws Exception;}
    private static JsonObject result=new JsonObject();
    private static Path output;
    private static void check(String name,Probe probe)throws Exception{
        JsonObject entry;
        try{entry=probe.run();entry.addProperty("ok",true);}
        catch(Exception error){entry=new JsonObject();entry.addProperty("ok",false);entry.addProperty("errorType",error.getClass().getSimpleName());
            if(error instanceof QQPlaybackApi.BusinessFailure||error instanceof QQSessionVerifier.Rejected)entry.addProperty("business",error.getMessage());}
        result.add(name,entry);Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(result));System.out.println("QQ read-only probe: "+name+" "+entry);
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("需要凭证文件路径和非敏感报告路径");output=Path.of(args[1]);
        Path file=Path.of(args[0]);if(Files.size(file)>65536)throw new IllegalArgumentException("凭证文件过大");JsonObject json=QQAuthHttp.parse(Files.readAllBytes(file));
        QQCredentials saved=new QQCredentials(QQAuthHttp.string(json,"userId"),QQAuthHttp.string(json,"musicKey"),(int)QQAuthHttp.integer(json,"loginType"),"",QQAuthHttp.integer(json,"expiresAtMillis"));
        QQCredentials account=new QQSessionVerifier().verify(saved);
        check("avatar",()->{
            var profile=QQAccountProfileApi.load(account,()->true);JsonObject info=new JsonObject();info.addProperty("hasUrl",!profile.avatarUrl().isBlank());
            Queue<Runnable> main=new ConcurrentLinkedQueue<>();
            try(var cache=new QQArtworkCache(main::add)){
                cache.image(profile.avatarUrl());long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                while(main.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(10);Runnable callback;while((callback=main.poll())!=null)callback.run();
                var image=cache.image(profile.avatarUrl());info.addProperty("decoded",image!=null);info.addProperty("publishedRevision",cache.revision());
            }
            return info;
        });
        check("recommend",()->{var page=new QQCatalogService(account,()->true).api().recommend(0);JsonObject info=new JsonObject();info.addProperty("count",page.items().size());
            if(!page.items().isEmpty()){var api=new QQCatalogService(account,()->true).api();var songs=api.tracks(page.items().getFirst(),0);info.addProperty("playlistSongs",songs.items().size());info.addProperty("hasMore",songs.hasMore());
                if(songs.hasMore()){var next=api.tracks(page.items().getFirst(),songs.nextOffset());var ids=new HashSet<String>();songs.items().forEach(t->ids.add(t.song().platformId()));int before=ids.size();next.items().forEach(t->ids.add(t.song().platformId()));info.addProperty("pageTwoNewSongs",ids.size()-before);info.addProperty("pageTwoOffset",next.nextOffset());}}return info;});
        check("daily",()->{var session=new QQCatalogService(account,()->true);var daily=session.daily();var songs=session.api().tracks(daily,0);JsonObject info=new JsonObject();info.addProperty("songs",songs.items().size());return info;});
        check("liked",()->{var session=new QQCatalogService(account,()->true);var api=session.api();var liked=api.liked();var songs=api.tracks(liked,0);JsonObject info=new JsonObject();info.addProperty("songs",songs.items().size());info.addProperty("hasMore",songs.hasMore());if(songs.hasMore()){var next=api.tracks(liked,songs.nextOffset());info.addProperty("pageTwoSongs",next.items().size());info.addProperty("pageTwoOffset",next.nextOffset());}return info;});
        check("qrc",()->{var song=new dev.lzasn.cloudxmusic.client.omni.UnifiedSong("qq","0039MnYb0qxYhV","","","");var lyrics=new QQLyricsApi().loadDetailed(song);JsonObject info=new JsonObject();info.addProperty("lines",lyrics.lyric().lines().length);info.addProperty("timedLines",lyrics.timed().size());return info;});
    }
}
