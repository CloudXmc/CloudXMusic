package dev.lzasn.cloudxmusic.client.audio;

import org.junit.jupiter.api.Test;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 FLAC 帧索引与跳转。
 *
 * <p>音轨用 440Hz 正弦编码，跳转后把首个输出采样与源数组的对应下标比对。
 * 该频率下相邻采样相差约 500，容差 ±2 足够分辨「跳错一个采样」这种偏差。</p>
 */
class FlacSeekableTest {

    private static final int RATE = 44100;

    /** 编码结果与它的原始采样，便于逐点比对。 */
    private record Track(byte[] flac, int[] samples) {}

    private static Track makeTrack(int total) throws Exception {
        int[] samples = new int[total];
        for (int i = 0; i < total; i++) {
            samples[i] = (int) Math.round(8000 * Math.sin(2 * Math.PI * 440 * i / RATE));
        }
        Path wav = Files.createTempFile("cxm-flac-test-", ".wav");
        Path flac = Files.createTempFile("cxm-flac-test-", ".flac");
        try {
            writeWav(wav, samples);
            // 用官方 WAV→FLAC 入口编码，避免自己拼编码器参数。
            io.nayuki.flac.app.EncodeWavToFlac.main(new String[]{wav.toString(), flac.toString()});
            return new Track(Files.readAllBytes(flac), samples);
        } finally {
            Files.deleteIfExists(wav);
            Files.deleteIfExists(flac);
        }
    }

    /** 写 16bit 单声道 PCM WAV。 */
    private static void writeWav(Path path, int[] samples) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            int dataBytes = samples.length * 2;
            out.writeBytes("RIFF");
            out.writeInt(Integer.reverseBytes(36 + dataBytes));
            out.writeBytes("WAVE");
            out.writeBytes("fmt ");
            out.writeInt(Integer.reverseBytes(16));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeShort(Short.reverseBytes((short) 1));
            out.writeInt(Integer.reverseBytes(RATE));
            out.writeInt(Integer.reverseBytes(RATE * 2));
            out.writeShort(Short.reverseBytes((short) 2));
            out.writeShort(Short.reverseBytes((short) 16));
            out.writeBytes("data");
            out.writeInt(Integer.reverseBytes(dataBytes));
            for (int sample : samples) out.writeShort(Short.reverseBytes((short) sample));
        }
    }

    @Test
    void seekLandsOnRequestedSample() throws Exception {
        Track track = makeTrack(RATE);
        try (FlacSeekable seekable = FlacSeekable.prepare(new ByteArrayInputStream(track.flac()))) {
            assertTrue(seekable.frameCount() > 1, "索引应包含多帧");
            assertEquals(RATE, seekable.info().numSamples);

            seekable.seekToMs(750);
            int[][] planes = new int[1][65536];
            int frames = seekable.readFrame(planes);
            assertTrue(frames > 0, "跳转后应能继续解码");

            int target = (int) (RATE * 750L / 1000L);
            // skip 是帧内需要丢弃的采样数，丢弃后第一个输出就等于目标采样。
            assertEquals(track.samples()[target], planes[0][seekable.skipSamples()], 2,
                    "跳转到 750ms 后首个采样应与源数据一致");
        }
    }

    @Test
    void seekBackwardsAndForwardsStayAccurate() throws Exception {
        Track track = makeTrack(RATE);
        try (FlacSeekable seekable = FlacSeekable.prepare(new ByteArrayInputStream(track.flac()))) {
            int[][] planes = new int[1][65536];
            for (long ms : new long[]{200, 800, 100, 950}) {
                seekable.seekToMs(ms);
                int frames = seekable.readFrame(planes);
                assertTrue(frames > 0, ms + "ms 跳转后应能解码");
                int target = (int) (RATE * ms / 1000L);
                assertEquals(track.samples()[target], planes[0][seekable.skipSamples()], 2,
                        ms + "ms 落点不准确");
            }
        }
    }

    @Test
    void indexCoversWholeTrack() throws Exception {
        Track track = makeTrack(RATE);
        try (FlacSeekable seekable = FlacSeekable.prepare(new ByteArrayInputStream(track.flac()))) {
            seekable.seekToMs(0);
            int[][] planes = new int[1][65536];
            long samples = 0;
            int frames;
            while ((frames = seekable.readFrame(planes)) > 0) samples += frames;
            assertEquals(RATE, samples, "顺序解应覆盖整轨且不重复");
        }
    }
}
