# CloudXMusic（云朵音乐盒）开发交接文档

更新时间：2026-10-29
开发者：Lzasn,xWtree,xiaota888
当前版本：2.4.10
目标环境：Minecraft 1.21.11、Fabric 客户端、Java 21

## 1. 项目定位

这是一个 Minecraft Fabric 客户端音乐播放器 Mod，后续可配合 Folia 1.21.11 服务端插件，实现：

- Minecraft 内的 ClickGUI 音乐界面
- 顶部 HUD 灵动岛
- 网易云账号接入
- 本地音乐播放
- 多人音乐房间
- 房间内播放状态同步
- 低网络开销的播放进度校准

当前仓库只完成了客户端原型，Folia 服务端和客户端网络协议尚未实现。

## 2. 当前实际完成情况

### 2.4.10 当前版本（2026-10-29）

- 明暗主题统一由 `FrostCanvas.setDark(boolean)` 管理，账号页独立纹理缓存键包含 `darkMode`，切换主题会立即重绘。
- 设置页按「灵动岛 / 界面 / 关于」分组并支持滚动；关于页使用本地打包的 GitHub 公开头像生成开发者列表。
- 开发者卡片使用左侧直角、右侧圆角的轮廓，粉色强调线从顶到底拉直，未添加额外阴影。
- 灵动岛位置调整改由透明 `IslandPositionScreen` 接管鼠标，HUD 只负责绘制遮罩和提示。
- `IslandPositionScreen.renderBackground(...)` 为空实现，避免原版菜单模糊把 HUD 整帧糊住；拖动位置在 `removed()` 时持久化。
- 退出登录顺序为 `NeteaseSession.logout()` → `MusicState.clearAccountData()`。
- `clearAccountData()` 会停止播放器，清空每日推荐、收藏列表、收藏 ID 缓存、收藏变更和播放队列，将当前歌曲恢复为 `NOTHING` 后写入 `state.json`。
- `startNetease()` 在异步回调中检查当前歌曲，退出登录或切歌后旧请求不会启动播放。
- `refreshLibrary()`、`loadPlaylist()`、`loadFavorites()` 的异步回调会检查登录态，避免退出后旧响应重新填充列表。
- 暗黑模式下账号登录方式卡片使用主题色底、边框和文字，禁用项单独降低对比度。
- `:cloudxmusic-client:build`、`:cloudxmusic-client:test` 和 `:cloudxmusic-client:renderUiPreview` 已通过；离线预览包含设置页和账号页的日间/暗黑版本。

#### 2.4.10 追加（品牌、导航与动效）

- 品牌统一为 `CloudXMusic`（大写 M）：`fabric.mod.json`、语言文件、免责声明、README/文档全部同步；`archives_base_name` 与 `rootProject.name` 一并改为大写，产物名变为 `CloudXMusic-<version>.jar`。mod id `cloudxmusic`、包名 `dev.lzasn.cloudxmusic`、资源路径 `assets/cloudxmusic/` 保持不动。
- `MusicView.Page` 在 `QUEUE` 之后新增 `ROOM`（音乐房间），导航 index 5，settings 的命中 index 顺延为 6。该页当前是占位页，`MusicLayout` 对它不注册任何行命中区，避免点击空白取到空 `rows` 越界。
- 导航区抽出 `NAV_TOP/NAV_BOTTOM/NAV_H/NAV_STEP/NAV_COUNT` 常量并支持 `navScroll`；账号组整体下移 42px（分隔线 412、标签 437、卡片 442）。当前 6 项刚好放下，`navMaxScroll()` 为 0，加项后才会真正滚动。
- 主面板升降：`MusicScreen` 用 `openAt/closeAt/closing/closeDone` + `slideProgress()`，420ms、四次方缓出，开与关共用同一条曲线（关闭取反）。切页不再缩放整块面板——之前那版会让点导航时整窗跳一下。
- 灵动岛过渡独立于面板：`TRANSITION_NANOS = 450ms`、二次方缓出。中部（`|x-0.5|≤0.22 && |y-0.5|≤0.22`）走缩放，其余朝最近边缘飞入飞出，位移距离按岛到该边缘的实际距离算。
- 关闭键改为 `KeyMapping.matches(KeyEvent)` 判断，左下角提示通过 `openKeyLabel()` 读取 `getTranslatedKeyMessage()`；`MusicView` 末尾新增 `openKey` 字段承载该文案。
- `MusicScreen.renderBackground(...)` 覆写为空实现：原版默认实现会额外铺一层固定透明度的模糊+暗色，导致面板关闭时遮罩「慢半拍」。

### 发布流程

发版分两次推送，避免 main 与 tag 两条流水线并行：

1. 更新 `gradle.properties` 的 `mod_version` 与 `CHANGELOG.md` 中 `## <version>` 条目（CI 会校验两者必须一致）。
2. `git push origin main` —— 不触发构建（`build.yml` 只监听 tag 与 PR）。
3. `git tag v<version> && git push origin v<version>` —— 触发构建并发布 Release，正文由 CHANGELOG 中该版本的段落自动抽取。

注意：tag 名必须与 `mod_version` 完全对应，否则 `Validate changelog entry` 步骤会失败。

### 已完成

- 从零建立 Fabric 客户端 Gradle 工程。
- Minecraft 版本配置为 `1.21.11`。
- 使用 Java 21。
- 使用官方 Mojang mappings：`loom.officialMojangMappings()`。
- 使用 Fabric Loom `1.17.21`。
- 使用 Fabric Loader `0.20.1`。
- 使用 Fabric API `0.141.6+1.21.11`。
- 开发者信息：`Lzasn,xWtree,xiaota888`。
- 注册默认快捷键 `H`。
- `H` 的职责是打开/关闭整个音乐界面，不负责播放/暂停。
- 实现了 Minecraft 内 HUD 灵动岛原型。
- 灵动岛显示歌曲、歌词和播放进度。
- 0.4.5 将 GUI 重构为独立字体和抗锯齿画布：左侧导航、播放、发现、搜索、收藏、队列和设置页。
- 歌曲页使用固定设计网格，歌词卡片、队列卡片和列表共享对齐线。
- 内置 Inter 与 Noto Sans SC 字体；图标使用模组自绘矢量路径。
- 保留白粉磨砂、较大圆角、半透明面板、软阴影与灵动岛扩散光。本版不包含深色模式。
- 播放状态模型包括歌曲、播放/暂停、进度、队列、音量和灵动岛开关。
- 首次使用显示网易云音乐免责声明；确认之前网易云 HTTP 和封面请求均不发送。
- 已修复 Minecraft 1.21.11 的背景模糊崩溃：删除了 `MusicScreen.render()` 中不必要的 `renderBackground()` 调用。
- 已验证客户端构建成功。
- 0.1.4 已把 1.21.11 新鼠标事件（`MouseButtonEvent`）接回，自绘区域全部可点：
  - 左侧分类切换（正在播放 / 发现 / 搜索 / 房间 / 队列 / 设置）
  - 导航右上角的 `⚙` 图标可直接跳到设置页
  - 播放控制：上一首 / 播放暂停 / 下一首（正在播放页与底部播放条两处都可点）
  - 曲目列表：点整行 = 选中播放，点右侧 `+` = 只加入队列
  - 进度条点击跳转（点击区域比视觉高度大，方便点中）
  - 设置页灵动岛开关
  - 所有可点区域都有 hover 反馈（含进度条加粗、开关变色）
- 0.1.4 的点击不再依赖 vanilla widget，改为"每帧算一份 hitbox，渲染判 hover 与点击判命中复用同一份"，避免出现看得到点不到。
- 0.1.4 已把 `render()` 里逐帧的点击判断全部移出，改为 `layout()` + `dispatchClick()` 两处，渲染方法只负责画。
- 0.1.5 视觉重做：白色磨砂玻璃 + 微粉，零新增依赖。
  - 新增 `Theme`（配色常量）与升级版 `GuiPrimitives`：抗锯齿圆角、竖向渐变、柔和投影、玻璃面板（渐变 + 发丝边 + 顶部高光 + 柔影）。
  - 面板尺寸整体缩小：导航 132~166、内容区 196~240，间距 10，两面板顶对齐。
  - 灵动岛改为 226×34 的玻璃胶囊，去掉原来那块生硬的深色方块和左侧紫条。
  - 真背景模糊走 `renderBlurredBackground`，每帧一次；若该 API 在当前场景抛异常，自动降级为半透明玻璃且不再重试（不再有反复崩溃风险）。**仍然没有调用 `renderBackground`**。
  - 列表支持滚轮翻页，超出可视行时右上角显示 `1-5 / 6`。
- 0.1.6 抗锯齿 / 边缘羽化 / 动画 / 掉帧修复：
  - **掉帧根因**：0.1.5 的 `roundedRect` 是逐行 `fill`，单面板约 1400 次提交，两个面板 + 灵动岛一帧近 3000 次，1.21.9 之后每次 fill 都进 `GuiRenderState`，必掉帧。
  - **修法**：只在圆角区逐行（约 2×radius 行），中间直边按 6 个色带整体填充；阴影 2 层不做 AA；描边直边整段画。单面板降到约 200 次，全屏约 500 次/帧。
  - **抗锯齿**：圆角行按 `√(r²-d²)` 算亚像素边界，左右各补一个按覆盖率算 alpha 的像素，消除阶梯感。
  - **边缘羽化**：面板外圈叠两层极淡白色（`feather`），做出边缘融化/模糊感。
  - **文本缓存**：`Component` 与按宽度截断结果都进 Map 缓存，避免每帧重建组件导致文字布局缓存失效（`fit()` 原本是 O(n²) 的 `font.width` 调用）。
  - **交互动画**：hover 全部走帧率无关的指数趋近（不再瞬间跳变）；切页内容上移淡入；播放键按下回弹；灵动岛淡入淡出 + 轻微下滑。
  - **`MusicScreen.FROSTED_BLUR` 常量**：背景高斯模糊总开关。开界面还掉帧就改 `false`。
- 0.2.2 试过把面板烘焙成 SDF 纹理（一次 `blit` 画完），**结果是界面全透明**。
- 0.2.3 已回退为纯 `fill` 自绘，并查清 0.2.2 的根因：
  - 1.21.11 的 `GuiGraphics.blit(Identifier, int, int, int, int, float, float, float, float)`
    中间四个 int 是**绝对坐标 x0, y0, x1, y1**，不是旧版的 `(x, y, w, h)`。
    原版 `innerBlit` 把它们直接写进 `BlitRenderState.x0/y0/x1/y1`。
  - 旧代码传的是 `(left, top, width + 8, height + 8)`，等于画一个从 `(left, top)` 到 `(248, 344)`
    的反向矩形：面板越靠右下越退化，最后什么都画不出来，只剩文字飘在世界里（就是"卡透明"）。
  - `GuiTextures.glass()` 现在默认委托 `GuiPrimitives.glass()`；烘焙路线修好了但默认关闭
    （`GuiTextures.BAKED = false`）。想要更锐的 AA / 一次 draw call，把它改 `true` 即可。
- 0.2.3 面板绘制细节：
  - 边框不再逐行描：先铺一块比面板大 1px 的实心圆角底，主体盖上去，露出 1px 边。
  - 阴影 2 层走 `roundedRectFast`（无 AA）；渐变分带从 6 提到 10，长面板不再有色阶。
  - 面板不透明度提到 93%~96%（`Theme.GLASS_TOP/BOTTOM`），文字用深梅色，白底上够黑。
  - 单块面板约 150 次 `fill`，两个面板 + 灵动岛一帧约 480 次，和 0.1.6 一个量级。
  - `MusicScreen` 的模糊不再因为 MUI 存在而跳过（MUI 只模糊它自己的 Screen）。
  - **【更正】上面这条结论只解释了 0.2.2，不能当作通用规则**：0.2.5 用同一条重载贴 emoji 小图时，
    出来的不是"没画"，而是**竖条状的切片**（说明 UV 也没按预期取样）。
    实测下来这条 8 参数重载的 `(x0,y0,x1,y1,u,v)` 语义在这两个方向上都不够确定，
    继续赌不划算——0.2.6 起**不再用 blit 贴小图**，emoji 改走字体（见下）。面板本身是纯 fill，不受影响。
- 0.2.9（图标改回**原版字体里的特殊符号**，不再用 emoji）：
  - emoji 路线（0.2.5 烘焙图集 → 0.2.6/0.2.8 字体）整体放弃，`Emoji.java`、`font/emoji.json`、
    `textures/gui/emoji.png` 全部删除；生成工具留在 `tools/EmojiAtlas.java` 备用。
  - 新方案 `gui/Symbols.java`：直接用原版字体自带字形的 BMP 符号，**颜色跟随主题**
    （选中变粉、hover 加深），这点比彩色 emoji 更好用。
  - 码点是逐个在 `assets/minecraft/font/include/default.json` 的 chars 里核过的，全部存在：
    ♪ U+266A / ★ U+2605 / ◎ U+25CE / ⌂ U+2302 / ☰ U+2630 / ※ U+203B / ⏮ U+23EE / ▶ U+25B6 /
    ⏸ U+23F8 / ⏭ U+23ED。**⚙ U+2699 原版没有字形**，所以"设置"用 ※。
  - **2 倍整数放大**（字模只有 8px，`pose().pushMatrix/scale(2,2)`）：像素对齐，不会糊；
    布局按 16px 算，导航图标中心 `hit.x+15`、文字 `hit.x+28`，灵动岛 `x+16` / 文字 `x+28`。
  - 播放键恢复 0.2.4 的粉色圆盘 + 白色符号（▶/⏸ 由符号画，不再有 emoji 的方形底）。
  - 验证方法：把原版 `nonlatin_european.png`（128×536、1 位调色板、8px 格子）解码出来，
    按字体表定位每个码点、6 倍放大渲染成预览图，逐个确认形状没选错。
- 0.2.8（emoji 竖直居中 + 换两个更好认的图标）：
  - **竖直偏移是实测出来的，不是猜的**：0.2.6 的图标整体高出一截、顶到上一行。
    把截图按像素统计（罗盘金色环的 bbox 对比"发现"文字的 bbox）量出：
    字号 20、内容高 18 时，字形内容中心比 `cy` 高 **8px**，也就是字形顶边实际落在 `y - 8`。
    原因是 bitmap 字形的纵向定位跟的是**全局字体**的基线，不是我们这个字体自己的 ascent。
    `Emoji.VERTICAL_NUDGE = 8` 就是这条实测值；改 `SIZE` 或字体 `height` 要重新量。
  - 图标顺序改为：🎵 音符 / 🧭 罗盘 / 🔍 放大镜 / 🏠 房子 / 📋 **剪贴板**（队列）/ ⚙ **齿轮**（设置）/
    ⏮ ▶ ⏸ ⏭。原来队列用的是 🎶、设置用的是 🎛，前者和"正在播放"的黑色音符撞脸，
    后者是一格蓝点、认不出是设置，都换掉了。
  - 图集重新按 20px 单元格生成（`tools/EmojiAtlas.java` 的 `CELL = 20`，`MARGIN = 1`，内容 18px）。
- 0.2.6（emoji 改走字体，彻底不用 blit 贴图）：
  - `assets/cloudxmusic/font/emoji.json`：注册字体 `cloudxmusic:emoji`，一个 `bitmap` provider 指向
    `cloudxmusic:gui/emoji.png`（就是 0.2.5 那张 8×2、每格 20px 的图集），`ascent = height = 20`。
    `ascent == height` 是关键：bitmap 字形的顶边正好落在 `drawString` 的 y 上，画 20px 的 emoji 时
    `y = cy - 10` 就是垂直居中，不用猜基线。
  - `chars` 两行各 8 个码点（不足的用 U+E000 占位，保证列数=8、格子宽=20px）：
    `🎵🧭🔍🏠🎶🎛` / `⏮▶⏸⏭`。**用不带 U+FE0F 的基础码点**，否则变体选择符会被当成缺字。
  - `Emoji.icon(i)` 返回 `Component.literal("🎵").withStyle(Style.EMPTY.withFont(...))`，
    1.21.11 的 `Style.withFont` 要的是 `FontDescription`（`new FontDescription.Resource(id)`），不是裸 `Identifier`。
  - **必须用白色画**（`0xFFFFFFFF`）：bitmap 字形会和文字颜色相乘，用粉色/灰色会把 emoji 染色。
  - 代码里现在就是普通 emoji 字符 + 指定字体，不用算 UV、不会串格、也不依赖任何前置模组。
- 0.2.5（emoji 图标 + 灵动岛紧凑化）：
  - **图标换成真 emoji**：原版字体没有 U+1F3xx 彩色 emoji（0.1.x 能显示是靠 MUI 的 Noto Color Emoji），
    所以不再走"字体渲染 emoji"这条路，改成**构建期烘焙图集**：
    `tools/EmojiAtlas.java` 把微软 Fluent Emoji（MIT）的 256px 3D PNG 裁边、逐级减半缩到 20px、
    拼成 `assets/cloudxmusic/textures/gui/emoji.png`（160×40，8 列 × 2 行，单元格 20px）。
    运行时 `Emoji.draw()` 用 `blit` 1:1 贴图，零前置依赖、不会豆腐块、不会被字体替换影响。
  - 图集顺序（与 `EmojiAtlas.NAMES` 一一对应）：0 音符 / 1 罗盘 / 2 放大镜 / 3 房子 / 4 音符组 /
    5 控制旋钮（原齿轮是浅灰，白底上几乎看不见，换成深色旋钮 emoji）/ 6 上一首 / 7 播放 / 8 暂停 / 9 下一首。
  - 导航行：emoji 18px 居中于 `hit.x+18`，文字右移到 `+32`；选中箭头仍是矢量小三角。
  - 播放控制：去掉原来的粉色圆盘（emoji 自带圆形底），播放/暂停 20px，上/下一首 20px，hover 只加一层淡粉光晕。
  - **灵动岛紧凑化**：200×40 → **176×30**，两行文字合成一行 `歌名 · 歌手`，左侧 18px emoji 音符，
    右侧时间，底部 2px 进度（`y+HEIGHT-5`）。
  - **删掉 LZSN**（导航标题只留 CLOUDX）、**删掉 MISC** 分组文字（分隔线与"设置"行保留）。
  - 排布验证：`.workbuddy/tmp/layout_mock.py` 按真实坐标拼模拟图（emoji 从图集取像素、文字用灰条），
    确认图标与文字没有挤压、没有出界。
- 0.2.4 收尾（按用户反馈的四条）：
  - **左侧分类图标回来了**：优先用 emoji（🎵🧭🔍🏠🎶⚙），字体缺字形时自动退回矢量图标。
    原版字体不含 U+1F3xx（那一大段彩色 emoji），以前能显示是 MUI 附带的 Noto Color Emoji；
    判定方式是用 U+10FFFD（必然无字形）探出"缺字推进宽度"，宽度不同才算字体真的有这个字形。
  - **MUI 全部移除**：`compat/MuiCompat`、`gui/mui/MuiMusicFragment`、J 快捷键、`open_mui` 语言键、
    `fabric.mod.json` 的 `jars`、`build.gradle` 里 `modCompileOnly` 与 `libs/nested` 打包段全部删掉。
    `THIRD_PARTY_NOTICES.md` 改为"当前无内置第三方组件"。产物从 25.3 MB 降到 1.49 MB。
  - **图标抗锯齿重做**：`Icons` 每条扫描线纵向再切 4 个子采样，行 alpha 按覆盖率折算（二维 AA）；
    新增 `tune()` 把 18% 以下的覆盖率抹掉、82% 以上算实心，避免 1px 渐变被 GUI scale 放大成"糊"。
    新增 `ring` / `polygon` / `line` / `block`，以及 compass、search、home、list、sliders 五个导航图标。
  - **进度条支持按住拖拽**：`mouseClicked` 命中即跳转并进入拖拽态，`mouseDragged` 跟手
    （拖出面板也继续，只夹在两端之间），`mouseReleased` 收尾；拖拽时进度条加粗到 3px、滑块 5px。
    命中区从视觉 2px 放宽到 13px（y+95 ~ y+108），不会压到下面的时间文字。

### 历史构建产物（0.4.4）

```text
CloudXMusic-0.4.4.jar
```

构建结果：

```text
BUILD SUCCESSFUL
```


## 2.5 网易云接入（0.3.0）

### 新增包结构

```text
client/netease/
  NeteaseCrypto.java    weapi(双 AES-CBC + RSA) / eapi(AES-ECB + md5 摘要)
  NeteaseHttp.java      JDK HttpClient + 手写 Cookie(MUSIC_U/__csrf)
  NeteaseApi.java       登录 / 搜索 / 歌单 / 歌曲详情 / 播放地址 / 歌词
  NeteaseQuality.java   standard 128K / higher 192K / exhigh 320K
  NeteaseSession.java   账号与偏好的落盘（config/cloudxmusic/netease.json）
client/lyric/Lyric.java  LRC 解析（多时间戳、原文+翻译按毫秒合并）
client/audio/Mp3Player.java  在线播放：HTTP 流 → JLayer 解码 → SourceDataLine
client/command/CloudXMusicCommand.java  /cxm 命令
client/util/Async.java     网络线程 + 回主线程
```

### 接口（已用 curl 实测通过）

| 用途 | 接口 | 结果 |
| --- | --- | --- |
| 扫码 key | `GET /api/login/qrcode/unikey?type=1` | 200 + unikey |
| 扫码状态 | `GET /api/login/qrcode/client/login?type=1&key=` | 800/801/802/803 |
| 手机号登录 | `POST /weapi/w/login/cellphone`（weapi） | 未实测（需要真账号） |
| 验证码 | `POST interfacepc/eapi/middle/captcha/sent/v1`（eapi） | 未实测 |
| 搜索 | `POST /api/cloudsearch/pc/` | 200，songs[].ar/al/dt 齐 |
| 歌曲详情 | `GET /api/v3/song/detail?c=[{id}]` | 200 |
| 播放地址 | `GET /api/song/enhance/player/url/v1?encodeType=mp3&ids=[id]&level=` | 320K mp3 直链，`audio/mpeg`，206 可流式 |
| 歌词 | `GET /api/song/lyric?id=&lv=0&tv=0` | lrc + tlyric |
| 歌单 | `GET /api/v6/playlist/detail?id=&n=1000` | playlist.tracks |
| 每日推荐 | `POST /api/v3/discovery/recommend/songs` | data.dailySongs |

**注意**：`ids=[..]` 的方括号必须编码成 `%5B %5D`，否则 `URI.create` 可能抛 `URISyntaxException`。
VIP/版权受限的歌 `url` 为 `null` + `code:-110`，代码里当成"没有可用播放地址"处理。

### 播放（在线，不下载）

- 全程只有一个 128KB 读缓冲 + 约 0.4s 声卡缓冲，边收边解边放；**不落盘、不缓存整首、不预下**。
- 跳转用 `Range: bytes=X-`，X 由 `总字节 × 时间占比`（CBR）估算；服务端不认 Range 就退回从头解码跳帧。
- 暂停 = 断流 + 记位置，恢复 = 用 Range 重连，不占 CDN 长连接。
- 解码实测：11.1MB / 320K 的 mp3，解码 10.45s 音频耗时 467ms（约 22 倍速），44.1kHz 立体声。

### 命令

```text
/cxm login qr                     生成登录链接（复制到剪贴板）并轮询扫码状态
/cxm login phone <手机号> <密码>   手机号+密码登录
/cxm login send <手机号>           发验证码
/cxm login captcha <手机号> <码>   验证码登录
/cxm login status | /cxm logout
/cxm refresh                      拉每日推荐当列表
/cxm playlist <id>                加载歌单
/cxm play <关键词>                搜索并播放第一首
/cxm quality [standard|higher|exhigh]
/cxm lyric [off|original|bilingual]   灵动岛歌词模式
/cxm volume <0-100> | pause | next | prev | status
```

### 灵动岛歌词

- `OFF`：一行 `歌名 · 歌手`（30px 高）
- `ORIGINAL`：一行当前歌词
- `BILINGUAL`：两行（原文 + 翻译），高度 30 → 44；翻译缺失自动退回单行

### 0.3.1 登录流程返工（实机反馈后）

用户实测问题：① 聊天区的登录链接点不了 ② 二维码只是链接、没真的弹出来
③ 只要手机号+验证码时命令报"无效命令" ④ 演示音乐还在。

**改法**

1. **二维码真的画在游戏里**：新增 `gui/QrLoginScreen`（一个 `Screen`），用 ZXing 把
   `https://music.163.com/login?codekey=<key>` 编码成矩阵，游戏内白底黑块渲染，手机直接扫。
   Esc 关闭 = 取消轮询；登录成功停 1.2 秒自动关。
   - `QrCodes.encode()` 用 `QRCodeWriter(width=0,height=0, MARGIN=0, EC=M)`，输出 37 模块。
   - **已做编码→解码回转验证**：`QRCodeReader` 解出来的文本与原链接完全一致。
2. **key 自动续**：unikey 约 2 分钟过期（code 800），以前直接报"已过期"让用户重来；
   现在立刻重新申请，最多续 3 次（`QrLogin`）。
3. **轮询独立线程**：`QrLogin` 自己开线程，不占 `Async` 的 4 线程池，
   避免一次登录把取播放地址之类的请求全堵住。
4. **手机号登录不再强制密码**：
   - `/cxm login phone <手机号>` → 发验证码，记住手机号
   - `/cxm login code <验证码>` → 用记住的手机号登录
   - `/cxm login phone <手机号> <密码>` → 密码直登
   - `/cxm login captcha <手机号> <验证码>` → 完整写法
   之前"去掉密码就报无效命令"是因为 brigadier 树里 phone 只有一个带 password 的分支。
5. **演示音乐删掉**：`MusicState` 里 `DEMO` 列表、假进度 `demoPosition` 全部移除，
   列表初始为空、`current` 用 `NOTHING` 占位（`未播放 / 等待网易云登录`）；
   GUI 里进度计算加了 `Math.max(1, duration)` 防止除零。
6. 登录失败会打印 `code` + 原始响应到日志（`cloudxmusic.log`），502 时提示改走验证码。

**weapi 加密已验证正确**：用自家 `NeteaseCrypto.weapiForm` 打 `/weapi/cloudsearch/pc/`，
返回 200 + 真实歌曲，说明 AES/RSA 参数没问题；手机号登录的 502 是服务端拒绝凭据，不是加密错。

### 0.3.2 `/cxm login qr` 没反应的真因

日志（0.3.1 实机）里**没有任何异常**，最后一行只是"未知或不完整的命令"，说明命令执行了但界面没留下。

**真因**：命令是在聊天框里跑的。`ChatScreen` 的顺序是「执行命令 → `setScreen(null)` 关聊天框」，
我们在命令里直接 `minecraft.setScreen(new QrLoginScreen())`，**紧接着就被聊天框的关闭操作覆盖了**。
表现就是"输入命令，什么都没发生"。

**修法**：把开界面丢到下一 tick —— `minecraft.execute(() -> minecraft.setScreen(...))`。
以后任何"命令里打开 Screen"的地方都要这么写。

另外：
- `/cxm login captcha <手机号>`（不带验证码）现在也当"发验证码"，不再报"未知或不完整的命令"。
- 生成二维码时顺手把链接放进剪贴板 + `config/cloudxmusic/login-url.txt`，界面底部也写了这句提示。
- `QrLogin` 会往日志打 `扫码登录：key=... 二维码模块=37`，状态轮询也打 debug，后面再排查有据可查。

### 0.3.3 播放 403（登录成功但放不出声）

**现象**：登录后播放，日志里一串 `Server returned HTTP response code: 403`，URL 都是
`http://m7xx/m8xx.music.126.net/.../jdymusic/obj/...mp3?vuutv=...&authSecret=...`。

**排查**：拿免费歌的直链用 curl 逐项试 —— 带/不带 Cookie、带/不带 Range、
Java 默认的怪异 Accept、http 与 https —— **全部 200/206**。所以不是通用请求头问题，
而是**带 `authSecret` 的会员/受限链接**在 CDN 侧被拒（这类链接没有会员授权拿不到，
我这边无法复现）。

**改法（把三种可能都兜住）**
1. `NeteaseApi.playableUrl()`：按 `当前音质 → higher → standard` 逐档取地址，
   每个地址先发一个 `Range: bytes=0-1` 的**探测请求**，通过才交给播放器。
   探测顺序是 `https 版 → http 原版`，因为部分带 authSecret 的链接只在 https 下生效。
2. `Mp3Player` 请求 CDN **不再携带网易云 Cookie**（只要 Referer 做防盗链），
   并显式 `Accept: */*`（Java 默认的 `text/html, image/gif, *; q=.2` 很不像浏览器）。
3. 非 200/206 直接抛 `音频服务器返回 HTTP 403（链接可能过期或该音质不可用）`，
   不再是个没头没尾的 IOException。
4. 新增 `/cxm test`：播一首**确定免费、直链可用**的歌（2652820720），
   用来区分"播放器坏了"和"这首歌本身放不了"。

**判断方法**：`/cxm test` 能出声 → 播放链路没问题，放不了的是受版权/会员限制的歌；
`/cxm test` 也没声 → 问题在解码或声卡（看日志里的 `播放失败`）。

### 0.3.4 播放 403 的真正原因：登录态失效（不是版权）

**关键验证**：直接拿用户机器上 `config/cloudxmusic/netease.json` 里的 Cookie 请求每日推荐里的
VIP 歌（fee=1，522444650）→ `data.code=200`、`br=320000`、**URL 里没有 authSecret**，
GET 直接 200 返回 ID3/mp3；JLayer 也能正常解（206、44.1kHz、10.45s 音频 233ms）。
→ **403 是"没带上有效 Cookie"时的产物**：无会话时接口给的是带 `authSecret` 的链接，CDN 一律 403。

**改的四处**
1. `NeteaseHttp.absorbCookies`：**空值不覆盖**已有 Cookie（退出登录/被清时 `MUSIC_U=` 会把好 Cookie 冲掉）。
   每次更新打日志（MUSIC_U 字符数）。
2. `NeteaseSession.load()` 打日志（路径 + Cookie 长度），方便确认"重启后到底读没读到"。
3. 扫码 803：**先把 Cookie 落盘再问账号信息**（`markLoggedIn`），`account()` 失败不再把整个登录判死；
   轮询间隔 2s → 1.5s。
4. `NeteaseApi.playableUrl`：API 各档位探测失败后，改用 `/song/media/outer/url` 兜底
   （302 到不带 authSecret 的直链；不可播会 302 到 /404）。探测请求**不再带 Range**（会被判异常请求）。

**播放器重写（照 Concerto 的三条原则）**
- **暂停不拆流**：只让解码线程停手，连接/声卡保留，位置冻结在最后解码样本上 → 不再乱跳；
  暂停超过 60s 才断流重连（CDN 会掐空闲连接）。
- **进度只信解码样本**：`positionMs` 由 PCM 采样数累加，容器时长只用于跳转估字节。
- **离开世界停播**：`ClientPlayConnectionEvents.DISCONNECT` → `STATE.stopAll()`，另加 `/cxm stop`。
- 播放失败自动跳过下一首，连续 4 首失败就停下并提示查登录态。

### 0.3.5 扫码会话 / 列表拆分 / 收藏 / 搜索 / 封面

**扫码确认没反应的真因（推断 + 修复）**：unikey 和后面的轮询必须属于**同一个会话**——
服务端靠游客 Cookie（NMTID、_ntes_nuid 等）把"这个二维码"和"这个客户端"绑起来。
之前只手动拼 `MUSIC_U`，轮询就跑到另一个会话去了，永远收不到 803。
→ 换成 `CookieManager` + `PersistentCookies`（`config/cloudxmusic/cookies.json`），整站 Cookie 统一管理与落盘。
顺带解决"重启掉登录"。803 时**先落盘再查账号信息**。

**队列不再被"发现"塞满**
`MusicState` 拆成三个浏览列表 + 队列：
- `discovery`（每日推荐/歌单）、`favorites`（我喜欢的音乐）、`searchResults`（搜索结果）
- `queue` **只由用户动作填充**：点列表里的 `+`（`enqueue`）或点行播放（`select`）
- `refreshLibrary()` / `loadPlaylist()` / `search()` 只填列表，**不自动播、不动队列**

**新增功能**
- `/cxm search <关键词>` + GUI「搜索」页（结果列表，点行播放，`+` 入队）
- `/cxm fav` + GUI「收藏」页（`/api/likelist` → `/api/v3/song/detail` 批量取详情）
- GUI 分类改为：正在播放 / 发现 / 搜索 / 收藏 / 队列 / 设置（移除未实现的"房间"）
- 正在播放页画专辑封面：`CoverCache` 后台下载 → `DynamicTexture` → 全图 `(0,0,1,1)` blit
- `/cxm logout` 未登录时提示"还没登录，无需退出"，退出时顺带停播

### 0.3.6 登录 Cookie 导入 / 旧版迁移

**0.3.5 的两个会话缺口**
- 网易云登录/扫码接口经常把 `MUSIC_U=...; __csrf=...` 放在 **JSON body 的 `cookie` 字段**，
  而不是标准 `Set-Cookie` 响应头。0.3.5 只依赖 `CookieManager`，会漏掉登录态。
- 0.3.4 及以前把 `MUSIC_U` 存在 `netease.json` 的 `cookie` 字段；0.3.5 改存
  `cookies.json` 时没有迁移，升级后会显示“曾登录”但没有可用 Cookie。

**修法**
- `NeteaseApi.QrStatus` / `LoginResult` 增加 `cookie` 字段，扫码 803 和普通登录都解析 body Cookie。
- `NeteaseHttp.importCookieString()` 把 Cookie 导入统一 CookieStore，域限定为 `.music.163.com`，
  不会把账号 Cookie 发到 `music.126.net` CDN。
- 启动时若 `cookies.json` 没有 `MUSIC_U`，自动从旧 `netease.json.cookie` 迁移，并重写旧文件清除敏感副本。
- `PersistentCookies` 不再把 `maxAge = -1` 的 session Cookie 误判为删除。
- 登录文件标记已登录但本地没有 `MUSIC_U` 时，启动会重置为未登录，避免 `/cxm status` 假阳性。

**验证**
- 用实机旧 `netease.json` Cookie 直连播放地址接口：`code=200`、`br=320000`、URL 不含 `authSecret`，
  证明旧登录态本身有效。
- `0.3.6` 全量构建通过，产物 `CloudXMusic-0.3.6.jar`（2,326,914 B）。
- **待实机验证**：旧登录迁移日志、重启后仍登录、扫码确认后拿到 803、受限歌曲不再 403。

### 0.4.0 GUI 搜索 / 收藏 / 专辑图 / 滚动歌词

**搜索页**
- 搜索页现在有游戏内输入框和「搜索」按钮，点击搜索选项卡即可直接输入。
- 支持 Enter 搜索、Backspace 删除、Ctrl+V 粘贴、Esc 取消焦点。
- 搜索请求与结果列表分离，输入后只刷新搜索结果，不自动播放、不改队列。

**收藏页**
- 切换到「收藏」选项卡时自动拉取「我喜欢的音乐」，不需要先执行 `/cxm fav`。
- 加载中会显示状态；只在首次进入或加载失败后重试，不会每次切页重复请求。
- 收藏列表仍遵守“点行播放、点 + 入队”，不会因为登录或拉取而自动播放。

**专辑封面**
- 正在播放页显示封面、歌名、歌手和专辑名。
- 封面改为请求 `param=96y96` 缩略图，下载体积更小。
- 修正 `NativeImage` / `DynamicTexture` 所有权：纹理注册后由 DynamicTexture 释放，不再提前关闭像素缓冲。
- 下载或解码失败时保留音符占位图，不影响界面。

**滚动歌词**
- 正在播放页播放控件下方原来的「本地演示 · 未连接音乐源」已替换为滚动歌词区。
- 按播放进度平滑滚动，当前行高亮，相邻行渐隐；双语歌词会在当前行下方显示翻译。
- 没有时间轴歌词、纯文本歌词或获取失败时显示「不支持滚动歌词」。
- 歌词区使用 scissor 裁剪，行切换时不会溢出到播放控件或面板外。

**验证**
- `0.4.0` 全量构建通过，产物 `CloudXMusic-0.4.0.jar`（2,330,972 B）。
- 搜索接口实测 `code=200`，返回的歌名、歌手、专辑字段齐全。
- 封面 URL 追加缩略参数后实测 HTTP 200、`image/jpg`、3,592 B，JPEG 数据有效。
- 歌词解析实测：多行时间轴、翻译对齐、纯文本判定为不可滚动均通过。
- **待实机验证**：搜索框输入法/粘贴、收藏自动加载、封面显示、歌词滚动观感和小窗口布局。

### 0.4.1 登录态持久化修复

**实机现象**：启动日志明确显示 `cookies.json` 已读到 26 条 Cookie，`MUSIC_U=true`、
`loggedIn=true`，但重新登录失败后又变成“每次启动都要登录”。

**根因**
- 登录失败时 `applyLogin()` 仍把 `loggedIn` 设为 false，并覆盖 uid/nickname/avatar。
- `netease.json` 被写坏后，下次启动即使 `MUSIC_U` 还在，`loggedIn()` 仍返回 false。
- 实机 `MUSIC_U` 本身没有过期：账号接口 `code=200` 且返回完整 profile。

**修法**
- 登录失败且本地已有 `MUSIC_U` 时，不再修改现有登录态与账号元数据。
- `loggedIn()` 改为以 CookieStore 是否有 `MUSIC_U` 为准，元数据只用于显示。
- 启动时若 CookieStore 有 `MUSIC_U` 但 `netease.json.loggedIn=false`，自动恢复并保存为 true。
- 新增 `refreshAccountAsync()`：启动后用现有 Cookie 异步请求账号信息，补回 uid/昵称/头像；网络失败不影响已有登录态。
- 登录失败提示会明确说明“现有登录态仍然有效，已保留”。

**验证**
- 现有实机 Cookie 请求账号接口：HTTP 200、`code=200`、profile 完整。
- `0.4.1` 全量构建通过，产物 `CloudXMusic-0.4.1.jar`（2,331,892 B）。
- 预期启动日志：`检测到有效 MUSIC_U，已恢复登录标记`，随后账号信息恢复。
- **待实机验证**：连续重启不登录、失败登录后重启、收藏页可直接加载。

### 0.4.2 队列持久化

- 新增 `config/cloudxmusic/state.json`，保存当前歌曲和完整播放队列。
- 选择歌曲、`+` 入队、上一首/下一首切歌时自动保存，写入采用临时文件 + 原子替换。
- 启动时恢复队列和当前歌曲，状态为**暂停**，不在主菜单突然自动播放。
- 恢复后打开 H 界面即可看到上次歌曲与队列，按播放键继续，不需要重新点歌。
- 只保存歌曲元数据（id/歌名/歌手/时长/专辑/封面 URL），不保存 Cookie、密码或音频内容。
- `0.4.2` 全量构建通过，产物 `CloudXMusic-0.4.2.jar`（2,334,625 B）。
- **待实机验证**：选歌/加队列后重启，队列顺序与当前歌曲应完整保留。

### 0.4.3 网易云式歌词 / 列表封面 / JPEG 解码

**封面根因**
- 网易云 96px 缩略图返回 JPEG，但 `NativeImage.read()` 只接受 PNG，日志因此刷了数千条 `Bad PNG Signature`。
- 解码失败后没有负缓存，GUI 每帧重新下载/解码，进一步拖慢界面。
- 修法：PNG 失败时用 `ImageIO` 读 JPEG，再转 PNG 交给 NativeImage；解码在后台线程完成。
- 新增失败缓存 `FAILED`，同一封面本次启动只尝试一次，不再每帧刷日志。

**歌曲列表 / 播放页**
- 歌曲列表每行显示 18px 真实专辑封面；加载中或确实无封面时用专辑首字色块，不再用统一音符占位符。
- 当前播放行给封面加粉色描边。
- 播放页封面增大到 48px，并显示专辑名。
- 账号状态显示在模块面板右上角：已登录显示网易云昵称，未登录才显示“未登录”。

**歌词**
- 歌词区改为网易云式深青灰面板。
- 每行同时显示原文和翻译，当前行纯白高亮，上下行按距离渐隐。
- 播放控制整体上移，为歌词区腾出更多高度；仍保持平滑滚动和 scissor 裁剪。
- 无时间轴歌词继续显示“不支持滚动歌词”。

**收藏登录态**
- 收藏页在 uid 尚未恢复时，会先补调账号接口再拉收藏，解决“刚进世界账号信息异步还没回来就点收藏”的竞态。
- 启动日志已验证：`检测到有效 MUSIC_U，已恢复登录标记`。

**验证**
- Java `ImageIO` 实测读取网易云 96×96 JPEG 成功并转出 PNG。
- `0.4.3` 全量构建通过，产物 `CloudXMusic-0.4.3.jar`（2,336,176 B）。
- **待实机验证**：列表封面、播放页 48px 封面、网易云式双语歌词、收藏首开加载。

### 0.4.4 每日推荐自动刷新 / 队列管理 / 关于页

**每日推荐**
- 进入世界触发 `ClientPlayConnectionEvents.JOIN`，已有登录态时自动刷新每日推荐，不再需要 `/cxm refresh`。
- `refreshLibrary()` 增加加载锁；日志记录“每日推荐已加载：N 首，有封面 M 首”。

**队列管理**
- 队列页每行右侧由 `+` 改为 `×`，点击删除单首。
- 删除当前歌曲时停止播放，切到剩余队列第一首并保持暂停。
- 队列页右上角新增“清空”按钮，一键停播并清空队列。
- 删除/清空都会立即写入 `state.json`。

**关于页**
- 原“设置”选项卡改名为“关于”，页面标题同步改为“关于”。
- 展示模组简介：轻量级 Minecraft 网易云音乐播放器。
- 展示作者头像、名字 `Lzasn`、身份“作者 / 开发者”、模组名和版本。
- 作者头像来自用户提供的 `C:\Users\Lzasn\Downloads\6C5A4CBBA58117859F00F885A3D76A3C.jpg`，
  已裁成圆形 PNG 打包进 `assets/cloudxmusic/textures/gui/author.png`。
- HUD 灵动岛开关保留在关于页内。

**封面**
- 列表绘制前会预取当前可见区域和后续 8 行封面，减少滚动/首次打开时长时间显示色块。
- 每日推荐日志会输出封面 URL 数量，便于确认接口数据是否完整。
- 真实网易云每日推荐实测 33 首，`al.picUrl` 均非空；96×96 JPEG 可被 Java ImageIO 正常解码。

**验证**
- `0.4.4` 全量构建通过，产物 `CloudXMusic-0.4.4.jar`（2,393,562 B）。
- jar 内已确认包含 `assets/cloudxmusic/textures/gui/author.png`。
- **待实机验证**：进入世界自动出现每日推荐、队列单删/清空、关于页头像与简介、列表真实封面。

### 已知限制

- 只支持 mp3 三档音质；无损（FLAC）已于 2.4.10 下线且不再计划支持——FLAC 是 VBR、没有帧索引，seek 只能从文件头逐帧解码。
- 扫码登录已在游戏内绘制二维码；`0.3.6` 已补齐 803 body Cookie 导入，但仍需实机确认扫码确认后的状态更新。
- 播放依赖 `javax.sound.sampled`（java.desktop 模块）；官方启动器自带完整 JDK 没问题，
  但如果是被裁剪过的 JRE，会在 `AudioSystem.getLine` 处报错。
- 登录接口（weapi/eapi）的验证码链路已实测成功；密码/邮箱登录仍可能受网易风控影响，失败时看 `code`。

### 0.4.5 CloudXMusic 界面和首次使用保护

- 项目名称为 `CloudXMusic` / `云朵音乐盒`，开发者为 `Lzasn,xWtree,xiaota888`；mod ID 和 Java 包名保持不变。
- `MusicScreen` 以固定网格画布渲染，绘制和点击使用同一套坐标布局。
- 内置 Inter、Noto Sans SC 及 SIL OFL 1.1 许可文本；图标由代码绘制，不依赖原版字形。
- 登录二维码、音乐界面和灵动岛使用统一字体及磨砂材质。
- 首次确认记录在 `config/cloudxmusic/disclaimer.txt`。确认前网易云 HTTP 请求和封面下载都会阻止；启动账号恢复也会跳过。
- 封面缓存限制条目、并发下载和响应字节；客户端退出时取消下载并释放 UI 纹理。
- Java 21.0.7 下 `:cloudxmusic-client:build` 和离线布局测试通过，导出页面预览到 `outputs/`。
- 尚未在 Minecraft 1.21.11 游戏实例中进行人工交互、性能或登录实测。
- 构建产物：`cloudxmusic-client/build/libs/CloudXMusic-0.4.5.jar`。

## 3. 当前未完成和必须注意的地方

这是交接时最重要的部分。

### 3.1 UI 交互已接回，但还没有经过人工验证

0.4.5 的 `MusicLayout` 同时提供绘制坐标和鼠标命中区域。Java 21 下构建、布局边界测试和离线截图渲染都通过；仍未在 Minecraft 实例中实际打开各页面。因此输入焦点、拖动、不同 GUI 缩放和屏幕尺寸必须实机确认。

离线预览只能证明画布排版和字体资源可用，不能证明 Minecraft GPU 纹理上传、真实音频、登录或持续帧率。

### 3.2 真实音频播放已接入，但还需要实机验收

当前在线播放链路为：网易云播放地址 → HTTP 流 → JLayer 解码 → `SourceDataLine`，不落盘。
已实现暂停/继续、Range 跳转、音量、失败自动跳过和离开世界停播。

仍需要人工确认：声音是否真的出声、暂停位置是否稳定、拖拽后是否从正确位置继续、
暂停超过 60 秒后的重连，以及连续失败后的自动跳过。

### 3.3 网易云已接入，登录态仍是当前验收重点

已接入扫码/验证码/密码/邮箱登录、歌曲搜索、每日推荐、歌单、收藏、歌曲详情、专辑封面、
歌词、音质切换和播放地址获取；GUI 已有搜索、收藏和队列页。

`0.3.6` 修复了登录 Cookie 可从 JSON body 导入、旧版 Cookie 自动迁移，以及 session Cookie
被误删的问题。仍需实机确认：首次扫码、扫码确认、重启保持登录、受限歌曲播放。

账号信息不应该发送给 Folia 服务端。建议客户端本地保存登录状态，服务器只同步歌曲元数据和房间控制状态。

### 3.4 还没有 Folia 服务端插件

当前工程只有 `cloudxmusic-client` 模块，没有 `music-server` 模块。

尚未实现：

- Folia 插件
- 房间创建、加入、退出
- 房主权限
- 播放队列同步
- 房间成员同步
- 服务端权威播放状态
- `RegionScheduler`
- `EntityScheduler`
- SQLite 或其他持久化
- 服务端权限节点

### 3.5 还没有客户端/服务端网络协议

当前没有 `music-protocol` 模块，也没有 custom payload 包定义。

后续不要让客户端直接把网易云 Cookie、账号密码或音频内容发给服务端。

## 4. 当前工程结构

```text
C:\Users\Lzasn\Desktop\CloudXMusic
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew.bat
├── gradle/
│   └── wrapper/
├── cloudxmusic-client/
│   ├── build.gradle
│   └── src/main/
│       ├── java/dev/lzasn/cloudxmusic/client/
│       │   ├── MusicPlayerClient.java
│       │   ├── gui/
│       │   │   ├── GuiPrimitives.java
│       │   │   └── MusicScreen.java
│       │   ├── hud/
│       │   │   └── DynamicIslandHud.java
│       │   └── state/
│       │       ├── MusicState.java
│       │       └── Track.java
│       └── resources/
│           ├── fabric.mod.json
│           └── assets/cloudxmusic/lang/
│               ├── en_us.json
│               └── zh_cn.json
```

### 主要类职责

`MusicPlayerClient.java`

- Fabric 客户端入口。
- 注册 `H` 快捷键。
- 在客户端 tick 中处理 `H`。
- 注册灵动岛 HUD。
- 持有全局 `MusicState`。

`MusicScreen.java`

- ClickGUI 主界面。
- 负责左右双面板布局、自绘导航、模块内容、播放页、房间页、设置页。
- `layout()` 计算 hitbox，`render()` 只画，`dispatchClick()` 处理点击。

`DynamicIslandHud.java`

- 绘制顶部圆角方形灵动岛。
- 当前使用 Fabric `HudRenderCallback`。
- 当前 API 已有 deprecated 警告，但可以构建。
- 后续可以迁移到 `HudLayerRegistrationCallback` 或 `HudElementRegistry`。

`MusicState.java`

- 当前客户端演示状态。
- 保存测试曲库、队列、当前歌曲、播放状态、播放进度、灵动岛开关。
- 后续应拆分为：本地播放器状态、房间状态、网易云数据源状态。

`Track.java`

- 歌曲元数据的最小 record：标题、歌手、时长。
- 后续应增加歌曲 ID、封面 URL、音频源标识、来源类型等字段。

`GuiPrimitives.java`

- 自绘圆角矩形和圆角边框。
- 当前通过 `GuiGraphics.fill` 实现，没有引入第三方 UI 库。

## 5. 构建环境和命令

### JDK

使用：

```text
C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot
```

### 本机 Gradle

本机 Gradle Wrapper 下载可能被网络拦截，但本机已有 Gradle 9.5.0 解压缓存。

如果 Wrapper 可用，标准命令：

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
.\gradlew.bat :cloudxmusic-client:build
```

当前环境曾使用本地 Gradle 9.5.0 直接构建：

```powershell
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.101-hotspot"
& "C:\Users\Lzasn\.gradle\wrapper\dists\gradle-9.5.0-bin\estug5qhtw5xqrldjyqnn9yr5\gradle-9.5.0\bin\gradle.bat" :cloudxmusic-client:build --no-daemon
```

如果 Fabric/Maven 依赖下载失败，用户本机可使用代理 `127.0.0.1:7890`。不要把代理写入项目配置或提交到仓库。

## 6. 版本策略

当前版本为 `2.4.7`，配置位置：

```text
gradle.properties
```

字段：

```properties
mod_version=2.4.10
```

每次产生可交付改动都要更新版本：

- patch：修复、兼容性调整、UI 小改动，例如 `0.1.3` -> `0.1.4`
- minor：新增完整功能，例如 `0.1.x` -> `0.2.0`
- major：破坏性协议或配置变更，例如 `0.x` -> `1.0.0`

构建产物文件名和 `fabric.mod.json` 的 `${version}` 会自动同步版本号。

## 7. 0.1.4 交付内容（已完成）

0.1.4 不接网易云，也不接 Folia。目标是把客户端 UI 原型变成真正可操作的客户端界面，该项已完成并通过编译。

### 已完成

1. 适配 Minecraft 1.21.11 官方映射的新鼠标事件签名（`MouseButtonEvent` + `ContainerEventHandler` 默认方法）。
2. 恢复左侧分类切换：正在播放、发现、搜索、房间、队列、设置。
3. 恢复播放控制点击：上一首、播放/暂停、下一首。
4. 恢复歌曲列表点击：点整行选中播放，点 `+` 只加入队列。
5. 恢复进度条点击跳转。
6. 恢复设置中的灵动岛开关。
7. 所有点击区域都有 hover 反馈。
8. `H` 只负责打开/关闭界面，不改变播放状态。
9. 未调用 `renderBackground()`，不会触发背景模糊崩溃。
10. 版本已更新到 `0.1.4`。

### 待人工验证（未做）

- 按 H 打开/关闭界面。
- 各分类切换、按钮点击、曲目选中、进度跳转的实际手感。
- 小分辨率下两个面板是否重叠或裁切文字。

### 质量要求

- 不要重新调用 `renderBackground()`，避免 `Can only blur once per frame`。
- 不要使用 Yarn 命名。
- 保持官方 Mojang mappings：`Minecraft`、`GuiGraphics`、`Component`、`KeyMapping`、`Identifier` 等。
- 不要把所有点击逻辑塞进渲染方法里，最好拆成明确的 hitbox/事件处理方法。
- 不要依赖第三方 ClickGUI 库，当前自绘结构已经足够。
- 小分辨率下不能让两个面板重叠或裁切关键文字。

## 8. 0.2.0 客户端目标

0.2.0 才开始接入真实音乐能力。

建议顺序：

1. 抽象 `AudioPlayer` 接口。
2. 增加本地测试音频播放。
3. 增加音量、暂停、跳转、停止、资源释放。
4. 增加音频播放线程。
5. 增加播放错误状态和重试。
6. 抽象 `MusicSource` 接口。
7. 实现网易云搜索数据源。
8. 设计本地登录状态存储。
9. 增加歌曲元数据和封面缓存。
10. 为真实音频源增加版权和不可播放提示。

建议接口形态：

```java
public interface AudioPlayer {
    void play(AudioSource source, float positionSeconds);
    void pause();
    void resume();
    void seek(float positionSeconds);
    void stop();
    void setVolume(float volume);
}
```

## 9. 0.3.0 客户端/服务端协议目标

后续再建立多模块结构：

```text
CloudXMusic/
├── music-protocol/
├── cloudxmusic-client/
└── music-server/
```

服务端状态应该包含：

```text
roomId
ownerUuid
members
currentTrack
queue
playing
position
lastUpdateTime
repeatMode
```

网络只在状态变化时广播：

- 创建房间
- 加入/离开房间
- 添加/删除队列歌曲
- 播放/暂停
- 切歌
- 跳转
- 房主转移
- 权限变化

播放进度不需要每帧同步。服务端发送 `position + serverTimestamp`，客户端本地计算进度，必要时每 5 到 10 秒校准一次。

## 10. 账号和安全原则

- 网易云账号凭据只保存在客户端。
- 不把 Cookie、密码、刷新令牌发送到 Folia 服务端。
- 服务端只接受歌曲 ID、标题、歌手、时长、封面等非敏感元数据。
- 第一版不要做服务端统一代理音频。
- 不要把用户账号信息写入日志。
- 第三方网易云接口可能存在不稳定、版权和合规问题，必须把数据源封装成可替换接口。

## 11. 给后续模型的直接任务

请后续模型按下面顺序工作：

### 第一优先级（下一步）

用 `CloudXMusic-0.4.5.jar` 做实机验收：

- 启动日志应出现“已迁移旧版网易云登录 Cookie”，且 `cookies.json` 含 `MUSIC_U`。
- `/cxm status` 显示已登录；连续重启客户端后仍显示已登录。
- 即使后续一次验证码/密码登录失败，也不能清空现有 MUSIC_U 与账号元数据。
- `/cxm login qr`：扫码、手机确认后，状态应更新并自动关闭。
- `/cxm test`：确认 MP3 流式播放、暂停、继续、拖拽。
- 播放一首受限歌曲：播放地址不应再带 `authSecret`，也不应 403。
- GUI：搜索输入框（Enter/粘贴/退格）、收藏页自动加载、专辑封面、滚动歌词。
- 歌词：有轴歌词应平滑滚动；纯文本/无歌词歌曲应显示“不支持滚动歌词”。
- 队列：选歌/加队列后重启，当前歌曲和队列应恢复且保持暂停。
- 封面：歌曲列表每行有真实封面，播放页 48px 封面正常，日志不再刷 `Bad PNG Signature`。
- 歌词：深色歌词面板逐句显示原文+翻译，当前句白字高亮。
- 登录：模块右上角应显示网易云昵称，收藏页首次进入可直接加载。
- 每日推荐：进入世界后自动加载，无需手动刷新。
- 队列：每行 `×` 可单删，右上角“清空”可一键清空并持久化。
- 关于：头像、Lzasn、模组简介和版本显示正常。
- 队列、切页、灵动岛开关仍正常。
- 首次免责声明在游戏实际 GUI 缩放下完整显示；未确认前不发出 API/封面请求，确认后继续进入播放器。

### 第二优先级

根据实机日志修剩余登录/播放问题；不要再用“猜请求头”排 403，先确认 `cookies.json`
里的 `MUSIC_U` 是否在、请求 API 时是否命中登录态。

### 第三优先级

补歌词滚动动画和更完整的播放器错误提示。

> 无损（FLAC）已于 2.4.10 下线：FLAC 是 VBR，没有帧索引，seek 只能从文件头逐帧解码，
> 跳一首 4 分钟歌的第 3 分钟要重下 3/4 的文件，实测表现为「点进度条后卡住」。
> jFLAC 依赖与音质菜单里的「无损」档一并移除；旧配置里的 `lossless` 会自动回落到「极高」。

### 第四优先级

建立 `music-protocol` 和 Folia 服务端模块；Cookie、账号密码和音频凭证永远不发服务端。

## 12. 当前结论

项目已经有可构建的 Fabric 1.21.11 客户端原型和基本视觉方向，但还不是完整音乐播放器。

当前最准确的状态是：

```text
客户端工程：已建立
官方映射：已切换并可构建
H 快捷键：已完成
HUD 灵动岛：原型已完成
ClickGUI 外观：0.4.5 白粉磨砂独立画布，已完成离线截图检查
ClickGUI 交互：已完成，待人工验证
真实音频：MP3 流式播放已接入，待实机验收
网易云：登录/搜索/收藏/歌词/封面/音质已接入，待实机验收
Folia 服务端：未开始
客户端/服务端协议：未开始
```
