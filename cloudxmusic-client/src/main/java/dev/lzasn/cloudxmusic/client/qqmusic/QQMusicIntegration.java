package dev.lzasn.cloudxmusic.client.qqmusic;

import dev.lzasn.cloudxmusic.client.omni.OmniRouter;
import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import dev.lzasn.cloudxmusic.client.util.Async;

/** QQ 搜索和独立账号服务的生命周期入口；恢复过程全部在后台执行。 */
public final class QQMusicIntegration {
    private static boolean registered;
    private static QQAccountManager accounts;
    private static QQLoginService login;
    private static QQPlaybackController playback;
    private static MusicSourceCoordinator coordinator;
    private static NeteasePlaybackAdapter netease;
    private static QQPresentation presentation;
    private static QQPreferences preferences;
    private static QQTermsConsent terms;
    private static QQArtworkCache artwork;
    private static QQQueueMetadata queueMetadata;
    private static PlaybackLifecycle lifecycle;
    private static long artworkAccount=Long.MIN_VALUE;
    private QQMusicIntegration() {}
    public static synchronized void register() {
        if (registered) return;
        OmniRouter.register(new NeteaseSearchAdapter());
        accounts = new QQAccountManager(new QQCredentialStore(FabricLoader.getInstance().getConfigDir()
                .resolve("cloudxmusic/qqmusic/account.json")), new QQSessionVerifier(),QQAccountProfileApi::load);
        QQProvider provider=new QQProvider(new QQPlaybackResolver(accounts));
        OmniRouter.register(provider);
        login = new QQLoginService(accounts, Async::main);
        playback=new QQPlaybackController(new QQAudioPlayer(),dev.lzasn.cloudxmusic.client.CloudXMusicClient.STATE::pauseForExternalPlayback,Async::main,accounts::revision);
        playback.qualityAware();
        netease=new NeteasePlaybackAdapter(CloudXMusicClient.STATE);
        presentation=new QQPresentation(provider,Async::main,accounts::revision);
        artwork=new QQArtworkCache(Async::main);
        queueMetadata=new QQQueueMetadata(provider::details,Async::main,accounts::revision,artwork::prefetch);
        QQQualityFeedback feedback=new QQQualityFeedback(System::nanoTime,QQQualityToast::show);
        playback.qualityFeedback(feedback::fallback);
        QQPlaybackNotice notices=new QQPlaybackNotice(System::nanoTime,QQQualityToast::playback);
        playback.notice(notices::show);
        coordinator=new MusicSourceCoordinator(MusicSource.NETEASE,
                source->source==MusicSource.NETEASE?CloudXMusicClient.STATE.pauseForExternalPlayback():playback.suspend(),Async::main,
                source->{if(source==MusicSource.NETEASE)CloudXMusicClient.STATE.refreshLibraryIfNeeded();});
        lifecycle=new PlaybackLifecycle(()->{
            try{playback.stop();}finally{CloudXMusicClient.STATE.stopAll();}
        });
        playback.sourceGuards(()->lifecycle.allowsPlayback()&&coordinator.allowsPlayback(MusicSource.QQ),
                ()->lifecycle.allowsPlayback()&&coordinator.allowsPlayback(MusicSource.NETEASE));
        QQMusicConfig config=QQMusicConfig.forCurrentInstance();
        preferences=new QQPreferences(config,Async::main,view->{playback.quality(view.quality());playback.volume(view.volume());});
        terms=new QQTermsConsent(config,Async::main,accounts::restore);
        playback.neteaseFailure(dev.lzasn.cloudxmusic.client.CloudXMusicClient.STATE::playbackHandoffFailed);
        dev.lzasn.cloudxmusic.client.CloudXMusicClient.STATE.playbackHandoff(playback::beforeNetease);
        // START 先于原有 END tick 的自动切歌；TitleScreen 及无世界状态均撤销播放权。
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(client->
                lifecycle.update(client.level!=null,client.screen instanceof net.minecraft.client.gui.screens.TitleScreen));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(artworkAccount!=accounts.revision()){artworkAccount=accounts.revision();artwork.clear();}
            playback.tick();boolean active=coordinator.allowsPlayback(MusicSource.QQ);
            presentation.update(playback.view().song(),active);
            queueMetadata.update(playback.queue(),active);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            lifecycle.close();
            coordinator.close();
            presentation.close();
            queueMetadata.close();
            QQCatalogController.closeAll();
            artwork.close();
            terms.close();
            preferences.close();
            playback.close();
            login.close();
            QQPlaybackProbeController.closeAll();
            accounts.close();
            QQSearchController.closeAll();
            QQSearchMetadata.closeAll();
            QQSearchPainter.clear();
        });
        registered = true;
    }
    public static QQLoginUi createLoginUi() {
        if (!registered) throw new IllegalStateException("QQ 模块尚未初始化");
        return new QQLoginUi(accounts,login,artwork::image,terms);
    }
    public static QQPlaybackController playback(){if(!registered)throw new IllegalStateException("QQ 模块尚未初始化");return playback;}
    public static MusicSourceCoordinator coordinator(){return coordinator;}
    public static boolean isQq(){return registered&&coordinator.view().source()==MusicSource.QQ;}
    public static boolean neteaseActive(){return !registered||lifecycle.allowsPlayback()&&coordinator.allowsPlayback(MusicSource.NETEASE);}
    public static void worldJoined(){if(lifecycle!=null){lifecycle.joined();lifecycle.update(true,false);}}
    public static void stopForMenu(){if(lifecycle!=null)lifecycle.disconnect();else CloudXMusicClient.STATE.stopAll();}
    public static QQArtworkCache artwork(){return artwork;}
    public static QQSearchMetadata createSearchMetadata(){
        QQProvider provider=(QQProvider)OmniRouter.getProvider("qq");
        return new QQSearchMetadata(new QQQueueMetadata(provider::details,Async::main,accounts::revision,artwork::prefetch));
    }
    public static QQCatalogController createCatalog(){
        return new QQCatalogController(active->{
            var session=accounts.view().loggedIn()?accounts.snapshot():null;
            return new QQCatalogService(session==null?null:session.credentials(),()->active.getAsBoolean()&&(session==null||accounts.isCurrent(session)));
        },Async::main,accounts::revision,()->coordinator.allowsPlayback(MusicSource.QQ),playback);
    }
    public static SongView queueSong(UnifiedSong song){return queueMetadata.song(song);}
    public static String queueStatus(){return queueMetadata.status();}
    public static QQAccountManager.View account(){return accounts.view();}
    public static void quality(QQQuality value){playback.quality(value);savePreferences();}
    public static void volume(float value){
        if(!Float.isFinite(value))return;
        float volume=Math.max(0,Math.min(1,value));playback.volume(volume);
        preferences.update(playback.preferredQuality(),volume,preferences.view().lyrics());
    }
    public static void cycleLyrics(){var v=preferences.view();preferences.update(playback.preferredQuality(),v.volume(),v.lyrics().next());}
    public static void toggleTranslation(){var v=preferences.view();preferences.update(playback.preferredQuality(),v.volume(),QQTranslationControl.next(v.lyrics()));}
    private static void savePreferences(){preferences.update(playback.preferredQuality(),preferences.view().volume(),preferences.view().lyrics());}
    public static PlaybackSnapshot snapshot(){
        if(!isQq())return netease.snapshot();
        var a=playback.view();var p=presentation.view();var pref=preferences.view();
        SongView cached=a.song()==null?SongView.empty(MusicSource.QQ):queueSong(a.song());
        SongView display=a.song()==null?cached:p.song().platformId().equals(a.song().platformId())&&!p.song().coverUrl().isBlank()?p.song():cached;
        SongView song=a.duration()>0&&a.song()!=null?new SongView(display.source(),display.platformId(),display.title(),display.artist(),display.album(),(int)Math.ceil(a.duration()),display.coverUrl(),display.vip()||cached.vip()):display;
        String status=a.error()||a.loading()?a.status():!p.status().isBlank()?p.status():!pref.error().isBlank()?pref.error():a.status();
        var route=coordinator.view();if(route.switching()||route.error())status=route.status();
        double duration=a.song()==null?0:a.duration()>0?a.duration():song.durationSeconds();
        return new PlaybackSnapshot(MusicSource.QQ,song,playback.queue().stream().map(s->s.equals(a.song())?song:queueSong(s)).toList(),
                a.song()!=null&&song.platformId().equals(p.song().platformId())?p.lyrics():java.util.List.of(),a.playing(),a.loading(),a.position(),duration,a.volume(),
                a.song()==null?playback.preferredQuality().barLabel():a.quality(),playback.preferredQuality().label(),pref.lyrics(),playback.mode(),false,status,a.error(),a.song()!=null&&a.song().platformId().equals(p.song().platformId())?p.timed():java.util.List.of());
    }
    public static QQPlaybackProbeController createPlaybackProbe(java.util.function.BiConsumer<dev.lzasn.cloudxmusic.client.omni.UnifiedSong,String> success) {
        if (!registered) throw new IllegalStateException("QQ 模块尚未初始化");
        return new QQPlaybackProbeController(Async::main, accounts::revision, success);
    }
}
