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
    /** 当前正在读的连接，切歌/切音质时强制断开，避免旧线程卡在网络超时里。 */
    private volatile HttpURLConnection activeConnection;
    /** FLAC 解码器声明的输出格式，由 STREAMINFO 决定。 */
    private volatile AudioFormat flacFormat =
            new AudioFormat(44100, 16, 2, true, false);

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
        // 换歌/换音质时旧的字节长度会失效，必须先清零，否则第一次 Range 会越界（HTTP 416）。
        streamBytes = 0;
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
        worker = new Thread(this::runLoop, "CloudXmusic-Audio");
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

    /** 消费掉 finished 标记：切歌要等异步取地址，否则每个 tick 都会再触发一次 next()。 */
    public void clearFinished() {
        finished = false;
    }

    public String error() {
        return error;
    }

    private synchronized void shutdown() {
        stopRequested = true;
        // 强制断开正在读的连接，让卡在 readTimeout 里的旧线程立刻返回，而不是等 15 秒。
        HttpURLConnection connection = activeConnection;
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException ignored) {
            }
        }
        Thread thread = worker;
        worker = null;
        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        closeLine();
    }

    private void runLoop() {
        long startAt = positionMs;
        // 无损音源是 FLAC，JLayer 解不了，走另一条解码路径。
        if (isFlac(currentUrl)) {
            runFlacLoop(startAt);
            return;
        }
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

    /** 链接是不是 FLAC（无损档位）。 */
    private static boolean isFlac(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        return lower.contains(".flac") || lower.contains("type=flac");
    }

    /**
     * FLAC 播放：jFLAC 推送式解码，解出来的 PCM 直接写声卡。
     *
     * <p>跳转和 mp3 一样按字节偏移重开 Range（FLAC 帧自带同步头，从任意帧起点都能接着解）；
     * 暂停时阻塞解码线程，不重新建流。</p>
     */
    private void runFlacLoop(long startAtMs) {
        long startAt = startAtMs;
        while (!stopRequested) {
            try {
                // FLAC 必须从文件头读：STREAMINFO 在开头，缺了它采样率/声道全错，声音会是噪音。
                Stream stream = open(currentUrl, 0);
                streamBytes = stream.contentLength();
                try (InputStream raw = stream.input()) {
                    org.jflac.FLACDecoder decoder =
                            new org.jflac.FLACDecoder(new BufferedInputStream(raw, 128 * 1024));
                    decoder.addPCMProcessor(new FlacSink(startAt));
                    decoder.decode();
                }
                finished = true;
                active = false;
                drainLine();
                break;
            } catch (RestartStream restart) {
                closeLine();
                startAt = positionMs;
            } catch (Throwable t) {
                error = String.valueOf(t.getMessage());
                CloudXMusicClient.LOGGER.warn("FLAC 播放失败：{}", error, t);
                active = false;
                break;
            }
        }
        closeLine();
    }

    /** jFLAC 的 PCM 回调：统一降到 16bit 小端输出（设备几乎都支持 24bit 很少支持）。 */
    private final class FlacSink implements org.jflac.PCMProcessor {
        private final long startAt;
        private long decodedMs;
        private AudioFormat source = new AudioFormat(44100, 16, 2, true, false);

        FlacSink(long startAt) {
            this.startAt = startAt;
        }

        @Override public void processStreamInfo(org.jflac.metadata.StreamInfo info) {
            if (info == null) return;
            AudioFormat declared = info.getAudioFormat();
            source = declared != null && declared.getSampleRate() > 0 ? declared
                    : new AudioFormat(info.getSampleRate() > 0 ? info.getSampleRate() : 44100,
                    info.getBitsPerSample() > 0 ? info.getBitsPerSample() : 16,
                    Math.max(1, info.getChannels()), true, false);
            flacFormat = source;
        }

        @Override public void processPCM(org.jflac.util.ByteData pcm) {
            if (stopRequested) throw new RestartStream();
            while (paused && !stopRequested) sleep(40);
            if (stopRequested) throw new RestartStream();

            long pending = seekTargetMs;
            if (pending >= 0 && Math.abs(pending - startAt) > 400) throw new RestartStream();
            seekTargetMs = -1;

            int len = pcm.getLen();
            if (len <= 0) return;
            int channels = Math.max(1, source.getChannels());
            int frameSize = Math.max(1, source.getFrameSize());
            long frameMs = (long) ((len / (double) frameSize) * 1000.0 / source.getSampleRate());
            if (decodedMs + frameMs <= startAt) {
                decodedMs += frameMs;
                return;
            }
            try {
                ensureLine(new AudioFormat(source.getSampleRate(), 16, channels, true, false));
            } catch (Exception e) {
                throw new IllegalStateException("打开声卡失败：" + e.getMessage(), e);
            }
            writeFlacPcm(pcm.getData(), len);
            decodedMs += frameMs;
            positionMs = decodedMs;
        }
    }

    /** 把任意位深的解码结果压到 16bit 小端并写声卡；音量为 1 且本来就是 16bit 时直接透传。 */
    private void writeFlacPcm(byte[] data, int len) {
        if (line == null || len <= 0 || stopRequested) return;
        AudioFormat source = flacFormat;
        int bytesPerSample = Math.max(1, source.getSampleSizeInBits() / 8);
        int samples = len / bytesPerSample;
        if (samples <= 0) return;
        if (bytesPerSample == 2 && !source.isBigEndian() && volume >= 0.999f) {
            line.write(data, 0, len);
            return;
        }
        boolean big = source.isBigEndian();
        byte[] out = new byte[samples * 2];
        float gain = volume;
        int drop = Math.max(0, (bytesPerSample - 2) * 8);
        for (int i = 0; i < samples; i++) {
            int base = i * bytesPerSample;
            int value = 0;
            for (int b = 0; b < bytesPerSample; b++) {
                int index = big ? base + b : base + bytesPerSample - 1 - b;
                value = (value << 8) | (data[index] & 0xFF);
            }
            int shift = 32 - bytesPerSample * 8;
            value = (value << shift) >> shift;
            int scaled = Math.round(value * gain) >> drop;
            if (scaled > 32767) scaled = 32767;
            if (scaled < -32768) scaled = -32768;
            out[i * 2] = (byte) scaled;
            out[i * 2 + 1] = (byte) (scaled >> 8);
        }
        line.write(out, 0, out.length);
    }

    /** FLAC 用解码器给的格式开声卡。 */
    private void ensureLine(AudioFormat format) throws Exception {
        if (line != null && line.isOpen()) return;
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        int buffer = (int) (format.getSampleRate() * 0.4) * Math.max(1, format.getFrameSize());
        line.open(format, Math.max(8192, buffer));
        line.start();
    }

    /** 时间 → 字节偏移（CBR 近似）。 */
    private long byteOffset(long ms) {
        if (streamBytes <= 0 || totalMs <= 0) return 0;
        long offset = (long) (streamBytes * (ms / (double) totalMs));
        // 不能超过整段，留一点余量
        return Math.max(0, Math.min(streamBytes - 8192, offset));
    }

    private Stream open(String url, long startByte) throws Exception {
        Stream stream = openOnce(url, startByte);
        if (stream == null) {
            // 416：Range 起点超出实际长度（多为长度估算偏差），退回从头读，解码时再跳过。
            stream = openOnce(url, 0);
        }
        if (stream == null) throw new java.io.IOException("音频服务器拒绝了请求（Range 越界）");
        return stream;
    }

    /** @return null 表示服务端 416，需要调用方退回无 Range 重试。 */
    private Stream openOnce(String url, long startByte) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        activeConnection = connection;
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
        if (code == 416 && startByte > 0) return null;
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
