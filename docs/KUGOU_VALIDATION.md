# 酷狗体验闭环与登录专项验证 · 2026-11-15

2026-11-16 最终定稿已改为自动音质，隐藏酷狗胶囊及菜单，不再发布手动档位降级提示。当前行为与交付验证见 [酷狗自动音质定稿](KUGOU_AUTOMATIC_QUALITY.md)；下文关于四档选择、菜单与降级通知的描述仅为历史记录。

2026-11-16 的 Web/Ogg 播放恢复与最新实测结果见 [酷狗播放恢复验证](KUGOU_PLAYBACK_RECOVERY.md)。下文保留旧批次记录；其“未访问真实账号”和旧测试数量不代表此次验证范围。

版本：`2.6.0-alpha.3`。基线提交：`a4881c2bfffb1429f5ef784e8636a7055e9c8e78`。

## 构建与自动化

- Java：Microsoft OpenJDK `21.0.12.1+1-LTS`；Gradle Wrapper `9.5.0`；Fabric Loom `1.17.21`。
- 本轮最终命令：`.\gradlew.bat clean build --offline --console=plain`。结果：`BUILD SUCCESSFUL in 59s`，11 个任务全部执行。
- JUnit XML 实际汇总：**119 个测试套件，604 项测试，失败 0，错误 0，跳过 0**。
- 新增回归先确认失败，再修复：QQ 恢复续期凭证未落盘、取消 Cookie 后状态未还原、扫码原始续期字段被换票丢失，以及酷狗返回新 token 时旧 Cookie 仍覆盖新票据。预览还发现权益浮层清空菜单底图，补充渲染回归后修复。
- 测试报告：`cloudxmusic-client/build/reports/tests/test/index.html`。Cookie 弹窗预览：`outputs/login-cookie/{qq,kugou}-{light,dark}.png`；酷狗页面预览：`outputs/cloudxmusic-kugou-*.png`。已人工检查明暗 Cookie 表单与修正后的音质菜单，均使用实际画布与虚构数据生成。

覆盖：MP3/16、24 bit FLAC 解码样本、Range/If-Range 续传、声卡排空及暂停/停止/拖拽；三音源交接；KRC/翻译/解压上限；权限与资源/网络错误区分；整首锁定防抖与有界跳过；实际音质及降级通知；云收藏真实 fileid、认证请求、写入后重读确认、回滚及账号代次；资料/权益解析；Cookie 解析、隔离存储、取消导入、过期保留文件、扫码续期与 Cookie 永不续期、注入时钟验证 12 小时节流；音质浮层保留文字、底图与对勾。测试未访问真实登录、云收藏或会员写接口。

## 功能与线程边界

| 入口 | 调用链与线程边界 | 清理或限制 |
| --- | --- | --- |
| 账号与换源 | 三音源弹窗 → 现有协调器 → 暂停确认 → 客户端提交选择 | 不另建状态机，不覆盖其他账号 |
| QQ / 酷狗 Cookie 登录 | 共享表单 → 独立后台解析/账号验证/原子保存 → 不可变视图 | 取消/退出/新扫码使晚到结果失效；输入掩码，提交或关闭清空 |
| 扫码与保活 | 各自后台任务 → 实际登录协议换票/验证 → 保存新票据 | 扫码每 12 小时最多一次；Cookie 仅只读验证；关闭取消定时器 |
| 酷狗云收藏 | 客户端乐观爱心 → 独立后台云请求/确认 → 主线程更新或回滚 | 单写状态锁、分页上限、切歌/换号刷新、旧本地列表保留 |
| 酷狗资料/权益 | 有界线程池 → 服务资料/逐档权益 → 客户端发布 | 取消队列 purge、账号/歌曲代次、过期缓存；新权益覆盖旧观测 |
| 权限切歌 | 取链显式整首拒绝 → Toast → tick 到期跳过 | 900ms 间隔、hash 去重、一轮队列上限；手动动作取消 |
| 播放页/HUD/3D | 统一 MusicPlayback → 酷狗快照 → KRC TimedLyrics | 换源清理旧歌词/纹理，不转换其他音源模型 |
| 音质/拖拽 | 四档动态标签菜单 → 偏好与取链；beginSeek → seek | 对勾/胶囊显示实际质量；销毁取消拖拽 |
| 封面 | 后台 HTTP/解码 → 客户端缓存 | 64 张 LRU、64 条失败退避、8 个待处理请求；2 MiB/2048 像素限制 |
| 房间/分享 | UI、房间服务、分享编码边界拒绝酷狗 | 不改协议 v5，不伪装 QQ/网易云 |

## 三遍审查

| 检查 | 结果 |
| --- | --- |
| 功能和范围 | 本批包含已授权的酷狗体验修复及后续登录专项，无新数据库、指令或无关重构 |
| 网易云隔离 | 生产 `netease/` 相对基线零修改 |
| QQ 隔离 | 仅改登录白名单：QQAccountManager、QQCredentialStore、QQCredentials、QQLoginPainter、QQLoginService、QQLoginUi、QQSessionVerifier，新增 QQCookieLogin、QQRenewal；QQ 播放、目录、取链业务零修改 |
| 网络与文件 | 所有新认证、续期、云收藏网络/文件操作在后台；客户端读取视图与提交动作 |
| 共享集合 | 资料和收藏待处理队列有界；元数据为 1024 条同步 LRU；后台写回带账号/歌曲代次 |
| 生命周期 | 会话、搜索、歌词、取链、声卡、封面、收藏与维护任务均可关闭/取消，过期结果不再发布 |
| 阻塞扫描 | HTTP 与音频头探测 Future.get 带超时且在后台；无新阻塞式 CompletableFuture.join、传统 Bukkit 调度或同步传送 |
| 配置与依赖 | Fabric、mixin、语言 JSON 可解析；构建与依赖声明未更改；未增加运行依赖 |
| 许可 | 保留 GPL、第三方来源声明及完整许可，详见 THIRD_PARTY_NOTICES.md |
| 差异 | git diff --check 与成品脚本检查网易云及 QQ 非登录包隔离 |

当前项目是 Java 21 / Minecraft 1.21.11 Fabric 客户端。YAML、plugin.yml、Paper/Folia 调度、不可用事件、传送、箱子 GUI lore、Help/Tab、身份和数据库热切换测试均不适用；没有为此添加服务端模块。

## 成品检查

- 运行 JAR：Fabric `client`，版本 `2.6.0-alpha.3`，Minecraft `~1.21.11`，Java `>=21`。
- 确认包含独立酷狗播放器、权限/收藏/账号控制器、统一播放路由和 QQ / 酷狗 Cookie 表单与续期类。
- 无测试 class、Java 源文件、测试音频、account.json 或 library.json。保留项目许可与第三方许可/来源声明。
- 4 个嵌套依赖与 Fabric 元数据一致：Nayuki FLAC、Skija shared、Skija Windows x64、HumbleUI types；沿用客户端现有打包方式。
- 源码 ZIP 包含当前修改、资源与构建所需 libs，逐文件核对；不含 Git 历史、outputs、构建缓存或真实账号。
- 登录专项成品见 `outputs/kugou-login-2.6.0-alpha.3/`；本轮 VIP/收藏修复成品与校验见 `outputs/kugou-vip-favorite-2.6.0-alpha.3/manifest.json` 和 `SHA256SUMS.txt`。

## 未进行的实机验收

- `defer`：酷狗房间联机按用户要求排除，创建/加入置灰并提示“酷狗暂不支持房间联机”。
- `uncertain`：真实账号 Cookie/扫码有效期、服务端 12 小时续期结果、云收藏真实写入、动态会员与地区版权返回、真实声卡听感、游戏内 HUD/3D/拖拽表现。固定响应与注入时钟只能验证客户端状态机，不能保证平台凭证永不过期。
- 核心阶段曾对公开匿名搜索接口做只读冒烟；本轮没有进行真实账号或会员接口验证。
- 未执行真实 Minecraft/Fabric 客户端验收，未执行真实 Paper/Folia 服务端测试。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。当前项目是 Fabric 客户端，上述结果同样不等于真实游戏运行验收。

## 酷狗 VIP 与收藏播放回归 · 2026-11-15

- 版本保持 `2.6.0-alpha.3`；播放和云收藏改动只在独立 `kugou/` 包内。
- 取链日志仅记录路由、请求档位、HTTP 业务状态/错误码、可用 URL 数量、账号/dfid/歌曲元数据是否存在、格式白名单、码率、服务端时长和权限分类；不记录 token、userid、dfid 原值、签名、播放地址或响应正文。
- VIP 降级标识现在按用户所选档位的明确权限拒绝记录。较低档位的 403、App `20018`、通用 `privilege` 字段、请求超时或解析/网络故障不会反向污染高档位的 VIP 提示。
- 本机旧日志中，App `high/flac/320/128` 分别累计 24/45/44/12 次 `status=2, code=20018, hasUrl=false`；Web 会话取链累计 61 次 `status=1, code=0, hasUrl=true`。这证明 App URL 路由反复拒绝该 Web 会话，不能证明会员无权，也不能单凭此码确定唯一成因。参考代码只说明登录票据应匹配其所属 `appid`；本机日志未记录旧响应格式、会员字段和 URL 音质，需用新版本脱敏日志确认 Web 实际返回格式/码率及有效 dfid 状态。
- “我喜欢”端到端回归从酷狗云歌单接口响应解析 `hash`、`album_id`、`mixsongid`，经歌单播放源缓存后将被选歌曲送入独立播放控制器/resolver，并校验这三个身份字段完整保留。运行时点击还记录 `[酷狗收藏] 点击播放` 与歌曲身份字段。
- `QQCredentialStore` / `QQAccountManager` 的 Cookie 会话重启恢复和不续期已有专项生命周期测试；本次没有修改 QQ 或网易云底层业务。
- 回归覆盖新增于 `KugouPlaybackResolverTest`、`KugouApiPlaybackTest`、`KugouPlaylistPlaybackTest`。真实账号高音质取链和真实游戏声卡播放尚未复测，不能据单元测试宣称会员无损已恢复。
