# 云朵音乐盒 · CloudXMusic

<p align="center">
  <img src="cloudxmusic-client/src/main/resources/assets/cloudxmusic/icon.png" width="120" alt="云朵音乐盒图标">
</p>

<p align="center">
  <strong>把音乐带进方块世界</strong><br>
  Minecraft Java Edition 的 Fabric 客户端音乐播放器
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Version-2.4.11-D96A91?style=flat-square" alt="版本 2.4.11">
  <img src="https://img.shields.io/badge/License-GPL--3.0--only-3572A5?style=flat-square" alt="GPL-3.0-only">
  <img src="https://img.shields.io/badge/Minecraft-1.21.11-5A9B7B?style=flat-square" alt="Minecraft 1.21.11">
  <img src="https://img.shields.io/badge/Loader-Fabric-E9A23B?style=flat-square" alt="Fabric">
  <img src="https://img.shields.io/badge/Java-21-ED8B00?style=flat-square" alt="Java 21">
</p>

## 目录

[功能](#功能) · [安装](#安装) · [使用](#使用) · [构建](#构建) · [配置与隐私](#配置与隐私) · [已知限制](#已知限制) · [开发资料](#开发资料)

## 项目简介

CloudXMusic（云朵音乐盒）是一款面向 Minecraft 1.21.11 的 Fabric 客户端模组，支持网易云音乐与 QQ 音乐双音源、独立账号与播放队列、歌词和 HUD 灵动岛。界面使用模组内置字体和抗锯齿画布绘制；QQ 音乐直接通过 Java 请求，无需安装 Node.js 或部署外部 API 服务。

开发者：Lzasn（客户端、网易云音源与解码）、xiaota888（QQ 音乐音源、双音源架构与逐字歌词）、xWtree（早期界面开发、项目宣传者）。

> **首次使用说明**：首次使用时须在游戏内阅读并确认相应音源的使用协议与免责声明；QQ 音乐登录前需要滚动到底部才能同意。CloudXMusic 是非官方第三方客户端，与网易云音乐、QQ 音乐及其运营方无隶属、授权或背书关系。服务请求由客户端直接发送至对应音乐服务及音频 CDN。账号凭证以未加密文本保存在本机，请勿分享；使用时请遵守服务条款、所在地法律法规及作品版权要求。

## 功能

| 音乐与账号 | 播放与界面 |
| --- | --- |
| 网易云账号登录、QQ 音乐扫码登录，左侧账号区域切换全局音源 | 两侧独立队列、单曲循环、列表循环与随机播放 |
| 网易云每日推荐、搜索与收藏；QQ 每日30首、推荐歌单、搜索与我喜欢 | 进度、音量及播放控制，双音源互斥播放 |
| QQ SQ FLAC、HQ 320k、标准 128k，取链失败自动降级 | LRC / QQ QRC 逐字歌词、翻译开关与 HUD 灵动岛 |
| 专辑封面、QQ 头像与会员徽章、歌曲 VIP 标识 | Unicode 字形回退、圆角磨砂界面与柔和阴影 |
| 音乐房间入口（Beta，占位） | 面板升降、导航滚动与灵动岛过渡动效 |

两侧账号凭证分开存放，不会保存到项目服务器。2.4.11 已移除 QQ“刷歌模式 / 个性电台”，每日30首与推荐歌单使用普通播放队列。

## 安装

1. 使用 Minecraft `1.21.11`，安装 Fabric Loader `0.20.1` 或更新版本及 Fabric API。
2. 将 `CloudXMusic-<version>.jar` 放入游戏实例的 `mods/` 目录。
3. 启动游戏，按 `H` 打开云朵音乐盒；首次打开时确认免责声明。

播放使用 `javax.sound.sampled`，Java 运行时需包含 `java.desktop` 模块。官方启动器提供的 Microsoft/OpenJDK 通常满足要求。

## 使用

默认是按 `H` 打开或关闭音乐盒（可在游戏按键设置中修改）。点击左侧账号区域切换网易云 / QQ 音乐，搜索、发现、收藏、队列、正在播放和底栏随全局音源更新。登录、搜索、音质与歌词切换等均在界面内完成，无需命令。返回游戏主标题界面时停止播放。

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

- 网易云支持标准 / 较高 / 极高 / 无损（FLAC）四档，QQ 支持 SQ FLAC、HQ 320kbps 与标准 128kbps；实际可用音质取决于歌曲及账号权限，自动降级不会覆盖全局偏好。
- 网易云无损会先把整轨下载到临时目录并建立帧索引（4 分钟曲目约 1–2 秒），之后拖动进度即时生效。
- QQ 的 FLAC / MP3 进度跳转通过后台从头解码并按 PCM 样本数跳过，长曲目或慢速网络可能需要等待。
- 网易云与 QQ 音乐接口为非官方接口，可能随服务端变化而失效。
- 部分受版权、地区或账号权限限制的曲目可能无法播放。
- 「音乐房间」页目前是 Beta 占位，只提供入口与说明，房间功能尚未接入。
- 当前仓库只实现 Fabric 客户端，不提供 Paper / Folia 服务端插件。

## 开发资料

- [更新日志](CHANGELOG.md)
- [UI 知识点与维护约定](UI_KNOWLEDGE.md)
- [UI 重构记录](UI_REFACTOR.md)
- [开发状态与交接记录](DEVELOPMENT_STATUS.md)
- [第三方组件与许可证](THIRD_PARTY_NOTICES.md)

主要代码位于 `cloudxmusic-client/src/main/java/dev/lzasn/cloudxmusic/client/`：`audio/` 处理原有音频，`gui/` 绘制界面，`hud/` 绘制灵动岛，`lyric/` 解析歌词，`netease/` 保留网易云业务，`state/` 管理原有播放状态，`omni/` 提供统一音源路由与快照，`qqmusic/` 管理 QQ 登录、目录、取链、解码和独立状态。

开发状态、UI 重构等文档包含历史阶段记录；当前版本的功能与变更以本 README 和 [CHANGELOG.md](CHANGELOG.md) 为准。

## 第三方组件与许可证

- [JLayer](https://repo1.maven.org/maven2/javazoom/jlayer/)：MP3 解码，LGPL-3.0-or-later。
- [ZXing Core](https://github.com/zxing/zxing)：二维码编码，Apache-2.0。
- [Project Nayuki FLAC library](https://github.com/nayuki/FLAC-library-Java)：FLAC 解码，LGPL-3.0-or-later，保留上游源码与许可。
- Inter、Noto Sans SC 与 Noto Sans CJK KR 字体的许可证随资源一并提供。

完整声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。项目原创代码采用 [GNU GPL-3.0-only](LICENSE)；版权归 Lzasn、xWtree、xiaota888 所有。
