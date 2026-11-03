package dev.lzasn.cloudxmusic.client.room;

import dev.lzasn.cloudxmusic.client.gui.design.MusicView;
import dev.lzasn.cloudxmusic.client.network.CloudXMusicPayload;
import dev.lzasn.cloudxmusic.client.omni.SongView;
import dev.lzasn.cloudxmusic.client.state.Track;
import dev.lzasn.cloudxmusic.protocol.MemberState;
import dev.lzasn.cloudxmusic.protocol.MusicSourceType;
import dev.lzasn.cloudxmusic.protocol.Packet;
import dev.lzasn.cloudxmusic.protocol.PacketCodec;
import dev.lzasn.cloudxmusic.protocol.PlaybackAction;
import dev.lzasn.cloudxmusic.protocol.ProtocolException;
import dev.lzasn.cloudxmusic.protocol.RejectCode;
import dev.lzasn.cloudxmusic.protocol.RepeatMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端房间会话：只保存不可变展示状态，不把网络容器带进绘制层。
 *
 * <p>播放进度不按帧同步：服务端推送「发出时的位置」，这里记下本地收到时刻，
 * 之后用本地时钟外推，每 10 秒左右被下一次推送校准。这样两端时钟不需要对齐。</p>
 */
@Environment(EnvType.CLIENT)
public final class MusicRoomClient {
    public static final MusicRoomClient INSTANCE = new MusicRoomClient();
    private static final Logger LOGGER = LoggerFactory.getLogger("CloudXMusic/Room");
    private static final String VERSION = FabricLoader.getInstance().getModContainer("cloudxmusic")
            .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    private final AtomicLong requestIds = new AtomicLong(1L);
    private boolean registered;
    private boolean inWorld;
    private boolean ready;
    private boolean unadvertisedSendLogged;
    private long lastHelloAttemptNanos;
    private String status = "未连接房间服务";
    private MusicSourceType selectedSource = MusicSourceType.NETEASE;
    private List<Packet.RoomSummary> publicRooms = List.of();
    private Packet.RoomSnapshot currentRoom;
    private List<Packet.RoomTrack> queue = List.of();
    private int index = -1;
    private boolean playing;
    private int positionTenths;
    private long positionAtNanos;
    private int durationSeconds;
    private RepeatMode repeatMode = RepeatMode.LIST;
    /** 成员本地暂停跟听：只影响自己，不下发给服务端，也不影响房主和其他成员。 */
    private boolean localPaused;
    private int pausedAtTenths;
    /** 成员恢复跟听、等待服务端补发一次权威队列与位置。 */
    private boolean awaitingSync;
    private long awaitingSince;
    /** 创建房间时带上去的个人队列，等 RoomJoined 确认后再一次性提交。 */
    private List<Packet.RoomTrack> pendingSeed = List.of();
    /** 上一次推送的房间队列签名，用来等回推、避免连发。 */
    private String lastPushedKey = "";
    private long lastPushedAt;

    private MusicRoomClient() {}

    public void register() {
        if (registered) return;
        registered = true;
        CloudXMusicPayload.register();
        ClientPlayNetworking.registerGlobalReceiver(CloudXMusicPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload.data())));
    }

    public void joinedWorld() {
        inWorld = true;
        ready = false;
        currentRoom = null;
        publicRooms = List.of();
        clearTimeline();
        lastHelloAttemptNanos = 0L;
        unadvertisedSendLogged = false;
        status = "正在连接房间服务";
        tick();
    }

    public void disconnected() {
        inWorld = false;
        ready = false;
        currentRoom = null;
        publicRooms = List.of();
        pendingSeed = List.of();
        clearTimeline();
        status = "未连接房间服务";
    }

    public MusicSourceType selectedSource() {
        return selectedSource;
    }

    public void selectSource(MusicSourceType source) {
        if (source == null || currentRoom != null) return;
        selectedSource = source;
    }

    public String status() {
        return status;
    }

    /**
     * 代理层可能不会把服务端的通道注册转发给客户端。此时 canSend() 会是 false，
     * 但自定义负载本身仍可被通透转发，因此未握手时绕过该探测并定时重试 HELLO。
     */
    public void tick() {
        if (!inWorld || ready) return;
        long now = System.nanoTime();
        if (lastHelloAttemptNanos != 0L && now - lastHelloAttemptNanos < 2_000_000_000L) return;
        sendHello();
    }

    public boolean joined() {
        return currentRoom != null;
    }

    /** 只有房主能改队列和时间轴；成员只跟随。 */
    public boolean owner() {
        if (currentRoom == null) return false;
        String name = localName();
        return !name.isBlank() && name.equals(currentRoom.ownerId());
    }

    public List<Packet.RoomSummary> publicRooms() {
        return publicRooms;
    }

    public void requestRooms() {
        if (!ready) {
            sendHello();
            return;
        }
        send(new Packet.ListRooms(nextRequestId()));
    }

    public void createRoom(MusicSourceType source, boolean loggedIn) {
        createRoom(source, loggedIn, List.of());
    }

    /**
     * 创建房间并把当前个人队列直接作为房间队列提交，不需要额外点「导入」。
     * seed 会按房间音源过滤，混源部分丢弃。
     */
    public void createRoom(MusicSourceType source, boolean loggedIn, List<SongView> seed) {
        if (!ready) {
            status = "尚未连接房间服务";
            return;
        }
        if (source == null) return;
        selectedSource = source;
        if (!loggedIn) {
            status = "请先登录对应音源";
            return;
        }
        pendingSeed = filterSeed(seed, source);
        // 房间没有「加歌」入口，空队列的房间会连播放按钮都没反应，所以这里直接拦住。
        if (pendingSeed.isEmpty()) {
            status = "个人队列为空：先播放或加入几首歌再创建房间";
            return;
        }
        status = "正在创建房间";
        send(new Packet.CreateRoom(nextRequestId(), source, loggedIn));
    }

    public void joinRoom(String roomCode, MusicSourceType source, boolean loggedIn) {
        if (!ready) {
            status = "尚未连接房间服务";
            return;
        }
        String code = roomCode == null ? "" : roomCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) {
            status = "请输入房间码";
            return;
        }
        status = loggedIn ? "正在加入房间" : "请先登录对应音源";
        send(new Packet.JoinRoom(nextRequestId(), code, source, loggedIn));
    }

    public void leaveRoom() {
        if (currentRoom == null) return;
        send(new Packet.LeaveRoom(nextRequestId()));
    }

    // ------------------------------------------------------------ 房主控制

    /** 按房间音源过滤个人队列并转成房间条目；混源部分直接丢弃。 */
    private static List<Packet.RoomTrack> filterSeed(List<SongView> songs, MusicSourceType roomSource) {
        if (songs == null || songs.isEmpty()) return List.of();
        return songs.stream()
                .filter(song -> song != null && songSource(song) == roomSource)
                .map(MusicRoomClient::toRoomTrack)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 房主：让房间队列跟着个人播放队列走。加歌、叉号移除、播放全部都会在下一次 tick 同步过来。
     * 会尽量保住正在播的那一首与播放位置，避免每次加删歌都把房间从头开始。
     */
    public void syncOwnerQueue(List<SongView> songs) {
        if (!owner() || currentRoom == null || songs == null) return;
        // 首包（创建房间时的 pendingSeed）由 RoomJoined 负责，这里等它回推后再接管。
        if (queue.isEmpty()) return;
        List<Packet.RoomTrack> tracks = filterSeed(songs, currentRoom.source());
        // 个人队列被清空时不动房间：否则会把所有人正在听的东西一起清掉。
        if (tracks.isEmpty()) return;
        String desired = signature(tracks);
        if (desired.equals(signature(queue))) {
            lastPushedKey = "";
            return;
        }
        long now = System.currentTimeMillis();
        // 推送后要等服务端回推，避免同一份队列连发好几包。
        if (desired.equals(lastPushedKey) && now - lastPushedAt < 1_500L) return;
        lastPushedKey = desired;
        lastPushedAt = now;
        int keepIndex = currentTrackIndexIn(tracks);
        int position = positionTenths();
        send(new Packet.QueueSet(nextRequestId(), tracks, Math.max(0, keepIndex)));
        // 当前曲还在队列里的话，把房间位置也拉回原处，避免加删一首歌就从头播。
        if (keepIndex >= 0 && position > 0) {
            send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.SEEK, 0, position, null));
        }
    }

    /** 当前房间曲在新队列里的下标；找不到（被删掉了）返回 -1。 */
    private int currentTrackIndexIn(List<Packet.RoomTrack> tracks) {
        if (index < 0 || index >= queue.size()) return -1;
        Packet.RoomTrack current = queue.get(index);
        String key = current.ref().source().name() + ":" + current.ref().songId();
        for (int i = 0; i < tracks.size(); i++) {
            Packet.RoomTrack candidate = tracks.get(i);
            if (key.equals(candidate.ref().source().name() + ":" + candidate.ref().songId())) return i;
        }
        return -1;
    }

    private static String signature(List<Packet.RoomTrack> tracks) {
        StringBuilder out = new StringBuilder(tracks.size() * 24);
        for (Packet.RoomTrack track : tracks) {
            out.append(track.ref().source().name()).append(':').append(track.ref().songId()).append('|');
        }
        return out.toString();
    }

    /**
     * 播放/暂停。房主下发给服务端推进房间时间轴；成员只切换自己的跟听状态：
     * 暂停后本机停在当前进度，不影响房主和其他成员；再次播放会重新对齐房间时间轴。
     */
    public void togglePlay() {
        LOGGER.info("[房间] 播放键：owner={} index={} queue={} serverPlaying={} localPaused={}",
                owner(), index, queue.size(), playing, localPaused);
        if (currentRoom == null) return;
        if (index < 0) {
            // 队列空时明确给出提示，别让按钮看起来"点了没反应"。
            status = "房间队列还是空的";
            return;
        }
        if (owner()) {
            boolean sent = send(new Packet.PlaybackControl(nextRequestId(),
                    playing ? PlaybackAction.PAUSE : PlaybackAction.PLAY, 0, 0, null));
            LOGGER.info("[房间] 下发 {}，发送={}", playing ? "PAUSE" : "PLAY", sent);
            return;
        }
        if (localPaused) {
            localPaused = false;
            // 暂停期间不同步房间队列，恢复时先等服务端补发一次权威快照再出声。
            awaitingSync = true;
            awaitingSince = System.currentTimeMillis();
            status = "正在同步房间时间轴";
            send(new Packet.MemberStateUpdate(nextRequestId(), MemberState.LISTENING));
            return;
        }
        pausedAtTenths = positionTenths();
        localPaused = true;
        status = "暂停跟听";
        // 上报给服务端，房间成员列表里其他人也能看到「暂停跟听」。
        send(new Packet.MemberStateUpdate(nextRequestId(), MemberState.PAUSED));
    }

    /** 是否处于「暂停跟听」：成员本地暂停，不代表房间停了。 */
    public boolean localPaused() {
        return localPaused;
    }

    /** 正在等恢复跟听后的那次补发；超时兜底，避免丢包后一直不出声。 */
    public boolean awaitingSync() {
        if (!awaitingSync) return false;
        if (System.currentTimeMillis() - awaitingSince > 3_000L) {
            awaitingSync = false;
            return false;
        }
        return true;
    }

    public void next() {
        if (!owner() || index < 0) return;
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.NEXT, 0, 0, null));
    }

    public void previous() {
        if (!owner() || index < 0) return;
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.PREVIOUS, 0, 0, null));
    }

    public void jump(int queueIndex) {
        if (!owner() || queueIndex < 0 || queueIndex >= queue.size()) return;
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.JUMP, queueIndex, 0, null));
    }

    /** fraction 是进度条上的 0..1 比例，换算成 0.1 秒单位后交给服务端。 */
    public void seek(float fraction) {
        if (!owner() || index < 0 || durationSeconds <= 0) return;
        int tenths = Math.round(Math.max(0f, Math.min(1f, fraction)) * durationSeconds * 10);
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.SEEK, 0, tenths, null));
    }

    public void cycleRepeat() {
        if (!owner()) return;
        RepeatMode next = switch (repeatMode) {
            case LIST -> RepeatMode.SINGLE;
            case SINGLE -> RepeatMode.SHUFFLE;
            case SHUFFLE -> RepeatMode.LIST;
        };
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.REPEAT, 0, 0, next));
    }

    public RepeatMode repeatMode() {
        return repeatMode;
    }

    /** 房间是否处于播放状态（房主下发的权威状态，不含成员的本地暂停）。 */
    public boolean serverPlaying() {
        return playing;
    }

    /** 房间固定音源；不在房间时为 null。 */
    public MusicSourceType roomSource() {
        return currentRoom == null ? null : currentRoom.source();
    }

    /** 房间队列长度，用来判断「队列页下标」和「房间下标」是否一一对应。 */
    public int queueSize() {
        return queue.size();
    }

    /** 房间当前曲目的原始条目；没有队列或下标失效时返回 null。 */
    public Packet.RoomTrack currentRoomTrack() {
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    /** 房间时间轴当前进度，毫秒。 */
    public long positionMs() {
        return positionTenths() * 100L;
    }

    /** 主动上报自己的状态（跟播失败时置为 UNAVAILABLE）。 */
    public void reportSelfState(MemberState state) {
        if (currentRoom == null || state == null) return;
        send(new Packet.MemberStateUpdate(nextRequestId(), state));
    }

    /** 本机是否在出声：房间在播且自己没有暂停跟听。 */
    public boolean playing() {
        return playing && !localPaused;
    }

    /** 房间当前曲的展示对象；没有队列或下标失效时返回 null。 */
    public SongView nowSong() {
        if (index < 0 || index >= queue.size()) return null;
        Packet.RoomTrack track = queue.get(index);
        var source = track.ref().source() == MusicSourceType.QQ
                ? dev.lzasn.cloudxmusic.client.omni.MusicSource.QQ
                : dev.lzasn.cloudxmusic.client.omni.MusicSource.NETEASE;
        return new SongView(source, track.ref().songId(), track.title(), track.artist(), "",
                track.durationSeconds(), track.coverUrl(), track.vip());
    }

    /** 房间循环模式映射到播放页那套三档枚举，图标与文案直接复用。 */
    public dev.lzasn.cloudxmusic.client.state.PlaybackMode playbackMode() {
        return switch (repeatMode) {
            case SINGLE -> dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_ONE;
            case SHUFFLE -> dev.lzasn.cloudxmusic.client.state.PlaybackMode.SHUFFLE;
            case LIST -> dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST;
        };
    }

    /** 房间当前曲是否已收藏；QQ 曲目暂不支持收藏，恒为 false。 */
    public boolean favoriteCurrent() {
        Track track = currentTrack();
        return track != null && dev.lzasn.cloudxmusic.client.CloudXMusicClient.STATE.isFavorite(track);
    }

    /**
     * 收藏房间当前曲。房间只同步公开 ID，所以用标题/歌手构造一个最小 Track，
     * 走与个人播放完全相同的网易云收藏链路。
     */
    public void toggleFavorite() {
        Packet.RoomTrack current = index >= 0 && index < queue.size() ? queue.get(index) : null;
        if (current == null) return;
        if (current.ref().source() != MusicSourceType.NETEASE) {
            status = "房间内暂不支持收藏 QQ 音乐曲目";
            return;
        }
        Track track = currentTrack();
        if (track == null) return;
        dev.lzasn.cloudxmusic.client.CloudXMusicClient.STATE.toggleFavorite(track);
    }

    private Track currentTrack() {
        if (index < 0 || index >= queue.size()) return null;
        Packet.RoomTrack current = queue.get(index);
        if (current.ref().source() != MusicSourceType.NETEASE) return null;
        return new Track(current.title(), current.artist(), current.durationSeconds(),
                current.ref().songId(), "", "");
    }

    // ------------------------------------------------------------ 展示快照

    /** 本地外推的当前位置，单位为 0.1 秒；暂停跟听时停在暂停那一刻。 */
    public int positionTenths() {
        if (localPaused) return pausedAtTenths;
        if (!playing) return positionTenths;
        long elapsed = System.nanoTime() - positionAtNanos;
        if (elapsed <= 0) return positionTenths;
        int advance = (int) (elapsed / 100_000_000L);
        int value = positionTenths + advance;
        return durationSeconds > 0 ? Math.min(durationSeconds * 10, value) : value;
    }

    public MusicView.RoomView view() {
        String source = sourceLabel(selectedSource);
        List<MusicView.RoomSummary> rooms = publicRooms.stream()
                .map(room -> new MusicView.RoomSummary(room.roomCode(), room.ownerId(),
                        sourceLabel(room.source()), room.members(), room.playing() ? "播放中" : "等待加入"))
                .toList();
        if (currentRoom == null) {
            return new MusicView.RoomView(false, "------", source, status,
                    "", "", 0, 0, false, false, List.of(), List.of(), rooms, 0, -1, repeatLabel(), 20, 0);
        }
        // 成员状态由服务端广播，所以别人暂停跟听时这里也能看到。
        List<MusicView.RoomMember> members = currentRoom.members().stream()
                .map(member -> {
                    // 房主的状态就是房间在不在播，不看他本地上报的跟播状态，
                    // 否则房主自己取链慢一点，全场会看到「房主 · 本曲不可播放」。
                    if (member.owner()) {
                        return new MusicView.RoomMember(member.playerId(),
                                playing ? "正在播放" : "已暂停", true, MusicView.MemberTone.ACTIVE);
                    }
                    String detail = switch (member.state()) {
                        case PAUSED -> "暂停跟听";
                        case BUFFERING -> "缓冲中";
                        case UNAVAILABLE -> "本曲不可播放";
                        case LISTENING -> playing ? "正在跟听" : "等待房主继续";
                    };
                    return new MusicView.RoomMember(member.playerId(), detail, false,
                            switch (member.state()) {
                                case PAUSED -> MusicView.MemberTone.PAUSED;
                                case UNAVAILABLE -> MusicView.MemberTone.UNAVAILABLE;
                                case LISTENING, BUFFERING -> MusicView.MemberTone.ACTIVE;
                            });
                })
                .toList();
        List<MusicView.RoomTrack> tracks = queue.stream()
                .map(track -> new MusicView.RoomTrack(track.title(), track.artist(),
                        Track.format(track.durationSeconds()), track.coverUrl()))
                .toList();
        String title = "";
        String artist = "";
        int duration = durationSeconds;
        if (index >= 0 && index < queue.size()) {
            Packet.RoomTrack track = queue.get(index);
            title = track.title();
            artist = track.artist();
            if (duration <= 0) duration = track.durationSeconds();
        }
        String roomStatus = status;
        if (localPaused) roomStatus = "暂停跟听";
        else if (queue.isEmpty()) roomStatus = "等待房主的队列";
        else if (!playing) roomStatus = "已暂停 · 等待房主继续";
        return new MusicView.RoomView(true, currentRoom.roomCode(), sourceLabel(currentRoom.source()),
                roomStatus, title, artist, positionTenths(), duration, playing(), owner(),
                members, tracks, rooms, 0, index, repeatLabel(), currentRoom.capacity(), 0);
    }

    private String repeatLabel() {
        return switch (repeatMode) {
            case LIST -> "列表循环";
            case SINGLE -> "单曲循环";
            case SHUFFLE -> "随机播放";
        };
    }

    /** 房间队列的 Track 视图，供「播放队列」页只读展示。 */
    public List<Track> queueTracks() {
        List<Track> out = new java.util.ArrayList<>(queue.size());
        for (Packet.RoomTrack track : queue) {
            String id = track.ref().source() == MusicSourceType.NETEASE ? track.ref().songId() : null;
            out.add(new Track(track.title(), track.artist(), track.durationSeconds(), id, "", track.coverUrl()));
        }
        return out;
    }

    private void clearTimeline() {
        queue = List.of();
        index = -1;
        playing = false;
        positionTenths = 0;
        positionAtNanos = System.nanoTime();
        durationSeconds = 0;
        repeatMode = RepeatMode.LIST;
        localPaused = false;
        pausedAtTenths = 0;
        awaitingSync = false;
    }

    private void receive(byte[] bytes) {
        try {
            Packet packet = PacketCodec.decode(bytes);
            switch (packet) {
                case Packet.Welcome welcome -> {
                    ready = true;
                    status = "房间服务已连接 · " + welcome.serverVersion();
                    requestRooms();
                }
                case Packet.Rejected rejected -> {
                    status = rejection(rejected.code());
                    LOGGER.warn("[房间] 请求被拒绝：{}", rejected.code());
                    if (rejected.code() == RejectCode.HANDSHAKE_REQUIRED) {
                        ready = false;
                        sendHello();
                    }
                }
                case Packet.RoomList list -> {
                    publicRooms = list.rooms();
                    status = publicRooms.isEmpty() ? "暂无公开房间" : "已获取公开房间";
                }
                case Packet.RoomJoined joined -> {
                    currentRoom = joined.room();
                    clearTimeline();
                    status = "已加入房间";
                    // 新建房间时把带上来的个人队列一次性提交，房间从此以服务端队列为准。
                    if (!pendingSeed.isEmpty()) {
                        if (owner()) send(new Packet.QueueSet(nextRequestId(), pendingSeed, 0));
                        pendingSeed = List.of();
                    }
                }
                case Packet.RoomUpdated updated -> {
                    if (currentRoom != null && currentRoom.roomCode().equals(updated.room().roomCode()))
                        currentRoom = updated.room();
                }
                case Packet.RoomQueue queueUpdate -> {
                    if (currentRoom == null) break;
                    // 暂停跟听期间不同步队列：成员此刻的界面与进度都停在暂停那一刻，
                    // 恢复时由服务端为这个人单独补发一次快照。
                    if (localPaused) break;
                    queue = queueUpdate.tracks();
                    index = queueUpdate.index();
                    LOGGER.info("[房间] 收到队列：{} 首，当前下标 {}", queue.size(), index);
                    if (queue.isEmpty()) {
                        index = -1;
                        playing = false;
                        positionTenths = 0;
                        durationSeconds = 0;
                    }
                }
                case Packet.RoomPlayback playback -> {
                    if (currentRoom == null) break;
                    if (localPaused) break;
                    awaitingSync = false;
                    playing = playback.playing();
                    positionTenths = playback.positionTenths();
                    positionAtNanos = System.nanoTime();
                    durationSeconds = playback.durationSeconds();
                    repeatMode = playback.repeatMode();
                }
                case Packet.RoomLeft left -> {
                    currentRoom = null;
                    clearTimeline();
                    status = "已退出房间 " + left.roomCode();
                    requestRooms();
                }
                case Packet.Pong ignored -> { }
                default -> { }
            }
        } catch (ProtocolException error) {
            status = "房间服务返回了无效数据";
            LOGGER.warn("拒绝无效房间协议包：{}", error.code());
        }
    }

    private void sendHello() {
        lastHelloAttemptNanos = System.nanoTime();
        send(new Packet.Hello(nextRequestId(), VERSION));
    }

    private boolean send(Packet packet) {
        boolean advertised = ClientPlayNetworking.canSend(CloudXMusicPayload.TYPE);
        try {
            ClientPlayNetworking.send(new CloudXMusicPayload(PacketCodec.encode(packet)));
            if (!advertised && !unadvertisedSendLogged) {
                unadvertisedSendLogged = true;
                LOGGER.info("[房间] 未收到服务端通道注册，已改用透明发送重试握手");
            }
            return true;
        } catch (ProtocolException error) {
            status = "无法编码房间请求";
            LOGGER.warn("房间请求编码失败：{}", error.code());
            return false;
        } catch (RuntimeException error) {
            status = "房间通道发送失败";
            LOGGER.warn("房间请求发送失败：{}", error.getMessage());
            return false;
        }
    }

    private long nextRequestId() {
        return requestIds.getAndIncrement();
    }

    private static String localName() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.getUser() == null ? "" : minecraft.getUser().getName();
    }

    /** 个人队列里的歌曲转成房间条目；缺少平台 ID 的占位行直接丢弃。 */
    private static Packet.RoomTrack toRoomTrack(SongView song) {
        if (song == null || song.platformId() == null || song.platformId().isBlank()) return null;
        try {
            return new Packet.RoomTrack(
                    new dev.lzasn.cloudxmusic.protocol.SongRef(songSource(song), song.platformId()),
                    song.title(), song.artist(), song.durationSeconds(), song.coverUrl(), song.vip());
        } catch (IllegalArgumentException invalid) {
            // 平台 ID 不符合线协议字符集时跳过该曲，不因此中断整份导入。
            return null;
        }
    }

    private static MusicSourceType songSource(SongView song) {
        return song.source() == dev.lzasn.cloudxmusic.client.omni.MusicSource.QQ
                ? MusicSourceType.QQ : MusicSourceType.NETEASE;
    }

    private static String sourceLabel(MusicSourceType source) {
        return source == MusicSourceType.QQ ? "QQ 音乐" : "网易云音乐";
    }

    private static String rejection(RejectCode code) {
        return switch (code) {
            case ROOM_NOT_FOUND -> "房间不存在";
            case ROOM_FULL -> "房间人数已满";
            case SOURCE_MISMATCH -> "房间音源不匹配";
            case LOGIN_REQUIRED -> "请先登录对应音源";
            case ALREADY_IN_ROOM -> "你已经在房间中";
            case NOT_IN_ROOM -> "当前不在房间中";
            case INVALID_ROOM_CODE -> "房间码格式无效";
            case ROOM_LIMIT_REACHED -> "房间数量已达上限";
            case NOT_ROOM_OWNER -> "只有房主可以控制房间播放";
            case QUEUE_EMPTY -> "房间队列还是空的";
            case ROOM_QUEUE_FULL -> "房间队列已满（最多 50 首）";
            case INVALID_QUEUE_INDEX -> "队列位置无效";
            case UNSUPPORTED_VERSION -> "客户端与房间服务协议版本不兼容";
            case HANDSHAKE_REQUIRED -> "正在重新连接房间服务";
            case RATE_LIMITED -> "操作太频繁，请稍后再试";
            case MALFORMED_PACKET -> "房间请求格式无效";
        };
    }
}
