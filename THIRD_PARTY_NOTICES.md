# 第三方组件声明 / Third-Party Notices

## 当前状态：不内置任何第三方**模组**（但内置 MP3 解码与二维码库）

## 内置代码：JLayer (MP3 解码)

- 文件：`libs/jlayer-1.0.1.jar`，类被直接合并进产物 jar（`javazoom.jl.*`）
- 版本：1.0.1
- 作者：JavaZoom / Laurent B…（原作者 J. C. Beaulieu 等）
- 许可：**LGPL-3.0-or-later**
- 来源：https://repo1.maven.org/maven2/javazoom/jlayer/
- 说明：只用于把网易云的 mp3 流实时解码成 PCM 交给 `SourceDataLine` 播放，未做任何修改。
  按 LGPL 要求保留声明并提供源码获取途径（见上面的 Maven 链接与 javazoom 官网）。

## 内置代码：ZXing Core (二维码编码)

- 文件：`libs/zxing-core-3.5.3.jar`，类被合并进产物 jar（`com.google.zxing.*`）
- 版本：3.5.3
- 作者：ZXing Authors
- 许可：**Apache-2.0**
- 来源：https://github.com/zxing/zxing
- 说明：只用来把扫码登录链接编码成二维码矩阵，未做修改。

## 界面资源

产物里没有 `META-INF/jars/`，也没有第三方字体或图标图集。

`assets/cloudxmusic/textures/gui/author.png` 是项目作者 Lzasn 提供的个人头像，仅用于模组「关于」页面。

界面里的图标用的是**原版字体自带的特殊符号字形**（`assets/minecraft/font/include/default.json`
里的 `nonlatin_european.png`），只是普通文本字符，不需要额外资源，也不涉及第三方授权：

| 用途 | 符号 | 码点 |
| --- | --- | --- |
| 正在播放 | ♪ | U+266A |
| 发现 | ★ | U+2605 |
| 搜索 | ◎ | U+25CE |
| 房间 | ⌂ | U+2302 |
| 队列 | ☰ | U+2630 |
| 关于 | ? | U+003F |
| 上一首 / 播放 / 暂停 / 下一首 | ⏮ ▶ ⏸ ⏭ | U+23EE / U+25B6 / U+23F8 / U+23ED |

这些码点都逐个核对过在 1.21.11 的原版字体表里确实有字形，不会渲染成豆腐块。

## 历史：已移除的内置组件

| 时期 | 组件 | 现状 |
| --- | --- | --- |
| 0.1.8 ~ 0.2.3 | Modern UI (mVUS fork, LGPL-3.0) + Forge Config API Port (LGPL-3.0)，jar-in-jar | 0.2.4 起移除，`libs/nested/` 下残留的 jar 不参与构建，可删 |
| 0.2.5 ~ 0.2.8 | Microsoft Fluent Emoji 3D PNG（MIT），烘焙成 20px emoji 图集 | 0.2.9 起移除。生成工具保留在 `tools/EmojiAtlas.java`，需要时可重新生成 |
