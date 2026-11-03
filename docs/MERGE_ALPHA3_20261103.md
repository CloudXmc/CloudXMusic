# 2026-11-03 alpha.3 本地合并与验证

## 合并范围

- 主目录：`D:\file\CloudXMusic`；来源：`D:\file\CloudXMusic_fork`。
- 已阅读主目录 19 份 Markdown（含 `.workbuddy/memory` 与备份说明），核对 fork 的 12 份 Markdown；排除 Git 内部文件、依赖缓存和生成目录。
- 接手时主目录正在合并：主线 `83da524`，导入提交 `e110ded`，共同基线 `fe48c46`。fork 无 `.git`；用独立临时索引核对其 270 个文件与 `e110ded`，差异为零。
- 五个冲突文件的文本标记已被上一个会话清除，但 Git 索引仍为 unmerged。逐项对照 ours/theirs 后保留两侧功能，完成本地合并。
- 保留主线高清光栅、软阴影、圆角抗锯齿、房间独立动态纹理、聊天分享，以及 fork 的 QQ 房间权威播放、滑动队列、权限反馈、封面重试、声卡 EOF 排空。
- 版本保持来源已有的 `2.5.0-alpha.3`；没有创建 tag、推送或部署到游戏。fork 原目录保持原样。
- `music-server/`、`music-protocol/` 接手时即为未跟踪的本地源码，继续留在本地，不纳入本次客户端合并提交。

## 修复的合并问题

1. `MusicRoomClient.send` 残留未定义的 `advertised`，导致编译失败；恢复通道探测变量及代理透明发送路径。
2. `sendHello` 丢失重试时间戳，导致每 tick 重发握手；恢复两秒间隔，并覆盖断开后停止、新连接重新握手。
3. `MusicView.RoomView.withClock` 走旧构造器丢失 `currentMid`，导致真实静态渲染丢失当前曲 VIP 与按 MID 高亮；保留 MID，同时保证时钟变化不会触发整幅静态重绘。
4. QQ 歌单时长从 x=930 向右绘制，与新分享按钮重叠；改为右对齐并缩窄专辑列，保留分享和加歌按钮各自的空间。
5. CHANGELOG 的 `[v2.5.0-alpha.3]` 标题不能被现有 CI 校验和 Release 提取命中；统一为 `## 2.5.0-alpha.3`。
6. README 与交接页补充当前合并状态及可用 JDK，历史记录保留。

## 验证结果

使用 Java `21.0.7+6-LTS`：

```powershell
.\gradlew.bat :cloudxmusic-client:build :cloudxmusic-client:renderUiPreview :music-protocol:build --offline --no-daemon
.\gradlew.bat :music-server:build --no-daemon
```

- 客户端：433 项；协议：10 项；服务端：14 项。合计 457 项，失败、错误、跳过均为 0。
- 客户端/协议及服务端两轮最终构建均 `BUILD SUCCESSFUL`；服务端离线首次缺依赖，通过本机代理补齐后构建成功，代理仅通过进程环境变量传入。
- 新增三项回归：握手重试与连接生命周期、静态 UI 时钟隔离且保留 MID/VIP、歌单时长不覆盖分享按钮。
- 离线布局检查与预览生成通过；实际打开检查浅色房间、深色长标题 VIP 和 QQ 歌单预览。
- `libs/cloudxmusic-protocol.jar` 与本地协议模块构建的 34 个 class 全部逐字节一致；未修改协议格式或运行包。
- 成品内版本为 alpha.3，包含聊天分享、QQ 房间桥接、权限分类及 PacketCodec；无测试 class 和音频测试夹具。
- CHANGELOG 当前版本仅匹配一次，Git 空白与冲突标记检查通过。

客户端成品：`cloudxmusic-client/build/libs/CloudXMusic-2.5.0-alpha.3.jar`

- 大小：24,389,059 字节。
- SHA-256：`1bbadf980ea92bbcaf7be8589ab97a6b97a14d3f07d5ecc2c4f34b5c44c8d470`。
- 详细日志与产物核验：`outputs/codex-merge-build.log`、`outputs/codex-server-online-build.log`、`outputs/codex-merge-verification.json`。

## 尚存的服务端配套风险

本地 `music-server` 1.0.5 的 `RoomService.tick` 仍在房间时钟达到歌曲标称时长时自动推进。alpha.3 QQ 客户端只有在真实 PCM 播放、声卡排空后才发出房主 EOF 请求；服务端仍可能先发下一曲广播，客户端会按权威广播停止旧曲，因此客户端 drain 修复本身不能消除联机截尾。

已用真实本地 `RoomService` 和协议类做离线复现：10 秒 QQ 曲目在服务端 10.5 秒时，无任何客户端 EOF 包也会跳到下一首；若本地取链起播耗时 2 秒，此时只播放了约 8.5 秒。复现源码和输出在 `outputs/RoomEofCompatibilityProbe.java`、`outputs/codex-eof-probe.log`。

此次保留服务端现有推进策略。后续需确定旧客户端兼容策略，并做服务端配套修改及真实 Minecraft/Folia 双端、真实账号和声卡验收；当前构建、单测及离线复现不代表已通过实机验收。

## 接手现场备份

`backups/codex-merge-20261103-180259/`：

- `workspace.zip`：接手时源码、配置、文档、未跟踪模块及本地工作记录。
- `history.bundle`：当时所有 Git 引用与历史。
- `index`、`HEAD`、`ORIG_HEAD`、`MERGE_HEAD`、`MERGE_MSG` 等：原未完成合并现场。

该备份在本轮源码修改前创建；Git bundle 与工作区 ZIP 同时保留，避免只备份已提交历史而遗漏工作区。
