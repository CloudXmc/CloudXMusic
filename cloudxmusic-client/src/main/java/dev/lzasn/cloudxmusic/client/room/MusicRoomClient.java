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
    /** PacketCodec 的硬上限是 30；房主本地队列不受此窗口限制。 */
    private static final int QUEUE_WINDOW_SIZE = 30;
    private static final int QUEUE_REFILL_THRESHOLD = 5;
    private final java.util.function.Supplier<String> playerName;
    private final java.util.function.BooleanSupplier qqSession;
    private final java.util.function.Consumer<Packet> transport;
    private final Runnable enterQqRoom;
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
    /** 房主完整个人队列的不可变快照，仅用于计算下一次窗口包。 */
    private List<Packet.RoomTrack> ownerQueue = List.of();
    private int ownerWindowStart;
    private String ownerCurrentMid = "";
    private int ownerPositionTenths;
    private boolean ownerPlaying;
    private dev.lzasn.cloudxmusic.client.state.PlaybackMode ownerMode =
            dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST;
    private long lastOwnerControlAt;
    /** 上一次推送的房间队列签名，用来等回推、避免连发。 */
    private String lastPushedKey = "";
    private long lastPushedAt;
    private boolean applyingRoomPlayback;
    private long seekRevision;
    private String playbackMid="";
    private Packet.RoomPlayback lastPlayback;
    private String lastPlaybackMid="";
    private MemberState selfState;
    private String selfStateMid="";
    /** 协议只广播状态枚举；具体权限原因留在本地展示，不扩展二进制结构。 */
    private String selfStateDetail="";
    private MemberState reportedSelfState;
    private String reportedSelfMid="";
    private long finishedTimeline=-1,requestedEofTimeline=-1;
    private String finishedMid="",requestedEofMid="";
    private boolean ownerPauseRequested;
    private java.util.function.IntUnaryOperator random=bound->java.util.concurrent.ThreadLocalRandom.current().nextInt(bound);
    void random(java.util.function.IntUnaryOperator random){this.random=java.util.Objects.requireNonNull(random);}

    private MusicRoomClient() {
        this(MusicRoomClient::localName,MusicRoomClient::qqLoggedIn,null,
                ()->dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.playback().roomMode(true));
    }

    MusicRoomClient(java.util.function.Supplier<String> playerName,java.util.function.BooleanSupplier qqSession,
                    java.util.function.Consumer<Packet> transport,Runnable enterQqRoom){
        this.playerName=playerName;this.qqSession=qqSession;this.transport=transport;this.enterQqRoom=enterQqRoom;
    }

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
        // QQ 房间的控制权必须和有效 QQ 会话绑定；失去登录后不能继续控制或接收房主权限。
        if (currentRoom.source() == MusicSourceType.QQ
                && !qqSession.getAsBoolean()) return false;
        String name = playerName.get();
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
        if (!loggedIn || source == MusicSourceType.QQ && !qqLoggedIn()) {
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
        if (source == null) {
            status = "未选择房间音源";
            return;
        }
        if (!loggedIn || source == MusicSourceType.QQ && !qqLoggedIn()) {
            status = "请先登录对应音源";
            return;
        }
        if (!ready) {
            status = "尚未连接房间服务";
            return;
        }
        String code = roomCode == null ? "" : roomCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) {
            status = "请输入房间码";
            return;
        }
        status = "正在加入房间";
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
        Packet.RoomTrack current = currentRoomTrack();
        syncOwnerQueue(songs, current == null ? "" : current.ref().songId(), positionTenths(), playing,
                dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST);
    }

    public void syncOwnerQueue(List<SongView> songs, String currentMid, int position,
                               boolean isPlaying, dev.lzasn.cloudxmusic.client.state.PlaybackMode mode) {
        if (!owner() || currentRoom == null || songs == null) return;
        if(currentRoom.source()==MusicSourceType.QQ){syncQqOwnerQueue(songs);return;}
        List<Packet.RoomTrack> tracks = filterSeed(songs, currentRoom.source());
        // 个人队列被清空时不动房间：否则会把所有人正在听的东西一起清掉。
        if (tracks.isEmpty()) return;
        int absoluteIndex = findMid(tracks, currentMid);
        if (absoluteIndex < 0) {
            int oldAbsolute = Math.max(0, ownerWindowStart + Math.max(0, index));
            absoluteIndex = Math.min(oldAbsolute, tracks.size() - 1);
        }
        // 保持当前扇区，只有播放到窗口末五首、当前曲越出窗口或完整队列发生变化时才换窗。
        int maxStart = Math.max(0, tracks.size() - QUEUE_WINDOW_SIZE);
        int start = Math.min(ownerWindowStart, maxStart);
        boolean outsideWindow = absoluteIndex < start || absoluteIndex >= start + QUEUE_WINDOW_SIZE;
        boolean nearWindowEnd = tracks.size() > QUEUE_WINDOW_SIZE
                && absoluteIndex >= start + QUEUE_WINDOW_SIZE - QUEUE_REFILL_THRESHOLD;
        if (tracks.size() <= QUEUE_WINDOW_SIZE || ownerQueue.isEmpty() || outsideWindow || nearWindowEnd) {
            start = Math.min(absoluteIndex, maxStart);
        }
        List<Packet.RoomTrack> window = List.copyOf(tracks.subList(start,
                Math.min(tracks.size(), start + QUEUE_WINDOW_SIZE)));
        int windowIndex = Math.max(0, Math.min(window.size() - 1, absoluteIndex - start));
        String desired = signature(window);
        long now = System.currentTimeMillis();
        boolean sameWindow = ownerQueue.equals(tracks) && ownerWindowStart == start
                && signature(queue).equals(signature(window));
        ownerCurrentMid = currentMid == null ? "" : currentMid;
        ownerPositionTenths = Math.max(0, position);
        ownerPlaying = isPlaying;
        ownerMode = mode == null ? dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST : mode;
        if (sameWindow) {
            int roomIndex = absoluteIndex - start;
            Packet.RoomTrack roomTrack = currentRoomTrack();
            if (!ownerCurrentMid.equals(mid(roomTrack)) || index != roomIndex)
                sendOwnerControl(now, PlaybackAction.JUMP, roomIndex, 0, null);
            if (playing != ownerPlaying)
                sendOwnerControl(now, ownerPlaying ? PlaybackAction.PLAY : PlaybackAction.PAUSE, 0, 0, null);
            if (Math.abs(positionTenths() - ownerPositionTenths) > 20)
                sendOwnerControl(now, PlaybackAction.SEEK, 0, ownerPositionTenths, null);
            RepeatMode desiredRepeat = roomRepeat(ownerMode);
            if (repeatMode != desiredRepeat)
                sendOwnerControl(now, PlaybackAction.REPEAT, 0, 0, desiredRepeat);
            return;
        }
        if (desired.equals(lastPushedKey) && now - lastPushedAt < 1_500L) return;
        lastPushedKey = desired;
        lastPushedAt = now;
        ownerQueue = List.copyOf(tracks);
        ownerWindowStart = start;
        send(new Packet.QueueSet(nextRequestId(), window, windowIndex));
        // 当前曲还在队列里的话，把房间位置也拉回原处，避免加删一首歌就从头播。
        send(new Packet.PlaybackControl(nextRequestId(), isPlaying ? PlaybackAction.PLAY : PlaybackAction.PAUSE, 0, 0, null));
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.SEEK, 0, ownerPositionTenths, null));
        RepeatMode desiredRepeat = roomRepeat(ownerMode);
        if (repeatMode != desiredRepeat)
            send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.REPEAT, 0, 0, desiredRepeat));
    }

    /** QQ 只同步队列编辑/滑动窗口；加载、声卡暂停、曲末均不能反向广播。 */
    public void syncQqOwnerQueue(List<SongView> songs){
        if(applyingRoomPlayback||!owner()||roomSource()!=MusicSourceType.QQ||songs==null)return;
        List<Packet.RoomTrack> tracks=filterSeed(songs,MusicSourceType.QQ);
        String currentMid=mid(currentRoomTrack());
        int selected=findMid(tracks,currentMid);
        if(selected<0)selected=Math.min(Math.max(0,findMid(ownerQueue,currentMid)),Math.max(0,tracks.size()-1));
        ownerQueue=tracks;
        long now=System.currentTimeMillis();
        // 等待 QueueSet 回推期间不能用旧队列覆盖尚未确认的跳播请求。
        if(lastPushedAt>0&&!lastPushedKey.equals(signature(queue))&&now-lastPushedAt<1_500L)return;
        var window=RoomQueueWindow.select(tracks,selected,ownerWindowStart);
        String desired=signature(window.tracks());
        if(desired.equals(signature(queue)))return;
        ownerWindowStart=window.start();lastPushedKey=desired;lastPushedAt=now;
        boolean preservePlaying=playing;
        int preservePosition=findMid(tracks,currentMid)>=0?positionTenths():0;
        send(new Packet.QueueSet(nextRequestId(),window.tracks(),window.index()));
        if(window.index()>=0){
            if(preservePosition>0)send(new Packet.PlaybackControl(nextRequestId(),PlaybackAction.SEEK,0,preservePosition,null));
            send(new Packet.PlaybackControl(nextRequestId(),preservePlaying?PlaybackAction.PLAY:PlaybackAction.PAUSE,0,0,null));
        }
    }

    public long seekRevision(){return seekRevision;}

    /** EOF 只允许房主提出下一曲请求；本机与听众均等待原协议的服务端广播后起播。 */
    public void qqAudioFinished(String expectedMid,long expectedRevision){
        if(roomSource()!=MusicSourceType.QQ||localPaused||applyingRoomPlayback
                ||seekRevision!=expectedRevision||!expectedMid.equals(mid(currentRoomTrack())))return;
        finishedMid=expectedMid;finishedTimeline=expectedRevision;
        if(!owner()||ownerPauseRequested||!playing&&(durationSeconds<=0||positionTenths()<durationSeconds*10-1)
                ||requestedEofTimeline==expectedRevision&&requestedEofMid.equals(expectedMid))return;
        // 服务端已推进时间轴时，旧声卡的迟到 EOF 不得再推进一次。
        List<Packet.RoomTrack> tracks=ownerQueue.isEmpty()?queue:ownerQueue;
        int current=findMid(tracks,expectedMid);if(current<0||tracks.isEmpty())return;
        requestedEofMid=expectedMid;requestedEofTimeline=expectedRevision;
        int next=switch(repeatMode){
            case SINGLE -> current;
            case LIST -> (current+1)%tracks.size();
            case SHUFFLE -> {
                if(tracks.size()==1)yield 0;
                int candidate=random.applyAsInt(tracks.size()-1);
                yield candidate>=current?candidate+1:candidate;
            }
        };
        LOGGER.info("[房间EOF] 房主请求下一曲：mode={} currentMID={} nextMID={} index={}",repeatMode,expectedMid,mid(tracks.get(next)),next);
        if(next==current){
            // 同 MID 重播依赖时间轴代次变化，确保听众也能区分短曲循环与重复心跳。
            jump(index);
            send(new Packet.PlaybackControl(nextRequestId(),PlaybackAction.SEEK,0,0,null));
            send(new Packet.PlaybackControl(nextRequestId(),PlaybackAction.PLAY,0,0,null));
        }else if(ownerQueue.isEmpty())jump(next);else jumpOwnerQueue(next);
    }

    /** 真正出声后才恢复听众状态；同曲新轮次和离开房间后的迟到回调均被隔离。 */
    public boolean qqAudioStarted(String expectedMid,long expectedRevision){
        if(roomSource()!=MusicSourceType.QQ||!playing||localPaused||awaitingSync()
                ||expectedRevision!=seekRevision||!expectedMid.equals(mid(currentRoomTrack())))return false;
        LOGGER.info("[房间听众] 音频流已启动，清除错误提示");
        reportSelfState(MemberState.LISTENING);
        return true;
    }

    /** 搜索、收藏和歌单的显式播放请求，只向服务端提交，不直接驱动本地解码器。 */
    public void replaceQqOwnerQueue(List<SongView> songs,int selected){
        if(!owner()||roomSource()!=MusicSourceType.QQ||selected<0||selected>=songs.size())return;
        String target=songs.get(selected).platformId();
        List<Packet.RoomTrack> tracks=filterSeed(songs,MusicSourceType.QQ);
        int absolute=findMid(tracks,target);if(absolute<0)return;
        ownerPauseRequested=false;
        ownerQueue=tracks;
        var window=RoomQueueWindow.select(tracks,absolute,absolute);
        ownerWindowStart=window.start();lastPushedKey=signature(window.tracks());lastPushedAt=System.currentTimeMillis();
        send(new Packet.QueueSet(nextRequestId(),window.tracks(),window.index()));
        send(new Packet.PlaybackControl(nextRequestId(),PlaybackAction.SEEK,0,0,null));
        send(new Packet.PlaybackControl(nextRequestId(),PlaybackAction.PLAY,0,0,null));
    }

    public void pause(){
        if(owner()&&playing){
            if(roomSource()==MusicSourceType.QQ)ownerPauseRequested=true;
            send(new Packet.PlaybackControl(nextRequestId(),PlaybackAction.PAUSE,0,0,null));
        }
    }

    private void sendOwnerControl(long now, PlaybackAction action, int controlIndex, int position, RepeatMode repeat) {
        if (now - lastOwnerControlAt < 500L) return;
        lastOwnerControlAt = now;
        send(new Packet.PlaybackControl(nextRequestId(), action, controlIndex, position, repeat));
    }

    private static RepeatMode roomRepeat(dev.lzasn.cloudxmusic.client.state.PlaybackMode mode) {
        return switch (mode) {
            case REPEAT_ONE -> RepeatMode.SINGLE;
            case SHUFFLE -> RepeatMode.SHUFFLE;
            case REPEAT_LIST -> RepeatMode.LIST;
        };
    }

    public void jumpOwnerQueue(int absoluteIndex) {
        if (!owner() || absoluteIndex < 0 || absoluteIndex >= ownerQueue.size()) return;
        Packet.RoomTrack target = ownerQueue.get(absoluteIndex);
        if(roomSource()==MusicSourceType.QQ)ownerPauseRequested=false;
        if(roomSource()==MusicSourceType.QQ){
            int existing=findMid(queue,target.ref().songId());
            if(existing>=0){jump(existing);return;}
        }
        int start = Math.min(absoluteIndex, Math.max(0, ownerQueue.size() - QUEUE_WINDOW_SIZE));
        if (absoluteIndex >= start + QUEUE_WINDOW_SIZE) start = absoluteIndex - QUEUE_WINDOW_SIZE + 1;
        List<Packet.RoomTrack> window = List.copyOf(ownerQueue.subList(start,
                Math.min(ownerQueue.size(), start + QUEUE_WINDOW_SIZE)));
        ownerWindowStart = start;
        ownerCurrentMid = target.ref().songId();
        int localIndex = absoluteIndex - start;
        lastPushedKey=signature(window);lastPushedAt=System.currentTimeMillis();
        send(new Packet.QueueSet(nextRequestId(), window, localIndex));
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.PLAY, 0, 0, null));
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.SEEK, 0, 0, null));
    }

    private static int findMid(List<Packet.RoomTrack> tracks, String target) {
        if (target == null || target.isBlank()) return -1;
        for (int i = 0; i < tracks.size(); i++) if (target.equals(mid(tracks.get(i)))) return i;
        return -1;
    }

    private static String mid(Packet.RoomTrack track) {
        return track == null || track.ref() == null ? "" : track.ref().songId();
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
            // 封面与时长异步补齐后也要同步；仅比较 MID 会让听众永久保留空封面。
            if(track.ref().source()==MusicSourceType.QQ){
                for(String field:List.of(track.title(),track.artist(),track.coverUrl()))out.append(field.length()).append(':').append(field);
                out.append(':').append(track.durationSeconds()).append(':').append(track.vip()).append('|');
            }
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
            if(roomSource()==MusicSourceType.QQ)ownerPauseRequested=playing;
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
        if(roomSource()==MusicSourceType.QQ&&!ownerQueue.isEmpty()){
            int current=findMid(ownerQueue,mid(currentRoomTrack()));
            int next=(Math.max(-1,current)+1)%ownerQueue.size();
            if(repeatMode==RepeatMode.SHUFFLE&&ownerQueue.size()>1){
                next=random.applyAsInt(ownerQueue.size()-1);if(next>=current)next++;
            }
            jumpOwnerQueue(next);return;
        }
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.NEXT, 0, 0, null));
    }

    public void previous() {
        if (!owner() || index < 0) return;
        if(roomSource()==MusicSourceType.QQ&&!ownerQueue.isEmpty()){
            int current=findMid(ownerQueue,mid(currentRoomTrack()));
            jumpOwnerQueue(Math.floorMod(current-1,ownerQueue.size()));return;
        }
        send(new Packet.PlaybackControl(nextRequestId(), PlaybackAction.PREVIOUS, 0, 0, null));
    }

    public void jump(int queueIndex) {
        if (!owner() || queueIndex < 0 || queueIndex >= queue.size()) return;
        if(roomSource()==MusicSourceType.QQ)ownerPauseRequested=false;
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
        reportSelfState(state, "");
    }

    public void reportSelfState(MemberState state, String detail) {
        if (currentRoom == null || state == null) return;
        if(roomSource()==MusicSourceType.QQ){
            String currentMid=mid(currentRoomTrack());
            String nextDetail=state==MemberState.UNAVAILABLE&&detail!=null?detail:"";
            boolean changed=selfState!=state||!selfStateMid.equals(currentMid)||!selfStateDetail.equals(nextDetail);
            selfState=state;selfStateMid=currentMid;selfStateDetail=nextDetail;
            if(changed){
                if(state==MemberState.LISTENING)LOGGER.info("[房间UI] 状态更新为正在播放");
                else LOGGER.info("[房间UI] 状态更新为 {}，MID={}，原因={}",state,selfStateMid,selfStateDetail);
            }
            if(reportedSelfState==state&&reportedSelfMid.equals(currentMid))return;
            reportedSelfState=state;reportedSelfMid=currentMid;
        }
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
                    boolean localState=roomSource()==MusicSourceType.QQ&&member.playerId().equals(playerName.get())
                            &&selfState!=null&&selfStateMid.equals(mid(currentRoomTrack()));
                    MemberState state=localState?selfState:member.state();
                    if(member.playerId().equals(playerName.get())&&localPaused)state=MemberState.PAUSED;
                    String detail = localState&&state==MemberState.UNAVAILABLE&&!selfStateDetail.isBlank()?selfStateDetail:switch (state) {
                        case PAUSED -> "暂停跟听";
                        case BUFFERING -> roomSource()==MusicSourceType.QQ?"正在获取音频...":"缓冲中";
                        case UNAVAILABLE -> "本曲不可播放";
                        case LISTENING -> playing ? "正在跟听" : "等待房主继续";
                    };
                    return new MusicView.RoomMember(member.playerId(), detail, false,
                            switch (state) {
                                case PAUSED -> MusicView.MemberTone.PAUSED;
                                case UNAVAILABLE -> MusicView.MemberTone.UNAVAILABLE;
                                case LISTENING, BUFFERING -> MusicView.MemberTone.ACTIVE;
                            });
                })
                .toList();
        List<Packet.RoomTrack> displayQueue = owner() && !ownerQueue.isEmpty() ? ownerQueue : queue;
        List<MusicView.RoomTrack> tracks = displayQueue.stream()
                .map(track -> new MusicView.RoomTrack(track.title(), track.artist(),
                        Track.format(track.durationSeconds()), track.coverUrl(), track.ref().songId(), track.vip()))
                .toList();
        String title = "";
        String artist = "";
        int duration = durationSeconds;
        boolean qqRoom=currentRoom.source()==MusicSourceType.QQ;
        String displayMid=qqRoom?mid(currentRoomTrack()):ownerCurrentMid;
        int displayIndex = owner() ? findMid(displayQueue, displayMid) : index;
        if (displayIndex >= 0 && displayIndex < displayQueue.size()) {
            Packet.RoomTrack track = displayQueue.get(displayIndex);
            title = track.title();
            artist = track.artist();
            if (duration <= 0) duration = track.durationSeconds();
        }
        String roomStatus = status;
        if (localPaused) roomStatus = "暂停跟听";
        else if (displayQueue.isEmpty()) roomStatus = "等待房主的队列";
        else if (!playing) roomStatus = "已暂停 · 等待房主继续";
        return new MusicView.RoomView(true, currentRoom.roomCode(), sourceLabel(currentRoom.source()),
                roomStatus, title, artist, owner()&&!qqRoom ? ownerPositionTenths : positionTenths(), duration,
                owner()&&!qqRoom ? ownerPlaying : playing(), owner(), members, tracks, rooms, 0, displayIndex,
                owner()&&!qqRoom ? ownerMode.label : repeatLabel(), currentRoom.capacity(), 0, displayMid);
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

    /** 房间 QQ 队列的不可变展示快照，供成员队列页隐藏个人队列操作。 */
    public List<dev.lzasn.cloudxmusic.client.omni.SongView> queueSongs() {
        return queue.stream().map(track -> new dev.lzasn.cloudxmusic.client.omni.SongView(
                track.ref().source() == MusicSourceType.QQ
                        ? dev.lzasn.cloudxmusic.client.omni.MusicSource.QQ
                        : dev.lzasn.cloudxmusic.client.omni.MusicSource.NETEASE,
                track.ref().songId(), track.title(), track.artist(), "", track.durationSeconds(),
                track.coverUrl(), track.vip())).toList();
    }

    private void clearTimeline() {
        queue = List.of();
        ownerQueue = List.of();
        ownerWindowStart = 0;
        ownerCurrentMid = "";
        ownerPositionTenths = 0;
        ownerPlaying = false;
        ownerMode = dev.lzasn.cloudxmusic.client.state.PlaybackMode.REPEAT_LIST;
        index = -1;
        playing = false;
        positionTenths = 0;
        positionAtNanos = System.nanoTime();
        durationSeconds = 0;
        repeatMode = RepeatMode.LIST;
        localPaused = false;
        pausedAtTenths = 0;
        awaitingSync = false;
        applyingRoomPlayback=false;seekRevision=0;playbackMid="";lastPushedKey="";lastPushedAt=0;
        lastPlayback=null;lastPlaybackMid="";
        selfState=null;selfStateMid="";selfStateDetail="";reportedSelfState=null;reportedSelfMid="";
        finishedTimeline=-1;requestedEofTimeline=-1;finishedMid="";requestedEofMid="";
        ownerPauseRequested=false;
    }

    void receive(byte[] bytes) {
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
                    if(roomSource()==MusicSourceType.QQ)enterQqRoom.run();
                    status = "已加入房间";
                    // 新建房间时把带上来的个人队列一次性提交，房间从此以服务端队列为准。
                    if (!pendingSeed.isEmpty()) {
                        List<Packet.RoomTrack> window = List.copyOf(pendingSeed.subList(0,
                                Math.min(QUEUE_WINDOW_SIZE, pendingSeed.size())));
                        ownerQueue = pendingSeed;
                        ownerWindowStart = 0;
                        if (owner()) {
                            lastPushedKey=signature(window);lastPushedAt=System.currentTimeMillis();
                            send(new Packet.QueueSet(nextRequestId(), window, 0));
                        }
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
                    String previousMid=mid(currentRoomTrack());
                    queue = queueUpdate.tracks();
                    index = queueUpdate.index();
                    String nextMid=mid(currentRoomTrack());
                    if(roomSource()==MusicSourceType.QQ&&!previousMid.equals(nextMid)){
                        positionTenths=0;positionAtNanos=System.nanoTime();
                        Packet.RoomTrack selected=currentRoomTrack();
                        durationSeconds=selected==null?0:selected.durationSeconds();
                        seekRevision++;
                        selfState=MemberState.BUFFERING;selfStateMid=nextMid;selfStateDetail="";
                    }
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
                    if(applyingRoomPlayback)break;
                    String currentMid=mid(currentRoomTrack());
                    boolean qq=roomSource()==MusicSourceType.QQ;
                    int difference=Math.abs(positionTenths()-playback.positionTenths());
                    // 同曲、同状态、同进度的房主回声只确认接收，不重启声卡、不回发控制。
                    if(qq&&playbackRevisionSeen(playback,currentMid))break;
                    boolean timelineChanged=lastPlayback==null||lastPlayback.revision()!=playback.revision();
                    // 同 MID 回到零点也可能是列表/单曲循环，短于一秒的曲目不能按小偏差去重。
                    boolean restart=qq&&timelineChanged&&playback.playing()&&playback.positionTenths()==0
                            &&currentMid.equals(playbackMid)&&(positionTenths()>0||lastPlayback!=null&&lastPlayback.positionTenths()>0
                            ||finishedTimeline==seekRevision&&finishedMid.equals(currentMid));
                    lastPlayback=playback;lastPlaybackMid=currentMid;
                    if(qq&&currentMid.equals(playbackMid)&&playing==playback.playing()
                            &&durationSeconds==playback.durationSeconds()&&repeatMode==playback.repeatMode()
                            &&difference<=10&&!restart)break;
                    applyingRoomPlayback=true;
                    try{
                        // 同代次的周期心跳只校准时钟，不能被误判成新的跳播命令。
                        if(qq&&timelineChanged&&currentMid.equals(playbackMid)&&(difference>40||restart))seekRevision++;
                        playing = playback.playing();
                        positionTenths = playback.positionTenths();
                        positionAtNanos = System.nanoTime();
                        durationSeconds = playback.durationSeconds();
                        repeatMode = playback.repeatMode();
                        playbackMid=currentMid;
                    }finally{applyingRoomPlayback=false;}
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

    private boolean playbackRevisionSeen(Packet.RoomPlayback playback,String currentMid){
        return lastPlayback!=null&&currentMid.equals(lastPlaybackMid)&&lastPlayback.revision()==playback.revision()
                &&lastPlayback.positionTenths()==playback.positionTenths()&&lastPlayback.playing()==playback.playing()
                &&lastPlayback.repeatMode()==playback.repeatMode()&&lastPlayback.durationSeconds()==playback.durationSeconds();
    }

    private void sendHello() {
        lastHelloAttemptNanos = System.nanoTime();
        String version=FabricLoader.getInstance().getModContainer("cloudxmusic")
                .map(container->container.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        send(new Packet.Hello(nextRequestId(), version));
    }

    private boolean send(Packet packet) {
        if(applyingRoomPlayback&&roomSource()==MusicSourceType.QQ
                &&(packet instanceof Packet.PlaybackControl||packet instanceof Packet.QueueSet))return false;
        if(transport!=null){transport.accept(packet);return true;}
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

    private static boolean qqLoggedIn() {
        return dev.lzasn.cloudxmusic.client.qqmusic.QQMusicIntegration.qqLoggedIn();
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
