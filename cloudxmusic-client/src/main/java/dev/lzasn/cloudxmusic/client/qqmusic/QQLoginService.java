package dev.lzasn.cloudxmusic.client.qqmusic;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.function.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** 一次只能有一个 QQ 扫码会话；迟到网络结果和主线程回调均按代次丢弃。 */
public final class QQLoginService implements AutoCloseable {
    public enum State { IDLE, LOADING, WAITING, SCANNED, VERIFYING, SUCCESS, EXPIRED, CANCELED, ERROR }
    public record View(State state, BufferedImage qr, String message) {
        public boolean error() { return state == State.ERROR || state == State.EXPIRED; }
    }
    record Event(String type, byte[] payload) {}
    interface Channel extends AutoCloseable {
        void connect() throws IOException, InterruptedException;
        Event next(long deadlineNanos) throws IOException, InterruptedException;
        @Override void close();
    }
    private static final long LIFETIME = TimeUnit.SECONDS.toNanos(180);
    private final QQAccountManager accounts;
    private final QQSessionVerifier verifier;
    private final QQAuthHttp.Transport transport;
    private final Function<String, Channel> channels;
    private final Consumer<Runnable> main;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), task -> { Thread t = new Thread(task, "CloudXMusic-QQ-QR"); t.setDaemon(true); return t; });
    private volatile View view = new View(State.IDLE, null, "使用手机 QQ 音乐 App 扫码");
    private long generation;
    private Future<?> pending;
    private Channel active;
    private boolean closed;
    QQLoginService(QQAccountManager accounts, Consumer<Runnable> main) {
        this(accounts, main, QQAuthHttp::post, QQLoginChannel::new, new QQSessionVerifier());
    }
    QQLoginService(QQAccountManager accounts, Consumer<Runnable> main, QQAuthHttp.Transport transport,
                   Function<String, Channel> channels, QQSessionVerifier verifier) {
        this.accounts = accounts; this.main = main; this.transport = transport; this.channels = channels; this.verifier = verifier;
    }
    public View view() { return view; }
    public synchronized void start() {
        if (closed) return;
        cancelPending(); long token = ++generation;
        view = new View(State.LOADING, null, "正在申请二维码并连接登录通道…");
        worker.purge(); worker.getQueue().clear();
        try { pending = worker.submit(() -> run(token)); }
        catch (RejectedExecutionException e) { view = new View(State.ERROR, null, "QQ 登录后台任务繁忙，请稍后重试"); }
    }
    public synchronized void cancel() {
        generation++; cancelPending();
        view = new View(State.IDLE, null, "使用手机 QQ 音乐 App 扫码");
    }
    private void cancelPending() {
        if (pending != null) { pending.cancel(true); pending = null; }
        if (active != null) { active.close(); active = null; }
    }
    private synchronized boolean current(long token) { return !closed && generation == token && !Thread.currentThread().isInterrupted(); }
    private void publish(long token, View next) {
        main.accept(() -> { synchronized (this) { if (current(token)) view = next; } });
    }
    private void run(long token) {
        Channel channel = null;
        try {
            if (!current(token)) return;
            JsonObject params = new JsonObject(); params.addProperty("tmeAppID", "qqmusic");
            params.addProperty("ct", 19); params.addProperty("cv", 2201);
            JsonObject request = QQAuthHttp.request("music.login.LoginServer", "CreateQRCode", params);
            request.getAsJsonObject("comm").addProperty("ct", 23); request.getAsJsonObject("comm").addProperty("cv", 0);
            JsonObject response = transport.post(request, QQAuthHttp.emptyCookies());
            if (!current(token)) return;
            long root = QQAuthHttp.integer(response, "code");
            if (root != 0) throw new QQSessionVerifier.Rejected(root);
            JsonObject block = QQAuthHttp.object(response, "req_0");
            long code = QQAuthHttp.integer(block, "code");
            if (code != 0) throw new QQSessionVerifier.Rejected(code);
            JsonObject data = QQAuthHttp.object(block, "data");
            String id = QQAuthHttp.string(data, "qrcodeID");
            if (!id.matches("[A-Za-z0-9_-]{1,256}")) throw new IOException("QQ 二维码 ID 无效");
            long deadline = System.nanoTime() + LIFETIME;
            BufferedImage image = decodeQr(QQAuthHttp.string(data, "qrcode"));
            synchronized (this) {
                if (!current(token)) return;
                channel = channels.apply(id); active = channel;
            }
            channel.connect(); // 先订阅，再展示二维码，防止极速确认丢失。
            publish(token, new View(State.WAITING, image, "请使用手机 QQ 音乐 App 扫码并确认（有效期约 3 分钟）"));
            while (current(token) && System.nanoTime() < deadline) {
                Event event = channel.next(deadline);
                if (!current(token)) return;
                if (event == null) break;
                switch (event.type()) {
                    case "scanned" -> publish(token, new View(State.SCANNED, image, "已扫码，请在手机 QQ 音乐上确认登录"));
                    case "timeout" -> { publish(token, new View(State.EXPIRED, null, "二维码已过期，请刷新")); return; }
                    case "canceled" -> { publish(token, new View(State.CANCELED, null, "手机已取消登录，可刷新重试")); return; }
                    case "loginFailed" -> throw new IOException("手机端登录失败，请刷新二维码重试");
                    case "cookies" -> {
                        publish(token, new View(State.VERIFYING, null, "手机已确认，正在验证 QQ 音乐会话…"));
                        if (event.payload().length > 65536) throw new IOException("QQ 登录凭证响应过大");
                        QQCredentials verified = verifier.complete(QQAuthHttp.parse(event.payload()), id);
                        main.accept(() -> {
                            synchronized (this) {
                                if (!current(token)) return;
                                accounts.acceptVerified(verified);
                                view = new View(State.SUCCESS, null, "QQ 音乐登录成功");
                            }
                        });
                        return;
                    }
                    default -> { /* 未识别的通知不改变登录态；仍受会话总时限限制。 */ }
                }
            }
            if (current(token)) publish(token, new View(State.EXPIRED, null, "二维码已过期，请刷新"));
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (IOException | RuntimeException e) {
            String message = e instanceof QQSessionVerifier.Rejected ? e.getMessage()
                    : "QQ 登录未完成：请检查网络后刷新二维码重试";
            if (current(token)) publish(token, new View(State.ERROR, null, message));
        } finally {
            if (channel != null) channel.close();
            synchronized (this) { if (active == channel) active = null; }
        }
    }
    static BufferedImage decodeQr(String encoded) throws IOException {
        int comma = encoded.startsWith("data:image/") ? encoded.indexOf(',') : -1;
        if (comma >= 0) encoded = encoded.substring(comma + 1);
        if (encoded.length() > 1400000) throw new IOException("QQ 二维码图像过大");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(encoded); }
        catch (IllegalArgumentException e) { throw new IOException("QQ 二维码编码无效"); }
        if (bytes.length > 1024 * 1024) throw new IOException("QQ 二维码图像过大");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("QQ 二维码图像格式无效");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w < 32 || w > 1024 || h < 32 || h > 1024 || w != h) throw new IOException("QQ 二维码图像尺寸无效");
                BufferedImage result = reader.read(0);
                if (result == null) throw new IOException("QQ 二维码解码失败");
                return result;
            } finally { reader.dispose(); }
        }
    }
    @Override public synchronized void close() { if (!closed) { cancel(); closed = true; worker.shutdownNow(); } }
}
