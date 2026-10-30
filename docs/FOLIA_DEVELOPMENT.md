# Folia 服务端与协议开发记录

## 本轮范围：2.5.0-alpha.1

作者：Lzasn。目标：Java 21、Folia / Paper 1.21.11。
本轮完成房间第一阶段垂直闭环：协议扩展、服务端创建/列表/加入/退出房间、客户端 Fabric Custom Payload 握手与房间页交互。
**尚未实现歌曲队列同步、播放进度同步和真实音源取链；也没有进行真实 Folia 联机测试。**

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
| 已实现（第一阶段） | 创建/列表/加入/退出房间；客户端声明已登录；音源固定；成员上限 8；房主退出后转交 | 房间状态只在 Global 线程修改；客户端状态只在客户端线程更新 | 房间码、玩家 ID、音源枚举、成员列表；不含 Cookie/Token/音频 URL | 断开连接自动退出；插件停用清空房间；客户端断线清理会话 | 1/2/3/6/8 |
| defer | 队列同步、播放进度同步、VIP Toast、声卡同步 | 下一里程碑 | 仅记录规则，不伪装为已实现 | 待实现 | 范围约束 |
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

- `music-protocol`：6 个编解码测试通过。
- `music-server`：8 个握手、令牌桶、配置资源和房间服务测试通过。
- `music-server:build` 与 `:cloudxmusic-client:build` 通过，使用 Java 21；2026-10-30 的 `v2.5.0-alpha.1` GitHub Actions 构建成功。
- 本地成品：`music-server/build/libs/CloudXMusic-Server-2.5.0-alpha.1.jar`；远端 Release 只包含客户端 `CloudXMusic-2.5.0-alpha.1.jar`。
- 远端仓库不包含 `music-protocol/`、`music-server/` 源码；客户端通过 `libs/cloudxmusic-protocol.jar` 合并协议运行包。
- JAR 检查：包含 `plugin.yml`、默认配置、消息、协议类与 LICENSE；未打包 Folia/Adventure 第三方类。
- 协议新增 CREATE_ROOM / JOIN_ROOM / LEAVE_ROOM / LIST_ROOMS / ROOM_LIST / ROOM_JOINED / ROOM_LEFT / ROOM_UPDATED，并为所有集合和字符串设置长度上限。
- 服务端 RoomService 已接入 Global 调度路径，断线会清理成员关系；房主离开后自动转交。
- 客户端已接入 Fabric Custom Payload，完成 HELLO/WELCOME、房间列表、创建、加入、退出和房间页输入/按钮。
- 尚未完成：真实 Folia 1.21.11 启停、两名真实玩家 Plugin Message 往返、队列/播放同步和真实音源取链联调。
