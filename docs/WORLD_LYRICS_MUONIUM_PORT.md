# 交接文档：移植 MuoniumPlayer 世界歌词（可直接执行的完整施工图）

> **交接对象**：下一个执行本任务的 agent。
> **任务**：把 MuoniumPlayer 的 3D 世界歌词子系统完整移植进本项目，效果对齐原作。
> **授权**：已核实，MuoniumPlayer 为 **Apache License 2.0**（`1.21.11 recode/LICENSE.txt`，
> 版权 2026 MuoniumPlayer Team：云杉、心事抱抱、N0ne、eyz2021；贡献者 Chryui/Cherry7593）。
> 与本项目 GPL-3.0-only 兼容，**可以复制代码**。义务见 §8。
> **上游仓库**：https://github.com/zihe114514/MuoniumPlayer-multiversion （main 分支）
> 原始下载基址：`https://raw.githubusercontent.com/zihe114514/MuoniumPlayer-multiversion/main/`

---

## 0. 当前仓库状态（动手前必读）

> **2026-11-10 执行状态：移植代码已落地并启用。** 世界歌词 Mixin、Skija 字形图集、
> 发光/绘光/心象动效和播放快照适配层已接入；旧 `client/world/` 自研实现已删除。
> 设置页已接入布局、翻译、发光和动效参数。Skija 原生加载与字体冒烟测试、客户端
> 全量测试、构建和 `renderUiPreview` 均通过；尚待实机视觉验收。

- 本项目已有一个**自研逐字版**渲染器，已可用：`client/src/main/java/dev/lzasn/cloudxmusic/client/world/`
  （`WorldLyricRenderer.java` 逐字 pop 版、`WorldLyricSurface.java` 整行画布 + 逐字图集、
  `WorldLyricTexture.java` DynamicTexture 桥接）。
  该旧实现已删除，由 vendored 代码接管；`MusicState` 字段与设置页保留，并扩展为
  `WorldLyricConfig` 数据源（见 §5）。
- 定位模型结论（已实测核对参考源码）：参考实现的 `keepDistance`（方位角锁死 +
  平移守恒）在屏幕上的表现是**完全静止**——转头不动、走路不动、大小恒定。
  我们此前自创的"世界投影/弹簧/曲线/贴边钳位"全部是多余的，已被用户否决，**不要复活**。

## 1. 要搬运的文件（32 个，实测闭包，全部 mojmap 1.21.11）

来源缩写：`C` = `muonium-common/src/main/java/com/muoniumplayer/`，
`R` = `1.21.11 recode/src/main/java/com/muoniumplayer/`，
`RF` = `1.21.11 recode/fabric/src/main/java/com/muoniumplayer/`。

| # | 目标包（保持原名） | 文件 | 来源 |
|---|---|---|---|
| 1 | core.lyrics3d | WorldLyricsRenderer.java (1156行) | `C/core/lyrics3d/` |
| 2 | core.lyrics3d | WorldLyricsState.java (2723行) | 同上 |
| 3 | core.lyrics3d | WorldLyricsSettings.java | 同上 |
| 4 | core.lyrics3d | SplitLines.java / LyricEffectsMath.java / CadenzaMotion.java / HuiguangMotion.java / HuiguangTrailCanvas.java / RowGlowSprite.java / LyricGlyphAtlas.java | 同上 |
| 5 | compat.mc | WorldLyricsBridge.java (488行) | `R/compat/mc/` |
| 6 | compat.mc | WorldBillboard.java (1088行) | 同上 |
| 7 | compat.mc | LyricGlyphAtlasBridge.java / CameraAccess.java / ShaderPackProbe.java | 同上 |
| 8 | mixin | RenderTypeAccessors.java / WorldLyricsRenderTailMixin.java | `RF/mixin/` |
| 9 | rendering.font | TextGlow.java / CFontRenderer.java (56K) / GlyphInk.java / GlyphInkMetrics.java / TextBlobCache.java / FontKerning.java | `C/core/rendering/font/` |
| 10 | management | FontManager.java / UiFont.java / UiFontLibrary.java / FontAssetDownload.java / AbstractManager.java | `C/core/management/` |
| 11 | render.skija | SkijaNatives.java / SkijaGfx.java / SkijaText.java / SkijaHost.java | `C/render/skija/` 与 `R/render/skija/` |
| 12 | utils.* | math/Mth.java、other/StringUtils.java、other/multithreading/MultiThreadingUtil.java、network/HttpDownloadRace.java、logging/LogManager.java、logging/Logger.java | `C/core/utils/` |
| 13 | rendering / interfaces | RGBA.java、rendersystem/RenderSystem.java、interfaces/SharedConstants.java | `C/core/rendering/` 等 |
| 14 | settings | ConfigPaths.java、JsonConfigStorage.java | `C/core/settings/` |

**不要搬**：`CloudMusic.java`（352KB，含整个音乐引擎）、`Music.java`（96KB）、
`HudConfig.java`（88KB）、`HudSettingsModel.java`、`core/screens/ncm/LyricLine.java`
（263 行，带 2D 界面的弹簧动画依赖）——这五个写适配层，见 §5。

## 2. 需要新写的适配层（放 `com.muoniumplayer.*` 原包名下，vendored 代码零改动）

### 2.1 `core.ncm.music.CloudMusic`（shim）
```java
public final class CloudMusic {
    public static java.util.List<com.muoniumplayer.core.screens.ncm.LyricLine> lyrics;
    public static String lyricsTrackKey;          // 用我们歌曲的 platformId
    public static long lyricsRevision;            // 换歌/歌词刷新时 ++
    public static boolean playing;
    public static long positionMs;
    public static class PlaybackSnapshot { public boolean playing; public long positionMs; }
    public static PlaybackSnapshot getSnapshot() { ... }
    public static boolean lyricsMatchCurrentTrack() { return lyricsTrackKey != null && !lyrics.isEmpty(); }
    public static String getSecondaryLyrics(LyricLine line) { return ""; }  // 译文副行已按需求移除
}
```
喂入点：`CloudXMusicClient` 的 `ClientTickEvents.END_CLIENT_TICK` 里，从
`QQMusicIntegration.snapshot()` 转换（`snap.lyrics()` → LyricLine 列表；
`snap.timedLyrics()` 供逐字轴，见 §2.3）。

### 2.2 `core.screens.ncm.LyricLine`（shim）
vendored 代码读取的成员（实测 grep）：`timestamp`、`lyric`（文本）、`.words`。
先 `grep -n "\.words\|\.lyric\|\.timestamp" WorldLyricsState.java` 核对完整字段面；
`.words` 的元素类型按 `WorldLyricsState` 实际引用补（其 `core/music/source/LyricWord.java`
可一并下载，字段 `startMs/durationMs/text` 与我们 `TimedLyrics.Word` 一一对应）。

### 2.3 逐字轴（QRC）
`WorldLyricsState` 的字级时刻来自 `LyricLine.words`。把我们的
`snap.timedLyrics()`（`TimedLyrics.Word{startMs,durationMs,text}`）映射进去：
每词按字符数线性内插出字时刻——参考实现同款内插已在我们
`WorldLyricRenderer.charAppearTimes()` 里实现过，可直接参照。

### 2.4 `core.settings.HudConfig`（shim）
只暴露 vendored 代码读的 34 个静态字段（清单 = `WorldLyricsSettings.buildFromConfig`
里读的每一个 `HudConfig.worldLyrics*`，类型对照该文件）。字段由 `MusicState` 同步：
- `worldLyricsEnabled` ← `state.worldLyrics()`
- `worldLyricsDistance` ← `state.worldLyricDistance()`（clamp 3~48，默认 10.5）
- `worldLyricsScale` ← `state.worldLyricScale()`（clamp 0.4~6）
- `worldLyricsSpreadDeg` = 30.0、`worldLyricsKeepDistance` = true、`worldLyricsFacePlayer` = true
- 其余（glow/translation/cover/wordPop/huiguang/cadenza/hudDraw/stayOnScreen…）
  按参考默认值给：`glowEnabled=true, glowStrength=0.35, glowSpread=1.3,
  styleModern=true, splitLines=true, translation=0`（译文已移除）、其余 false/0。

### 2.5 `core.screens.hud.HudSettingsModel`（shim）
只一个方法：`public static void syncWorldLyricsQuality() {}`（no-op 即可）。

### 2.6 `core.utils.logging.LogManager / Logger`
可直接搬（133 + 589 行，自包含，已确认无外部依赖）；或 slf4j 转发。

## 3. 构建改动

```gradle
// build.gradle dependencies 里追加
dependencies {
    modImplementation("io.github.humbleui:skija-windows-x64:0.143.17")   // Apache-2.0
    // 上线发布再改 include（嵌套打包，jar +15~20MB）：
    // include("io.github.humbleui:skija-windows-x64:0.143.17")
    compileOnly 'org.projectlombok:lombok:1.18.34'
    annotationProcessor 'org.projectlombok:lombok:1.18.34'
}
```
新依赖解析需要联网：去掉 `--offline` 跑一次（腾讯镜像 + mavenCentral 都有）。

**Mixin 配置**（本项目此前没有 mixin 体系）：
新建 `src/main/resources/muonium-worldlyrics.mixins.json`：
```json
{
  "required": true,
  "package": "com.muoniumplayer.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": ["RenderTypeAccessors", "WorldLyricsRenderTailMixin"],
  "injectors": { "defaultRequire": 1 }
}
```
`fabric.mod.json` 加 `"mixins": ["muonium-worldlyrics.mixins.json"]`。

## 4. 接线

1. `CloudXMusicClient.onInitializeClient()`：
   `WorldLyricsBridge.install()`（装配字形图集宿主）；
   `HudRenderCallback` 里在 `state.worldLyrics()` 时调 `WorldLyricsBridge.hudPhaseDraw()`
   （光影包激活时的"更激进适配"路径）。
2. 世界阶段由 `WorldLyricsRenderTailMixin`（`LevelRenderer#renderLevel` TAIL）调
   `WorldLyricsBridge.worldRenderEnd()`——**这个 mixin 是可见性的关键**：
   fabric 的 `WorldRenderEvents.END_MAIN` 画的东西会被云/后期链盖住，本项目此前
   "顶点提交成功但看不见"极可能是这个原因。
3. 我们的 `WorldLyricRenderer` 不再注册（验证通过后删除 `client/world/` 三文件）。
4. 数据喂入见 §2.1。

## 5. 编译环境

- `JAVA_HOME=D:/minecraft/meta/java_versions/mojang-java-runtime-delta-windows-x64-cb4394a27089`（JDK 21）
- 构建：`./gradlew :cloudxmusic-client:build --offline`（引入新依赖那次去掉 --offline）
- 安装（用户已授权覆盖、不备份）：
  `cp cloudxmusic-client/build/libs/CloudXMusic-2.6.0-alpha.1.jar "D:/minecraft/profiles/生电客户端 Fabric 1.21.11/mods/"`
- 离线 UI 预览：`./gradlew :cloudxmusic-client:renderUiPreview`
- 预期编译错误量：20~60 个（Lombok 差异、`LyricLine.words` 字段面、少量映射差异），逐个修。

## 6. 已核实的技术事实（省你重新踩坑）

- 参考实现绘制时机 = Mixin 注入 `renderLevel` TAIL；`END_MAIN` 会被云盖住。
- 公告牌矩阵：`translate(rel) → rotateY(-yaw) → rotateX(pitch) → scale(-s,-s,s)`，
  模型视图栈 push/identity/pop，相机转向自算 `rotateX(pitch)·rotateY(yaw+180)`。
- 光影兼容：`ShaderPackProbe` 激活走信标全亮程序；`hudDraw` 开时捕获关卡透视切片
  推迟到 HUD 阶段直画，连续 12 帧失败回退世界阶段。
- 定位：`keepDistance`（锚点随人平移、偏移向量守恒、径向恒定 ±0.5 死区）、
  `spreadDeg=30` 锥体随机、`facePlayer`、`aboveGround`（不低于眼下 0.5）。
- 逐字：QRC 按词内插到字级；LRC `chars×55ms`；±20ms 抖动；入场 320ms easeOutBack
  （0.6→过冲→1.0）+ 下方 8px（字号 10 口径）；wordPop 160ms `1+0.55×(1-t)²`。
- Skija 缺位时参考实现自动回退原版像素字（`styleModern=false`）——调试期可先
  关 modern 验证链路，再开 Skija。
- `Camera.position()` 在 1.21.11 存在（本 session 编译验证过）。
- 本项目没有 mixin 体系，需要从零加（见 §3）。

## 7. 验收矩阵（实机）

无光影 / Iris 开 / `hudDraw` 开 三态 × 窗口缩放 × F1 × 暂停菜单 × 走路/划船/跳跃；
QRC（QQ）与 LRC（网易）两种歌词；换句淡出、逐字 pop、翻译副行（当前关闭）。

## 8. 许可义务（Apache-2.0，不可省略）

1. 把上游 `1.21.11 recode/LICENSE.txt` 与 `licenses/`（Skia/Skija/NOTICE）复制进本项目资源。
2. `THIRD_PARTY_NOTICES.md` 增补：MuoniumPlayer（Apache-2.0，附版权与作者署名）。
3. 每个搬运文件顶部加注：来源仓库 + "Modified by CloudXMusic project"（Apache-2.0 §4b）。
4. 用户已声明会自行与原作者确认授权（2026-11-09），文件头部保留出处即满足署名要求。

## 9. 明确不搬 / 已放弃

- `CloudMusic.java` 全量（352KB 音乐引擎）、`Music.java`（96KB）→ shim。
- 加载器其他子系统（party/voicechat/rhino）。
- `external/modernui/`（Modern UI 集成，与歌词无关）。
