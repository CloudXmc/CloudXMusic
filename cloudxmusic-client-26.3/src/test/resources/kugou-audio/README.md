# 酷狗 Vorbis 回归夹具

`tone.ogg` 是本地 FFmpeg lavfi 合成的 440 Hz 正弦波，0.25 秒、44.1 kHz、双声道、Vorbis 编码。
没有使用平台歌曲内容；仅用于离线测试，不随运行 JAR 打包，采用项目 GPL-3.0-only 许可。

生成命令：

```text
ffmpeg -f lavfi -i sine=frequency=440:sample_rate=44100:duration=0.25 -ac 2 -c:a libvorbis -q:a 3 tone.ogg
```

覆盖 CDN 内容探测、分段流式解码、按样本跳过、播放器自然 EOF 排空及无扩展名地址。
