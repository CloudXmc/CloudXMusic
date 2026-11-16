# 酷狗核心、体验闭环与登录 · 2.6.0-alpha.3

本阶段交付独立的酷狗本地音源基础与核心类，并完成游戏内三音源 UI 与全局路由接线。酷狗账号、收藏、偏好、搜索、播放、封面、KRC 逐字歌词和自动取链均在独立模块工作；音乐房间联机因协议限制在 UI 与业务入口禁用。2026-11-16 定稿移除酷狗音质胶囊与菜单，使用 Web 优先、App 320 / 128 兜底。

项目是 Java 21 / Minecraft 1.21.11 的 Fabric 客户端，不是 Bukkit/Paper 插件。本阶段不引入服务端调度器、plugin.yml、数据库或箱子 GUI。保留项目 GPL-3.0-only 及原作者声明。

## 核心职责

所有新增平台逻辑位于 `dev.lzasn.cloudxmusic.client.kugou`：

| 类 | 职责 |
| --- | --- |
| `KugouModule` | 后台读盘、客户端线程装配，拥有模块及页面控制器生命周期，产生共享播放快照 |
| `KugouProvider`、`KugouTrack`、`KugouJson` | 搜索、标准 hash/album_id/album_audio_id、VIP/封面/时长、1024 条有界元数据缓存 |
| `KugouHttp`、`KugouTransport`、`KugouApi`、`KugouSigning` | 固定 API、请求签名与 token 加密交换、隔离 Cookie、响应大小和时间上限 |
| `KugouAccount`、`KugouAccountStore` | 不可变账号快照、独立 JSON、原子替换、文件访问限制、账号代次 |
| `KugouLoginService`、`KugouLoginController` | 普通版扫码、2 秒轮询、180 秒期限、Web token 交换，取消/刷新隔离 |
| `KugouCookieLogin` | 完整 Cookie 解析、token/userid 与官方 KuGoo 校验；后台验证成功后才保存 |
| `KugouProfile`、`KugouAccountDetails`、`KugouBadges` | 真实资料与会员展示、有界后台资料/权益查询、晚到结果隔离 |
| `KugouPrivileges` | 解析服务的逐档权益，合并更新后的取链观测；未知权益不推断标签 |
| `KugouFavoritesService`、`KugouFavoritesController` | 真实账号“我喜欢”分页/添加/删除与确认，乐观爱心、失败回滚、并发及账号隔离 |
| `KugouQuality` | 真实音频分类与旧偏好兼容；不再提供手动切换菜单 |
| `KugouPlaybackResolver`、`KugouAudioUrlVerifier`、`KugouPlaybackAccessException` | Web 自动取链与 App 320 / 128 兜底、带专辑及 hash-only 回退、最多 3 个 CDN 候选、完整资源与权限检查 |
| `KugouAudioPlayer`、`KugouPlaybackAudio` | 独立声卡会话、停止确认、PCM 时钟、播放/暂停/恢复/关闭 |
| `KugouAudioHttp`、`KugouAudioDecoder`、`KugouSampleSkip`、`KugouPcm`、`KugouAudioFailure` | 顺序下载与有界断点续传、MP3/FLAC 解码、精确样本跳过、音量过渡与错误脱敏 |
| `KugouPlaybackController` | 独立队列/循环、取链取消、账号代次、声卡交接与拖拽锁 |
| `KugouKrc`、`KugouPresentation` | KRC XOR/zlib、逐词绝对时间、翻译、LRC 回退、逐行与 TimedLyrics 同代发布 |
| `KugouSearchController` | 独立的后台搜索与页面关闭取消 |
| `KugouCapabilities` | 声明不支持音乐房间，供下一阶段入口禁用使用 |

`omni/MusicSource` 增加 `KUGOU`；同一个 `MusicSourceCoordinator` 可登记第三音源的暂停回调。旧构造器和双音源行为保留，未登记第三音源时拒绝切换到酷狗，避免把酷狗错误交给现有 QQ 暂停回调。

## 装配及线程边界

1. 后台调用 `KugouModule.open(CloudHome.dir(), mainExecutor)`；账号文件仅为 `config/cloudxmusic/kugou/account.json`（遵循已有 CloudHome 覆盖设置）。
2. 回客户端线程调用 `module.attach(existingCoordinator)`，其中 `existingCoordinator` 是现有 QQ 集成公开的全局实例，不能另建一套选择状态。
3. 每个客户端 tick 调用 `module.tick(inWorld, titleScreen)`；连接/断开调用 `joined()` / `disconnect()`。
4. 界面读取 `snapshot()`，按 `MusicSource.KUGOU` 分支处理搜索、播放并隐藏音质入口。3D 和 HUD 直接消费此快照的 `timedLyrics`，不转换为其他音源歌曲。
5. 全局搜索控制器随模块保留搜索结果，临时搜索控制器由拥有者调用 `releaseSearch()`。页面关闭取消扫码与拖拽锁、释放纹理；客户端停止释放搜索、封面缓存、本地收藏写入器并调用模块 `close()`。所有网络、解码和 IO 执行器可关闭，暂停/停止 Future 成功后才允许另一音源输出。

生产 `netease/` 不修改；QQ 改动仅限已授权的登录、凭证与登录 UI 类，不修改其搜索、目录、取链、解码和播放业务。酷狗播放器复制后只依赖共享 `audio/PcmPlaybackClock`。解码跳转从头重建输入与解码器，再丢弃精确样本，不用压缩字节估算时间。断流续传则保留当前解码器，以 Range/If-Range 从实际收到的字节续接，两种机制不同。

FLAC 输入关闭在 finally 中调用 Nayuki `super.close()`，释放 byteBuffer 并清除 bitBuffer/CRC；新会话重新创建全部解码状态。自然 EOF 的 `drain()` 只运行在解码线程，不持有输出锁；暂停保留曲尾，停止/换曲能够关闭正在排空的声卡。

## 音质和权限

| 内部音频分类 | 兼容参数名 | 当前用途 |
| --- | --- | --- |
| Hi-Res音质 | `high` | 仅保留分类；不向 App 请求该档位 |
| 无损音质 | `flac` | 仅保留分类；不向 App 请求该档位 |
| 高品音质 | `320` | Web 无可用结果时的首个 App 请求档位 |
| 标准音质 | `128` | App 320 无可用结果时兜底，也可能需要会员 |

已登录时优先请求同账号 Web `/play/songinfo`（不传 quality），接受第一份经过音频探测的可播完整结果，不为了旧偏好再次请求更高档位。Web 无可用资源、CDN 不可播或网络失败时，尝试 App 320 → 128；游客保留 App 路径。每条路由可使用专辑元数据和 hash-only，旧音质字段不限制自动取链。安全验证 30020、明确登录失效或整首锁定立即停止；最终保留权限、登录或网络错误，不承诺任何网络或权限条件下都能播放。

显式整首锁定/无试听资格立即结束取链，标准档明确无权限则按整首锁定处理。Toast 为“当前歌曲为 VIP 专属，暂无播放资格”，由客户端 tick 延迟 900ms 跳下一首，按 hash 去重且最多遍历整队列一轮；换账号、换音源、停止或手动选曲取消旧跳过任务。自动模式不再发布手动音质降级 Toast 或状态文字；音质菜单及其中 VIP / SVIP 标签已移除，账号与歌曲会员标识保留。

FLAC 通过 STREAMINFO 验证格式并确认位深/采样率，不能只凭 high 请求标签显示 Hi-Res；MP3 根据服务返回码率确认高品或标准。可辨认的试听时长/标志会被拒绝。实际权限仍以服务返回为准，不提供权限绕过或会员权益领取。

## 登录方式与续期

`gui/login` 提供共享 Cookie 解析、表单绘制、输入对话框与友好通知。QQ 与酷狗复用网易云输入框和登录按钮的绘制组件，保留各自账号文件；完整 Cookie 不进入日志、纹理缓存键或异常文本，关闭/提交即清空输入框。取消、退出和新扫码使旧导入结果失效，验证失败不覆盖原账号。

QQ 保存 `LoginServer` 返回的 `refresh_key`、`refresh_token` 等真实续期字段，以 `loginMode=2` 续期；缺少必要字段时只读验证，不伪造续期请求。酷狗保存返回的可选 `refresh_token`，沿用已确认的 Web `login_by_token_get` 当前 token 换票协议，不把 refresh_token 充当其他种类的 token。两者扫码会话每 12 小时最多一次后台续期；Cookie 会话始终跳过换票端点。30 分钟的状态检查仅用于只读有效性校验，明确过期才撤销内存登录并通知，网络失败保留会话。关闭账号服务取消定时器与进行中的任务。

## 已验证与未验收

- 自动化覆盖：自动路由顺序、旧音质偏好不干预、酷狗胶囊及命中区域隐藏、其他音源控件保留、权限和试听拒绝、扫码签名/交换、账号隔离与过期提交、搜索元数据、KRC/翻译/解压上限、LRC 回退、真实 MP3/FLAC/Ogg 测试音频、HTTP 续传与文件一致性、后台排空、暂停/取消/换曲、声卡故障、拖拽锁、全局协调器。
- 已向公开匿名搜索接口做只读冒烟：返回 status=1，包含标准 hash 与 album_id。
- `uncertain`：未使用真实酷狗账号扫码，未验证 VIP/豪华 VIP 取链、地区版权和真实声卡听感；测试中的账号与响应均为虚构数据。
- `defer`：真实游戏内扫码、会员权限和实际音频输出验收；协议 v5 没有酷狗枚举，因此房间联机继续禁用。
- 酷狗房间联机不在本次目标内，也不向协议 v5 的 QQ/网易云枚举伪装发送酷狗曲目。
- 未执行真实 Minecraft/Fabric 游戏验收，未执行真实 Paper/Folia 服务端测试；后者不是本客户端的运行目标。静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试，也不等于真实 Fabric 游戏运行测试。
