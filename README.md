# 云朵音乐盒 · CloudXMusic

<p align="center">  
  <img src="cloudxmusic-client/src/main/resources/assets/cloudxmusic/icon.png" width="112" alt="云朵音乐盒图标">  
</p>

<p align="center">  
  <strong>把音乐带进方块世界</strong>  
  
  Minecraft Java Edition · Fabric 客户端音乐播放器  
  
  <strong>2.6.0-alpha.3</strong> · Minecraft 1.21.11 · Java 21 · GPL-3.0-only  
</p>

CloudXMusic 将网易云音乐、QQ 音乐与酷狗音乐接入同一套游戏内界面，提供独立账号、搜索与播放队列、账号歌单、逐字歌词、灵动岛 HUD 和 3D 世界歌词。三音源通过全局状态机交接播放权，账号凭证和底层播放器分别隔离。QQ 与酷狗通过 Java 直接访问对应服务，无需 Node.js 或额外部署音乐 API 服务

本项目是非官方第三方客户端，与上述音乐平台及其运营方没有从属、授权或背书关系。歌曲能否播放、能取得什么音质，由平台对当前账号、曲目、地区和会话的实时返回决定；模组不提供会员权益，也不绕过版权或付费限制

## 导航

- [功能与音源差异](#功能与音源差异)
- [安装与开始使用](#安装与开始使用)
- [账号与 Cookie 登录](#账号与-cookie-登录)
- [架构与源码](#架构与源码)
- [构建与验证](#构建与验证)
- [本地数据与隐私](#本地数据与隐私)
- [使用须知与免责声明](#使用须知与免责声明)
- [贡献、资料与许可证](#贡献资料与许可证)

## 功能与音源差异

| 能力             | 网易云音乐             | QQ 音乐              | 酷狗音乐            |
| -------------- | ----------------- | ------------------ | --------------- |
| 账号登录           | Cookie、账号登录、验证码登录 | 扫码、Cookie          | 扫码、Cookie       |
| 发现音乐           | 每日推荐              | 每日30首、排行榜与推荐歌单     | 独立获取官网新歌推荐      |
| 搜索             | 歌曲                | 歌曲、歌单              | 歌曲              |
| 我喜欢的音乐         | 收藏列表与原有交互         | 读取账号收藏；写入已停用       | 读取、添加、取消云收藏     |
| 我的歌单 / 收藏歌单    | 仅收藏               | 账号目录、分页详情、播放全部     | 账号目录、分页详情、播放全部  |
| 音质             | 标准、较高、极高、无损 FLAC  | 臻品母带、SQ FLAC、HQ、标准 | 自动取链，无手动音质菜单    |
| 歌词             | LRC 逐行歌词          | QRC 逐字歌词           | KRC 逐字歌词、LRC 回退 |
| 队列、封面、进度、音量、循环 | 支持                | 支持                 | 支持              |
| 灵动岛、播放页、3D 歌词  | 支持                | 支持                 | 支持              |
| 音乐房间 / 聊天歌曲分享  | 支持                | 支持                 | 无               |

## 安装与开始使用

1. 准备 Minecraft Java Edition **1.21.11** 与 **Java 21**。
2. 安装 Fabric Loader 和对应 Minecraft 版本的 Fabric API。当前构建使用 Loader `0.20.1`、Fabric API `0.141.6+1.21.11`；模组声明的 Loader 最低版本为 `0.18.1`，建议使用当前构建版本或兼容的新版本。
3. 将发行物 **`CloudXMusic-2.6.0-alpha.3.jar`** 放入该游戏实例的 `mods/` 目录。
4. 启动游戏，默认按 **H** 打开或关闭音乐盒，可在游戏按键设置中修改。
5. 首次进入时阅读统一的《用户使用协议与免责声明》，滚动到底部后选择“同意并继续”。“拒绝并退出”会离开音乐界面。
6. 在左侧账号区选择音源并登录，再使用发现、搜索、我的收藏和播放队列。

**不要将 `-sources.jar` 或源码 ZIP 放入 `mods/`。** Gradle 生成的 sources JAR 用于阅读源码，不是可运行模组

播放使用 Java Sound，运行时需要 `java.desktop` 模块及可用声卡。当前发行包内置 Windows x64 Skija；Linux、macOS 和其他架构需要适配相应原生运行库。

## 账号与 Cookie 登录

QQ 与酷狗的账号弹窗均提供扫码和“Cookie 登录”

Cookie 登录操作：

1. 在对应平台网页版登录。
2. 按 F12 打开开发者工具，在 Network 中选择对应平台请求，复制请求头里的完整 Cookie。
3. 将其粘贴到模组的 Cookie 输入框，点击“验证并登录”。
4. 后台校验成功后保存账号并刷新头像、昵称和会员资料；校验失败或取消不会覆盖原账号。

QQ 解析 `uin` / `qm_keyst`，酷狗解析 `userid` / `token`，并兼容官方 `KuGoo` 组合 Cookie。不同网页、地区和会话返回的字段可能不同；缺失必要字段时应重新复制有效请求的 Cookie。

- **Cookie 会话**：每次请求使用已保存的对应凭证，只做只读有效性检查，不调用续期接口。
- **扫码会话**：保存平台实际返回的续期信息，后台每 12 小时最多尝试一次静默续期；是否可续期取决于平台返回的会话能力。
- **明确失效**：提示重新扫码或粘贴 Cookie；网络异常不会直接把已有凭证清空。
- **重启恢复**：从对应账号文件加载会话并在后台复查。任何客户端都不能保证服务端永不撤销凭证。

账号头像、昵称、VIP / SVIP 资料来自平台。会员徽章只代表读取到的账号资料，不能作为某首歌或某档音质必定可播的保证。不要向他人发送 Cookie、token 或账号文件。

## 架构与源码

```text
cloudxmusic-client/src/main/java/
├─ dev/lzasn/cloudxmusic/client/
│  ├─ CloudXMusicClient       生命周期、输入、模块装配
│  ├─ omni/                  全局音源状态机、统一歌曲与播放快照、路由
│  ├─ netease/ + audio/      网易云接入及原有解码链路
│  ├─ qqmusic/               QQ 登录、目录、取链、歌词与独立播放器
│  ├─ kugou/                 酷狗登录、推荐、搜索、云收藏、歌单、KRC、独立播放器
│  ├─ gui/design/            自绘组件、布局与纯绘制层
│  ├─ gui/login/             共享 Cookie 登录表单与通知
│  ├─ gui/playlist/          账号歌单分页、取消、完整队列提交与绘制
│  ├─ gui/terms/             全局协议资源与阅读状态
│  ├─ hud/ + lyric/          HUD 与通用歌词模型
│  └─ room/ + chat/          房间同步与聊天分享
└─ com/muoniumplayer/         已注明来源的世界歌词子系统
```

## 构建与验证

需要 JDK 21。首次构建需要网络下载 Gradle、Minecraft、Fabric 和 Maven 依赖；已具备完整本地缓存时可加 `--offline`。

Windows：

```powershell
.\gradlew.bat clean build --console=plain
```

Linux / macOS 的源码构建命令：

```bash
chmod +x gradlew
./gradlew clean build --console=plain
```

上述跨平台构建命令不代表当前 Windows 原生库组合已完成其他平台的运行适配。客户端产物位于：

```text
cloudxmusic-client/build/libs/CloudXMusic-2.6.0-alpha.3.jar
```

运行测试或离线 UI 预览：

```powershell
.\gradlew.bat :cloudxmusic-client:test --console=plain
.\gradlew.bat :cloudxmusic-client:renderUiPreview --console=plain
```

JUnit XML 位于 `cloudxmusic-client/build/test-results/test/`，HTML 报告位于 `cloudxmusic-client/build/reports/tests/test/`；离线预览输出到 `outputs/`。测试覆盖登录与持久化、签名与脱敏、搜索/推荐隔离、KRC、取链与试听拒绝、分页、取消、完整队列、播放互斥、拖拽及 UI 几何等。固定响应、回环 HTTP 和测试音频不能代替真实账号与游戏声卡验收；本次发布准备的实际构建结果以随交付产物提供的验证记录为准。

打包时应检查 `fabric.mod.json`、运行 class、嵌套库、许可证与源代码归档，并排除真实凭证、运行目录、构建缓存及调试产物。公开问题前请人工复核日志，默认日志保留路由与错误摘要，完整脱敏取链请求和回包仅在 DEBUG 级别输出

## 本地数据与隐私

默认数据目录为游戏实例下的 **`config/cloudxmusic/`**。需要同时运行多个客户端时，可分别设置 JVM 参数 `-Dcloudxmusic.home=<独立目录>`，避免覆盖同一份会话和状态。

| 路径（相对此目录）                           | 用途                                    |
| ----------------------------------- | ------------------------------------- |
| `netease/cookies.json`              | 网易云 Cookie；凭证以明文保存                    |
| `netease/netease.json`              | 网易云账号资料、音质与歌词偏好；旧格式可能带凭证              |
| `qqmusic/account.json`              | QQ 账号票据、Cookie 登录方式与实际返回的扫码续期信息       |
| `qqmusic/config.json`               | QQ 音质、音量、歌词与兼容协议偏好                    |
| `kugou/account.json`                | 酷狗 token / userid、Cookie、设备会话、登录方式及资料 |
| `kugou/library.json`                | 酷狗偏好、兼容的旧音质字段与旧本地列表                   |
| `state.json`                        | 原有播放状态与队列                             |
| `disclaimer.txt`                    | 全局协议修订版确认记录                           |
| 根目录 `cookies.json` / `netease.json` | 历史路径；现有迁移逻辑兼容，不应公开                    |

凭证保存在本机，**不等于加密保险箱**。请保护操作系统账户、实例目录和备份；不要分享完整配置目录、存档打包、账号截图、Cookie 或未检查的日志。对应认证请求仍需把必要凭证发送给对应官方服务，音频 CDN 下载不附带账号 Cookie；项目不通过自建账号服务器中转登录。

## 使用须知与免责声明

首次使用的完整协议随 JAR 发布于 [`user-agreement.txt`](cloudxmusic-client/src/main/resources/assets/cloudxmusic/terms/user-agreement.txt)

使用前请理解以下事项：

- **非官方接口**：平台接口、网页结构、签名和风控可随时变化，服务可能限流、中断或失效。CloudXMusic 不代表平台官方客户端或平台授权。
- **账号风险**：第三方客户端、Cookie 导入、扫码和设备验证可能触发账号限制、凭证撤销或封禁。是否登录应由账号所有者自行决定，请遵守平台协议。
- **版权与权限**：音频从服务返回的 CDN 地址获取，歌词和封面来自相应服务；不因使用模组而取得传播、下载再分发或商业利用作品的权利。禁止绕过付费、版权和地区限制，不提供侵权内容。
- **云端操作**：酷狗收藏会修改真实账号数据；聊天分享按所在频道公开，房间向成员同步曲目和播放状态。操作前确认账号与可见范围。
- **软件责任**：项目按许可证和适用法律以现状提供，不保证接口持续可用、特定音质、账号安全或同步效果。在法律允许范围内不作相应担保；法律规定不得免除的责任不因本声明排除。
- **许可边界**：原创程序采用 GPL-3.0-only。学习、修改和再分发应遵守 GPL，第三方组件遵守各自许可；平台内容和账号权限不属于程序许可证授予的权利。本项目不附加与 GPL 冲突的“禁止商用”软件许可限制。

## 贡献、资料与许可证

原作者与贡献者：**Lzasn**（客户端、网易云音源与解码）、**xWtree**（早期界面与宣传）、**xiaota888**（QQ 音源、聚合架构与逐字歌词等）。保留既有作者信息与第三方来源声明。

- [本版本与历史更新](CHANGELOG.md)
- [本次发布构建与验证记录](docs/RELEASE_VALIDATION_2.6.0-alpha.3.md)
- [酷狗自动音质定稿](docs/KUGOU_AUTOMATIC_QUALITY.md) · [取链验证](docs/KUGOU_PLAYBACK_REVIEW.md) · [播放恢复记录](docs/KUGOU_PLAYBACK_RECOVERY.md)
- [酷狗核心结构](docs/KUGOU_CORE.md) · [接线验证](docs/KUGOU_VALIDATION.md) · [歌单验证](docs/PLAYLIST_VALIDATION.md)
- [统一协议说明](docs/USER_AGREEMENT.md) · [QQ 徽章验证](docs/QQ_BADGE_VALIDATION.md)
- [3D 世界歌词移植记录](docs/WORLD_LYRICS_MUONIUM_PORT.md) · [房间与 Folia 联机](docs/FOLIA_DEVELOPMENT.md) · [私聊分享](docs/PRIVATE_SHARE.md)

历史开发文档包含当时的设计与实验结论，当前功能以本 README、本版本 CHANGELOG 和最终代码为准。提交问题时请提供版本、系统、复现步骤与已脱敏错误摘要，不上传账号文件。

原创代码采用 [GNU GPL-3.0-only](LICENSE)。解码、二维码、世界歌词和字体等第三方组件分别遵循各自许可。完整来源、固定版本和随包声明见 [THIRD\_PARTY\_NOTICES.md](THIRD_PARTY_NOTICES.md)；第三方许可证、NOTICE 和规定提供的源码归档随发行包保留。
