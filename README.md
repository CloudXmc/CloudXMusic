# CloudXMusic

<p align="center">
  <img src="cloudxmusic-client/src/main/resources/assets/cloudxmusic/icon.png" width="160" alt="CloudXMusic icon">
</p>

<p align="center">轻量级 Minecraft Fabric 网易云音乐播放器</p>

CloudXMusic 是一个面向 Minecraft Java Edition 的 Fabric 客户端模组。它提供游戏内自绘音乐界面、顶部灵动岛、网易云登录与搜索、收藏、队列、专辑封面、滚动歌词和 MP3 流式播放。

> 本项目是非官方第三方客户端，与网易云音乐官方无关。请遵守网易云音乐服务条款及当地法律法规，仅用于个人学习和客户端体验，不要分发受版权保护的音乐内容。

## 功能

- 自绘 ClickGUI：正在播放、每日推荐、搜索、收藏、队列、关于
- 网易云扫码登录、短信验证码登录、密码与邮箱登录
- 每日推荐自动刷新，搜索歌曲/歌手，加载收藏与歌单
- 标准 / 较高 / 极高 MP3 音质切换
- 歌曲列表真实专辑封面与播放页大封面
- 网易云式滚动歌词：原文、翻译、当前行高亮
- 顶部 HUD 灵动岛，支持原文/双语歌词模式
- 队列单首删除、一键清空、重启恢复
- 当前歌曲、队列、音质、歌词模式和登录态持久化
- 中文/英文快捷键名称

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.18.1+ |
| Fabric API | 0.141.6+1.21.11 |
| Java | 21 |

播放使用 `javax.sound.sampled`，需要包含 `java.desktop` 的完整 Java 运行时。官方启动器自带的 Microsoft/OpenJDK 通常可以直接使用。

## 安装

1. 安装 Minecraft 1.21.11、Fabric Loader 和 Fabric API。
2. 将构建出的 `CloudXMusic-<version>.jar` 放入实例的 `mods/` 目录。
3. 启动客户端，按 `H` 打开/关闭 CloudXMusic 界面。

## 构建

Windows：

```powershell
.\gradlew.bat :cloudxmusic-client:build
```

Linux/macOS（使用标准 Gradle Wrapper）：

```bash
./gradlew :cloudxmusic-client:build
```

产物位于：

```text
cloudxmusic-client/build/libs/CloudXMusic-<version>.jar
```

项目使用 Mojang 官方映射、Java 21 和 Fabric Loom。构建需要网络下载 Minecraft、Fabric 和编译依赖。

## 使用

按 `H` 打开界面。客户端命令以 `/cxm` 开头：

| 命令 | 说明 |
| --- | --- |
| `/cxm login qr` | 在游戏内显示网易云扫码登录二维码 |
| `/cxm login phone <手机号>` | 发送短信验证码 |
| `/cxm login code <验证码>` | 使用验证码完成登录 |
| `/cxm login phone <手机号> <密码>` | 手机号密码登录 |
| `/cxm refresh` | 手动刷新每日推荐 |
| `/cxm search <关键词>` | 搜索歌曲并显示在搜索页 |
| `/cxm fav` | 加载我喜欢的音乐 |
| `/cxm playlist <歌单 ID>` | 加载歌单 |
| `/cxm play <关键词>` | 搜索并播放第一首 |
| `/cxm quality [standard\|higher\|exhigh]` | 切换音质 |
| `/cxm lyric [off\|original\|bilingual]` | 切换灵动岛歌词模式 |
| `/cxm pause` | 播放/暂停 |
| `/cxm next` / `/cxm prev` | 下一首 / 上一首 |
| `/cxm stop` | 停止播放 |
| `/cxm status` | 查看登录、音质、歌词和播放状态 |
| `/cxm logout` | 退出网易云登录并停止播放 |

## 配置与隐私

配置保存在实例目录：

```text
config/cloudxmusic/
```

| 文件 | 内容 |
| --- | --- |
| `cookies.json` | 网易云登录 Cookie 与游客会话 Cookie |
| `netease.json` | 昵称、UID、音质与歌词偏好 |
| `state.json` | 当前歌曲与播放队列 |

- 账号 Cookie 只保存在本地客户端。
- 不会把 Cookie、密码或音频凭证发送到任何服务端。
- `cookies.json` 属于敏感文件，请勿上传、截图或分享。
- 退出登录会清理本地 Cookie。

## 已知限制

- 仅支持 MP3 三档音质；FLAC / Hi-Res 尚未接入。
- 网易云接口为第三方非官方接口，可能随服务端调整而失效。
- 当前只实现客户端；Folia 多人音乐房间和客户端/服务端协议尚未实现。
- 需要有效的网易云登录态才能访问每日推荐、收藏和会员歌曲。
- 部分受版权或账号权限限制的歌曲仍可能无法播放。

## 项目结构

```text
CloudXMusic/
├── cloudxmusic-client/
│   ├── src/main/java/dev/lzasn/cloudxmusic/client/
│   │   ├── audio/       MP3 流式播放
│   │   ├── command/     /cxm 客户端命令
│   │   ├── gui/         自绘界面、封面缓存、二维码界面
│   │   ├── hud/         顶部灵动岛
│   │   ├── lyric/       LRC 歌词解析
│   │   ├── netease/     登录、Cookie、网易云 API
│   │   ├── state/       播放状态与队列持久化
│   │   └── util/        异步与浏览器工具
│   └── src/main/resources/
├── libs/                JLayer 与 ZXing
├── tools/               资源/图集辅助工具
├── DEVELOPMENT_STATUS.md 开发交接记录
└── THIRD_PARTY_NOTICES.md 第三方组件声明
```

## 第三方组件

- [JLayer](https://repo1.maven.org/maven2/javazoom/jlayer/)：MP3 解码，LGPL-3.0-or-later
- [ZXing Core](https://github.com/zxing/zxing)：二维码编码，Apache-2.0

完整声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 开发状态

详细开发记录、接口处理和已知问题见 [DEVELOPMENT_STATUS.md](DEVELOPMENT_STATUS.md)。

## License

[MIT](LICENSE) © 2026 Lzasn
