# CloudXMusic UI 知识点

本文记录当前 Fabric 客户端界面的实现约定，供后续维护与扩展时参考。

## 结构与边界

- `MusicView` 是渲染输入快照，只携带绘制需要的数据；不要把 `Minecraft`、Screen 或网络客户端传入画布层。
- `MusicPainter` 负责纯绘制，`MusicLayout` 同时定义位置和命中区域，`MusicScreen` 负责输入、动画和业务动作。
- 新页面或控件应沿用这三层边界，避免把布局、网络请求、持久化和绘制堆进一个类。

## 音乐房间

- `MusicRoomClient` 只维护客户端房间会话、请求 ID 和网络状态；绘制层只读取 `MusicView.RoomView` 不可变快照。
- 房间页分为未加入和已加入两态：未加入注册音源、创建、加入和房间列表命中区；已加入只注册退出命中区。`MusicScreen` 检测加入状态变化后重建布局。
- 房间码输入只接受 `A-Z0-9`，最多 6 位；输入焦点与搜索框焦点互斥。进入房间后当前歌曲、成员和队列全部由服务端快照驱动。
- 房间队列使用 `queueScroll`、`ROOM_QUEUE_VISIBLE` 和 `ROOM_QUEUE_STEP` 共享绘制/滚动参数；滚动条滑块高度按可见比例计算。
- 成员列表使用 `memberScroll`、`ROOM_MEMBER_VISIBLE`（6 行）、`ROOM_MEMBER_STEP`（50）和 `roomMemberScrollBar()`；上限 20 人，超出后滚轮滚动并画比例滚动条。行内状态灯 10px，与 28px 头像垂直居中。
- 房间队列条目自带封面地址与 VIP 标记（由房主的个人队列带上来），缩略图走 `coverArt()`：有图按圆角裁剪等比铺满，没图回落 `albumPlaceholder()`；标题后的 VIP 角标读同一条 `SongView.vip()`。
- 房主行状态不看本人上报，直接用房间播放态显示「正在播放/已暂停」；成员行才按 `MemberState` 显示「正在跟听/暂停跟听/缓冲中/本曲不可播放」。
- 关于页版本徽章宽度按 `c.width(text, 10, true) + 28` 实测，右对齐到 1022；不要写死宽度，`v2.5.0-alpha.2` 这类长版本号会被截断。
- 公开房间列表标题为“玩家ID的房间”，房间码仍是独立标识，不能拿玩家 ID 当房间码。

## QQ 音质、排行榜与搜索

- QQ 音质菜单有四档：臻品母带、SQ、HQ、标准。绘制、命中、徽章和实际档位必须使用同一套顺序。
- 母带使用金色 SVIP 徽章，SQ 使用绿色 VIP 徽章；徽章宽度按文字实测，不能写死。
- 官方排行榜切换榜单时分页不能换期；单曲/歌单搜索的 Tab、网格和底栏必须共用同一套命中几何。
- 灵动岛进度使用 PCM 声卡时钟；原文/译文分别滚动，QRC 高亮不能按整句纹理反复重绘。

## 头像与品牌资源

- `MojangAvatarCache` 按玩家 ID 请求 Mojang UUID、Session Server 皮肤纹理并裁切头部；网络在后台线程执行，缓存有界，失败时显示空白问号。
- 头像缓存 revision 必须进入 `MusicScreen.artworkRevision()`，否则异步加载完成后静态画布不会刷新。
- 网易云音乐、QQ 音乐图标优先读取 `assets/cloudxmusic/textures/gui/*.png`；同目录 `sources/` 保留 SVG 源文件。资源缺失时使用 `UiIcons` 的几何回退图标。

## 坐标与命中

- UI 使用固定设计坐标，Minecraft 窗口通过统一比例与偏移显示；鼠标坐标必须使用这组变换的逆变换后再命中。
- 绘制和点击共用 `MusicLayout` 的 `Box`。移动控件时一并改布局，不要在绘制代码里另写一套坐标。
- 带拖动的滑块应让命中范围略宽于可见轨道，并按可见轨道起止位置计算数值，避免控件看起来能点、实际点不到。
- `PAD` 是画布透明留白，不属于 UI 内容尺寸。它必须覆盖**阴影的实际可见范围（3σ）**，否则纹理边缘会把渐隐裁成硬线。当前整窗阴影 `σ = blur(28)`，`3σ = 84`，`PAD` 取 96，不能再往下调。

## 画布、字体与图标

- 画布按窗口 GUI 缩放和显示像素密度生成抗锯齿纹理；限制最大光栅倍率，避免高分辨率下占用过多内存。
- **光栅倍率必须对上设备物理像素**：`rasterScale = scale × GUI 缩放`（`scale` 是设计像素→GUI 像素，`guiScale` 是 GUI 像素→物理像素）。任何固定上限（曾用 1.5）在高分屏上都等于强制拉伸整张画布，字和线一起糊。上限只应该用**像素预算**来设（`MusicScreen.RASTER_PIXEL_BUDGET`，6.5M 像素）；灵动岛同理（`DynamicIslandHud` 里的 3 倍上限），真机卡顿先调这两个数。

  实测整窗玻璃（含阴影卷积）：1.5 倍 117ms / 2.0 倍 233ms / 2.4 倍 290ms。玻璃走 `MusicPainter.backdrop` 按尺寸缓存，只在改窗口尺寸、切主题、改缩放时重烤一次，切页不受影响——所以预算按清晰度优先取。
- 仅在视图状态变化时重绘并上传主纹理。高频鼠标悬停反馈应走轻量绘制层，避免每次鼠标移动都重绘整张画布。
- 字体从模组资源加载，并为中英文字符选择合适字体；新增字体资源时同步附带许可证文件。
- 普通 UI 图标使用统一尺寸、中心点和线宽的矢量路径；平台品牌图标使用项目内 PNG 资源并在小尺寸下检查清晰度。
- **切圆/切圆角的图片（头像、封面、缩略图）一律走 `FrostCanvas.roundImage`**，不要 `g.clip(shape) + drawImage`：Java2D 的 clip 是硬遮罩、不做抗锯齿，实测 36px 圆角封面在旧写法下半透明过渡像素为 **0**（纯台阶），`roundImage` 是 464。缩放本身不是问题（画布 `g` 默认就是 BICUBIC），只有头像裁切那条链显式用了最近邻。
- `roundImage` 内部用「离屏贴图 + 双线性缩放 + AA 遮罩」，最后按设备像素 1:1 贴回画布。遮罩那步有个陷阱：**`AlphaComposite` 只作用于源覆盖到的像素**，所以 `setComposite(DstIn)` 之后直接 `fill(圆)` 等于没遮罩（圆外根本没被处理）；必须先画一张盖满整块贴图的蒙版图，再 `DstIn drawImage`。
- 开销按面积走：18–36px 缩略图约 0.1–0.5ms/张（一屏 8 行合计 <1ms），110px 约 2.4ms，240px 大封面约 11ms。大封面只在 `paintCover` 换图时重画，列表缩略图只在静态层重绘时付一次，不必再加缓存层。

## 阴影与模糊

- 阴影由低分辨率蒙版、高斯卷积和有界缓存生成。扩散范围通过模糊半径与轻微扩张控制，不靠提高不透明度制造边缘。
- **蒙版必须比可见范围更大**：`ConvolveOp` 会把「离图边不足一个核半径」的像素原样拷贝，蒙版只留 `blur*3` 而核半径是 `blur*1.4` 时，外侧整圈都落在拷贝带里，高斯等于没生效——阴影会被切成一个**硬边矩形**（面板和灵动岛外面那圈「直角阴影」就是这么来的）。正确做法是蒙版四周额外留出 `radius`，并用 `EDGE_ZERO_FILL`。
- 参数对应关系：`sigma = blur * 0.5`（半分辨率坐标），也就是设计像素下 `σ = blur`，可见范围 `3σ = blur*3 = margin`。这是 `margin` 选 `blur*3` 的原因，改任一处要连带核对 `PAD`。
- 外层窗口可使用较宽、低透明度的渐隐；内部卡片和按钮的阴影应更短、更轻，层级靠明暗和间距区分。
- 改变模糊半径、扩散量或阴影尺寸后，检查四边和圆角是否渐隐到透明；画布留白不足会造成截断，过大的半径则增加纹理尺寸和卷积成本。
- 阴影缓存必须有容量上限；缓存键要覆盖会影响阴影形状的尺寸、圆角、模糊量、扩散量和颜色。
- 验证方法：不要只看图。把蒙版单列 alpha 打出来，外侧必须是**单调递减**（面板 12→0 约 48px）；出现「掉到 0 之前先持平一段」或「突然归零」就是又被边界拷贝截断了。

## 主题与缓存

- 明暗主题色统一通过 `FrostCanvas.setDark(boolean)` 切换，绘制代码引用同一批主题色字段。
- 任何独立纹理缓存都必须把 `darkMode` 放进缓存键；账号页 `accountSurface` 曾因漏掉主题键而在切换后保留旧配色。
- 暗黑模式下的浅色卡片不能继续使用硬编码白色底；应按 `CARD_BG`、`INK`、`MUTED` 等主题色计算文字和边框对比度。
- 当前播放卡与队列专辑缩略图必须复用 `albumPlaceholder(...)`，不要维护第二套占位画法。

## 透明交互界面

- 需要拖动 HUD 时，必须打开一个 `Screen` 接管鼠标；不能只靠 HUD 回调轮询 `MouseHandler`，否则游戏会保持鼠标捕获并触发挖掘/放置。
- `IslandPositionScreen` 负责点击命中、拖动、松开保存，并在 `render(...)` 每帧调用 `DynamicIslandHud.renderAdjustmentOverlay(...)` 绘制遮罩、灵动岛和提示。
- 调整界面存在时，普通 `DynamicIslandHud` 回调必须直接返回。不能让 `adjustingIslandPos` 这个瞬时状态位单独决定提示层是否可见，否则第二次进入会出现屏幕已打开但遮罩和文案不绘制。
- 透明调整界面必须重写 `renderBackground(...)` 为空，避免原版菜单背景模糊把 HUD 一起糊住。
- 内部卡片需要单侧直角时使用路径轮廓（例如 `leftFlatCard`），不要用四角统一的圆角矩形。

## 静态层与动态层

界面按「变化频率」拆成两张纹理，这是性能红线，不要合并回去：

- 静态层（`MusicPainter.paint`）：整窗磨砂玻璃、导航、卡片、列表与文字。只在 `MusicView.staticView()` 变化时重绘，整窗玻璃还会按尺寸烘焙缓存一次（`MusicPainter.backdrop`）。
- 动态层（`MusicPainter.paintDynamic`）：播放进度、进度时间、音量，区域由 `MusicLayout.dynamicBands` 定义，约 10Hz 刷新，面积只有全画布的 5%。
- 原因：Java2D 的「圆角路径 + 对角渐变 + 抗锯齿 + SrcOver」是最慢的软件光栅化路径，一张 1100×674 的整窗玻璃在 1.5 倍画布上单次就要数百毫秒。把播放进度塞进静态视图，等于每秒重画 10 次整窗，帧率会直接掉到个位数。
- 新增会随播放时钟变化的内容时，必须把它画进 `paintDynamic`，并同时扩展 `dynamicBands`；放回静态层会立刻退回上述问题。
- **`staticView()` 里不能留任何每帧变化的字段**。`MusicView` 是 record，`equals` 逐字段比较：房间页的 `RoomView.positionTenths` 是本地外推的实时位置，曾被一起带进静态快照，结果房间页以 30Hz 重绘整幅画布（关面板时表现为严重掉帧）。现在由 `RoomView.withClock(0)` 抹掉，进度与已播时间改在 `paintDynamic` 的 ROOM 分支里画，条带见 `dynamicBands(ROOM)`。
- 房间进度用独立纹理 `roomSurface`，**不能复用 `overlay`**：QQ 音源下 `overlay` 已经被底栏播放条占用，两者落点完全不同。
- 阴影放大使用双线性插值（`FrostCanvas.shadow`），不要改回双三次：阴影本身已模糊，双三次只会让每次拖尾多花一倍 CPU。

## 3D 世界歌词（MuoniumPlayer 世界阶段移植）

当前生效实现是 `com.muoniumplayer.*` 下的 MuoniumPlayer 世界歌词栈，由
`WorldLyricsRenderTailMixin` 注入 `LevelRenderer#renderLevel(...)` 的尾部。
旧 `dev.lzasn.cloudxmusic.client.world.*` HUD/FOV 自研版已删除，设置页只读写
`MusicState` 中由 `WorldLyricConfig` 同步到 `HudConfig` 的字段。

- Mixin 必须在 `renderLevel` 尾部绘制；Fabric 的 `WorldRenderEvents.END_MAIN`
  位于云、天气与后处理链之前，歌词会被后续 pass 覆盖。Loom remap 后目标方法为
  `method_22710`，构建时不要手写 refmap 覆盖这一结果。
- `WorldLyricsBridge` 自己构造 `rotateX(pitch) · rotateY(yaw + 180°)` 的视图姿态，
  临时把 `RenderSystem.getModelViewStack()` 置为单位阵，并在结束帧恢复。
- `keepDistance` 的语义是锚点随玩家平移、方位偏移守恒，所以歌词表现为始终贴在
  视野前方，不是固定世界坐标。不要复活旧版的弹簧/贴边/屏幕投影。
- 关深度测试、关剔除并保持透明度混合：歌词不会被方块遮挡。Iris 激活时
  `WorldBillboard` 走信标全亮 RenderType，必要时可切到 `hudDraw` 路径。
- `LyricGlyphAtlas` 按字符烘焙 Skija 超采样字形；墨格与光晕格分离，支持经典光晕、
  HDR bloom、逐字 pop、绘光拖尾和心象分块。字体使用项目现有 Inter + Noto Sans SC，
  不随包分发 MuoniumPlayer 的苹果系统字体。
- 「经典」使用逐字晕格；「辉光」改走整行低频 bloom。辉光分支会额外放大发光强度与
  扩散半径，并在白天提高到 0.82 基础能量，否则两种模式会收敛成几乎相同的细边光。
- 心象块内偏移必须用「本字 x − 本块首字 x」，块首 x 从当前行 `-lineWidth/2` 起算；
  不能复用整行左缘，也不能用只在逐字时间轴模式推进的 `n`。否则无 QRC 时所有字会
  归到首块，并把整行累计宽度重复加进每个块，表现为巨大字间距。
- `CloudMusic.update(...)` 在客户端 tick 中把双音源 `PlaybackSnapshot` 转换成
  `LyricLine`/`Word`；`lyricsTrackKey` 与 `lyricsRevision` 必须随切歌或歌词刷新更新。
  `LyricMode.OFF` 只控制灵动岛，不参与世界歌词可用性判断，两套显示必须保持独立。

## 动画与交互性能
- 歌曲列表不绘制整行悬浮底色；静态画布与 `MusicScreen` 实时绘制层都应遵守，避免静态层删掉高亮后又被实时圆角遮罩补回。保留行点击命中与按钮反馈。
- 动画根据帧间隔或绝对时间计算，不按固定帧数递增，避免不同帧率下速度不一致。
- 音乐进度只保留必要的小数精度用于画面刷新；拖动时先更新预览，松开后再请求音频跳转，避免反复重启解码。
- 页面切换、封面加载和账号请求应异步处理；回到客户端线程后再更新界面状态。
- `removed()` 中释放动态纹理和界面持有资源；网络回调要避免更新已关闭页面或已失效会话。

## 面板升降与灵动岛过渡

- 切页只换内容，**不要缩放整块面板**。曾把 Q 弹缩放加在整个面板上，点导航时整窗一跳一弹，观感很硬；面板是外壳，只有内容该动。
- 面板打开/关闭走 `MusicScreen.slideProgress()`：420ms、四次方缓出，`closing` 时取反，开与关共用同一条曲线。位移直接加在 `pose` 最外层，`localX/localY` 的逆变换必须同步减去它，否则点击会整体偏移。
- 关闭动画结束不要立刻 `setScreen(null)` 再 `return`——那样鼠标捕获和 HUD 恢复会推迟到下一个渲染阶段；交给 `minecraft.execute(...)` 在主线程 tick 里切界面。
- 灵动岛过渡有独立参数（`DynamicIslandHud.TRANSITION_NANOS`），不要和面板共用时长：面板是一整块大玻璃，420ms 看得清；灵动岛只有一条药丸，同样参数会「一闪就没」。
- 缓动指数别一味求大。四次方在 1/4 时间点就走完 68% 行程，小元素用它会像没动画；二次方在同样时间点只走 44%，过程才看得见。
- 飞入/飞出的位移距离按「元素到目标边缘的实际距离」算，不要按整屏倍数：岛贴着上边时只需走几十像素，按整屏算会快到看不清。
- 收起动画不能带过冲：back 缓动会让缩放值越过 0 变成负数（元素翻转），位移也会折返一次。过冲只用于展开。

## 遮罩与背景

- `MusicScreen.renderBackground(...)` 必须是空实现。原版 `Screen.render()` 会自动调它，默认实现又铺一次模糊加暗色，那层透明度写死在 MC 里、也不认 `closing`——面板滑走了画面还压着灰，表现为「遮罩慢半拍」。
- 需要淡出的遮罩只能用 `graphics.fill` 画纯色层，alpha 跟动画进度走。原版 `renderBlurredBackground` 是整屏后处理，无法调节透明度，只能选择「铺」或「不铺」。

## 导航与滚动

- 导航区位置由 `MusicLayout.NAV_TOP/NAV_BOTTOM/NAV_H/NAV_STEP/NAV_COUNT` 统一控制，`navScroll` 参与命中区计算；完全滚出可视区的项不要注册命中区，否则点空白会误切页面。
- 导航项多于可视高度时靠滚动承载，不要压缩项高去硬塞；压缩会连带挤到下方的账号组。

## 验收清单

- Java 21 下运行 `:cloudxmusic-client:test`、`:cloudxmusic-client:renderUiPreview` 和 `:cloudxmusic-client:build`。
- 预览检查四档音质菜单、排行榜、单曲/歌单搜索、房间两态、对齐、文字截断、阴影四边渐隐和滚动条。
- 在 Minecraft 里按不同窗口尺寸与 GUI 缩放验证点击、拖动、键盘输入、页面切换和纹理释放；离线预览不能代替实机验证。
