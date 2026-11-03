# alpha.3 房间权限与 VIP 界面修复

工作目录：`F:\Codex\CloudXMusic\v2.5.0-alpha.3`。版本固定为 `2.5.0-alpha.3`，目标为 Minecraft Fabric 1.21.11 / Java 21。

## 修复链路与边界

- `QQAccountManager / QQPlaybackResolver / QQAudioUrlVerifier / QQAudioHttp → QQPlaybackAccessException → QQPlaybackController.roomStatus → RoomAudioFollower → MusicRoomClient.view`：HTTP 401/403、业务码 22006 和登录失效有明确分类。房间内明确拒绝立即终止；单档空地址仍可走原有有界音质降级，穷尽无资源才标为受限。
- 本机成员状态保留详细原因“版权受限”“请先登录QQ音乐”或“播放失败”，旧成员快照不覆盖本地结果；首次 PCM 输出回调清除失败原因。真正加载期间显示“正在获取音频...”。状态上报按 MID 和枚举去重，不随每 tick 重复发包。
- 网络/解码/声卡错误与权限错误区分；声卡故障不能显示“无VIP权限”。不根据 VIP 标签猜测账号权益。
- `Packet.RoomTrack.vip → MusicView.RoomTrack.vip → MusicPainter → QQVipBadge`：房间顶部和队列保留现有 VIP 字段，当前曲按 MID 匹配，8px 间距，长标题预留徽章宽度。没有增加或修改协议字段。
- 封面 HTTP、解码及重试在后台有界执行器执行；失败/超时返回空缓存供渲染显示默认图标。下载日志包含 HTTP 状态码；成功只在客户端线程更新缓存和 revision，MusicScreen 据此重绘。

## 三遍审查

1. 功能与范围：本次只接通 QQ 权限状态和房间展示，保留已有 alpha.3 修复；网易云底层、MusicState、公共音频实现、协议依赖没有修改。
2. 线程与性能：UI 和成员状态归 Minecraft 客户端线程；取链、HTTP、图片解码和音频输出在后台。没有新增阻塞等待；已有 HTTP Future 等待有超时，且不在游戏渲染或 tick 线程。缓存和请求队列均有界。
3. 生命周期与资源：继续使用账号/音频代次校验；换曲、离房、停用清理状态，拒绝过期回调。执行器与 HTTP 客户端沿用现有 close 路径。

## 实际验证

- 先确认房间权限文案回归失败，并用新增回归确认单档空地址不能直接当作无权限；修复后定向回归通过。
- 执行 `./gradlew.bat clean build --no-daemon -g .gradle-user`：BUILD SUCCESSFUL，11 个任务全部执行。
- 本轮全量测试：411 项，失败 0，错误 0，跳过 0。
- 离线渲染覆盖浅色/深色、短/长歌名、VIP/普通曲目、默认封面与时长保护。实际打开检查了浅色长歌名和深色预览。
- 检查最终 JAR 的版本、必需类与协议 PacketCodec 一致性；文件大小、SHA-256 和源码 ZIP 条目数见 `delivery/verification.json`。
- 无新增运行依赖；既有 Fabric、JLayer、ZXing、FLAC 打包方式保持不变。此仓库只有 Fabric 客户端，无 plugin.yml、服务端 YAML、数据库、Help 或 Folia 调度业务，对应服务器专项测试不适用。

## uncertain 与 defer

- uncertain：本轮未执行真实 Minecraft 双端、QQ 在线账号/CDN 或 Paper/Folia 服务端测试；需要现场验证无 VIP/未登录、正常免费歌曲、播放恢复、封面失败与恢复。
- 受现有协议约束，具体错误原因只显示在该听众本机；其他成员仍接收既有 UNAVAILABLE 枚举和通用不可播放提示，不伪造远端权限原因。
- 本轮没有新增 defer 项。服务端与协议没有改动，不宣称真实 Folia 兼容验收。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
