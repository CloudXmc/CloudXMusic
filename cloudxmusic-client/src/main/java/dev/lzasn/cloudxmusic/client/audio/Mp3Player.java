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
    /**
     * 代次。每次 play / shutdown / 超时重连都自增，线程启动时捕获自己那一代，
     * 之后再拿它和这里的值比。旧线程一旦发现代次变了就立刻收工，
     * 不会再碰连接和声卡——这是以前「两代线程互相掐断对方连接」的根治点。
     *
     * <p>必须是 volatile：写发生在 synchronized 的 play/shutdown 里，读发生在解码线程，
     * 不加的话旧线程可能一直读到自己的旧值，永远不退出。</p>
     */
    private volatile long generation = 1;
    /** 当前这一代独占的连接、声卡和解码状态。 */
    private volatile Engine engine;

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
    /** 上一次位置推进的时间，用来识别「线程活着但已经不产出声音」的卡死。 */
    private volatile long lastProgressAt;
    /** 暂停超过这个时长就断流重连，免得 CDN 掐掉空闲连接。 */
    private static final long IDLE_RESTART_MS = 60_000;
    /** 播放中位置多久没推进就认为卡住。正常每 26ms 推进一次，3 秒不动基本就是卡在连接上了。 */
    private static final long STALL_MS = 3_000;

    /**
     * 一代解码线程私有的运行时资源。
     *
     * <p>以前这些东西（activeConnection / line / streamBytes）是实例字段，
     * 新旧两代线程共用同一份，任何一方 close 都会打断另一方正在进行的读或写。
     * 现在每代各持一份，谁关闭都只影响自己。</p>
     */
    private static final class Engine {
        final long gen;
        HttpURLConnection connection;
        SourceDataLine line;
        long streamBytes;

        Engine(long gen) {
            this.gen = gen;
        }

        /** 幂等：shutdown 和线程退出都会调，先关的那个生效。 */
        void close() {
            SourceDataLine current = line;
            line = null;
            if (current != null) {
                try {
                    current.stop();
                    current.close();
                } catch (RuntimeException ignored) {
                    // 已经关掉了
                }
            }
        }
    }

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
        // 有未完成的跳转目标时优先用它：用户 seek 之后马上点了播放重启，
        // 不能让他跳回 seek 之前的位置。
        long start = seekTargetMs >= 0 ? seekTargetMs : Math.max(0, startMs);
        positionMs = start;
        seekTargetMs = start > 0 ? start : -1;
        error = "";
        finished = false;
        paused = false;
        active = true;
        lastProgressAt = System.currentTimeMillis();
        spawn();
    }

    private void spawn() {
        Engine created = new Engine(++generation);
        engine = created;
        Thread thread = new Thread(() -> runLoop(created), "CloudXmusic-Audio");
        thread.setDaemon(true);
        worker = thread;
        thread.start();
    }

    /** 本线程是否已经被新的一代取代。所有循环和写入检查都必须用它。 */
    private boolean stale(long gen) {
        return gen != generation;
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
        // 恢复瞬间重置计时，否则暂停期间的时间会被算进「卡住」判定。
        lastProgressAt = System.currentTimeMillis();
        if (System.currentTimeMillis() - pausedSince < IDLE_RESTART_MS
                && worker != null && worker.isAlive()) {
            return;
        }
        // 重开一代：旧线程靠代次自增自行退出，这里只负责断开旧连接和回收旧声卡。
        generation++;
        Engine old = engine;
        engine = null;
        if (old != null) {
            HttpURLConnection connection = old.connection;
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
            old.close();
        }
        Thread thread = worker;
        worker = null;
        if (thread != null) {
            try {
                thread.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        seekTargetMs = positionMs;
        spawn();
    }

    public void seek(long ms) {
        if (!active) return;
        long target = Math.max(0, Math.min(totalMs, ms));
        seekTargetMs = target;
        // 暂停时没有音频在跑，直接写回位置，界面下一帧就显示新值、不会回弹。
        if (paused) positionMs = target;
        // 播放中不预写：跳转要重开 Range 流，先写目标值会让界面假装已到位，
        // 再被解码线程的旧位置覆盖回去，进度条会颤一下。
    }

    public synchronized void stop() {
        shutdown();
        active = false;
        paused = false;
        positionMs = 0;
        // 清掉残留的跳转目标，否则下一首歌会莫名其妙从上一首的 seek 位置开始。
        seekTargetMs = -1;
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

    /**
     * 是否应该放弃当前播放状态、重新取地址重启。
     *
     * <p>三种情况都要重启，否则 togglePlaying 只会在 pause/resume 之间空转，
     * 用户看到的就是「点播放没反应」：</p>
     * <ul>
     *   <li>根本没在播；</li>
     *   <li>解码线程已经没了（僵死）；</li>
     *   <li>线程还活着但位置几秒没推进——典型是重开 Range 流时卡在 CDN 连接上，
     *       此时 isAlive() 为真，resume() 会以为一切正常直接返回。</li>
     * </ul>
     */
    public boolean needsRestart() {
        if (!active) return true;
        Thread thread = worker;
        if (engine == null || thread == null || !thread.isAlive()) return true;
        return !paused && System.currentTimeMillis() - lastProgressAt > STALL_MS;
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
        // 代次自增是最关键的一步：在跑的线程下一轮检查就会发现自己是旧一代，
        // 主动收工，不会再 rearm 自己去抢新线程的连接。
        generation++;
        Engine old = engine;
        engine = null;
        if (old != null) {
            // 强制断开正在读的连接，让卡在 readTimeout 里的旧线程立刻返回，而不是等 15 秒。
            HttpURLConnection connection = old.connection;
            if (connection != null) {
                try {
                    connection.disconnect();
                } catch (RuntimeException ignored) {
                }
            }
            old.close();
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
    }

    private void runLoop(Engine e) {
        long startAt = positionMs;
        int retries = 0;
        while (!stale(e.gen)) {
            boolean ranged;
            try {
                long startByte = byteOffset(e, startAt);
                Stream stream = open(e, currentUrl, startByte);
                e.streamBytes = stream.contentLength();
                ranged = stream.ranged();

                try (InputStream raw = stream.input()) {
                    Bitstream bitstream = new Bitstream(new BufferedInputStream(raw, 128 * 1024));
                    Decoder decoder = new Decoder();
                    // Range 生效时直接从目标位置开始；没生效就得从头解码跳帧
                    long decodedMs = ranged ? startAt : 0;
                    Header header;
                    while (!stale(e.gen)) {
                        while (paused && !stale(e.gen)) {
                            sleep(40);
                        }
                        if (stale(e.gen)) break;
                        if ((header = bitstream.readFrame()) == null) {
                            finished = true;
                            active = false;
                            drainLine(e);
                            return;
                        }
                        SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        bitstream.closeFrame();

                        int channels = Math.max(1, decoder.getOutputChannels());
                        int rate = decoder.getOutputFrequency() > 0 ? decoder.getOutputFrequency() : 44100;
                        int samples = buffer.getBufferLength();
                        long frameMs = (long) ((samples / (double) channels) * 1000.0 / rate);

                        // 和 decodedMs（解码线程的真实进度）比，不能和 positionMs 比：
                        // positionMs 已被 seek() 预写成目标值，两者相等就永远判不出需要跳转。
                        long pendingSeek = seekTargetMs;
                        if (pendingSeek >= 0 && Math.abs(pendingSeek - decodedMs) > 400) {
                            startAt = pendingSeek;
                            seekTargetMs = -1;
                            throw new RestartStream();
                        }
                        seekTargetMs = -1;

                        if (!ranged && decodedMs + frameMs <= startAt) {
                            decodedMs += frameMs;
                            positionMs = decodedMs;
                            lastProgressAt = System.currentTimeMillis();
                            continue;
                        }

                        ensureLine(e, rate, channels);
                        writeFrames(e, buffer.getBuffer(), samples);
                        decodedMs += frameMs;
                        positionMs = decodedMs;
                        lastProgressAt = System.currentTimeMillis();
                        retries = 0;
                    }
                    // 内层循环可能是「代次过期」退出的。这时 active/finished 属于新一代，
                    // 旧线程一行都不能写，否则会把正在播的新线程设成停止状态。
                    if (stale(e.gen)) break;
                    finished = true;
                    active = false;
                    drainLine(e);
                }
                break;
            } catch (RestartStream restart) {
                if (stale(e.gen)) break;
                e.close();
                positionMs = startAt;
            } catch (Throwable t) {
                // 旧线程被 disconnect 掉抛出的异常不是播放失败，不能污染新一代的状态。
                if (stale(e.gen)) break;
                if (retryable(t) && retries < 2) {
                    retries++;
                    CloudXMusicClient.LOGGER.warn("播放中断，第 {} 次重连：{}", retries, t.getMessage());
                    sleep(700L * retries);
                    continue;
                }
                error = String.valueOf(t.getMessage());
                CloudXMusicClient.LOGGER.warn("播放失败：{}", error, t);
                active = false;
                break;
            }
        }
        e.close();
    }

    /**
     * 网络抖动可以重连；HTTP 状态错误（403/416 之类）和本地资源错误重连也没用。
     * 主动 disconnect 造成的中断已经由 stale 拦在外面，不会走到这里。
     */
    private static boolean retryable(Throwable t) {
        return t instanceof java.io.IOException
                && !String.valueOf(t.getMessage()).contains("HTTP ");
    }

    /** 时间 → 字节偏移（CBR 近似）。 */
    private long byteOffset(Engine engine, long ms) {
        if (engine.streamBytes <= 0 || totalMs <= 0) return 0;
        long offset = (long) (engine.streamBytes * (ms / (double) totalMs));
        // 不能超过整段，留一点余量
        return Math.max(0, Math.min(engine.streamBytes - 8192, offset));
    }

    private Stream open(Engine engine, String url, long startByte) throws Exception {
        Stream stream = openOnce(engine, url, startByte);
        if (stream == null) {
            // 416：Range 起点超出实际长度（多为长度估算偏差），退回从头读，解码时再跳过。
            stream = openOnce(engine, url, 0);
        }
        if (stream == null) throw new java.io.IOException("音频服务器拒绝了请求（Range 越界）");
        return stream;
    }

    /** @return null 表示服务端 416，需要调用方退回无 Range 重试。 */
    private Stream openOnce(Engine engine, String url, long startByte) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        engine.connection = connection;
        // CDN 正常 200ms 内连上，5 秒连不上基本就是不通，早点失败好让上层重试或让用户手动重播。
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(15_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", NeteaseHttp.USER_AGENT);
        // CDN 只要 Referer 做防盗链，别把网易云的 Cookie 带过去：
        // 一是没必要，二是带会员 Cookie 请求音乐文件曾出现 403
        connection.setRequestProperty("Referer", "https://music.163.com/");
        connection.setRequestProperty("Accept", "*/*");
        if (startByte > 0) connection.setRequestProperty("Range", "bytes=" + startByte + "-");

        int code = connection.getResponseCode();
        if (code == 416 && startByte > 0) {
            try {
                connection.disconnect();
            } catch (RuntimeException ignored) {
            }
            return null;
        }
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            throw new java.io.IOException("音频服务器返回 HTTP " + code
                    + "（链接可能过期或该音质不可用）");
        }
        boolean ranged = code == HttpURLConnection.HTTP_PARTIAL;
        long length = connection.getContentLengthLong();
        if (ranged) length = startByte + Math.max(0, length);
        return new Stream(connection.getInputStream(), length, ranged);
    }

    private void ensureLine(Engine engine, int rate, int channels) throws Exception {
        if (engine.line != null && engine.line.isOpen()) return;
        AudioFormat format = new AudioFormat(rate, 16, channels, true, BIG_ENDIAN);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
        // 约 0.4 秒的声卡缓冲，网络抖动时不至于立刻断音
        line.open(format, Math.max(8192, (int) (rate * 0.4) * channels * 2));
        line.start();
        engine.line = line;
    }

    private void writeFrames(Engine engine, short[] samples, int length) {
        SourceDataLine line = engine.line;
        if (line == null || length <= 0 || stale(engine.gen)) return;
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

    private void drainLine(Engine engine) {
        SourceDataLine line = engine.line;
        if (line != null) {
            try {
                line.drain();
            } catch (RuntimeException ignored) {
                // 设备被占用时 drain 会抛，忽略
            }
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
