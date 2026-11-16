# 酷狗取链逐项核对与只读实测 · 2026-11-16

版本保持 `2.6.0-alpha.3`，目标为 Java 21 / Minecraft 1.21.11 Fabric 客户端。

## 结论与边界

本记录整理酷狗 Web / App 请求契约、固定签名向量测试和脱敏请求/响应日志。
**没有取得该账号收藏样本的 FLAC/Hi-Res 链接，不能将本次交付称为会员无损修复成功。**
当前结论仅针对已记录样本，不代表所有账号、曲目和客户端的表现。
第三方来源、固定参考版本与许可统一记录于 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。

## 逐项源码证据

| 项目 | 请求契约 | CloudXMusic 验证结果 |
| --- | --- | --- |
| Web 入口 | GET `https://wwwapi.kugou.com/play/songinfo` | 同端点、GET，无 Body |
| Web 音质参数 | **没有 `quality` 参数**，也没有数字 `4` 音质请求 | 不编造 `flac/high/4` 参数 |
| Web 歌曲参数 | 原始规范 `hash`、`album_id`（或空字符串）、`platid=4` | 修正此前将 Web hash 转小写的差异 |
| Web 身份 | `userid`、`token`、`mid=deviceId`、`uuid=deviceId`、`kg_dfid` Cookie 或 `-` | 带当前账号与作用域 Cookie；缺失指纹不伪造 |
| Web 通用参数 | `appid=1014`、`srcappid=2919`、`clientver=20000`、毫秒 `clienttime` | 完整参数固定向量校验 |
| Web 签名 | Web 固定盐 + 按键排序的 `key=value` 无分隔拼接 + Web 固定盐，MD5 大写 | 原算法已一致，增加独立已知答案校验 |
| App 入口 | GET `https://gateway.kugou.com/v5/url` | 保留备用通道 |
| App 音质 | 当前路径只循环 `320`、`128` | 不向该路径发送未经验证的高档请求 |
| App hash/mid | hash 小写；mid 为 `MD5(deviceId)` 的十进制大整数 | 完整固定向量校验 |
| App 客户端 | `appid=1005`、`clientver=11430`、秒 `clienttime`；歌曲元数据缺失为 `0` | 不套用资料接口的 20489 客户端参数 |
| App key | MD5(hash + track 固定盐 + `1005` + mid + userid) | 独立固定向量校验 |
| App 签名 | App 固定盐 + 排序参数 + 空 Body + App 固定盐，MD5 小写 | 独立固定向量校验 |
| App 路由头 | `x-router=trackercdn.kugou.com`、mid/dfid/clienttime、kg-rc/kg-rec/kg-thash/kg-rf | 完整请求头断言 |
| 普通取链 UA | `Mozilla/5.0` | Web 与 App 播放路由均为此值 |
| Referer/Cookie | `https://www.kugou.com/`；仅发送 CookieManager 对目标 URI 返回的 Cookie | 相同，不生成额外“加密 Cookie” |

解码器接受 `flac` 不等于当前路径能够请求或取得 `flac/high` 资源。
概念版账号和私有云使用不同的权限与资源范围，不能替换普通账号的歌曲取链协议。

兼容保留：CloudXMusic 在无 `kg_dfid` 时可读取既有的 `dfid` Cookie 别名；若两者都没有，仍传 `-`。
本次实测账号两种方式均未提供有效 dfid，不存在此别名导致的对照差异。

## 固定签名向量

测试使用虚构账号，时间固定为 `1700000000123` 毫秒。期望值由独立 MD5 实现按源码公式计算，
不调用被测签名函数生成期望值。App 排序按字符序，特别覆盖 `IsFreePart` 的大小写。

- Web：`CC6DD7AA7D723D441EAADCE9E1DA1EDF`。
- App：`244510f2c6a6f438681cb2f7ee9e7140`。
- App key：`0df423227691478581873bec61b21268`。

完整歌曲、账号、参数和请求头夹具见 `KugouPlaybackSigningContractTest`。

## 真实账号只读对照

使用游戏实例现有账号，只读取云收藏和播放接口；没有换票、刷新登录、修改收藏、领取会员或更改账号文件。
账号文件前后 SHA-256 比较一致（只保存布尔结果，不公开账号文件摘要或内容）。

对同一首 VIP 标记收藏曲，使用完全相同的已签名参数，分别调用：

1. CloudXMusic 当前 `KugouHttp`。
2. 使用相同 UA、Referer、Accept-Encoding 和 Cookie，通过 `HttpURLConnection` 执行的临时对照器。

| 请求 | 当前 HTTP | 独立传输对照 |
| --- | --- | --- |
| Web，带 album_id | HTTP 200，status=1，err_code=0，完整链接，bitrate=96 | 相同 |
| Web，去 album_id | HTTP 200，status=1，空链接，has_privilege=false | 相同 |
| App 320，带元数据 | HTTP 200，status=2，error_code=20018，priv_status=0，auth_through=[] | 相同 |
| Web 音频内容探测 | CDN HTTP 206，实际为标准档 Ogg/Vorbis | 相同 |

App 原始 JSON（本回包无凭证字段）：

```json
{"priv_status":0,"error_code":20018,"trans_param":{"display":0,"display_rate":0},"status":2,"auth_through":[]}
```

回包没有更具体的错误消息。这个结果不能区分凭证所属客户端、账号/曲目权限、地区或平台限制，
也不能仅凭该错误码断言签名错误或设备风控。带 album_id 的 Web 成功不支持“所有请求都签错”的判断。
当前参数校验和传输对照均不能证明此曲可以取得无损，需要已验证可工作的高档协议或平台端进一步证据。

## 诊断日志与范围

- `[酷狗取链请求]`：关联 id、真实端点及参数名称、脱敏参数值、实际请求头、Cookie 名称，GET 的 payload 为 null。
- `[酷狗取链HTTP]`：同一 id 的 HTTP 状态。
- `[酷狗取链响应]`：保留回包结构、错误码/消息、权限、格式/码率、未知嵌套字段和数组。
- token、userid、设备标识、签名、key、Cookie 值和带签名 CDN 地址递归遮蔽；错误消息回显的请求秘密同样遮蔽。
- 响应日志最多 16 KiB，另有深度、节点、字段、字符串预算。截断保留有效 JSON；非 JSON 仅记类型和字节数。
- HTTP 4xx 先记录脱敏响应再抛错，防止诊断信息被状态校验丢弃。
- 仅两个播放路由打印此诊断，不为登录或刷新请求记录正文；不新增持久缓存或任务池。

该阶段只修改 `kugou/`、其测试和本文档，修改前备份及原始验证记录保留在本地 outputs 目录，不进入发行源码包。
202 个非酷狗生产源码逐文件 SHA-256 对照一致；版本、依赖与其他音源代码保持本轮开始时状态。
流式解码、Range、isSeeking、FLAC 缓冲、声卡 drain 沿用此前恢复版本。

线程边界：UI 提交歌曲不可变快照 → 原后台 resolver → 有期限 HTTP → 有界 CDN 探测 → 原播放会话。
新增日志处理在这些后台上下文同步执行；没有访问游戏实时对象，也没有增加跨线程集合或长期资源。
HTTP Futures、响应流和执行器沿用现有关闭/取消路径。

## 验证与尚未解决项

TDD 首轮 11 项目标测试中 4 项失败，证实 Web hash 与未知字段诊断缺陷；随后修复。
另先复现 Cookie 布尔标记误遮蔽 status/client id 的测试，再修复。
`.\gradlew.bat clean build --offline --console=plain` 实际成功，38 秒、11 个任务全部执行。
JUnit XML 汇总为 **123 套件、632 项测试，失败 0、错误 0、跳过 0**。
源码 ZIP、JAR 内容检查及 SHA-256 见交付目录的 `VERIFICATION.md`。

`uncertain`：此真实账号样本的 FLAC/Hi-Res 取链仍未成功；没有在游戏内、真实声卡上验收本构建。
`defer`：酷狗房间联机保持禁用，未进行其他音源或登录体系重构。
YAML/plugin.yml、Bukkit/Folia、箱子 GUI、数据库和服务器 Help 检查不适用于本 Fabric 客户端。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
本项目也未进行本轮真实 Minecraft/Fabric 客户端运行验收。
