# 云朵音乐盒 · CloudXMusic

<p align="center">    
  <img src="cloudxmusic-client-26.3/src/main/resources/assets/cloudxmusic/icon.png" width="112" alt="云朵音乐盒图标">    
</p>

<p align="center">    
  <strong>把音乐带进方块世界</strong>    
 </p>   
 <p align="center">  
   <strong>Minecraft Java Edition · Fabric 客户端音乐播放器</strong>  
 </p> 

<p align="center">  
  <img src="https://img.shields.io/badge/Version-3.0.0-D96A91?style=flat-square" alt="版本 3.0.0">  
  <img src="https://img.shields.io/badge/License-GPL--3.0--only-3572A5?style=flat-square" alt="GPL-3.0-only">  
  <img src="https://img.shields.io/badge/Minecraft-26.3-5A9B7B?style=flat-square" alt="Minecraft 26.3">  
  <img src="https://img.shields.io/badge/Loader-Fabric-E9A23B?style=flat-square" alt="Fabric">  
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=flat-square" alt="Java 25">  
</p>

CloudXMusic 将网易云音乐、QQ 音乐与酷狗音乐接入同一套游戏内界面，提供独立账号、搜索与播放队列、账号歌单、逐字歌词、灵动岛 HUD 和 3D 世界歌词。三音源通过全局状态机交接播放权，账号凭证和底层播放器分别隔离。QQ 与酷狗通过 Java 直接访问对应服务，无需 Node.js 或额外部署音乐 API 服务

## 导航

- [功能与音源差异](#功能与音源差异)
- [安装与开始使用](#安装与开始使用)
- [账号与 Cookie 登录](#账号与-cookie-登录)
- [架构与源码](#架构与源码)
- [构建与验证](#构建与验证)
- [本地数据与隐私](#本地数据与隐私)
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

酷狗“我喜欢的音乐”写入会修改真实账号的云收藏；音乐房间向成员同步曲目与播放状态，聊天分享按当前频道公开。

## 安装与开始使用

1. 准备 Minecraft Java Edition **26.3** 与 **Java 25**。
2. 安装 Fabric Loader 与 26.3 的 Fabric API。当前构建使用 Loader `0.20.1`、Fabric API `0.167.0+26.3`；模组声明的最低 Loader 为 `0.18.1`。
3. 将发行物 **`CloudXMusic-3.0.0.jar`** 放入该游戏实例的 `mods/` 目录。
4. 启动游戏，默认按 **H** 打开或关闭音乐盒，可在游戏按键设置中修改。
5. 首次进入时阅读《用户使用协议》，滚动到底部选择“同意并继续”。
6. 在左侧账号区选择音源并登录，再使用发现、搜索、我的收藏和播放队列。

播放使用 Java Sound，运行时需要 `java.desktop` 模块及可用声卡。发行包内置 Windows x64 Skija 原生库。

## 账号与 Cookie 登录

QQ 与酷狗的账号弹窗均提供扫码和“Cookie 登录”

Cookie 登录操作：

1. 在对应平台网页版登录。
2. 按 F12 打开开发者工具，在 Network 中选择对应平台请求，复制请求头里的完整 Cookie。
3. 将其粘贴到模组的 Cookie 输入框，点击“验证并登录”。
4. 后台校验成功后保存账号并刷新头像、昵称和会员资料；校验失败或取消不会覆盖原账号。

QQ 解析 `uin` / `qm_keyst`，酷狗解析 `userid` / `token`，并兼容官方 `KuGoo` 组合 Cookie。不同网页、地区和会话返回的字段可能不同；缺失必要字段时重新复制有效请求的 Cookie。

- **Cookie 会话**：每次请求使用已保存的对应凭证，只做只读有效性检查，不调用续期接口。
- **扫码会话**：保存平台实际返回的续期信息，后台每 12 小时最多尝试一次静默续期；能否续期取决于平台返回的会话能力。
- **明确失效**：提示重新扫码或粘贴 Cookie；网络异常不会清空已有凭证。
- **重启恢复**：从对应账号文件加载会话并在后台复查。

账号头像、昵称、VIP / SVIP 资料来自平台。会员徽章代表读取到的账号资料，与单曲或音质的可播性无关。

## 架构与源码

```text
cloudxmusic-client-26.3/src/main/java/
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

26.3 客户端是独立 Gradle 构建，与仓库根构建互不干扰；`libs/`、`music-protocol/`、`music-server/` 由两者共用；1.21.11 客户端保留在 `cloudxmusic-client/`，版本 2.6.0

## 构建与验证

需要 JDK 25。首次构建需要网络下载 Gradle、Minecraft、Fabric 与 Maven 依赖；本地缓存完整时可加 `--offline`。

客户端在模块目录内构建：

```powershell
cd cloudxmusic-client-26.3
.\gradlew.bat clean build --console=plain
```

产物：

```text
cloudxmusic-client-26.3/build/libs/CloudXMusic-3.0.0.jar
```

测试与离线 UI 预览：

```powershell
.\gradlew.bat test --console=plain
.\gradlew.bat renderUiPreview --console=plain
```

JUnit XML 位于 `cloudxmusic-client-26.3/build/test-results/test/`，HTML 报告位于 `build/reports/tests/test/`；离线预览输出到 `outputs/`。测试覆盖登录与持久化、签名与脱敏、搜索/推荐隔离、KRC、取链与试听拒绝、分页、取消、完整队列、播放互斥、拖拽及 UI 几何。

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
| `disclaimer.txt`                    | 协议确认记录                                |
| 根目录 `cookies.json` / `netease.json` | 历史路径；现有迁移逻辑兼容                         |

凭证以明文保存在本机。认证请求需要把对应凭证发送给对应官方服务；音频 CDN 下载不附带账号 Cookie，项目不通过自建账号服务器中转登录。

## 贡献、资料与许可证

原作者与贡献者：**Lzasn**（客户端、网易云音源与解码）、**xWtree**（早期界面与宣传）、**xiaota888**（QQ 音源、聚合架构与逐字歌词等）。保留既有作者信息与第三方来源声明。

原创代码采用 [GNU GPL-3.0-only](LICENSE)。解码、二维码、世界歌词和字体等第三方组件分别遵循各自许可。完整来源、固定版本和随包声明见 [THIRD\_PARTY\_NOTICES.md](THIRD_PARTY_NOTICES.md)；第三方许可证、NOTICE 和规定提供的源码归档随发行包保留。
