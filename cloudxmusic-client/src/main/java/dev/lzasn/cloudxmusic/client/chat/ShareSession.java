package dev.lzasn.cloudxmusic.client.chat;

import dev.lzasn.cloudxmusic.protocol.ShareWire;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** 客户端线程持有；未握手时禁止发送分享，没有公开聊天回退。 */
final class ShareSession {
    private final Predicate<ShareWire.Message> send;
    private final Consumer<ShareWire.Broadcast> receive;
    private final Consumer<String> notify;
    private final LongSupplier clock;
    private boolean online, ready, helloSent;
    private long lastHello;
    ShareSession(Predicate<ShareWire.Message> send, Consumer<ShareWire.Broadcast> receive,
                 Consumer<String> notify, LongSupplier clock) {
        this.send = send; this.receive = receive; this.notify = notify; this.clock = clock;
    }
    void joined() { disconnected(); online = true; tick(); }
    void disconnected() { online = false; ready = false; helloSent = false; lastHello = 0; }
    void tick() {
        if (!online || ready) return;
        long now = clock.getAsLong();
        if (helloSent && now - lastHello < 2_000_000_000L) return;
        helloSent = true; lastHello = now;
        send.test(new ShareWire.Hello());
    }
    boolean share(ShareWire.Song song) {
        if (!online || !ready) {
            notify.accept("服务器尚未启用模组专用分享，请确认已安装 CloudXMusic 服务端 1.0.6 或更新版本");
            return false;
        }
        if (send.test(new ShareWire.Request(song))) return true;
        ready = false;
        notify.accept("分享通道发送失败，请稍后重试");
        return false;
    }
    void receive(ShareWire.Message message) {
        if (!online) return;
        switch (message) {
            case ShareWire.Welcome ignored -> { if (helloSent) ready = true; }
            case ShareWire.Broadcast broadcast -> { if (ready) receive.accept(broadcast); }
            case ShareWire.Rejected rejected -> {
                if (rejected.reason() == ShareWire.Failure.HANDSHAKE_REQUIRED) ready = false;
                notify.accept(switch (rejected.reason()) {
                    case HANDSHAKE_REQUIRED -> "分享握手已失效，请稍后重试";
                    case RATE_LIMITED -> "分享过于频繁，请稍后重试";
                    case INVALID_REQUEST -> "服务器拒绝了无效的分享请求";
                });
            }
            default -> { }
        }
    }
}
