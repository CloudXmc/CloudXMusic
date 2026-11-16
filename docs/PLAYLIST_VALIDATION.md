# QQ / 酷狗账号歌单说明与验证

版本保持 `2.6.0-alpha.3`，目标是 Java 21 / Minecraft 1.21.11 Fabric 客户端。没有 Bukkit/Paper/Folia 入口，服务端插件调度、plugin.yml、箱子 GUI、数据库和身份迁移规则不适用；不引入上述设施。

## 回退基线与范围

开发前已核对原交付 JAR 和源码 ZIP 的 SHA-256，源码 ZIP 与当前 414 个项目文件逐一一致。独立备份位于 `backups/pre-playlists-20261115-024634/`，包含源码、JAR、Git 差异和逐文件基线。不能使用 `git reset --hard` 清理本任务之前仍未提交的工作。

新增九个生产类：`qqmusic/QQPlaylistApi`、`QQPlaylistSource`，`kugou/KugouPlaylistApi`、`KugouPlaylistSource`，`gui/playlist/PlaylistSource`、`PlaylistView`、`PlaylistController`、`PlaylistLayout`、`PlaylistPainter`。

现有接线文件：`QQCatalogService`（复用加密用户标识查询）、`QQMusicIntegration`（装配及关闭）、`KugouProvider`（目录通道及有界队列元数据）、`KugouModule`（装配）、`MusicScreen` 和 `MusicPainter`。网易云源码、QQ/酷狗播放器、取链、登录和音乐房间协议均不修改。目录网络使用既有认证通道，不增加依赖。

## 接口与行为

- QQ 自建：`PlaylistBaseRead/GetPlaylistByUin`；收藏：`PlaylistFavRead/CgiGetPlaylistFavInfo`，使用加密用户标识；歌曲：`DissInfo/CgiGetDiss`。复用现有认证请求、元数据和 VIP 解析。
- 酷狗：同账号 token/userid 与隔离 Cookie，通过现有 appid=1014 的 H5 签名请求 `/v7/get_all_list`、`/v4/get_list_all_file`。目录排除删除项和系统“我喜欢”；按 type 区分自建与收藏。
- 酷狗分页以累计原始记录数判断总数，支持接口返回短页；没有总数时继续读取到空页。重复页、游标异常、空页提前结束均作为错误处理。
- 两音源在“我的收藏”页使用三个 Tab，网易云显示分支不变。封面通过 `FrostCanvas.roundImage` 进行等比居中裁剪与抗锯齿圆角遮罩。详情保留 QQ 分享和两音源加队列入口。
- 未登录点击分类走现有 `QueueToast`，原生 `SystemToast` 正文严格为“请先登录账号”。
- 播放全部使用独立后台读取，不更改现有队列。收齐成功后在客户端线程一次性提交；酷狗所有歌曲都有 sort 时稳定排序，点击单曲时保留被点击歌曲的身份。400 页、20,000 首为读取上限，超限报错并保留原队列。

## 线程、取消与资源

| 调用链 | 所有者与跨线程数据 | 结束与清理 |
| --- | --- | --- |
| MusicScreen → PlaylistController → 平台 Source/API | 客户端捕获凭证快照、分类、游标；后台只读 HTTP 与 DTO；主线程发布只读 View | 切账号、换音源、离开页面和关闭时递增代次、取消 Future、清空排队任务 |
| 播放全部 → 分页收集 → 播放器 | 后台不碰声卡；客户端核对账号、页面代次和播放标记，再调用已有 play | 中途错误不提交；重复点击不启动第二个收集任务 |
| 控制器与缓存 | 每页面单工作线程，排队容量 1；视图和完整歌曲最多 20,000 条；酷狗分页记录最多 16 个目录，每目录最多 400 页 | Screen.removed 关闭；客户端停止调用 closeAll；账号变更清理视图，旧账号完整元数据访问时失效，Provider.close 清空 |

## 自动化验证与实际限制

先新增接口和控制器测试，并保存缺失类导致的失败输出；短分页、排序后点击第一首，以及精简搜索缓存覆盖完整歌单元数据的回归测试均先确认失败再修复。覆盖分类、鉴权装配、隔离账号标识、重复条目/分页、完整队列一次提交、进度、失败保留、取消/关闭、过期回调、封面裁剪及浅/深主题绘制。测试精确统计、构建命令、JAR 内容检查和交付 SHA-256 见 `outputs/playlists-2.6.0-alpha.3/manifest.json`。

`uncertain`：未使用真实 QQ/酷狗账号完成线上目录接口验收；接口字段与服务可用性仍可能变化。未运行 Minecraft 实机，原生 Toast、按键/房间交互和真实大歌单需要游戏内确认。`defer`：创建、删除、编辑、收藏/取消收藏歌单不属于本次只读歌单范围。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。本项目是 Fabric 客户端，离线 UI 预览也不等于 Minecraft 实机测试。
