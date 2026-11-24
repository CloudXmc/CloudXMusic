package dev.lzasn.cloudxmusic.client.room;

import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackController;
import dev.lzasn.cloudxmusic.client.state.PlaybackMode;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.BiConsumer;

/** 仅消费服务端时间轴，不发送控制包。所有状态均由 Minecraft 客户端线程拥有。 */
public final class QQRoomPlaybackBridge {
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("CloudXMusic/QQRoomPlayback");
    private static final double DRIFT_SECONDS=4;
    private static final long SEEK_COOLDOWN=TimeUnit.SECONDS.toNanos(10);
    private static final long SERVER_SEEK_COOLDOWN=TimeUnit.SECONDS.toNanos(3);
    private final QQPlaybackController player;
    private final LongSupplier clock;
    private final BiConsumer<String,Long> started,finished;
    private String mid="";
    private boolean wanted,applying;
    private long lastSeekAt,appliedSeekRevision;

    public QQRoomPlaybackBridge(QQPlaybackController player){this(player,System::nanoTime);}
    public QQRoomPlaybackBridge(QQPlaybackController player,LongSupplier clock){
        this(player,clock,(mid,revision)->{},(mid,revision)->{});
    }
    public QQRoomPlaybackBridge(QQPlaybackController player,LongSupplier clock,BiConsumer<String,Long> started,BiConsumer<String,Long> finished){
        this.player=Objects.requireNonNull(player);this.clock=Objects.requireNonNull(clock);
        this.started=Objects.requireNonNull(started);this.finished=Objects.requireNonNull(finished);
    }

    public void follow(UnifiedSong song,boolean playing,double position,PlaybackMode mode,long seekRevision){
        if(applying||song==null)return;
        applying=true;
        try{
            player.roomMode(true);player.roomRepeatMode(mode);
            boolean changed=!mid.equals(song.platformId())||wanted!=playing;
            if(playing&&wanted&&!mid.isEmpty()&&!mid.equals(song.platformId()))logTimelineReplacement(song.platformId(),position,seekRevision);
            mid=song.platformId();wanted=playing;
            long now=clock.getAsLong();
            String songMid=song.platformId();
            Runnable onStarted=()->started.accept(songMid,seekRevision),onFinished=()->finished.accept(songMid,seekRevision);
            if(changed){
                appliedSeekRevision=seekRevision;lastSeekAt=now;
                if(playing)player.playRoomTrack(song,position,onStarted,onFinished);else player.roomPause();
                return;
            }
            var local=player.view();
            // 同一广播与加载中的回声直接忽略，不能取消取链、再播放或反向发包。
            if(!playing)return;
            boolean serverSeek=seekRevision!=appliedSeekRevision;
            if(serverSeek){
                // 自然结束后的权威重播不能被 seek 冷却吞掉，单首短曲也必须能循环。
                if(!player.finished()&&now-lastSeekAt<SERVER_SEEK_COOLDOWN)return;
                if(position<1&&local.position()>1)logTimelineReplacement(song.platformId(),position,seekRevision);
                appliedSeekRevision=seekRevision;lastSeekAt=now;player.roomSeek(position,onStarted,onFinished);return;
            }
            if(local.loading()||local.error()||player.seeking())return;
            // 曲末等待服务端的下一首/时间轴重置，不在本机重复播放或自动切歌。
            if(player.finished())return;
            if(!local.playing()){
                lastSeekAt=now;player.playRoomTrack(song,position,onStarted,onFinished);return;
            }
            if(now-lastSeekAt<SEEK_COOLDOWN||Math.abs(local.position()-position)<=DRIFT_SECONDS)return;
            lastSeekAt=now;player.roomSeek(position,onStarted,onFinished);
        }finally{applying=false;}
    }

    /** 仅记录权威换曲/重播与真实 EOF 的先后关系，手动切歌也会出现，不能据此断言服务端故障。 */
    private void logTimelineReplacement(String nextMid,double target,long revision){
        var local=player.view();
        if(!local.playing()||player.finished())return;
        LOGGER.info("[房间时间轴] 收到换曲/重播时本机尚未自然 EOF：MID={} nextMID={} localPosition={}s localDuration={}s target={}s revision={}",
                mid,nextMid,local.position(),local.duration(),target,revision);
    }

    public void clear(){mid="";wanted=false;appliedSeekRevision=0;lastSeekAt=0;player.roomPause();}
}
