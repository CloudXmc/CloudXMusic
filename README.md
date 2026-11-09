# 云朵音乐盒 · CloudXMusic

<p align="center">
  <img src="cloudxmusic-client/src/main/resources/assets/cloudxmusic/icon.png" width="120" alt="云朵音乐盒图标">
</p>

<p align="center">
  <strong>把音乐带进方块世界</strong><br>
  Minecraft Java Edition 的 Fabric 客户端音乐播放器
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-2.6.0--alpha.1-D96A91?style=flat-square" alt="版本 2.6.0-alpha.1">
  <img src="https://img.shields.io/badge/License-GPL--3.0--only-3572A5?style=flat-square" alt="GPL-3.0-only">
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-5A9B7B?style=flat-square" alt="Minecraft 1.21.11">
  <img src="https://img.shields.io/badge/Loader-Fabric-E9A23B?style=flat-square" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square" alt="Java 21">
</p>

## 目录

[项目简介](#项目简介) · [功能](#功能) · [安装](#安装) · [使用](#使用) · [构建](#构建) · [配置与隐私](#配置与隐私) · [已知限制](#已知限制) · [开发资料](#开发资料) · [第三方组件与许可证](#第三方组件与许可证)

## 项目简介

CloudXMusic（云朵音乐盒）是一款面向 Minecraft 1.21.11 的 Fabric 客户端模组，支持网易云音乐与 QQ 音乐双音源、独立账号与播放队列、歌词和 HUD 灵动岛。界面使用模组内置字体和抗锯齿画布绘制；QQ 音乐直接通过 Java 请求，无需安装 Node.js 或部署外部 API 服务。

开发者：Lzasn（客户端、网易云音源与解码）、xiaota888（QQ 音乐音源、双音源架构与逐字歌词）、xWtree（早期界面开发、项目宣传者）。

当前源码版本为 `2.6.0-alpha.1`，基于 2.6.0 代码，3D 世界歌词因稳定性与实际表现仍未达到预期而重新标记为 Alpha；更新日志见 [CHANGELOG.md](CHANGELOG.md)，后续发布构建由 `v2.6.0-alpha.1` 标签触发 CI 完成。

歌曲分享会先尝试 CloudXMusic 服务端插件 `1.0.6+` 的专用握手：握手成功时由插件转发，显示名优先沿用玩家列表样式。单人、局域网主机或无插件服务器会自动降级为普通公开聊天分享，末尾携带短机器标记；本机和其他安装模组的客户端会将它解析为可点击的「点击播放」，普通聊天插件与系统提示仍照常显示。

> [!IMPORTANT]
> 音乐房间已完成协议、房间管理、队列与播放同步，以及「每名成员用自己的账号取链跟播」，但尚未完成真实多人联机验收。房间内的进度对齐、成员暂停跟听、VIP/版权受限曲目的表现仍可能有偏差，请勿用于正式房间活动。

> **首次使用说明**：首次使用时须在游戏内阅读并确认相应音源的使用协议与免责声明；QQ 音乐登录前需要滚动到底部才能同意。CloudXMusic 是非官方第三方客户端，与网易云音乐、QQ 音乐及其运营方无隶属、授权或背书关系。服务请求由客户端直接发送至对应音乐服务及音频 CDN。账号凭证以未加密文本保存在本机，请勿分享；使用时请遵守服务条款、所在地法律法规及作品版权要求。

## 功能

| 音乐与账号 | 播放与界面 |
| --- | --- |
| 网易云账号登录、QQ 音乐扫码登录，左侧账号区域切换全局音源 | 两侧独立队列、单曲循环、列表循环与随机播放 |
| 网易云每日推荐、搜索与收藏；QQ 每日30首、官方排行榜、单曲/歌单搜索、歌单详情与我喜欢 | 进度、音量及播放控制，双音源互斥播放 |
| QQ 臻品母带、SQ FLAC、HQ 320k、标准 128k，按“母带 → SQ → HQ → 标准”降级 | LRC / QQ QRC 逐字歌词、翻译开关与 HUD 灵动岛 |
| 专辑封面、QQ 头像与会员徽章、歌曲 VIP 标识 | Unicode 字形回退、圆角磨砂界面与柔和阴影 |
| 音乐房间：20 人上限、房间码邀请、房主队列与成员跟播 | 面板升降、导航滚动与灵动岛过渡动效 |

两侧账号凭证分开存放，不会保存到项目服务器。2.4.11 已移除 QQ“刷歌模式 / 个性电台”，每日30首、官方排行榜与推荐歌单使用普通播放队列。QQ 收藏写入已停用，仅保留“我的收藏”读取；QQ 底栏原爱心位置改为一键停止。

## 安装

1. 使用 Minecraft `1.21.11`，安装 Fabric Loader `0.20.1` 或更新版本及 Fabric API。
2. 将 `CloudXMusic-<version>.jar` 放入游戏实例的 `mods/` 目录。
   **不要放 `-sources.jar`**：它只有源码、没有 class，`fabric.mod.json` 里的版本也还是 `${version}`，装进 mods 会直接启动崩溃。
3. 启动游戏，按 `H` 打开云朵音乐盒；首次打开时确认免责声明。

播放使用 `javax.sound.sampled`，Java 运行时需包含 `java.desktop` 模块。官方启动器提供的 Microsoft/OpenJDK 通常满足要求。

## 使用

默认是按 `H` 打开或关闭音乐盒（可在游戏按键设置中修改）。点击左侧账号区域切换网易云 / QQ 音乐，搜索、发现、收藏、队列、正在播放和底栏随全局音源更新。QQ 搜索支持单曲 / 歌单 Tab，音质支持臻品母带、SQ、HQ 和标准四档并自动降级。登录、搜索、音质与歌词切换等均在界面内完成，无需命令。返回游戏主标题界面时停止播放。

播放页标题及歌曲列表提供分享按钮，可将歌曲发到游戏聊天栏；安装模组的接收者可点击播放。房间内点播仍受房间权限限制。

音乐房间需要服务端插件 `CloudXMusic-Server`（本地 `music-server` 模块构建）。房主创建房间时，当前个人队列会按房间音源过滤后作为房间队列，之后房主的加歌/移除/播放全部会实时同步；成员加入后看到的是房间队列（只读）。房主控播放/暂停、上下首、进度、循环三档，成员只能本地「暂停跟听」，不影响其他人。

### 3D 世界歌词（MuoniumPlayer 移植）

> [!IMPORTANT]
> 3D 世界歌词目前仍不稳定，可能存在字形缓存与排版错位、动效间距异常、字体加载失败、Iris / 光影兼容问题，以及不同设备、窗口尺寸和 GUI 缩放下显示不一致等情况。实际表现尚未达到预期范围，相关功能仍在持续修复中，请勿将其作为核心功能或正式展示场景使用，并留意游戏日志与问题反馈。

在设置页的「3D 世界歌词」双列分组里开关并调整布局、翻译、发光和动效。渲染器使用 MuoniumPlayer 的世界歌词子系统，在世界渲染尾部按相机朝向生成公告牌：

- 3D 世界歌词与灵动岛歌词模式相互独立；关闭灵动岛歌词不会停止播放或隐藏 3D 世界歌词；
- 歌词跟随玩家位置并保持在视野前方，支持视线锥体扩散、回屏保持与地面高度限制；
- 每句按字符独立烘焙 Skija 超采样字形，带入场回弹、逐字出现、wordPop、绘光拖尾与心象分块动效；
- 逐字歌词（QQ QRC）按进度推进高亮，普通 LRC 按字符时间轴展开；
- 光晕使用 Skija 多层高斯或 HDR bloom 烘焙进字形图集；
- 关闭时不创建字形纹理，也不进入世界歌词渲染流程。

## 构建

需要 Java 21 和网络连接，以下载 Minecraft、Fabric 与编译依赖。

Windows：

```powershell
.\gradlew.bat :cloudxmusic-client:build
```

Linux/macOS：

```bash
./gradlew :cloudxmusic-client:build
```

构建产物位于 `cloudxmusic-client/build/libs/CloudXMusic-<version>.jar`。

完整回归及离线界面预览：

```powershell
.\gradlew.bat clean :cloudxmusic-client:build :cloudxmusic-client:renderUiPreview
```

`libs/` 中的本地依赖与 Gradle Wrapper 均属于构建所需文件，应与源码一并保留。Nayuki FLAC 库使用固定提交的本地编译 JAR，由 Loom 嵌套打包；来源和许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 配置与隐私

配置目录：`config/cloudxmusic/`

| 文件 | 内容 |
| --- | --- |
| `cookies.json` | 网易云登录 Cookie 与游客会话 Cookie，未加密保存 |
| `netease/netease.json` | 昵称、UID、音质与歌词偏好 |
| `netease/cookies.json` | 网易云登录 Cookie，明文保存 |
| `state.json` | 当前歌曲与播放队列 |
| `qqmusic/account.json` | QQ 账号票据，明文存储并由操作系统用户权限保护 |
| `qqmusic/config.json` | QQ 音质、音量、歌词与协议确认等独立偏好 |

- 登录凭证仅保存于本机客户端，并用于对应音乐服务的认证请求。
- 请勿上传、截图或分享 `cookies.json`、`qqmusic/account.json` 等账号文件。
- 不要把账号密码、Cookie 或音频凭证提交到 Git 仓库或公开工单。

## 已知限制

- 网易云支持标准 / 较高 / 极高 / 无损（FLAC）四档，QQ 支持臻品母带、SQ FLAC、HQ 320kbps 与标准 128kbps；实际可用音质取决于歌曲及账号权限，自动降级不会覆盖全局偏好。
- 网易云无损会先把整轨下载到临时目录并建立帧索引（4 分钟曲目约 1–2 秒），之后拖动进度即时生效。
- QQ 的 FLAC / MP3 进度跳转通过后台从头解码并按 PCM 样本数跳过，长曲目或慢速网络可能需要等待。
- 网易云与 QQ 音乐接口为非官方接口，可能随服务端变化而失效。
- 部分受版权、地区或账号权限限制的曲目可能无法播放。
- 「音乐房间」已完成协议（v5）、房间管理（20 人上限）、队列与播放同步、成员状态广播和本地跟播（每名成员用自己的账号取链解码，偏差超 4 秒才纠偏）。房主控播放/切歌/循环，成员可本地「暂停跟听」，双方都不影响对方；**尚未完成真实双端联机验收**，请勿用于正式房间活动。
- 房间音源在创建时固定；进入房间不会自动切换全局音源。若当前全局音源与房间音源不一致，房间内会提示先手动切换，退出房间后全局音源保持不变。
- 远端发行物只包含客户端；`music-protocol` 与 `music-server` 源码仅在本地开发，客户端通过 `libs/cloudxmusic-protocol.jar` 合并协议运行包。
- 3D 世界歌词使用 `keepDistance` 跟随模型：歌词随玩家平移并锁定在视野前方，不是固定世界坐标物件；转开视角时可按设置回屏。
- 世界歌词在 `LevelRenderer#renderLevel` 尾部绘制并关闭深度测试，因此不会被方块遮挡；Iris 下的信标全亮回退路径仍需实机验收。
- 当前发行包只嵌套 Windows x64 的 Skija 原生库；其他平台需要补充对应 `skija-<platform>` 运行包。
- 本地服务端仍按歌曲标称时长自动推进队列；QQ 客户端则等待声卡排空后提出曲末请求。取链或缓冲延迟可能导致服务端先切歌，需进行服务端配套与真实双端验收。

## 开发资料

- [更新日志](CHANGELOG.md)
- [UI 知识点与维护约定](UI_KNOWLEDGE.md)
- [UI 重构记录](UI_REFACTOR.md)
- [开发状态与交接记录](DEVELOPMENT_STATUS.md)
- [Folia 服务端与协议开发记录](docs/FOLIA_DEVELOPMENT.md)
- [第三方组件与许可证](THIRD_PARTY_NOTICES.md)

主要代码位于 `cloudxmusic-client/src/main/java/dev/lzasn/cloudxmusic/client/`：`audio/` 处理原有音频，`gui/` 绘制界面，`hud/` 绘制灵动岛，`lyric/` 解析歌词，`netease/` 保留网易云业务，`state/` 管理原有播放状态，`omni/` 提供统一音源路由与快照，`qqmusic/` 管理 QQ 登录、目录、取链、解码和独立状态。

开发状态、UI 重构等文档包含历史阶段记录；当前版本的功能与变更以本 README 和 [CHANGELOG.md](CHANGELOG.md) 为准。

## 第三方组件与许可证

- [JLayer](https://repo1.maven.org/maven2/javazoom/jlayer/)：MP3 解码，LGPL-3.0-or-later。
- [ZXing Core](https://github.com/zxing/zxing)：二维码编码，Apache-2.0。
- [Project Nayuki FLAC library](https://github.com/nayuki/FLAC-library-Java)：FLAC 解码，LGPL-3.0-or-later，保留上游源码与许可。
- [Skija](https://github.com/HumbleUI/Skija)：世界歌词字形与辉光烘焙，Apache-2.0；Windows x64 运行库随 JAR 嵌套打包。
- [MuoniumPlayer](https://github.com/zihe114514/MuoniumPlayer-multiversion)：3D 世界歌词子系统来源，Apache-2.0；许可与 NOTICE 随资源打包。
- Inter、Noto Sans SC 与 Noto Sans CJK KR 字体的许可证随资源一并提供。

完整声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。项目原创代码采用 [GNU GPL-3.0-only](LICENSE)；版权归 Lzasn、xWtree、xiaota888 所有。
