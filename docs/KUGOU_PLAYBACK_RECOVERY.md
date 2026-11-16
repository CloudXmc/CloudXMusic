# 酷狗播放恢复验证 · 2026-11-16

版本保持 `2.6.0-alpha.3`，目标为 Java 21 / Minecraft 1.21.11 Fabric 客户端。

## 实际证据与原因

读取游戏实例 `H:/PCL2/.minecraft/versions/小云朵/logs/latest.log`，并通过该实例现有酷狗会话进行只读验证。
没有修改真实账号文件、刷新凭证、写入收藏或保存真实音频文件。

- Web `/play/songinfo` 返回 HTTP 200、`status=1`、`err_code=0`，含有效 `data.play_url` 和 `data.play_backup_url`。
- 收藏样本实际返回 Ogg/Vorbis，CDN 返回 HTTP 206、`audio/ogg`，头部为 `OggS`。原播放器只支持 MP3/FLAC，因此探测阶段拒绝了有效音频，未进入声卡播放。
- 随后的 App `/v5/url` 对该会员曲返回 `status=2, error_code=20018`。先前的立即抛错策略还会丢弃已验证的低档 Web 链接。
- 浏览器 User-Agent 与 `Referer: https://www.kugou.com/` 已在原请求中存在。本次样本不支持“签名算错”或“防盗链 403”为主要原因的结论。

## 修复范围与调用链

- 只修改独立 `kugou/` 的取链、CDN、解码及其测试，另增加本文与测试音频说明。其他生产包逐文件 SHA-256 对照修改前快照。
- 后台 resolver → 固定 Web/App API → 有界 CDN 前缀探测 → `KugouAudioHttp` 顺序读取/Range 续传 → 内容识别 → FLAC/JLayer/目标游戏自带 JOrbis 解码 → 原 PCM Sink/样本跳过 → 原独立声卡播放器。
- 新 Vorbis 解码每次只保存固定上限的 PCM 块，不下载或缓存完整歌曲；中断、暂停、停止、切源、拖拽、自然 EOF 排空继续复用现有会话控制。
- 地址不再依赖 `.mp3/.flac` 后缀判断解码器；仍检查酷狗 CDN 域名、协议、重定向、响应类型与实际音频头。Ogg Opus 不会误送入 Vorbis 解码器。
- 单 CDN 的 403/超时会尝试同回包备用地址。Web 失败会继续独立 App 320/128；App 非权限错误不会丢弃已验证的 Web 结果。全部路径失败时保留网络/API 原因。
- 明确整首锁定、安全验证和登录拒绝仍然阻止播放；试听片段不能作为完整歌曲接受。网络和 `20018` 不会伪报成 VIP 权限不足。
- 按路由兼容 Web 毫秒与 App 秒数时长；日志同时输出原始数值的脱敏 JSON 快照与规范化 `durationMs`。
- HTTP 状态、服务端状态/错误码、CDN 探测及拒绝原因进入日志。JSON 快照使用字段白名单，不包含 token、userid、Cookie、dfid、签名或完整 CDN 地址。

## 真实只读链路检查

通过当前实现实际完成网络取链、CDN 顺序读取和整曲 PCM 解码，PCM 仅计数并丢弃，没有打开真实声卡：

| 样本 | 请求与结果 | 实际 PCM |
| --- | --- | --- |
| 账号“我喜欢”会员标记曲 | HIGH 请求后保留 Web 标准档；App 320 返回 20018 | 44.1 kHz，7,807,869 帧，177.049 秒 |
| 同一收藏曲 | 直接选标准档，Web 成功 | 44.1 kHz，7,807,869 帧，177.049 秒 |
| 普通搜索会员标记曲 | 直接选标准档，Web 成功 | 44.1 kHz，9,856,512 帧，223.504 秒 |
| `privilege=0` 的普通搜索曲 | Web 标准档后，独立 App 320 成功 | 44.1 kHz，11,203,200 帧，254.041 秒 |

匿名请求会员曲仍被服务端拒绝，没有对其作成功结论。该账号部分会员曲的 App 20018 原因及高档资源仍需平台侧进一步证据；恢复基础播放不等于确认这些曲目可以取得无损。

## 自动验证与限制

先补回归测试并确认 25 项目标测试中有 11 项失败，再实现修复，目标测试转绿；额外覆盖响应脱敏、HTTP 状态保留、Vorbis 头与 Opus 排除、分段解码、样本跳过、自然 EOF drain。

最终命令 `.\gradlew.bat clean build --offline --console=plain`：`BUILD SUCCESSFUL in 35s`，11 个任务全部执行。JUnit XML 汇总为 **122 个测试套件、623 项测试，失败 0、错误 0、跳过 0**。修改前后的 164 个非酷狗生产源码文件 SHA-256 一致。构建只报告现有弃用 API/Gradle 弃用功能提示，没有编译或测试错误。成品检查与校验值见交付目录的 `VERIFICATION.md`、`manifest.json` 和 `SHA256SUMS.txt`。

没有新增任务池、持久缓存、第三方运行依赖或全局 CookieHandler。现有后台请求均可取消且有期限，CDN 前缀探测有字节/超时上限，解码流随会话关闭。没有引入新的游戏线程阻塞。

Paper/Folia 调度、plugin.yml、箱子 GUI、YAML、数据库等服务端检查对本 Fabric 客户端不适用。扫描没有发现 Bukkit 调度、同步传送、所列 Folia 事件或 CompletableFuture.join；原有网易云 MP3 引擎含有界 Thread.join，属于范围外既有代码，本次未修改。
`defer`：酷狗房间联机继续按既定范围禁用。`uncertain`：游戏内声音、HUD、拖拽和设备差异尚需实机验收；部分会员曲真实无损取链未获成功证据。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。本次同样未运行真实 Minecraft/Fabric 游戏客户端验收。
