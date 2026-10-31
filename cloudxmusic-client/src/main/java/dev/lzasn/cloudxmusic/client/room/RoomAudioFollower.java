package dev.lzasn.cloudxmusic.client.room;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.omni.MusicSource;
import dev.lzasn.cloudxmusic.client.omni.UnifiedSong;
import dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration;
import dev.lzasn.cloudxmusic.client.qqmusic.QQPlaybackController;
import dev.lzasn.cloudxmusic.client.state.MusicState;
import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.protocol.MemberState;
import dev.lzasn.cloudxmusic.protocol.MusicSourceType;
import dev.lzasn.cloudxmusic.protocol.Packet;

import java.util.List;

/**
 * 房间跟播：每名成员用自己的账号取链、解码、出声，并对齐房间时间轴。
 *
 * <p>不新建第三套播放器：网易云走 {@link MusicState} 的声卡，QQ 走
 * {@link QQPlaybackController}，两者都保留原有的交接与代次隔离。
 * 服务端只提供「哪一首 + 什么位置 + 是否在播」，音频权限由成员自己的账号决定。</p>
 *
 * <p>失败（版权、会员、无可用地址）只影响本人：上报 {@link MemberState#UNAVAILABLE}
 * 并弹右上角提示，留在房间里等下一首，不踢人也不让房间跳歌。</p>
 */
public final class RoomAudioFollower {
    public static final RoomAudioFollower INSTANCE = new RoomAudioFollower();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("CloudXMusic/RoomAudio");
    /** 房间时钟与本机解码位置差超过这个值才纠正，避免一直和缓冲打架。 */
    private static final long DRIFT_TOLERANCE_MS = 4_000L;
    private static final long RESYNC_COOLDOWN_MS = 3_000L;

    private String activeKey = "";
    private String failedKey = "";
    private boolean sounding;
    private boolean wantSoundLastTick;
    private long lastActionAt;
    /** 音源不匹配只提示一次，避免每 tick 弹提示。 */
    private boolean sourceHintShown;
    /** 上一 tick 是否在房间里，用来在"刚离开"时做一次清理。 */
    private boolean joinedLastTick;

    private RoomAudioFollower() {}

    public void tick() {
        MusicRoomClient room = MusicRoomClient.INSTANCE;
        if (!room.joined()) {
            // 只要上一 tick 还在房间里，就无条件清理一次：避免残留的房间音频或标志位。
            if (joinedLastTick || sounding || !activeKey.isEmpty() || !failedKey.isEmpty()) shutdown();
            joinedLastTick = false;
            return;
        }
        if (!joinedLastTick) {
            // 刚进房间：先把本地个人播放掐掉，否则房间里还在放自己的歌。
            // 房间若在播，下面会按房间位置起播；若没在播，就安静等房主开始。
            LOGGER.info("[房间] 进入房间，停止本地播放");
            CloudXMusicClient.STATE.clearRoomPlayback();
            QQMusicIntegration.playback().stop();
        }
        joinedLastTick = true;
        // 房间内关掉个人队列的曲末自动切歌：切哪首由服务端决定。
        CloudXMusicClient.STATE.roomMode(true);
        // 房主：房间队列实时跟随个人播放队列（加歌、叉号移除、播放全部都会同步）。
        MusicSourceType roomType = room.roomSource();
        if (roomType != null && room.owner()) {
            room.syncOwnerQueue(roomType == MusicSourceType.QQ
                    ? QQMusicIntegration.playback().queue().stream()
                            .map(QQMusicIntegration::queueSong).toList()
                    : CloudXMusicClient.STATE.queue().stream()
                            .map(dev.lzasn.cloudxmusic.client.omni.SongView::from).toList());
        }
        Packet.RoomTrack track = room.currentRoomTrack();
        if (track == null) {
            if (sounding) shutdown();
            activeKey = "";
            return;
        }
        String key = key(track);
        // 房间音源在创建时固定。这里**不主动切换全局音源**：切源会暂停两边的声卡，
        // 交接失败还会把 coordinator 锁在 error（所有播放和主面板点击都会失效）。
        // 音源不一致时只提示用户自己去左侧账号区切换。
        MusicSource roomSource = track.ref().source() == MusicSourceType.QQ
                ? MusicSource.QQ : MusicSource.NETEASE;
        var coordinator = QQMusicIntegration.coordinator();
        if (coordinator.view().switching()) return;
        if (coordinator.source() != roomSource) {
            if (!sourceHintShown) {
                sourceHintShown = true;
                RoomToast.show("房间音源是" + (roomSource == MusicSource.QQ ? "QQ 音乐" : "网易云音乐")
                        + "，请先在左侧账号区切换音源");
            }
            return;
        }
        sourceHintShown = false;
        // 恢复跟听时先等服务端补发一次权威位置，避免从「暂停那一刻」的旧位置起播。
        boolean wantSound = room.serverPlaying() && !room.localPaused() && !room.awaitingSync();
        // 从「不出声」切回「要出声」时清掉失败记忆，重新给这次跟播一次机会。
        if (wantSound && !wantSoundLastTick) failedKey = "";
        wantSoundLastTick = wantSound;

        if (!wantSound) {
            if (sounding) pauseLocal(track);
            activeKey = key;
            return;
        }
        if (!sounding && key.equals(failedKey)) return;
        if (!sounding || !key.equals(activeKey)) {
            start(track, room.positionMs());
            return;
        }
        resyncIfDrifted(track, room.positionMs());
        checkPlayback(track);
    }

    /** 离开房间、断线、停用：停掉房间音频；个人队列随界面回到自己的状态。 */
    public void shutdown() {
        sounding = false;
        wantSoundLastTick = false;
        sourceHintShown = false;
        joinedLastTick = false;
        activeKey = "";
        failedKey = "";
        CloudXMusicClient.STATE.roomMode(false);
        CloudXMusicClient.STATE.clearRoomPlayback();
        QQMusicIntegration.playback().stop();
    }

    // ------------------------------------------------------------ 起播与暂停

    private void start(Packet.RoomTrack track, long roomMs) {
        activeKey = key(track);
        lastActionAt = System.currentTimeMillis();
        LOGGER.info("[房间] 跟播起播：{} - {} @{}ms", track.title(), track.ref().songId(), roomMs);
        if (track.ref().source() == MusicSourceType.NETEASE) {
            MusicState state = CloudXMusicClient.STATE;
            // 封面必须一起带进来：灵动岛和播放页的封面都读 state.current 的 coverUrl，
            // 只填 ID 的话会出现「有歌名、没封面」的占位图。
            sounding = state.playRoomTrack(new Track(track.title(), track.artist(), track.durationSeconds(),
                    track.ref().songId(), "", track.coverUrl(), track.vip()), roomMs);
            return;
        }
        QQPlaybackController qq = QQMusicIntegration.playback();
        // QQ 的播放受全局音源门禁限制；音源没切到 QQ 时抢不到声卡，按播不了处理。
        if (!QQMusicIntegration.coordinator().allowsPlayback(MusicSource.QQ)) {
            markUnavailable(track, "当前全局音源不是 QQ 音乐，无法跟播这首");
            return;
        }
        UnifiedSong song = new UnifiedSong("qq", track.ref().songId(), track.title(),
                track.artist(), "");
        qq.play(List.of(song), 0);
        if (roomMs > 1_500L) qq.seek(roomMs / 1000.0);
        sounding = true;
    }

    private void pauseLocal(Packet.RoomTrack track) {
        LOGGER.info("[房间] 停止本地跟播：{}", track.title());
        if (track.ref().source() == MusicSourceType.NETEASE) {
            CloudXMusicClient.STATE.pauseRoomPlayback();
        } else {
            QQPlaybackController qq = QQMusicIntegration.playback();
            if (qq.view().playing()) qq.toggle();
        }
        sounding = false;
    }

    // ------------------------------------------------------------ 对齐与失败判定

    private void resyncIfDrifted(Packet.RoomTrack track, long roomMs) {
        if (track.ref().source() != MusicSourceType.NETEASE) return;
        long now = System.currentTimeMillis();
        if (now - lastActionAt < RESYNC_COOLDOWN_MS) return;
        MusicState state = CloudXMusicClient.STATE;
        if (!state.roomAudioActive()) return;
        long localMs = (long) (state.position() * 1000f);
        if (Math.abs(localMs - roomMs) <= DRIFT_TOLERANCE_MS) return;
        lastActionAt = now;
        state.seek(roomMs / 1000f);
    }

    private void checkPlayback(Packet.RoomTrack track) {
        String key = key(track);
        if (key.equals(failedKey)) return;
        if (track.ref().source() == MusicSourceType.NETEASE) {
            MusicState state = CloudXMusicClient.STATE;
            // 只认「取链真的失败」这一个信号。之前按「起播后 6 秒内还没声音」猜，
            // 首次播放要跑 VIP 降级链 + 探测 + 拉歌词，很容易超时误报成播不了。
            if (state.lastFailureTrackId().equals(track.ref().songId())) {
                markUnavailable(track, "这首暂时播不了（版权/会员或无可用地址），已留在房间里等下一首");
            }
            return;
        }
        QQPlaybackController.View view = QQMusicIntegration.playback().view();
        if (view.error()) markUnavailable(track, "这首暂时播不了：" + view.status());
    }

    private void markUnavailable(Packet.RoomTrack track, String message) {
        sounding = false;
        failedKey = key(track);
        activeKey = key(track);
        LOGGER.warn("[房间] 跟播失败：{}（{}）", track.title(), message);
        MusicRoomClient.INSTANCE.reportSelfState(MemberState.UNAVAILABLE);
        RoomToast.show(message);
    }

    private static String key(Packet.RoomTrack track) {
        return track.ref().source().name() + ":" + track.ref().songId();
    }
}
