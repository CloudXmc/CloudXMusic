package dev.lzasn.cloudxmusic.client.qqmusic;

import io.nayuki.flac.common.FrameInfo;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.AbstractFlacLowLevelInput;
import io.nayuki.flac.decode.DataFormatException;
import io.nayuki.flac.decode.FrameDecoder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 一次性的独立 FLAC 实验，不读写网易云登录、播放队列或音质设置。 */
public final class QQAudioTestPlayer {
    private static final Logger LOGGER = LoggerFactory.getLogger("CloudXMusic-QQAudioTest");
    private static final String TEST_URL = "https://raw.githubusercontent.com/ietf-wg-cellar/"
            + "flac-test-files/aa7b0c6cf32994c106ae517a08134c28a96ff5b2/"
            + "subset/01%20-%20blocksize%204096.flac";
    private static final long MAX_BYTES = 16L * 1024 * 1024;
    private static final long MAX_SAMPLES = 44100L * 20;
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicReference<TestRun> ACTIVE = new AtomicReference<>();

    private QQAudioTestPlayer() {}

    /** 只安排后台工作；初始化线程不进行网络、解码、声卡 IO 或等待。 */
    public static void testPlay() {
        if (!STARTED.compareAndSet(false, true)) return;
        TestRun run = new TestRun();
        ACTIVE.set(run);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            TestRun active = ACTIVE.get();
            if (active != null) active.requestStop();
        });
        Thread worker = new Thread(() -> runTest(run), "CloudXMusic-QQ-FLAC-Test");
        worker.setDaemon(true);
        run.worker = worker;
        worker.start();
    }

    private static void runTest(TestRun run) {
        var timeout = run.cleanup.schedule(run::requestStop, 30, TimeUnit.SECONDS);
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL).build()) {
            run.client.set(client);
            checkCancelled(run.cancelled::get);
            HttpRequest request = HttpRequest.newBuilder(URI.create(TEST_URL))
                    .timeout(Duration.ofSeconds(15)).header("Accept", "audio/flac").GET().build();
            LOGGER.info("开始独立 FLAC 测试：16 bit / 44.1 kHz，最多执行 30 秒");
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                run.input.set(body);
                checkCancelled(run.cancelled::get);
                if (response.statusCode() != 200) throw new IOException("HTTP 状态码：" + response.statusCode());
                if (response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES) {
                    throw new IOException("测试文件超过 16 MiB 上限");
                }
                long samples = playStream(body, AudioSystem::getSourceDataLine,
                        run.cancelled::get, run.line::set);
                LOGGER.info("独立 FLAC 测试完成：{} 个采样帧，PCM 已写入声卡并排空", samples);
            }
        } catch (Exception exception) {
            if (run.cancelled.get() || exception instanceof InterruptedException) {
                LOGGER.info("独立 FLAC 测试已取消或超时");
            } else {
                LOGGER.warn("独立 FLAC 测试失败，不自动重试", exception);
            }
        } finally {
            run.closeResources();
            timeout.cancel(false);
            run.cleanup.shutdownNow();
            run.worker = null;
            ACTIVE.compareAndSet(run, null);
        }
    }

    @FunctionalInterface
    interface LineFactory {
        SourceDataLine create(AudioFormat format) throws LineUnavailableException;
    }

    // 该入口只用于独立验证，允许测试替换声卡，不连接任何现有播放器。
    static long playStream(InputStream source, LineFactory factory, BooleanSupplier cancelled,
                           Consumer<SourceDataLine> lineCreated) throws IOException, LineUnavailableException {
        try (StreamInput input = new StreamInput(source)) {
            checkCancelled(cancelled);
            StreamInfo info = readStreamInfo(input);
            if (info.sampleDepth != 16 || info.sampleRate != 44100 || info.numChannels > 2) {
                throw new IOException("测试仅接受 16 bit / 44.1 kHz 的单声道或双声道 FLAC");
            }
            if (info.numSamples > MAX_SAMPLES) throw new IOException("测试音频超过 20 秒上限");
            AudioFormat format = new AudioFormat(44100, 16, info.numChannels, true, false);
            checkCancelled(cancelled);
            try (SourceDataLine line = factory.create(format)) {
                lineCreated.accept(line);
                checkCancelled(cancelled);
                line.open(format, 4410 * format.getFrameSize());
                checkCancelled(cancelled);
                line.start();
                FrameDecoder decoder = new FrameDecoder(input, info.sampleDepth);
                int[][] samples = new int[info.numChannels][65536];
                byte[] pcm = new byte[65536 * format.getFrameSize()];
                MessageDigest md5 = md5();
                long count = 0;
                while (true) {
                    checkCancelled(cancelled);
                    FrameInfo frame = decoder.readFrame(samples, 0);
                    if (frame == null) break;
                    info.checkFrame(frame);
                    count += frame.blockSize;
                    if (count > MAX_SAMPLES || (info.numSamples != 0 && count > info.numSamples)) {
                        throw new IOException("解码样本数超过上限或 STREAMINFO 声明");
                    }
                    int length = 0;
                    // FLAC 输出按声道分离；Java Sound 需要小端、有符号、声道交错的 PCM。
                    for (int i = 0; i < frame.blockSize; i++) {
                        for (int channel = 0; channel < info.numChannels; channel++) {
                            int sample = samples[channel][i];
                            pcm[length++] = (byte)sample;
                            pcm[length++] = (byte)(sample >>> 8);
                        }
                    }
                    md5.update(pcm, 0, length);
                    for (int offset = 0; offset < length;) {
                        checkCancelled(cancelled);
                        int written = line.write(pcm, offset, length - offset);
                        if (written <= 0 || written > length - offset || written % format.getFrameSize() != 0) {
                            throw new IOException("声卡未接收完整的 PCM 采样帧");
                        }
                        offset += written;
                    }
                }
                if (count == 0 || (info.numSamples != 0 && count != info.numSamples)) {
                    throw new IOException("FLAC 为空或在声明的样本数之前结束");
                }
                if (!Arrays.equals(info.md5Hash, new byte[16])
                        && !MessageDigest.isEqual(info.md5Hash, md5.digest())) {
                    throw new IOException("解码 PCM 的 MD5 与 STREAMINFO 不一致");
                }
                // drain 也可能等待声卡，只允许在后台执行，由超时任务负责解除阻塞。
                checkCancelled(cancelled);
                line.drain();
                checkCancelled(cancelled);
                return count;
            }
        } catch (DataFormatException | IllegalArgumentException | IllegalStateException exception) {
            throw new IOException("FLAC 数据或声卡状态无效", exception);
        }
    }

    private static StreamInfo readStreamInfo(StreamInput input) throws IOException {
        if (input.readUint(32) != 0x664C6143) throw new IOException("响应不是原生 FLAC 文件");
        StreamInfo info = null;
        int metadataBytes = 0;
        for (int blocks = 0; blocks < 128; blocks++) {
            boolean last = input.readUint(1) != 0;
            int type = input.readUint(7);
            int length = input.readUint(24);
            metadataBytes += 4 + length;
            if (metadataBytes > 1024 * 1024) throw new IOException("FLAC 元数据超过 1 MiB 上限");
            if ((blocks == 0 && (type != 0 || length != 34)) || (blocks != 0 && type == 0) || type == 127) {
                throw new IOException("FLAC STREAMINFO 缺失、重复或元数据非法");
            }
            byte[] data = new byte[length];
            input.readFully(data);
            if (type == 0) {
                info = new StreamInfo(data);
                info.checkValues();
            }
            if (last) return info;
        }
        throw new IOException("FLAC 元数据块数量超过上限");
    }

    private static MessageDigest md5() {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java 运行时缺少 MD5 实现", exception);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) throws InterruptedIOException {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("FLAC 测试已取消");
        }
    }

    /** 只提供顺序输入；位读取、CRC 和 FLAC 帧解码仍由 nayuki 实现。 */
    static final class StreamInput extends AbstractFlacLowLevelInput {
        private final InputStream source;
        private long bytesRead;

        StreamInput(InputStream source) { this.source = java.util.Objects.requireNonNull(source); }

        @Override public long getLength() { return -1; }

        @Override public void seekTo(long position) { throw new UnsupportedOperationException("HTTP 测试流不支持 seek"); }

        @Override protected int readUnderlying(byte[] buffer, int offset, int length) throws IOException {
            checkCancelled(() -> false);
            int allowed = (int)Math.min(length, MAX_BYTES - bytesRead + 1);
            int read = source.read(buffer, offset, allowed);
            if (read > 0 && (bytesRead += read) > MAX_BYTES) throw new IOException("FLAC 输入超过 16 MiB 上限");
            return read;
        }

        @Override public void close() throws IOException {
            try { source.close(); } finally { super.close(); }
        }
    }

    private static final class TestRun {
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicReference<HttpClient> client = new AtomicReference<>();
        final AtomicReference<InputStream> input = new AtomicReference<>();
        final AtomicReference<SourceDataLine> line = new AtomicReference<>();
        final ScheduledThreadPoolExecutor cleanup = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "CloudXMusic-QQ-FLAC-Cleanup");
            thread.setDaemon(true);
            return thread;
        });
        volatile Thread worker;

        TestRun() { cleanup.setRemoveOnCancelPolicy(true); }

        void requestStop() {
            if (!cancelled.compareAndSet(false, true)) return;
            Thread current = worker;
            if (current != null) current.interrupt();
            // 生命周期回调只发出取消信号，关闭网络与声卡交给后台线程。
            try { cleanup.execute(this::closeResources); } catch (RejectedExecutionException ignored) { }
        }

        void closeResources() {
            HttpClient http = client.getAndSet(null);
            if (http != null) {
                try { http.shutdownNow(); } catch (RuntimeException exception) { LOGGER.debug("关闭 FLAC HTTP 客户端失败", exception); }
            }
            InputStream body = input.getAndSet(null);
            if (body != null) {
                try { body.close(); } catch (IOException exception) { LOGGER.debug("关闭 FLAC 输入流失败", exception); }
            }
            SourceDataLine audio = line.getAndSet(null);
            if (audio != null) {
                try { audio.close(); } catch (RuntimeException exception) { LOGGER.debug("关闭 FLAC 声卡失败", exception); }
            }
        }
    }
}
