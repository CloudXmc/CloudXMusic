package dev.lzasn.cloudxmusic.client.chat;

import dev.lzasn.cloudxmusic.protocol.ShareWire;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** 客户端线程持有；握手就绪时走专用通道，调用方负责公开聊天降级。 */
final class ShareSession {
    private static final long WELCOME_GRACE_NANOS = 750_000_000L;
    private static final long HELLO_RETRY_NANOS = 2_000_000_000L;
    private final Predicate<ShareWire.Message> send;
    private final Consumer<ShareWire.Broadcast> receive;
    private final Consumer<String> notify;
    private final LongSupplier clock;
    private boolean online, ready, helloSent, unavailable;
    private long lastHello;
    ShareSession(Predicate<ShareWire.Message> send, Consumer<ShareWire.Broadcast> receive,
                 Consumer<String> notify, LongSupplier clock) {
        this.send = send; this.receive = receive; this.notify = notify; this.clock = clock;
    }
    void joined() { disconnected(); online = true; tick(); }
    void disconnected() {
        online = false; ready = false; helloSent = false; unavailable = false; lastHello = 0;
    }
    void tick() {
        if (!online || ready) return;
        long now = clock.getAsLong();
        if (helloSent && !unavailable && now - lastHello >= WELCOME_GRACE_NANOS) {
            unavailable = true;
        }
        if (helloSent && now - lastHello < HELLO_RETRY_NANOS) return;
        helloSent = true; lastHello = now;
        unavailable = false;
        send.test(new ShareWire.Hello());
    }
    boolean ready() { return online && ready; }
    boolean handshakeInFlight() {
        return online && helloSent && !ready && !unavailable;
    }
    boolean share(ShareWire.Song song) {
        if (!ready()) return false;
        if (send.test(new ShareWire.Request(song))) return true;
        ready = false;
        return false;
    }
    void receive(ShareWire.Message message) {
        if (!online) return;
        switch (message) {
            case ShareWire.Welcome ignored -> {
                if (helloSent) {
                    ready = true;
                    unavailable = false;
                }
            }
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
