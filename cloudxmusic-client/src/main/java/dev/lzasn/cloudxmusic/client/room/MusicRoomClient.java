package dev.lzasn.cloudxmusic.client.room;

import dev.lzasn.cloudxmusic.client.gui.design.MusicView;
import dev.lzasn.cloudxmusic.client.network.CloudXMusicPayload;
import dev.lzasn.cloudxmusic.protocol.MusicSourceType;
import dev.lzasn.cloudxmusic.protocol.Packet;
import dev.lzasn.cloudxmusic.protocol.PacketCodec;
import dev.lzasn.cloudxmusic.protocol.Protocol;
import dev.lzasn.cloudxmusic.protocol.ProtocolException;
import dev.lzasn.cloudxmusic.protocol.RejectCode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** 客户端房间会话：只保存不可变展示状态，不把网络容器带进绘制层。 */
@Environment(EnvType.CLIENT)
public final class MusicRoomClient {
    public static final MusicRoomClient INSTANCE = new MusicRoomClient();
    private static final Logger LOGGER = LoggerFactory.getLogger("CloudXMusic/Room");
    private static final String VERSION = FabricLoader.getInstance().getModContainer("cloudxmusic")
            .map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    private final AtomicLong requestIds = new AtomicLong(1L);
    private boolean registered;
    private boolean ready;
    private String status = "未连接房间服务";
    private MusicSourceType selectedSource = MusicSourceType.NETEASE;
    private List<Packet.RoomSummary> publicRooms = List.of();
    private Packet.RoomSnapshot currentRoom;

    private MusicRoomClient() {}

    public void register() {
        if (registered) return;
        registered = true;
        CloudXMusicPayload.register();
        ClientPlayNetworking.registerGlobalReceiver(CloudXMusicPayload.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload.data())));
    }

    public void joinedWorld() {
        ready = false;
        currentRoom = null;
        publicRooms = List.of();
        status = "正在连接房间服务";
        sendHello();
    }

    public void disconnected() {
        ready = false;
        currentRoom = null;
        publicRooms = List.of();
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

    public boolean joined() {
        return currentRoom != null;
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
        if (!ready) {
            status = "尚未连接房间服务";
            return;
        }
        if (source == null) return;
        selectedSource = source;
        status = loggedIn ? "正在创建房间" : "请先登录对应音源";
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

    public MusicView.RoomView view() {
        String source = sourceLabel(selectedSource);
        List<MusicView.RoomSummary> rooms = publicRooms.stream()
                .map(room -> new MusicView.RoomSummary(room.roomCode(), room.ownerId(),
                        sourceLabel(room.source()), room.members(), room.playing() ? "播放中" : "等待加入"))
                .toList();
        if (currentRoom == null) {
            return new MusicView.RoomView(false, "------", source, status,
                    "", "", 0, 0, false, List.of(), List.of(), rooms, 0);
        }
        List<MusicView.RoomMember> members = currentRoom.members().stream()
                .map(member -> new MusicView.RoomMember(member.playerId(),
                        member.owner() ? "房主 · 正在跟听" : "正在跟听", member.owner(), true))
                .toList();
        return new MusicView.RoomView(true, currentRoom.roomCode(), sourceLabel(currentRoom.source()),
                status, "等待房主选择歌曲", "房间控制已连接", 0, 0, false,
                members, List.of(), rooms, 0);
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
                    status = "已加入房间";
                }
                case Packet.RoomUpdated updated -> {
                    if (currentRoom != null && currentRoom.roomCode().equals(updated.room().roomCode()))
                        currentRoom = updated.room();
                }
                case Packet.RoomLeft left -> {
                    currentRoom = null;
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
        send(new Packet.Hello(nextRequestId(), VERSION));
    }

    private boolean send(Packet packet) {
        if (!ClientPlayNetworking.canSend(CloudXMusicPayload.TYPE)) return false;
        try {
            ClientPlayNetworking.send(new CloudXMusicPayload(PacketCodec.encode(packet)));
            return true;
        } catch (ProtocolException error) {
            status = "无法编码房间请求";
            LOGGER.warn("房间请求编码失败：{}", error.code());
            return false;
        }
    }

    private long nextRequestId() {
        return requestIds.getAndIncrement();
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
            case UNSUPPORTED_VERSION -> "客户端与房间服务协议版本不兼容";
            case HANDSHAKE_REQUIRED -> "正在重新连接房间服务";
            case RATE_LIMITED -> "操作太频繁，请稍后再试";
            case MALFORMED_PACKET -> "房间请求格式无效";
        };
    }
}
