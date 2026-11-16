package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.CloudHome;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import dev.lzasn.cloudxmusic.client.util.Async;
import java.io.IOException;

/** 酷狗模块的客户端生命周期与 UI 适配入口；平台业务仍全部留在 kugou 包。 */
public final class KugouIntegration {
    private static KugouModule module;
    private static KugouLibrary library;
    private static KugouArtworkCache artwork;
    private static KugouSearchController search;
    private static boolean registered;
    private static boolean started,closed;
    private KugouIntegration(){}
    public static synchronized void register(){
        if(started)return;started=true;
        var home=CloudHome.dir();
        Async.run(()->{
            try {
                var opened=KugouModule.open(home,Async::main);var restored=KugouLibrary.open(home);
                Async.main(()->{if(closed){opened.close();restored.close();return;}module=opened;library=restored;});
            } catch(IOException|RuntimeException error) {dev.lzasn.cloudxmusic.client.CloudXMusicClient.LOGGER.error("酷狗模块初始化失败",error);}
        });
    }
    private static void library(KugouModule value){value.library(library);value.playback().volume(library.volume());value.lyricMode(library.lyricMode());}
    public static boolean ready(){return registered&&module!=null;}
    public static KugouModule module(){if(!ready())throw new IllegalStateException("酷狗模块尚未初始化");return module;}
    public static KugouLibrary library(){return library;}
    public static KugouArtworkCache artwork(){if(artwork==null)artwork=new KugouArtworkCache();return artwork;}
    public static KugouSearchController search(){return search;}
    public static KugouLoginController login(){return module().login();}
    public static boolean active(){return ready()&&QQMusicIntegration.coordinator().source()==MusicSource.KUGOU;}
    public static void tick(boolean inWorld,boolean title){
        if(closed)return;
        if(!registered&&module!=null&&!QQMusicIntegration.coordinator().view().switching()){
            module.attach(QQMusicIntegration.coordinator());library(module);library.favorites().forEach(module.provider()::remember);
            search=module.createSearch();artwork();registered=true;
        }
        if(ready())module.tick(inWorld,title);
    }
    public static void joined(){if(ready())module.joined();}
    public static void disconnect(){if(ready())module.disconnect();}
    public static void close(){closed=true;registered=false;if(search!=null)module.releaseSearch(search);if(module!=null)module.close();if(library!=null)library.close();if(artwork!=null)artwork.close();search=null;module=null;}
    public static void volume(float value){if(!ready()||!Float.isFinite(value))return;module.playback().volume(value);library.preferences(library.quality(),value,module.snapshot().lyricMode());}
    public static void lyricMode(LyricMode value){if(!ready())return;module.lyricMode(value);library.preferences(library.quality(),module.playback().view().volume(),value);}
    public static void toggleFavorite(){if(!ready()||!module.playback().hasTrack())return;var song=module.playback().view().song();module.favorites().toggle(module.provider().track(song));}
    public static boolean favorite(){return ready()&&module.playback().hasTrack()&&module.favorites().contains(module.playback().view().song());}
}
