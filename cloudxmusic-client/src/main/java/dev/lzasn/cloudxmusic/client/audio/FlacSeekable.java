package dev.lzasn.cloudxmusic.client.audio;

import io.nayuki.flac.common.FrameInfo;
import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.decode.AbstractFlacLowLevelInput;
import io.nayuki.flac.decode.FrameDecoder;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 可跳转的 FLAC 音轨。
 *
 * <p>FLAC 是 VBR、帧长不固定，也没有可用的字节↔时间换算，所以网络顺序流没法直接 seek。
 * 这里把整轨落到临时文件，先顺序解一遍记录「每帧起始字节 + 起始采样号」，之后跳转只需
 * 二分查到最近的帧字节位置、从那里重新开一个解码器，再丢弃帧内多余采样即可。</p>
 *
 * <p>代价是首播要等一次下载 + 一次扫描（数秒）；索引本身很小，约每帧 16 字节。</p>
 */
final class FlacSeekable implements Closeable {
    private final Path path;
    private final FileInput input;
    private final StreamInfo info;
    private final long[] frameByte;
    private final long[] frameSample;
    private FrameDecoder decoder;
    /** 当前帧内需要丢弃的采样数，仅在同帧内跳转时非 0。 */
    private int skipSamples;

    private FlacSeekable(Path path, FileInput input, StreamInfo info,
                         long[] frameByte, long[] frameSample) {
        this.path = path;
        this.input = input;
        this.info = info;
        this.frameByte = frameByte;
        this.frameSample = frameSample;
        this.decoder = new FrameDecoder(input, info.sampleDepth);
    }

    /** 下载整轨到临时文件并建立帧索引。调用线程会被阻塞。 */
    static FlacSeekable prepare(InputStream source) throws IOException {
        Path path = Files.createTempFile("cloudxmusic-", ".flac");
        boolean ok = false;
        try {
            try (OutputStream out = Files.newOutputStream(path)) {
                source.transferTo(out);
            }
            FileInput input = new FileInput(new RandomAccessFile(path.toFile(), "r"));
            try {
                StreamInfo info = readStreamInfo(input);
                long[][] index = buildIndex(input, info);
                FlacSeekable track = new FlacSeekable(path, input, info, index[0], index[1]);
                ok = true;
                return track;
            } finally {
                if (!ok) input.close();
            }
        } finally {
            if (!ok) Files.deleteIfExists(path);
        }
    }

    StreamInfo info() {
        return info;
    }

    /** 索引规模，便于日志与测试断言。 */
    int frameCount() {
        return frameByte.length;
    }

    /** 跳到指定毫秒；下一次 readFrame 从该位置开始。 */
    void seekToMs(long ms) {
        long target = Math.max(0, ms) * (long) info.sampleRate / 1000L;
        int idx = java.util.Arrays.binarySearch(frameSample, target);
        if (idx < 0) idx = -idx - 2;
        if (idx < 0) idx = 0;
        if (idx >= frameByte.length) idx = frameByte.length - 1;
        input.seekTo(frameByte[idx]);
        // 跳转后必须换新解码器，旧实例的位缓冲还停在原来的帧里。
        decoder = new FrameDecoder(input, info.sampleDepth);
        skipSamples = (int) Math.max(0, target - frameSample[idx]);
    }

    /**
     * 解下一帧。
     *
     * @param planes 每个声道一条缓冲，长度需不小于最大块长
     * @return 本帧采样数；返回 0 表示结束
     */
    int readFrame(int[][] planes) throws IOException {
        FrameInfo frame = decoder.readFrame(planes, 0);
        if (frame == null) return 0;
        return frame.blockSize;
    }

    int skipSamples() {
        return skipSamples;
    }

    void clearSkip() {
        skipSamples = 0;
    }

    @Override
    public void close() {
        try {
            decoder = null;
            input.close();
        } catch (IOException ignored) {
            // 关闭失败只影响临时文件删除，不打断播放线程。
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件留在系统临时目录，随系统清理。
        }
    }

    /** 读 FLAC 头：magic + 元数据块，取出 StreamInfo。与顺序解码器保持同样的校验。 */
    private static StreamInfo readStreamInfo(FileInput input) throws IOException {
        if (input.readUint(32) != 0x664C6143) throw new IOException("响应不是原生 FLAC");
        StreamInfo info = null;
        for (int blocks = 0; blocks < 128; blocks++) {
            boolean last = input.readUint(1) != 0;
            int type = input.readUint(7);
            int length = input.readUint(24);
            if (type == 127 || length > 16 * 1024 * 1024) throw new IOException("FLAC 元数据无效");
            byte[] data = new byte[length];
            input.readFully(data);
            if (type == 0) {
                info = new StreamInfo(data);
                info.checkValues();
            }
            if (last) break;
        }
        if (info == null) throw new IOException("FLAC 缺少 StreamInfo");
        if (info.numChannels < 1 || info.numChannels > 2)
            throw new IOException("暂不支持该 FLAC 声道数");
        return info;
    }

    /**
     * 顺序解一遍，记录每帧的起始字节与起始采样号。
     *
     * @return {@code [字节偏移数组, 采样偏移数组]}
     */
    private static long[][] buildIndex(FileInput input, StreamInfo info) throws IOException {
        FrameDecoder scanner = new FrameDecoder(input, info.sampleDepth);
        int[][] planes = new int[info.numChannels][65536];
        long[] bytes = new long[4096];
        long[] samples = new long[4096];
        int count = 0;
        long sample = 0;
        while (true) {
            long position = input.getPosition();
            FrameInfo frame = scanner.readFrame(planes, 0);
            if (frame == null) break;
            if (count == bytes.length) {
                bytes = java.util.Arrays.copyOf(bytes, count * 2);
                samples = java.util.Arrays.copyOf(samples, count * 2);
            }
            bytes[count] = position;
            samples[count] = sample;
            count++;
            sample += frame.blockSize;
        }
        if (count == 0) throw new IOException("FLAC 没有可解码帧");
        return new long[][]{
                java.util.Arrays.copyOf(bytes, count),
                java.util.Arrays.copyOf(samples, count)
        };
    }

    /** 基于文件的随机访问输入；{@code seekTo} 必须通知基类重置位缓冲。 */
    static final class FileInput extends AbstractFlacLowLevelInput {
        private final RandomAccessFile file;

        FileInput(RandomAccessFile file) {
            this.file = file;
        }

        @Override
        public long getLength() {
            try {
                return file.length();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void seekTo(long position) {
            try {
                file.seek(position);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            positionChanged(position);
        }

        @Override
        protected int readUnderlying(byte[] buffer, int offset, int length) throws IOException {
            return file.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            file.close();
        }
    }
}
