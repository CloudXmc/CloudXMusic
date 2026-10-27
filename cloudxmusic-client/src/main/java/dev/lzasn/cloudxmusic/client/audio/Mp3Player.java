package dev.lzasn.cloudxmusic.client.audio;

import dev.lzasn.cloudxmusic.client.CloudXMusicClient;
import dev.lzasn.cloudxmusic.client.netease.NeteaseHttp;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * 在线播放：HTTP 流式读取 → JLayer 实时解码 → SourceDataLine 输出。
 *
 * <p><b>不下载</b>：全程只有一个 128KB 的读缓冲 + 声卡缓冲，边收边解边放，不写磁盘、不缓存整首、
 * 不预先下完。歌有多长就听多长，没听到的部分不会被拉下来。</p>
 *
 * <p><b>跳转走 Range</b>：mp3 基本都是 CBR，按 {@code 字节 = 总字节 × 时间占比} 估算偏移，
 * 用 {@code Range: bytes=X-} 重开连接，所以拖进度条只从目标位置往后拉，不会把前面重下一遍。
 * 服务端不认 Range（返回 200）时退回从头解码跳帧，功能仍然正确。</p>
 *
 * <p><b>暂停即断流</b>：暂停时直接关掉连接并记住位置，恢复时用 Range 从该位置重连，
 * 不占着 CDN 长连接等超时。</p>
 */
public final class Mp3Player {
    /** JLayer 输出大端 16bit PCM。 */
    private static final boolean BIG_ENDIAN = true;

    private Thread worker;
    private volatile boolean stopRequested;
    private volatile boolean paused;
    private volatile boolean active;
    private volatile boolean finished;
    private volatile String currentUrl = "";
    private volatile long totalMs;
    private volatile long positionMs;
    private volatile long seekTargetMs = -1;
    private volatile float volume = 0.8f;
    private volatile String error = "";
    private volatile long pausedSince;
    /** 暂停超过这个时长就断流重连，免得 CDN 掐掉空闲连接。 */
    private static final long IDLE_RESTART_MS = 60_000;

    private SourceDataLine line;
    private long streamBytes;

    /**
     * 开始（或重启）播放。
     *
     * @param url      CDN 直链（带时效）
     * @param startMs  起始位置
     * @param totalMs  总时长，只用来把时间换算成字节偏移
     */
    public synchronized void play(String url, long startMs, long totalMs) {
        shutdown();
        currentUrl = url;
        this.totalMs = Math.max(1, totalMs);
        positionMs = Math.max(0, startMs);
        seekTargetMs = startMs > 0 ? startMs : -1;
        error = "";
        finished = false;
        paused = false;
        stopRequested = false;
        active = true;
        spawn();
    }

    private void spawn() {
        worker = new Thread(this::runLoop, "CloudXMusic-Audio");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * 暂停：只让解码线程停手，连接和声卡都保留。
     *
     * <p>以前这里是断流 + 重连 + 按字节估算位置，估偏了进度就乱跳；现在位置冻结在
     * 最后一个解码样本上，恢复即续播。</p>
     */
    public synchronized void pause() {
        if (!active || paused) return;
        paused = true;
        pausedSince = System.currentTimeMillis();
    }

    /** 恢复：暂停太久（CDN 会掐空闲连接）才重开流，否则原地继续。 */
    public synchronized void resume() {
        if (!active || !paused) return;
        paused = false;
        if (System.currentTimeMillis() - pausedSince < IDLE_RESTART_MS
                && worker != null && worker.isAlive()) {
            return;
        }
        stopRequested = true;
        Thread old = worker;
        worker = null;
        if (old != null) {
            try {
                old.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeLine();
        stopRequested = false;
        seekTargetMs = positionMs;
        spawn();
    }

    public void seek(long ms) {
        if (!active) return;
        seekTargetMs = Math.max(0, Math.min(totalMs, ms));
        positionMs = seekTargetMs;
    }

    public synchronized void stop() {
        shutdown();
        active = false;
        paused = false;
        positionMs = 0;
    }

    public void setVolume(float value) {
        volume = Math.max(0f, Math.min(1f, value));
    }

    public float volume() {
        return volume;
    }

    public long positionMs() {
        return positionMs;
    }

    public long totalMs() {
        return totalMs;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean finished() {
        return finished;
    }

    public String error() {
        return error;
    }

    private synchronized void shutdown() {
        stopRequested = true;
        Thread thread = worker;
        worker = null;
        if (thread != null) {
            try {
                thread.join(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeLine();
    }

    private void runLoop() {
        long startAt = positionMs;
        while (!stopRequested) {
            boolean ranged;
            try {
                long startByte = byteOffset(startAt);
                Stream stream = open(currentUrl, startByte);
                streamBytes = stream.contentLength();
                ranged = stream.ranged();

                try (InputStream raw = stream.input()) {
                    Bitstream bitstream = new Bitstream(new BufferedInputStream(raw, 128 * 1024));
                    Decoder decoder = new Decoder();
                    // Range 生效时直接从目标位置开始；没生效就得从头解码跳帧
                    long decodedMs = ranged ? startAt : 0;
                    Header header;
                    while (!stopRequested) {
                        while (paused && !stopRequested) {
                            sleep(40);
                        }
                        if (stopRequested) break;
                        if ((header = bitstream.readFrame()) == null) {
                            finished = true;
                            active = false;
                            drainLine();
                            return;
                        }
                        SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        bitstream.closeFrame();

                        int channels = Math.max(1, decoder.getOutputChannels());
                        int rate = decoder.getOutputFrequency() > 0 ? decoder.getOutputFrequency() : 44100;
                        int samples = buffer.getBufferLength();
                        long frameMs = (long) ((samples / (double) channels) * 1000.0 / rate);

                        long pendingSeek = seekTargetMs;
                        if (pendingSeek >= 0 && Math.abs(pendingSeek - startAt) > 400) {
                            startAt = pendingSeek;
                            seekTargetMs = -1;
                            throw new RestartStream();
                        }
                        seekTargetMs = -1;

                        if (!ranged && decodedMs + frameMs <= startAt) {
                            decodedMs += frameMs;
                            continue;
                        }

                        ensureLine(rate, channels);
                        writeFrames(buffer.getBuffer(), samples);
                        decodedMs += frameMs;
                        positionMs = decodedMs;
                    }
                    finished = true;
                    active = false;
                    drainLine();
                }
                break;
            } catch (RestartStream restart) {
                closeLine();
                positionMs = startAt;
            } catch (Throwable t) {
                error = String.valueOf(t.getMessage());
                CloudXMusicClient.LOGGER.warn("播放失败：{}", error, t);
                active = false;
                break;
            }
        }
        closeLine();
    }

    /** 时间 → 字节偏移（CBR 近似）。 */
    private long byteOffset(long ms) {
        if (streamBytes <= 0 || totalMs <= 0) return 0;
        long offset = (long) (streamBytes * (ms / (double) totalMs));
        // 不能超过整段，留一点余量
        return Math.max(0, Math.min(streamBytes - 8192, offset));
    }

    private Stream open(String url, long startByte) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", NeteaseHttp.USER_AGENT);
        // CDN 只要 Referer 做防盗链，别把网易云的 Cookie 带过去：
        // 一是没必要，二是带会员 Cookie 请求音乐文件曾出现 403
        connection.setRequestProperty("Referer", "https://music.163.com/");
        connection.setRequestProperty("Accept", "*/*");
        if (startByte > 0) connection.setRequestProperty("Range", "bytes=" + startByte + "-");

        int code = connection.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            throw new java.io.IOException("音频服务器返回 HTTP " + code
                    + "（链接可能过期或该音质不可用）");
        }
        boolean ranged = code == HttpURLConnection.HTTP_PARTIAL;
        long length = connection.getContentLengthLong();
        if (ranged) length = startByte + Math.max(0, length);
        return new Stream(connection.getInputStream(), length, ranged);
    }

    private void ensureLine(int rate, int channels) throws Exception {
        if (line != null && line.isOpen()) return;
        AudioFormat format = new AudioFormat(rate, 16, channels, true, BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        // 约 0.4 秒的声卡缓冲，网络抖动时不至于立刻断音
        line.open(format, Math.max(8192, (int) (rate * 0.4) * channels * 2));
        line.start();
    }

    private void writeFrames(short[] samples, int length) {
        if (line == null || length <= 0 || stopRequested) return;
        byte[] bytes = new byte[length * 2];
        float gain = volume;
        for (int i = 0; i < length; i++) {
            int value = Math.round(samples[i] * gain);
            if (value > Short.MAX_VALUE) value = Short.MAX_VALUE;
            if (value < Short.MIN_VALUE) value = Short.MIN_VALUE;
            bytes[i * 2] = (byte) (value >> 8);
            bytes[i * 2 + 1] = (byte) value;
        }
        line.write(bytes, 0, bytes.length);
    }

    private void drainLine() {
        if (line != null) {
            try {
                line.drain();
            } catch (RuntimeException ignored) {
                // 设备被占用时 drain 会抛，忽略
            }
        }
    }

    private void closeLine() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (RuntimeException ignored) {
                // 已经关掉了
            }
            line = null;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Stream(InputStream input, long contentLength, boolean ranged) {}

    /** 内部控制流：需要换 Range 重开连接。 */
    private static final class RestartStream extends RuntimeException {
        RestartStream() {
            super(null, null, false, false);
        }
    }
}
