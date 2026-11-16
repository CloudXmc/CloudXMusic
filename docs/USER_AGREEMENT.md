# CloudXMusic 用户协议与免责声明

当前界面展示的完整文本位于 [`user-agreement.txt`](../cloudxmusic-client/src/main/resources/assets/cloudxmusic/terms/user-agreement.txt)，本文件记录接入方式与维护边界。

协议适用于网易云音乐、QQ 音乐和酷狗音乐三种音源，涵盖扫码登录、Cookie 登录、账号文件、续期、搜索、歌单、云收藏、VIP / SVIP 动态权限、音频 CDN、歌词、封面、歌曲分享及音乐房间。它明确说明 CloudXMusic 是非官方客户端，平台可以对第三方客户端、异常请求或 Cookie 登录实施限流、验证、冻结或封禁。

凭证的 `cookies.json`、`netease/cookies.json`、`qqmusic/account.json` 和 `kugou/account.json` 仅写入用户本机的 CloudXMusic 目录，但以明文存储，且鉴权请求仍会把必要信息发送给对应平台。协议不承诺第三方平台的数据处理或服务可用性。

协议中的版权条款只说明本模组不提供音乐内容许可，要求用户遵守平台规则和权利人授权；它不限制 GPL-3.0-only 对软件代码的允许行为。责任条款保留适用法律不得排除的责任，避免把“免责”表述成绝对免责。

每次条款版本变更都必须递增 `UserAgreement.VERSION`。协议只在全局首次使用入口确认一次，并在全局本地配置中保存版本号；QQ 和酷狗登录页不再重复弹出协议，也不自行阻塞扫码或 Cookie 登录。保存使用临时文件加原子替换，保存失败时继续保持未确认状态。协议 UI 的所有可见文字由资源文件提供，滚动区与底部操作区分离，避免长文本遮挡按钮。
