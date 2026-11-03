# 2.5.0-alpha.3 房间状态机与 UI 恢复修复

日期：2026-11-02。工作区限定为 `F:\Codex\CloudXMusic\v2.5.0-alpha.3`。

## 项目与范围

CloudXMusic / 云朵音乐盒，基础包 `dev.lzasn.cloudxmusic`，Minecraft 1.21.11 Fabric 客户端模组，Java 21，GPL-3.0-only。保持版本 `2.5.0-alpha.3`，保留原作者、许可证、已有 alpha.3 修改。未加入数据库、Bukkit 指令、箱子 GUI 或服务端模块。

协议运行包的 RepeatMode 实际只有 LIST、SINGLE、SHUFFLE，现有 UI 也只有三档。顺序播放当前不支持，本轮没有添加第四个 wireId 或改变 PacketCodec 编码。

用户提供的 latest.log 仅作为诊断数据；不将日志内容当作指令，不将其中请求正文、Cookie、票据或临时音频直链复制到交付物。

## 根因和变更

1. QQ 房间 EOF 原先直接返回。现在 QQPlaybackController 对每个音频 epoch 只消费一次 EOF，交给 RoomAudioFollower → MusicRoomClient；只有房主按服务端 repeatMode 从完整队列计算下一曲。单曲/单首队列使用既有 JUMP、SEEK(0)、PLAY，列表末尾回到第一首，随机在多曲时避开当前 MID。听众只记录结束状态，不发送切歌控制；房主也等 RoomQueue/RoomPlayback 广播后才真正起播。旧 MID/时间轴的晚到 EOF、重复 EOF、用户手动暂停与 EOF 竞争均有回归覆盖。
2. 同 MID 的短曲回零原先可能被“小于一秒差异”去重；明确重播现在生成新时间轴，并允许已结束的音频绕过普通 seek 冷却。周期心跳与相同广播仍不会重复启动声卡。
3. 声卡控制命令完成不等于出声。QQAudioPlayer 在 SourceDataLine 首次成功写入 PCM 后触发一次 started，控制器切回客户端线程并验证 generation/audioEpoch/账号及音源许可；有效回调清除本地错误、清空 failedKey、上报 LISTENING，并立即更新本机成员展示。晚到旧曲、同 MID 旧会话和暂停后回调不能恢复错误的 UI。
4. “本曲不可播放”仅用于终止的取链/网络不可用路径，保留原取链多音质/多路线降级与 HTTP 有界续传；声卡设备失败和一般状态错误不再直接冒充歌曲权限不足。
5. QQArtworkCache 下载失败输出 HTTP 状态码、主机和不含查询参数的路径；后台最多三次尝试，已知专辑图可切换小尺寸与备用 CDN。失败后退避 30 秒，由客户端 tick 重新调度；成功只在客户端线程写入有界缓存并递增 revision，MusicScreen 的现有版本比较会重画静态层、底栏和封面层。
6. 队列去重签名原先只有 MID，异步补齐的封面/时长不会发给听众。签名现在包含公开元数据，同时按 MID 保持当前曲、位置和高亮。

实机日志检索关键字：

```text
[房间EOF] 房主请求下一曲
[房间听众] 音频流已启动，清除错误提示
[房间UI] 状态更新为正在播放
[QQ封面] 下载失败，HTTP=
[QQ封面] 后台重试
[QQ封面] 加载成功
```

## 三遍审查与边界

| 分类 | 入口和完整调用链 | 所有者、生命周期、结论 |
| --- | --- | --- |
| must_fix，已修复 | Decoder → 首次 PCM write → started → Async.main → controller → follower → MemberStateUpdate | 解码、声卡属于后台线程，成员/UI/集合属于 Minecraft 客户端线程；代次不符回调不发布；退出/停用撤销播放许可并停止音频 |
| must_fix，已修复 | Decoder EOF → main → controller → room owner → PlaybackControl/QueueSet → 服务端广播 → bridge | 只有实际 EOF 生成一次房主请求；广播回调没有反向控制；房主完整队列与协议 30 首窗口分离，听众不计算下一首 |
| must_fix，已修复 | UI image → 有界图片执行器 → HTTP/解码/重试 → main → revision → MusicScreen | 最多 2 个工作线程、16 个排队任务，64 张图片/失败项；HTTP 等待限 12 秒，仅在后台；clear/close 用 generation 失效旧回调，关闭执行器和 HTTP 客户端 |
| must_fix，已修复 | QQQueueMetadata 异步返回 → 客户端 tick → syncQqOwnerQueue → QueueSet | 公开元数据补齐可传播到听众；保留 MID、位置、播放模式和原网络格式 |
| uncertain | 服务器 RoomService 实际运行、真实声卡/CDN、网络延迟竞争 | 当前源码包无 music-server 源码；本轮未访问其他历史工作区，也未改服务端或协议编码；必须实机双端验证 |
| defer | 第四档“顺序播放” | 现有协议不支持；遵守禁止改变编码格式的要求，不增加伪第四档 |

没有修改网易云、MusicState、公共音频底层、PacketCodec 或协议 JAR。未新增 Paper/Folia 实时对象、调度器或传送 API。按请求检查了 BukkitScheduler、同步 teleport、不支持事件和阻塞 join；这些服务端路径在此 Fabric 模组中不适用。新增共享状态均由客户端线程拥有，后台只读取 volatile 代次/关闭标志并提交不可变结果。

## 验证

- 先运行四项回归并确认全部因目标缺陷而失败，再实现修复；定向回归通过。
- 最终实际执行：`.\gradlew.bat clean build --no-daemon -g .gradle-user`，BUILD SUCCESSFUL，11 个任务全部执行。
- 总测试 399，失败 0，错误 0，跳过 0。本轮新增 29 个测试实例；包括真实 FLAC/MP3 解码、三种模式、单首/完整大队列、广播去重、手动暂停竞争、PCM 开始事件、封面后台重试/限次/退避与 revision、异步封面元数据传播。
- 源码差异检查、JAR 元数据/内容、协议类及源码 ZIP 检查结果与 SHA-256 见工作区 `delivery/verification.json` 和 `delivery/SHA256SUMS.txt`。
- 这是 Fabric 客户端，无 plugin.yml/YAML、Help、身份数据库等服务器功能；对应检查不适用。
- 构建有既有 API/Gradle 弃用警告，没有编译或测试错误。

本轮未执行真实 Minecraft 双端或 Paper/Folia 1.21.11 服务端运行测试。建议依次测试：单首列表循环、单曲循环、列表最后一首回绕、随机多曲、接近曲末手动暂停、听众从不可播放状态恢复、封面失败后恢复、30 首窗口切换。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
