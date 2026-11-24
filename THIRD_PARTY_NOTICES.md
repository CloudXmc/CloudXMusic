# 第三方组件声明 / Third-Party Notices

## 项目自身代码许可

CloudXMusic 的原创代码采用 **GNU GPL-3.0-only**，完整许可文本见 [LICENSE](LICENSE)。该许可不改变下列第三方组件各自的许可证；字体资源也继续遵循随文件提供的 SIL Open Font License 1.1。

`libs/cloudxmusic-protocol.jar` 由本项目 `music-protocol` 源码构建，属于 CloudXMusic 自有代码的运行包；它随客户端构建合并，但对应源码目录不进入远端仓库。

## 内置代码：JLayer (MP3 解码)

- 文件：`libs/jlayer-1.0.1.jar`，类被直接合并进产物 jar（`javazoom.jl.*`）
- 版本：1.0.1
- 作者：JavaZoom / Laurent B…（原作者 J. C. Beaulieu 等）
- 许可：**LGPL-3.0-or-later**
- 来源：<https://repo1.maven.org/maven2/javazoom/jlayer/>
- 说明：用于网易云与 QQ 音乐的 MP3 流实时解码为 PCM，未修改 JLayer 库本身。  
  按 LGPL 要求保留声明并提供源码获取途径（见上面的 Maven 链接与 javazoom 官网）。

## 内置代码：ZXing Core (二维码编码)

- 文件：`libs/zxing-core-3.5.3.jar`，类被合并进产物 jar（`com.google.zxing.*`）
- 版本：3.5.3
- 作者：ZXing Authors
- 许可：**Apache-2.0**
- 来源：<https://github.com/zxing/zxing>
- 说明：只用来把扫码登录链接编码成二维码矩阵，未做修改。

## FLAC 运行依赖：Project Nayuki FLAC library (Java)

- 文件：`libs/nayuki-flac-0.0.0-e807ab25.jar`，由 Loom 嵌套打包到 `META-INF/jars/`，不合并进现有播放器。
- 固定提交：`e807ab25e28a3759ac82e119a406490f8e336b9b`。
- Gradle 专用本地坐标：`dev.lzasn.local:nayuki-flac:0.0.0-e807ab25`，仅从本项目 `libs/` 解析，不是上游官方 Maven 发布。
- 作者：Project Nayuki。
- 许可：**LGPL-3.0-or-later**；依赖 JAR 内保留上游 `COPYING.txt` 和 `COPYING.LESSER.txt`。
- 来源：<https://github.com/nayuki/FLAC-library-Java/tree/e807ab25e28a3759ac82e119a406490f8e336b9b>
- 未修改的上游源码：`libs/nayuki-flac-e807ab25-sources.zip`，成品中同样提供于 `META-INF/licenses/nayuki-flac/`。
- 构建：使用 Java 21 编译该提交的 `src/io/`；仅打包库代码和许可证，不打包上游演示程序。
- 用途：供 `qqmusic/QQAudioDecoder` 和 `QQAudioPlayer` 顺序流式解码原生 FLAC；保留旧测试类作为手动实验入口，正式启动不再调用。

## 音乐服务协议与歌词格式参考

来源：[DangoMusic](https://github.com/Sorihiko/DangoMusic)，作者 Sorihiko 及贡献者，许可为 **GNU GPL v3**。参考范围集中列于下表，保留其作者、来源与许可信息。

参考版本：`7d1609d6b259a6b439542058b3f8f0c5969b6e2b`（酷狗接入、账号歌单与登录交互）；`42397a1c8b78138175cfba4781d35cc9ff8aca94`（QRC 解码与早期 QQ 目录）。文件名均指上游对应源码。

| 涉及范围              | 来源文件                                                                                   | 使用方式                                                                                                       |
| ----------------- | -------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 酷狗普通版登录、取链与 KRC   | `KugouWeb.kt`、`KugouPlayback.kt`、`KugouGateway.kt`、`KugouLyrics.kt`、`NativeLogin.kt`   | 参考 Web/App 请求、签名、扫码 token 交换、hash/album_id 与歌词编码，适配为本项目的 Java 接口。                                          |
| QQ 搜索、歌曲元数据、封面与取链 | `QqMusicSource.kt`、`SourceHttp.kt`、`QqPlayback.kt`                                     | 参考匿名搜索、字段回退与高亮清理、media_mid、封面/LRC 地址、UrlGetVkey/CgiGetVkey 与 purl/sip 解析。                                  |
| QQ 登录与续期          | `NativeLogin.kt`、`QqAppChannel.kt`、`QqAppSession.kt`、`NativeAccount.kt`、`QqRenewal.kt` | 参考 CreateQRCode、MQTT 5 登录订阅、票据验证/换票、Cookie/hash33 及续期字段。                                                   |
| 账号歌单、目录与酷狗发现页     | `QqCatalog.kt`、`KugouCatalog.kt`                                                       | 参考自建/收藏分类、加密 QQ 用户标识、推荐目录与 dirid=201、酷狗 H5 云目录、分页与排序，以及官网“新歌首发新”区块的 `dataobj` 元数据。                         |
| 登录交互              | `common/ui/.../LoginDialog.kt`                                                         | 参考交互流程；界面使用 CloudXMusic 的绘制组件。                                                                             |
| QRC 格式解码          | `QqLyrics.kt`                                                                          | 将 QQ 专用 DES 表、位序与密钥调度翻译为 `QQQrcCodec.java`，并参考逐字时间格式与歌词 XML 结构。此项属于代码移植，包含非标准 S 盒值与 PC2 位移，不能替换为标准 DESede。 |

CloudXMusic 负责 Java 21 网络适配、有界响应与超时、凭证存储、异步取消与代次隔离、分页缓存、完整队列提交及共享 UI 接线；QRC 移植另加有界解压、严格 UTF-8、取消检查与测试。酷狗流式播放器沿用本项目自有 QQ 播放引擎并保持包隔离；解码库的许可见对应条目。上述参考项目不作为运行库打包，也未接入概念版每日权益领取。第三方来源不表示获得音乐平台的官方授权。

## 酷狗资料、权益与云收藏协议参考：KuGouMusicApi

- 来源：<https://github.com/MakcRe/KuGouMusicApi> ，固定提交 `443d4a416dd344ccf7b022b9fb932e324afc77b4`，Copyright (c) 2023 MakcRe，MIT License。
- 参考 `module/user_detail.js`、`user_vip_detail.js`、`privilege_lite.js`、`user_playlist.js`、`playlist_tracks_add.js`、`playlist_tracks_del.js` 以及请求签名/加密协议字段。Java 实现不引入 Node.js 运行依赖，不复制按音质硬编码的会员映射或概念版免费听假设。
- MIT 原许可文本随 `assets/cloudxmusic/licenses/kugou-api/LICENSE` 及 `META-INF/licenses/kugou-api/LICENSE` 提供；服务响应仍是权限与资源可用性的依据。

## vendored 代码：MuoniumPlayer 世界歌词

- 来源：<https://github.com/zihe114514/MuoniumPlayer-multiversion> ，`main` 分支的 `1.21.11 recode`、`26.3` 与 `muonium-common`。
- 作者/版权：MuoniumPlayer Team（云杉、心事抱抱、N0ne、eyz2021）及贡献者 Chryui/Cherry7593。
- 许可：**Apache-2.0**，完整许可文本与 Skia/Skija NOTICE 保存在 `assets/cloudxmusic/licenses/muonium/`，同时复制到产物 `META-INF/licenses/muonium/`。
- vendored 范围：世界歌词状态机、公告牌渲染、逐字字形图集、发光/绘光/心象动效、字体渲染辅助层、Skija 桥接与两个 Mixin。相关文件位于 `com.muoniumplayer.*`，并按 Apache-2.0 §4(b) 在文件头注明来源及 CloudXMusic 修改。
- 本项目新增适配层：播放快照/逐字歌词转换、`HudConfig` 字段同步、简化字体管理和 Skija 原生库加载。未搬入 MuoniumPlayer 的音乐引擎、2D界面、声学、语音、Modern UI 等无关子系统。

## 嵌套运行依赖：Skija

- Maven 坐标：`io.github.humbleui:skija-windows-x64:0.143.17`，传递依赖 `skija-shared:0.143.17` 与 `types:0.2.0`。
- 作者/版权：JetBrains / HumbleUI 及 Skia 贡献者。
- 许可：Skija 为 **Apache-2.0**；Skia 的 BSD 风格许可与 NOTICE 随 MuoniumPlayer 许可资源一并提供。
- 打包方式：Loom 嵌套到产物 `META-INF/jars/`；Windows x64 平台包内含 `skija.dll` 与 `icudtl.dat`。
- 用途：为世界歌词逐字字形图集提供高质量抗锯齿、字体整形和 Gaussian glow 烘焙；不用于游戏主界面。

## 界面资源

FLAC 测试依赖位于 `META-INF/jars/`；界面随模组提供下列开源字体。

### Inter

- 文件：`assets/cloudxmusic/fonts/Inter.ttf`
- 作者：Rasmus Andersson / Inter Project Authors
- 许可：SIL Open Font License 1.1
- 声明：`assets/cloudxmusic/fonts/Inter-OFL.txt`
- 来源：<https://github.com/google/fonts/tree/main/ofl/inter>

### Noto Sans SC

- 文件：`assets/cloudxmusic/fonts/NotoSansSC-Regular.otf`
- 作者：Google / Noto CJK Project Authors
- 许可：SIL Open Font License 1.1
- 声明：`assets/cloudxmusic/fonts/NotoSansSC-OFL.txt`
- 来源：<https://github.com/notofonts/noto-cjk/tree/main/Sans/SubsetOTF/SC>

### Noto Sans CJK KR（日韩及扩展 CJK 字形回退）

- 文件：`assets/cloudxmusic/fonts/NotoSansCJKkr-Regular.otf`
- 作者：Adobe / Google / Noto CJK Project Authors。
- 许可：SIL Open Font License 1.1，未修改原字体。
- 声明：`assets/cloudxmusic/fonts/NotoSansCJK-OFL.txt`
- 固定提交：`f8d157532fbfaeda587e826d4cd5b21a49186f7c`。
- 来源：<https://github.com/notofonts/noto-cjk/blob/f8d157532fbfaeda587e826d4cd5b21a49186f7c/Sans/OTF/Korean/NotoSansCJKkr-Regular.otf>
- 用途：当 Inter 或 Noto Sans SC 不含目标 Unicode 字形时，供同一套 UI 测宽与绘制回退，包含韩文 Hangul 和日文假名；无需玩家安装字体或联网下载字体。

这些字体只用于界面文字排版。普通界面图标由 `gui/design/UiIcons.java` 使用矢量路径绘制；网易云音乐与 QQ 音乐品牌图标使用随模组打包的 PNG 资源。旧版本曾使用原版符号字形。

### 音乐平台品牌图标

- 文件：`assets/cloudxmusic/textures/gui/netease-cloud-music.png`、`assets/cloudxmusic/textures/gui/qq-music.png`。
- SVG 源文件：`assets/cloudxmusic/textures/gui/sources/netease-cloud-music.svg`、`assets/cloudxmusic/textures/gui/sources/qq-music.svg`。
- 用途：仅在房间音源选择等界面中标识对应音乐平台，不表示与平台存在隶属、授权或背书关系。
- 网易云音乐、QQ 音乐名称、标识及相关商标权利归各自权利人所有。

`assets/cloudxmusic/textures/gui/author.png` 是项目开发者 Lzasn 提供的个人头像，仅用于模组「关于」页面。

## 品牌

- 英文名：CloudXMusic
- 中文名：云朵音乐盒
- 开发者：Lzasn,xWtree,xiaota888



## QQ 会员资料接口契约参考

- 接口名称与响应字段参考 QQMusicApi 的公开文档/模型说明（<https://github.com/l-1124/QQMusicApi，PyPI> qqmusic-api-python 0.8.2）。
- 仅核对 `VipLogin.VipLoginInter / vip_login_base`、`svip`、`identity.vip`、`identity.HugeVip` 与 `identity.level` 字段；未复制其 Python 实现或加入运行依赖。Java 请求和隔离展示逻辑由本项目实现。

## 2.5.0-alpha.2：QQ 母带、收藏与排行榜协议参考

- QQMusicApi（<https://github.com/l-1124/QQMusicApi，GPL-3.0），参考> `qqmusic_api/modules/song.py`、`songlist.py`、`top.py` 的公开协议字段：母带可解码 FLAC 文件前缀 `AI00`，`AddSonglist` / `DelSonglist` 的真实 songId 与 dirId=201，以及 `music.musicToplist.Toplist` 的 `GetAll` / `GetDetail`。
- 本项目独立实现 Java 请求、格式探测与降级、账号/音源代次隔离、固定期次分页及测试；未复制 Python 运行代码、未加入 Python 依赖。母带实际可用性受账号权益、歌曲及服务端返回限制，不把其他编码格式标记为已支持。
- QQ 收藏写入已因腾讯风控停用；当前只保留 `GetPlaylistByUin` / `CgiGetDiss` 的“我的收藏”读取。

## 其他 QQ 推荐接口与测试样本

- 历史参考（2.13.0–2.13.4，2.13.5 已移除）：个性电台接口契约曾参考 luren-dc/QQMusicApi 的 `qqmusic_api/modules/recommend.py`、`models/recommend.py`（GPL-3.0）：<https://github.com/luren-dc/QQMusicApi> 。保留来源记录；当前源码与成品已无相关接口实现，未加入 Python 或 Node.js 运行依赖。
- 每日30首/今日私享资源发现参考 jsososo/QQMusicApi 的 `routes/recommend.js` 中 `/daily` 对官方 `https://c.y.qq.com/node/musicmac/v6/index.html` 登录态页面的读取方式：<https://github.com/jsososo/QQMusicApi/blob/master/routes/recommend.js> 。Java 使用 JDK HTML 解析器，只读取明确标记的每日资源，不复制该 JavaScript 实现或引入其运行依赖。
- `src/test/resources/qq-qrc-public.hex` 是用于验证解密协议的 QQ 公网返回测试样本，歌曲 MID `0039MnYb0qxYhV`，2026-10-30 获取，仅在测试源码中提供，不打入运行 JAR；不含账号、Cookie 或 Token。歌词内容的著作权归相应权利人。
