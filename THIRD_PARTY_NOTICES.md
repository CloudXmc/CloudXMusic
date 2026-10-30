# 第三方组件声明 / Third-Party Notices

## 项目自身代码许可

CloudXMusic 的原创代码采用 **GNU GPL-3.0-only**，完整许可文本见 [LICENSE](LICENSE)。该许可不改变下列第三方组件各自的许可证；字体资源也继续遵循随文件提供的 SIL Open Font License 1.1。

## 当前状态：内置 MP3 解码、二维码库与嵌套 FLAC 解码库

## 内置代码：JLayer (MP3 解码)

- 文件：`libs/jlayer-1.0.1.jar`，类被直接合并进产物 jar（`javazoom.jl.*`）
- 版本：1.0.1
- 作者：JavaZoom / Laurent B…（原作者 J. C. Beaulieu 等）
- 许可：**LGPL-3.0-or-later**
- 来源：https://repo1.maven.org/maven2/javazoom/jlayer/
- 说明：用于网易云与 QQ 音乐的 MP3 流实时解码为 PCM，未修改 JLayer 库本身。
  按 LGPL 要求保留声明并提供源码获取途径（见上面的 Maven 链接与 javazoom 官网）。

## 内置代码：ZXing Core (二维码编码)

- 文件：`libs/zxing-core-3.5.3.jar`，类被合并进产物 jar（`com.google.zxing.*`）
- 版本：3.5.3
- 作者：ZXing Authors
- 许可：**Apache-2.0**
- 来源：https://github.com/zxing/zxing
- 说明：只用来把扫码登录链接编码成二维码矩阵，未做修改。

## FLAC 运行依赖：Project Nayuki FLAC library (Java)

- 文件：`libs/nayuki-flac-0.0.0-e807ab25.jar`，由 Loom 嵌套打包到 `META-INF/jars/`，不合并进现有播放器。
- 固定提交：`e807ab25e28a3759ac82e119a406490f8e336b9b`。
- Gradle 专用本地坐标：`dev.lzasn.local:nayuki-flac:0.0.0-e807ab25`，仅从本项目 `libs/` 解析，不是上游官方 Maven 发布。
- 作者：Project Nayuki。
- 许可：**LGPL-3.0-or-later**；依赖 JAR 内保留上游 `COPYING.txt` 和 `COPYING.LESSER.txt`。
- 来源：https://github.com/nayuki/FLAC-library-Java/tree/e807ab25e28a3759ac82e119a406490f8e336b9b
- 未修改的上游源码：`libs/nayuki-flac-e807ab25-sources.zip`，成品中同样提供于 `META-INF/licenses/nayuki-flac/`。
- 构建：使用 Java 21 编译该提交的 `src/io/`；仅打包库代码和许可证，不打包上游演示程序。
- 用途：供 `qqmusic/QQAudioDecoder` 和 `QQAudioPlayer` 顺序流式解码原生 FLAC；保留旧测试类作为手动实验入口，正式启动不再调用。

## 参考实现：DangoMusic QQ 匿名搜索

- 参考本地 DangoMusic 的 `common/core/src/main/kotlin/me/sorihiko/dango/music/QqMusicSource.kt` 与 `SourceHttp.kt`。
- 本地参考版本许可证为 GNU GPL v3，保留其项目名称和来源说明；未将 DangoMusic 作为运行依赖打包。
- 参考范围：`client_search_cp` 无 sign 请求、MID/标题/歌手字段回退及搜索高亮清理；Java 超时、分页、响应限制和界面整合由本项目实现。
- QQ 公网接口和网易云接口是独立远程服务；此说明不表示获得这些服务的官方授权。

## 参考实现：DangoMusic QQ App 扫码登录

- 参考本地 DangoMusic 的 `common/core/src/main/kotlin/me/sorihiko/dango/music/NativeLogin.kt`、`QqAppChannel.kt`、`QqAppSession.kt` 和 `NativeAccount.kt`。
- 原项目许可为 GPL-3.0，本项目保留其来源及许可说明；未作为运行依赖打包。
- 参考范围：CreateQRCode 参数、MQTT 5 登录订阅与状态、候选票据验证/换票顺序、QQ Cookie 与 hash33。
- Java 21 HttpClient/WebSocket 传输、凭证权限与原子存储、会话取消和 CloudXMusic 界面接线由本项目实现。
- 此阶段使用服务端返回的二维码图片，不增加二维码或网络依赖；不实现 QQ 音乐播放地址。

## 参考实现：DangoMusic QQ 元数据与取链

- 参考本地 DangoMusic 的 `common/core/src/main/kotlin/me/sorihiko/dango/music/QqPlayback.kt` 与 `QqMusicSource.kt`，保留 GPL-3.0 来源说明，不作为运行依赖打包。
- 参考范围：get_song_detail_yqq 的 media_mid 补齐、桌面 UrlGetVkey/Web CgiGetVkey 参数、purl/sip 解析及无资源时的只读账号校验。
- 本项目新增 F000 优先、M800 降级、账号代次隔离、URL 域名/文件匹配和无凭证 Range 音频头检查；不实现 QQ 解码、播放或队列接线。
- 不增加依赖，不自动安装 Node.js 或外部 API 服务，不向音频 CDN 发送 QQ 登录 Cookie。

## 界面资源

FLAC 测试依赖位于 `META-INF/jars/`；界面随模组提供下列开源字体。

### Inter

- 文件：`assets/cloudxmusic/fonts/Inter.ttf`
- 作者：Rasmus Andersson / Inter Project Authors
- 许可：SIL Open Font License 1.1
- 声明：`assets/cloudxmusic/fonts/Inter-OFL.txt`
- 来源：https://github.com/google/fonts/tree/main/ofl/inter

### Noto Sans SC

- 文件：`assets/cloudxmusic/fonts/NotoSansSC-Regular.otf`
- 作者：Google / Noto CJK Project Authors
- 许可：SIL Open Font License 1.1
- 声明：`assets/cloudxmusic/fonts/NotoSansSC-OFL.txt`
- 来源：https://github.com/notofonts/noto-cjk/tree/main/Sans/SubsetOTF/SC

### Noto Sans CJK KR（日韩及扩展 CJK 字形回退）

- 文件：`assets/cloudxmusic/fonts/NotoSansCJKkr-Regular.otf`
- 作者：Adobe / Google / Noto CJK Project Authors。
- 许可：SIL Open Font License 1.1，未修改原字体。
- 声明：`assets/cloudxmusic/fonts/NotoSansCJK-OFL.txt`
- 固定提交：`f8d157532fbfaeda587e826d4cd5b21a49186f7c`。
- 来源：https://github.com/notofonts/noto-cjk/blob/f8d157532fbfaeda587e826d4cd5b21a49186f7c/Sans/OTF/Korean/NotoSansCJKkr-Regular.otf
- 用途：当 Inter 或 Noto Sans SC 不含目标 Unicode 字形时，供同一套 UI 测宽与绘制回退，包含韩文 Hangul 和日文假名；无需玩家安装字体或联网下载字体。

这些字体只用于界面文字排版，图标由模组代码的矢量路径绘制。

`assets/cloudxmusic/textures/gui/author.png` 是项目开发者 Lzasn 提供的个人头像，仅用于模组「关于」页面。

当前界面图标由 `gui/design/UiIcons.java` 使用矢量路径绘制，不依赖 Minecraft 位图字形或第三方图标集。旧版本曾使用原版符号字形。

## 品牌

- 英文名：CloudXMusic
- 中文名：云朵音乐盒
- 开发者：Lzasn,xWtree,xiaota888

## 历史：已移除的内置组件

| 时期 | 组件 | 现状 |
| --- | --- | --- |
| 0.1.8 ~ 0.2.3 | Modern UI (mVUS fork, LGPL-3.0) + Forge Config API Port (LGPL-3.0)，jar-in-jar | 0.2.4 起移除，`libs/nested/` 下残留的 jar 不参与构建，可删 |
| 0.2.5 ~ 0.2.8 | Microsoft Fluent Emoji 3D PNG（MIT），烘焙成 20px emoji 图集 | 0.2.9 起移除。生成工具保留在 `tools/EmojiAtlas.java`，需要时可重新生成 |
| 2.4.10 | jFLAC（LGPL-2.1），FLAC 无损解码 | 已移除。无损档位随之下线：FLAC 是 VBR，seek 只能从头逐帧解码，跳转代价过高。`libs/jflac-codec-1.5.2.jar` 已删除 |

## 参考实现：DangoMusic QQ 封面与逐行歌词（2.9.0）

- 参考 `common/core/src/main/kotlin/me/sorihiko/dango/music/QqMusicSource.kt` 的专辑 MID 封面地址和公网 LRC 歌词接口，来源许可 GPL-3.0，保留上述项目来源说明。
- 本项目以 Java 21 实现有界 HTTP 响应、超时、HTML/Base64 歌词解析、独立展示快照、图片缓存及异步代次隔离。2.13.0 的 QRC 解密移植另见下节；未引入 DangoMusic 运行库或其网易云业务数据模型。
- 不新增运行依赖；Nayuki、JLayer 和 ZXing 沿用现有声明与打包方式。

## QQ 会员资料接口契约参考

- 接口名称与响应字段参考 QQMusicApi 的公开文档/模型说明（https://github.com/l-1124/QQMusicApi，PyPI qqmusic-api-python 0.8.2）。
- 仅核对 `VipLogin.VipLoginInter / vip_login_base`、`svip`、`identity.vip`、`identity.HugeVip` 与 `identity.level` 字段；未复制其 Python 实现或加入运行依赖。Java 请求和隔离展示逻辑由本项目实现。

## 2.13.0：QRC 解码与目录接口参考

- DangoMusic，Sorihiko 及贡献者，GPL-3.0；本项目遵守原 GPL-3.0-only 许可，保留原项目来源。固定参考提交 `42397a1c8b78138175cfba4781d35cc9ff8aca94`：https://github.com/Sorihiko/DangoMusic/tree/42397a1c8b78138175cfba4781d35cc9ff8aca94 。
- `QQQrcCodec.java` 由 `common/core/src/main/kotlin/me/sorihiko/dango/music/QqLyrics.kt` 的 QQ 专用 DES 表、位序及密钥调度翻译为 Java；包含 QQ 对标准 DES 的非标准 S 盒值和 PC2 位移，不能替换为标准 DESede。词后绝对时间格式及歌词 XML 结构参考同文件，增加有界解压、严格 UTF-8、取消检查及独立测试。
- `QQCatalogApi.java` 的推荐歌单与私人 dirid=201 取歌契约参考同目录 `QqCatalog.kt`；Java 的异步会话、分页、缓存与 UI 单独实现。
- 历史参考（2.13.0–2.13.4，2.13.5 已移除）：个性电台接口契约曾参考 luren-dc/QQMusicApi 的 `qqmusic_api/modules/recommend.py`、`models/recommend.py`（GPL-3.0）：https://github.com/luren-dc/QQMusicApi 。保留来源记录；当前源码与成品已无相关接口实现，未加入 Python 或 Node.js 运行依赖。
- 每日30首/今日私享资源发现参考 jsososo/QQMusicApi 的 `routes/recommend.js` 中 `/daily` 对官方 `https://c.y.qq.com/node/musicmac/v6/index.html` 登录态页面的读取方式：https://github.com/jsososo/QQMusicApi/blob/master/routes/recommend.js 。Java 使用 JDK HTML 解析器，只读取明确标记的每日资源，不复制该 JavaScript 实现或引入其运行依赖。
- `src/test/resources/qq-qrc-public.hex` 是用于验证解密协议的 QQ 公网返回测试样本，歌曲 MID `0039MnYb0qxYhV`，2026-10-30 获取，仅在测试源码中提供，不打入运行 JAR；不含账号、Cookie 或 Token。歌词内容的著作权归相应权利人。
