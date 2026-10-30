# 离线解码测试夹具

这些音频由本项目使用 FFmpeg 的 lavfi sine / anullsrc 本地生成，无外部音乐内容。
仅供测试，不随成品模组打包，采用项目 GPL-3.0-only 许可。

- tone16.flac：440Hz，0.25 秒，16 bit / 44.1kHz / 双声道。
- tone24.flac：660Hz，0.25 秒，24 bit / 48kHz / 双声道。
- tone.mp3：440Hz，0.25 秒，320kbps MP3 / 44.1kHz / 双声道，包含编码器延迟。
- long-silence.flac：21.2 秒，16 bit / 48kHz / 双声道，用于验证正式解码不再受旧自测 20 秒限制。
