package dev.lzasn.cloudxmusic.client.qqmusic;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

/** QQ 官方 App 二维码状态推送：仅固定 QQ 域名，取消时立即中断连接。 */
final class QQLoginChannel implements QQLoginService.Channel {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final String id, topic;
    private volatile boolean closed;
    private volatile WebSocket socket;
    private volatile CompletableFuture<?> pending;
    private volatile Inbox inbox;
    private long pingInterval = 20000, lastPing;
    QQLoginChannel(String id) {
        if (!id.matches("[A-Za-z0-9_-]{1,256}")) throw new IllegalArgumentException("QQ 二维码 ID 无效");
        this.id = id; topic = "management.qrcode_login/" + id;
    }
    @Override public void connect() throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        String path = "/ws/handshake";
        for (int attempt = 0; attempt < 4; attempt++) {
            ensureOpen(); Inbox next = new Inbox(); inbox = next;
            CompletableFuture<WebSocket> connecting = client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(8))
                    .subprotocols("mqtt").header("Origin", "https://y.qq.com").header("Referer", "https://y.qq.com/")
                    .header("User-Agent", "Mozilla/5.0").buildAsync(URI.create("wss://mu.y.qq.com" + path), next);
            pending = connecting;
            WebSocket opened;
            try { opened = await(connecting, deadline); }
            finally { connecting.cancel(true); pending = null; }
            socket = opened;
            if (closed) { opened.abort(); throw new IOException("QQ 登录已取消"); }
            send(QQMqttCodec.connect(id), deadline);
            QQMqttCodec.Packet ack = next.take(deadline);
            if (ack == null || ack.header() != 0x20) throw new IOException("QQ 登录通道未确认连接");
            QQMqttCodec.Cursor cursor = new QQMqttCodec.Cursor(ack.body());
            if (cursor.byteValue() != 0) throw new IOException("QQ 登录通道会话标记无效");
            int reason = cursor.byteValue(); Map<String, String> props = cursor.properties(); cursor.end();
            if (reason == 0x9c || reason == 0x9d) {
                String reference = props.getOrDefault("reference", "");
                path = routePath(reference); opened.abort(); socket = null; continue;
            }
            if (reason != 0) throw new IOException("QQ 登录通道拒绝连接，code=" + reason);
            if (props.containsKey("keepalive")) pingInterval = Math.max(1000, Math.min(20000, Long.parseLong(props.get("keepalive")) * 500));
            send(QQMqttCodec.subscribe(topic), deadline);
            QQMqttCodec.Packet sub = next.take(deadline);
            if (sub == null || sub.header() != 0x90) throw new IOException("QQ 登录通道未确认订阅");
            cursor = new QQMqttCodec.Cursor(sub.body());
            if (cursor.u16() != 1) throw new IOException("QQ 登录通道订阅 ID 不匹配");
            cursor.properties(); int granted = cursor.byteValue(); cursor.end();
            if (granted > 2) throw new IOException("QQ 登录通道订阅被拒绝");
            lastPing = System.nanoTime(); return;
        }
        throw new IOException("QQ 登录通道路由重试次数已用尽");
    }
    static String routePath(String reference) throws IOException {
        if (!reference.matches("[A-Za-z0-9_.:-]{1,128}")) throw new IOException("QQ 登录通道路由无效");
        return "/ws/handshake/" + reference;
    }
    @Override public QQLoginService.Event next(long deadline) throws IOException, InterruptedException {
        while (System.nanoTime() < deadline) {
            ensureOpen();
            if (System.nanoTime() - lastPing >= TimeUnit.MILLISECONDS.toNanos(pingInterval)) {
                send(new byte[]{(byte) 0xc0, 0}, deadline); lastPing = System.nanoTime();
            }
            QQMqttCodec.Packet packet = inbox.take(Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(1)));
            if (packet == null) continue;
            int type = packet.header() >>> 4;
            if (type == 13 && packet.header() == 0xd0 && packet.body().length == 0) continue;
            if (type == 14) throw new IOException("QQ 登录通道已断开，请刷新二维码");
            if (type != 3) throw new IOException("QQ 登录通道收到意外数据包");
            QQMqttCodec.Cursor cursor = new QQMqttCodec.Cursor(packet.body());
            String incomingTopic = cursor.string(); int qos = packet.header() >>> 1 & 3;
            if (qos > 1) throw new IOException("QQ 登录通道 QoS 不支持");
            int packetId = qos == 1 ? cursor.u16() : 0;
            if (qos == 1 && packetId == 0) throw new IOException("QQ 登录消息 ID 无效");
            Map<String, String> props = cursor.properties();
            if (qos == 1) send(new byte[]{0x40, 2, (byte) (packetId >>> 8), (byte) packetId}, deadline);
            if (incomingTopic.equals(topic)) return new QQLoginService.Event(props.getOrDefault("type", ""), cursor.rest());
        }
        return null;
    }
    private void send(byte[] bytes, long deadline) throws IOException, InterruptedException {
        ensureOpen(); WebSocket current = socket;
        if (current == null) throw new IOException("QQ 登录通道未连接");
        CompletableFuture<WebSocket> send = current.sendBinary(ByteBuffer.wrap(bytes), true); pending = send;
        try { await(send, Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(8))); }
        finally { send.cancel(true); pending = null; }
    }
    private static <T> T await(CompletableFuture<T> future, long deadline) throws IOException, InterruptedException {
        try { return future.get(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS); }
        catch (TimeoutException e) { future.cancel(true); throw new IOException("QQ 登录通道超时"); }
        catch (ExecutionException | CancellationException e) { throw new IOException("QQ 登录通道连接失败或已取消"); }
    }
    private void ensureOpen() throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        if (closed) throw new IOException("QQ 登录已取消");
    }
    @Override public void close() {
        closed = true; CompletableFuture<?> current = pending; if (current != null) current.cancel(true);
        WebSocket ws = socket; if (ws != null) ws.abort(); client.shutdownNow();
    }
    private final class Inbox implements WebSocket.Listener {
        private final QQMqttCodec.Decoder decoder = new QQMqttCodec.Decoder();
        private final BlockingQueue<QQMqttCodec.Packet> packets = new ArrayBlockingQueue<>(32);
        private volatile IOException failure;
        @Override public void onOpen(WebSocket ws) {
            if (closed) ws.abort(); else ws.request(1);
        }
        @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            try {
                for (QQMqttCodec.Packet packet : decoder.feed(data)) {
                    if (!packets.offer(packet)) throw new IOException("QQ 登录通道缓冲区已满");
                }
                ws.request(1);
            } catch (IOException e) { failure = e; ws.abort(); }
            return null;
        }
        @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            failure = new IOException("QQ 登录通道返回非二进制消息"); ws.abort(); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            failure = new IOException("QQ 登录通道已关闭，请刷新二维码"); return null;
        }
        @Override public void onError(WebSocket ws, Throwable error) { failure = new IOException("QQ 登录通道发生网络错误"); }
        QQMqttCodec.Packet take(long deadline) throws IOException, InterruptedException {
            while (System.nanoTime() < deadline) {
                if (failure != null) throw failure;
                QQMqttCodec.Packet packet = packets.poll(Math.min(TimeUnit.MILLISECONDS.toNanos(100),
                        Math.max(1, deadline - System.nanoTime())), TimeUnit.NANOSECONDS);
                if (packet != null) return packet;
            }
            return null;
        }
    }
}
