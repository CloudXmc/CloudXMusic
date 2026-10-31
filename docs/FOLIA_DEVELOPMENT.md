# Folia 服务端与协议开发记录

## 本轮范围：2.5.0-alpha.2

作者：Lzasn。目标：Java 21、Folia / Paper 1.21.11。
当前开发版 `2.5.0-alpha.2` 在 `2.5.0-alpha.1` 的基础上完成房间主链路：协议扩展、服务端创建/列表/加入/退出房间、客户端 Fabric Custom Payload 握手与房间页交互。
第二阶段补齐队列与播放时间轴：创建房间时自动带上个人队列、播放/暂停/切歌/跳转/循环同步、加入时补发权威快照、曲末自动切歌、每 10 秒低频校准，房主的加歌/移除实时跟随。
第三阶段接入真实跟播：每名成员用自己的账号取链解码出声，按房间时间轴起播与纠偏，播不了只影响本人；成员可本地「暂停跟听」，恢复时由服务端补发一次权威快照。
**仍没有真实双端联机验收；跟播的取链、声卡与多人同步需要实机确认。**

## 已确定的产品规则

- 房间创建时固定音源：网易云或 QQ 音乐，不允许混源队列。
- 创建/加入必须登录对应音源；客户端前置检查，服务端拒绝未声明已登录的申请。
- 登录声明不是可验证的账号证明；不上传 Cookie、密码、Token、音乐账号 UID 或授权音频 URL。
- 每名成员使用自己的账号取链、解码。会员或版权限制不踢人、不让房间自动跳歌。
- 无权限播放时仅本人右上角弹 Minecraft 原版 SystemToast；留在房间等待下一首可播曲目。
- 提示按房间会话和本次播放实例去重，不能用每次都变化的 revision 作为去重键。
- 网络错误、解码错误和登录失效必须与歌曲权限不足区分。
- 房间队列与两个个人音源队列隔离；房主转移和退出后的恢复策略后续细化。
- 通过现有 omni/ 协调层接入，保留声卡交接和异步代次隔离，不另建第三套播放器。

## 修改前边界检查

| 分类 | 入口/完整调用链 | 当前上下文与所有者 | 跨边界数据 | 终止/清理 | 规则 |
| --- | --- | --- | --- | --- | --- |
| must_fix | Bukkit PluginMessageListener → 解析/限流 → Global 调度 → 会话状态 → Entity 调度 → sendPluginMessage | 不假设接收回调线程；仅使用入口玩家身份及调度句柄，协议会话在 Global 上处理 | 有界字节数组、不可变包和连接代次；玩家引用仅用于投递 Entity 任务，不保存进会话表 | Quit 按连接身份移除；停用关闭调度、清空表、注销频道与监听器；过期任务检查代次 | 1/2/3/6/7/8 |
| 已实现（第一阶段） | Fabric JOIN/END_TICK → canSend → HELLO → WELCOME → PING → PONG | 客户端线程持有连接和握手状态 | 有界纯 Java 协议包，不包含任何账号凭证 | DISCONNECT 清空握手、超时停止重试；旧连接回调不能修改新连接 | 2/8；客户端已接入 Custom Payload |
| 已实现 | /cloudxmusic reload → Async 读 YAML → Global 发布不可变配置 → 原发送者上下文回复 | 读盘归 Async；配置快照线程安全发布 | 不可变配置和消息快照 | 关闭后不再发布或回复；失败保留旧配置 | 1/3/6/8 |
| 已实现（第一阶段） | 创建/列表/加入/退出房间；客户端声明已登录；音源固定；成员上限 20；房主退出后转交 | 房间状态只在 Global 线程修改；客户端状态只在客户端线程更新 | 房间码、玩家 ID、音源枚举、成员列表；不含 Cookie/Token/音频 URL | 断开连接自动退出；插件停用清空房间；客户端断线清理会话 | 1/2/3/6/8 |
| 已实现（第二阶段） | QueueSet/QueueAdd/QueueRemove → 房主校验 → 房间队列 → RoomQueue 广播；PlaybackControl → 时间轴推进 → RoomPlayback 广播 + 每秒 tick 校准 | 队列与播放状态只在 Global 线程修改；客户端只保存不可变展示快照 | 公开歌曲 ID、标题、歌手、时长、封面地址、VIP 标记；不含 Cookie/Token/音频 URL | 断线退出、房主转交、队列空时停播；停用清空房间与重复任务 | 1/2/3/6/8 |
| 已实现（第三阶段） | 房间当前曲 → 成员本地取链解码 → 声卡 → 按房间位置起播；漂移超 4 秒才 seek（3 秒冷却） | 每名成员自己的账号与声卡；服务端不知道也不接触音频地址 | 只有公开歌曲 ID 走网络；音频 URL 只在本地 | 切歌/暂停/退出房间/断线都停止并清空占位；失败标记 UNAVAILABLE | 1/2/3/6/8 |
| defer | 房间内共享歌词时间轴、跨音源混播、房主转移后的时间轴继承 | 下一里程碑 | 仅记录规则，不伪装为已实现 | 待实现 | 范围约束 |
| uncertain | 真实 Folia 1.21.11 运行、代理转发和 Bukkit-only 服务端 | 未提供真实测试服务器 | 无 | 编译/离线测试不代表运行验收 | 版本边界 |

## API 核对依据

- Folia ver/1.21.11 官方 README：https://github.com/PaperMC/Folia/blob/ver/1.21.11/README.md
- 官方 API Maven：dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT；开始开发时元数据对应 20260222.203011-14。
- EntityScheduler 官方源码：https://github.com/PaperMC/Paper/blob/ver/1.21.11/paper-api/src/main/java/io/papermc/paper/threadedregions/scheduler/EntityScheduler.java
- Fabric 使用项目已锁定 0.141.6+1.21.11 中 networking-api-v1 5.1.6+6b6d71a53e 的源码/映射产物核对，不照搬新版本命名。
- 只在启动时用 RegionizedServer 类进行一次 Folia 检测；不能只看调度方法是否存在，因为 Paper 也提供这些方法。
- 本轮不使用传送或此前规则列出的争议事件，不对其在目标核心上的可用性作未经核对的结论。
- Folia 官方文档说明服务端 `/reload` 被禁用：https://docs.papermc.io/folia/admin/reference/

## 热加载边界

`/cloudxmusic reload` 只重载 `config.yml` 与 `messages.yml`：

```text
指令入口
  → Async 读取并严格校验配置
  → Global 发布不可变 Snapshot
  → 原发送者上下文回复
失败则继续使用旧 Snapshot
```

它**不会**替换插件类、监听器、调度器、协议通道或依赖 JAR。修改 Java 代码、协议或依赖后必须停止服务器、替换 JAR 并重新启动。Folia 禁用服务端 `/reload`，因此不提供 `/reload confirm` 式热重载，也不承诺在运行中替换 JAR。

## 验证记录

- `music-protocol`：10 个编解码测试通过（含队列/播放往返、封面与 VIP 字段、超限队列与非法位置拒绝、空标签归一化）。
- `music-server`：14 个握手、令牌桶、配置资源和房间服务测试通过（含房主独占校验、混源队列拒绝、位置插值、曲末自动切歌与单曲循环、切歌必须推队列、成员状态广播与去重、恢复跟听补发快照）。
- `music-server:build` 与 `:cloudxmusic-client:build` 已通过，使用 Java 21；2026-10-30 发布的 `v2.5.0-alpha.1` GitHub Actions 构建成功。
- 当前工作区客户端成品为 `cloudxmusic-client/build/libs/CloudXMusic-2.5.0-alpha.2.jar`；alpha.2 尚未发布，服务端仍只在本地构建。
- 远端仓库不包含 `music-protocol/`、`music-server/` 源码；客户端通过 `libs/cloudxmusic-protocol.jar` 合并协议运行包。改协议后必须重新 `:music-protocol:jar` 并覆盖该文件，否则客户端仍按旧协议编译。
- JAR 检查：包含 `plugin.yml`、默认配置、消息、协议类与 LICENSE；未打包 Folia/Adventure 第三方类。
- 协议 `VERSION = 5`：在 FIRST 阶段的基础包之外新增 QUEUE_SET / QUEUE_ADD / QUEUE_REMOVE / PLAYBACK_CONTROL / ROOM_QUEUE / ROOM_PLAYBACK / MEMBER_STATE_UPDATE；队列条目带封面地址与 VIP 标记；所有集合、字符串与播放位置均有上限校验。
- 服务端 RoomService 已接入 Global 调度路径，断线会清理成员关系；房主离开后自动转交；`tick` 每秒推进一次，负责曲末切歌与 10 秒一次的位置校准。
- 客户端已接入 Fabric Custom Payload，完成 HELLO/WELCOME、房间列表、创建、加入、退出、队列渲染、当前曲高亮与房主控制。
- 房间队列没有手动导入入口：创建房间时把当前个人队列（按房间音源过滤）一次性提交为房间队列；成员加入后本地的「播放队列」页展示房间队列且只读，退出房间才回到自己的队列。
- 播放控制统一放在「正在播放」页底栏，房间页不另加控件：房间内该页显示房间当前曲与房间进度，播放/暂停、上下首、循环三档、进度跳转直接下发房间时间轴（仅房主生效）。
- 成员的上下一首、队列、循环按钮置灰；中间的播放/暂停**保持可用**，作为本机「暂停跟听」：暂停只停自己，不影响房主和其他成员；再次播放会重新对齐房间时间轴。成员收藏仍可用（QQ 曲目暂不支持）。
- 成员状态随快照广播（协议 `VERSION = 5`）：`MemberStateUpdate` 上报 LISTENING / PAUSED / BUFFERING / UNAVAILABLE，`RoomMember` 带状态位；服务端只在状态真的变化时广播 `RoomUpdated`，新成员加入时快照里就能看到谁在暂停跟听。房主状态按房间播放状态显示「正在播放 / 已暂停」，成员按各自上报显示「正在跟听 / 暂停跟听 / 缓冲中 / 本曲不可播放」。
- 房间队列条目带封面地址与 VIP 标记（协议 v4 加封面、v5 加 VIP）：成员直接复用房主的元数据，不用各自再查一次歌曲详情。
- 离线预览已核对：房主/成员两套底栏配色、当前曲高亮、房间页仅剩「退出房间」（日间/暗黑）。
- 尚未完成：真实 Folia 1.21.11 启停、两名真实玩家 Plugin Message 往返、真实取链与多人跟播的实机验收。
- 跟播实现（`room/RoomAudioFollower`）：不新建第三套播放器，网易云走 `MusicState` 的声卡、QQ 走 `QQPlaybackController`。**不自动切换全局音源**——音源不一致时只弹提示让用户自己去切，避免声卡交接失败把 `coordinator` 锁死在 error（那会导致两边都放不了、主面板点击也被吞）。
- 进入房间时会先停掉本地个人播放，再交给跟播逻辑；房间在播就按房间位置起播，没在播就安静等待。
- 起播位置来自房间快照；本地解码位置与房间时钟差超过 4 秒才 `seek`，并带 3 秒冷却，避免和缓冲互相拉扯。
- 成员「暂停跟听」只停自己的声卡，并**冻结**房间队列与进度的同步；恢复时上报 `LISTENING`，服务端单独给这个人补发一次权威快照后再起播（3 秒超时兜底）。
- 房间里关掉个人播放器的曲末自动切歌（`MusicState.roomMode`），否则会按个人队列切到和房间不同的下一首。
- 播不了（版权/会员/无可用地址/声卡交接失败）只影响本人：`MemberState.UNAVAILABLE` + 右上角 `SystemToast`，留在房间等下一首，不踢人不跳歌。
