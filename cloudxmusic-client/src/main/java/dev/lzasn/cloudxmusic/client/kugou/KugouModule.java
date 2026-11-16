package dev.lzasn.cloudxmusic.client.kugou;

import dev.lzasn.cloudxmusic.client.omni.*;
import dev.lzasn.cloudxmusic.client.state.LyricMode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** 核心模块装配。open 在后台读盘，attach/tick/状态操作在客户端线程；全局界面接入时复用同一个协调器。 */
public final class KugouModule implements AutoCloseable {
    private final KugouAccountStore accounts;
    private final KugouProvider provider;
    private final Consumer<Runnable> main;
    private final KugouLoginController login;
    private final KugouPresentation presentation;
    private final KugouFavoritesController favorites;
    private final KugouAccountDetails details;
    private final KugouRecommendController recommendations;
    private KugouPlaybackController playback;
    private PlaybackLifecycle lifecycle;
    private MusicSourceCoordinator coordinator;
    private LyricMode lyricMode=LyricMode.ORIGINAL;
    private KugouLibrary library;
    private boolean closed;
    private final Set<KugouSearchController> searches=new HashSet<>();
    public static KugouModule open(Path cloudHome,Consumer<Runnable> main)throws IOException {return new KugouModule(new KugouAccountStore(cloudHome.resolve("kugou/account.json")),main);}
    private KugouModule(KugouAccountStore accounts,Consumer<Runnable> main){
        this.accounts=accounts;this.main=main;provider=new KugouProvider(accounts);login=new KugouLoginController(accounts,main);presentation=new KugouPresentation(provider,main,accounts::revision);
        recommendations=new KugouRecommendController(new KugouRecommendApi(),main);
        favorites=new KugouFavoritesController(provider.favorites(),accounts::account,accounts::revision,main,KugouToast::show);details=new KugouAccountDetails(accounts,provider,main);
    }
    public void attach(MusicSourceCoordinator coordinator){
        if(closed||playback!=null)throw new IllegalStateException("酷狗核心模块不可重复装配");
        this.coordinator=Objects.requireNonNull(coordinator);
        playback=new KugouPlaybackController(provider,()->coordinator.pauseOthers(MusicSource.KUGOU),main,accounts::revision);
        playback.notices(KugouToast::show);
        lifecycle=new PlaybackLifecycle(playback::stop);
        playback.sourceGuard(()->!closed&&lifecycle.allowsPlayback()&&coordinator.allowsPlayback(MusicSource.KUGOU));
        coordinator.register(MusicSource.KUGOU,playback::suspend);
    }
    public KugouProvider provider(){return provider;}
    public dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistController createPlaylists(Consumer<String> notice){
        return new dev.lzasn.cloudxmusic.client.gui.playlist.PlaylistController(active->{
            var snapshot=accounts.account();long revision=accounts.revision();
            java.util.function.BooleanSupplier current=()->active.getAsBoolean()&&accounts.revision()==revision&&coordinator.allowsPlayback(MusicSource.KUGOU);
            return new KugouPlaylistSource(provider.playlists(snapshot,current),provider,playback(),current);
        },main,accounts::revision,()->accounts.account().loggedIn(),notice);
    }
    public KugouFavoritesController favorites(){return favorites;}
    public KugouAccountDetails details(){return details;}
    public KugouRecommendController recommendations(){return recommendations;}
    public void library(KugouLibrary library){this.library=Objects.requireNonNull(library);}
    public KugouAccount account(){return accounts.account();}
    public KugouLoginController login(){return login;}
    public KugouPlaybackController playback(){if(playback==null)throw new IllegalStateException("酷狗核心模块尚未装配");return playback;}
    public KugouSearchController createSearch(){if(closed)throw new IllegalStateException("酷狗核心模块已关闭");var search=new KugouSearchController(provider,main);searches.add(search);return search;}
    public void releaseSearch(KugouSearchController search){searches.remove(search);search.close();}
    public void tick(boolean inWorld,boolean titleScreen){if(closed||playback==null)return;lifecycle.update(inWorld,titleScreen);playback.tick();boolean active=coordinator.allowsPlayback(MusicSource.KUGOU);var song=playback.view().song();presentation.update(song,active);details.update(song,active);favorites.update(song,active);}
    public void disconnect(){if(lifecycle!=null)lifecycle.disconnect();}
    public void joined(){if(lifecycle!=null)lifecycle.joined();}
    public void lyricMode(LyricMode mode){lyricMode=Objects.requireNonNull(mode);}
    private SongView songView(UnifiedSong song){var track=provider.track(song);return new SongView(MusicSource.KUGOU,song.platformId(),song.title(),song.artist(),song.album(),track.durationSeconds(),track.coverUrl(),song.vip());}
    /** timedLyrics 与逐行歌词同源发布，可直接交给现有 3D/HUD 渲染消费。 */
    public PlaybackSnapshot snapshot(){
        var view=playback().view();var lyrics=presentation.view();SongView song=view.song()==null?SongView.empty(MusicSource.KUGOU):songView(view.song());
        boolean current=view.song()!=null&&lyrics.hash().equals(view.song().platformId());var route=coordinator.view();
        String status=route.switching()||route.error()?route.status():view.loading()||view.error()?view.status():!lyrics.status().isBlank()?lyrics.status():view.status();
        return new PlaybackSnapshot(MusicSource.KUGOU,song,playback.queue().stream().map(this::songView).toList(),current?lyrics.lyrics():List.of(),view.playing(),view.loading(),view.position(),view.duration()>0?view.duration():song.durationSeconds(),view.volume(),view.quality(),playback.preferredQuality().menuLabel(),lyricMode,playback.mode(),favorites.contains(view.song()),status,view.error(),current?lyrics.timed():List.of());
    }
    @Override public void close(){if(closed)return;closed=true;if(lifecycle!=null)lifecycle.close();searches.forEach(KugouSearchController::close);searches.clear();recommendations.close();favorites.close();details.close();presentation.close();login.close();if(playback!=null)playback.close();provider.close();}
}
