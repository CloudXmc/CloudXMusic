# 第三方组件声明 / Third-Party Notices

## 项目自身代码许可

CloudXmusic 的原创代码采用 **GNU GPL-3.0-only**，完整许可文本见 [LICENSE](LICENSE)。该许可不改变下列第三方组件各自的许可证；字体资源也继续遵循随文件提供的 SIL Open Font License 1.1。

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

产物里没有 `META-INF/jars/`；界面随模组提供下列开源字体。

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

这两种字体只用于界面文字排版，图标由模组代码的矢量路径绘制。

`assets/cloudxmusic/textures/gui/author.png` 是项目开发者 Lzasn 提供的个人头像，仅用于模组「关于」页面。

当前界面图标由 `gui/design/UiIcons.java` 使用矢量路径绘制，不依赖 Minecraft 位图字形或第三方图标集。旧版本曾使用原版符号字形。

## 品牌

- 英文名：CloudXmusic
- 中文名：云朵音乐盒
- 开发者：Lzasn,xWtree,cloudfl4re,xiaota888

## 历史：已移除的内置组件

| 时期 | 组件 | 现状 |
| --- | --- | --- |
| 0.1.8 ~ 0.2.3 | Modern UI (mVUS fork, LGPL-3.0) + Forge Config API Port (LGPL-3.0)，jar-in-jar | 0.2.4 起移除，`libs/nested/` 下残留的 jar 不参与构建，可删 |
| 0.2.5 ~ 0.2.8 | Microsoft Fluent Emoji 3D PNG（MIT），烘焙成 20px emoji 图集 | 0.2.9 起移除。生成工具保留在 `tools/EmojiAtlas.java`，需要时可重新生成 |
