# WLED Grabber 品牌与图标规范

> 版本 1.0 · 状态：资源已落地，三模块 Debug 构建通过；真机 launcher / themed icon / TV banner 显示待验收。
> 关联技术规范：[WLED Grabber 技术架构](../architecture.md)。本文是图标、色板、字标与 Android 资源映射的唯一设计规范。

## 1. 设计决策

### 1.1 标志构成原则

标志由四段独立灯带围成屏幕方框，转角留接缝，四色顺时针排列。它直接对应四边布局与四段可寻址灯带：颜色顺序即物理槽位顺序。

四条边、逐边采样与单设备是产品的核心结构；颜色不是装饰，而是技术规范 §4.1 的 `Top L→R / Right T→B / Bottom R→L / Left B→T` 顺时针索引顺序。深色屏加渐变字母的形态不足以表达四边与逐边，且字母标记容易与 WLED 官方身份混淆，不采用。

设计资产：[三案概念板](concepts.png)、[彩色方向](color-sheet.png)、[方案对比](before-after.png)。

## 2. 标志构造

### 2.1 几何

在 256×256 网格上：

| 元素 | 数值 |
|---|---|
| 屏幕参考框 | `x=32..224`，`y=32..224`（192×192） |
| 单段灯带 | 长 124、厚 26，圆角半径 13 |
| 四段位置 | top `(53,32,150,26)`、right `(198,53,26,150)`、bottom `(53,198,150,26)`、left `(32,53,26,150)` |
| 转角接缝 | 两段圆角端之间约 4 单位（对角方向），是四条独立灯带的接缝，不是误差 |
| 画布留白 | 四周 32（12.5%），即标志的最小清空区 |

### 2.2 颜色顺序

渐变端点必须成对相接，形成闭环：

| 边 | 起点 → 终点 | 颜色 |
|---|---|---|
| Top | `(66,45)` → `(190,45)` | Cyan `#22E0FF` → Violet `#7A5CFF` |
| Right | `(211,66)` → `(211,190)` | Violet → Magenta `#FF4FBE` |
| Bottom | `(190,211)` → `(66,211)` | Magenta → Amber `#FFB454` |
| Left | `(45,190)` → `(45,66)` | Amber → Cyan |

四边方向固定为 **顺时针**：Top 左→右、Right 上→下、Bottom 右→左、Left 下→上，与 §4.1 的逻辑槽位一致。任何旋转、镜像或色序调整都会改变物理含义，非经技术规范变更不得使用。

### 2.3 清空区与最小尺寸

- 标志清空区：四周至少 **一个灯带厚度**（26/192 ≈ 13.5% 标志宽度）。
- 横向 lockup：标志与字标间距 76 单位（约 1× 字标 cap height）；lockup 四周至少 0.5× 字标 cap height（≈28 单位）。
- 最小尺寸：app icon 48dp；独立标志 24dp；通知图标 24dp；TV banner 320×180；数字 lockup 宽度 ≥ 120px。
- 16–24px 时四段接缝会自然闭合，只剩方框轮廓，这是可接受的；不要为了小尺寸额外加细节。

## 3. 色板

| Token | HEX | RGB | CMYK（涂布纸，需打样） | 用途 |
|---|---|---|---|---|
| Ink | `#0B0F1A` | 11, 15, 26 | 58, 42, 0, 90 | 图标 tile、深色背景、正文 |
| Ink Lift | `#121A2B` | 18, 26, 43 | 58, 40, 0, 83 | 仅用于径向背景中心 |
| Cyan | `#22E0FF` | 34, 224, 255 | 87, 12, 0, 0 | 顶边起点；深色底上的强调 |
| Violet | `#7A5CFF` | 122, 92, 255 | 52, 64, 0, 0 | 上/右边过渡；**不用于小字** |
| Magenta | `#FF4FBE` | 255, 79, 190 | 0, 69, 25, 0 | 右/下边；深色底强调 |
| Amber | `#FFB454` | 255, 180, 84 | 0, 29, 67, 0 | 下/左边；深色底强调 |
| Paper | `#F5F7FB` | 245, 247, 251 | 2, 2, 0, 2 | 深色底字标、浅色底容器 |

对比度（近似，WCAG 相对亮度）：Paper on Ink ≈ 17.8:1；Cyan on Ink ≈ 12.0:1；Magenta on Ink ≈ 6.5:1；Amber on Ink ≈ 10.8:1；Violet on Ink ≈ 4.4:1。**Cyan/Amber/Magenta 在白色上只能作图形，不能作小字**；浅色底一律使用 Ink 单色版本。Pantone 匹配需以印刷打样为准，不要从 HEX 反推编号。

一色/反白规则：

- 浅色底：Ink tile + Paper 灯带；或 Ink 标志 + Ink 字标。
- 深色底：Ink tile + 彩色灯带；或 Paper 字标 + 彩色灯带。
- 纯单色：Ink（浅底）或 Paper（深底）；不得用彩虹径向替代。

## 4. 字体与字标

- 字标：**Inter**，`WLED` 用 500、`Grabber` 用 700；词间距 0.32em，tracking 0；字号 76（960×256 横向 lockup）。
- 字标已转为轮廓路径，masters 内没有 `<text>`；不随系统字体变化。
- 许可：Inter 为 SIL Open Font License 1.1，可用于标志；字体文件不随仓库分发，字标轮廓已内嵌。
- 字标的基线在 256 高画布上为 153，与标志 32..224 的视觉中心对齐；修改字号/字重后需重新检查基线与间距。
- 字标不用于 Android app 内部 UI；当前用于 TV banner、README/store/演示板。app 内部仍使用系统字体。

## 5. 资产与集成

### 5.1 版本化源文件

位于 `docs/design/app-icon/source/`：

| 文件 | 用途 |
|---|---|
| `wled-grabber-icon-512.svg` | 传统方形 app icon 母版（512，圆角 tile） |
| `wled-grabber-icon-round-512.svg` | 圆形 app icon 母版 |
| `wled-grabber-icon-mono-dark.svg` / `-mono-light.svg` | 单色深/浅底变体 |
| `wled-grabber-symbol.svg` / `-ink.svg` / `-white.svg` | 独立标志（透明底） |
| `wled-grabber-lockup-horizontal-dark.svg` / `-light.svg` | 横向 lockup（深/浅底） |
| `wled-grabber-lockup-stacked-dark.svg` / `-light.svg` | 纵向 lockup（深/浅底） |
| `wled-grabber-banner.svg` | TV banner 源文件（320×180） |
| `wled-grabber-notification-24.svg` | 24dp 通知图标源文件 |

### 5.2 Android 资源映射

| 资源 | 说明 |
|---|---|
| `common/src/main/res/drawable/ic_launcher_background.xml` | 实色 Ink 背景（API <24 兜底） |
| `common/src/main/res/drawable-v24/ic_launcher_background.xml` | Ink → Ink Lift 径向渐变（API 24+） |
| `common/src/main/res/drawable-v24/ic_launcher_foreground.xml` | 彩色四段，108dp 前景；标志占 40%（43.2dp） |
| `common/src/main/res/drawable/ic_launcher_monochrome.xml` | Android 13 themed icon 单色层（白色四段） |
| `common/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`、`ic_launcher_round.xml` | adaptive icon：background / foreground / monochrome |
| `common/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` | 48/72/96/144/192 传统方形图标 |
| 同名 `ic_launcher_round.png` | 同尺寸圆形图标 |
| `common/src/main/res/drawable/ic_notification_icon.xml` | 24dp 白色四段通知图标 |
| `tv/src/main/res/drawable/banner.png` | 320×180 TV banner（mdpi） |
| `tv/src/main/res/drawable-xhdpi/banner.png` | 640×360 TV banner（xhdpi） |

mobile 与 tv 的 `AndroidManifest.xml` 通过 common 的 application `android:icon` / `android:roundIcon` 继承图标；TV 额外使用 `android:banner="@drawable/banner"`；通知图标由两端 manifest 的 service `android:icon` 引用同一资源。

adaptive 几何：108 viewport 中 `symbol × 0.225 + 25.2`；标志最远点距中心 25.2dp，小于 66dp 安全圈半径 33dp，圆形/异形 mask 下不会被裁切。可见 72dp 视口中标志约占 60%。**不要**把前景放大到 40% 以上而不重新核算安全圈。通知图标：`symbol × (20/192) + (24 − 256×20/192)/2`。

### 5.3 主题与后续 UI

图标、通知、TV banner 与本文规范已落地。app 的 `colors.xml`（`colorPrimary #3F51B5`、`colorAccent #FF4081`）使用独立的 Material 色板，不随图标配色修改；如调整 UI 主题，以 §3 的 token 为源，并重新检查浅色背景上的对比度（尤其 Cyan/Violet）。

## 6. 使用规则

**Do**

- 彩色四段只放在 Ink 或深色照片上；浅色底用 Ink 单色版。
- 保持四边色序与顺时针方向；保持约 4 单位的转角接缝。
- app icon 使用 Ink tile；锁屏/通知/紧凑位可用独立标志或 24dp 通知切图。
- 大尺寸可保留渐变；小尺寸允许接缝闭合为方框，不要加细节补偿。
- 动画/动效中，颜色流动方向与 §4.1 的顺时针索引一致。

**Don't**

- 不重排四色、不旋转/镜像单独一边、不闭合接缝（会改变“四段独立灯带”的含义）。
- 不在白底上直接放彩色四段（Cyan/Amber 对比不足）；不加阴影、外发光、描边或 3D 效果。
- 不使用与本标志冲突的元素：彩虹径向渐变、其他应用的字母标或与本标志无关的 banner 画面。
- 不用字母 W tile 替代本 app 图标；不把标志拉伸成非正方形。
- 不在图标内加入小字、LED 点阵或额外装饰。

## 7. 迭代与验收

### 7.1 修改规则

1. 以 `source/` 下的 SVG 为唯一母版；256 网格、灯带 124×26、r13、约 4 单位接缝、固定色序，不要用位图重画。
2. 改色只改 §2.2 的渐变端点，并保证相邻边端点同色；改形状需同步更新本文 §2.1 与所有派生资源。
3. 字标如需改字/改重，必须用 Inter 重新转轮廓，并复核基线与词距；masters 内不允许 live text。
4. 更新后同步替换 Android 资源，并按 §7.2 复验。

### 7.2 重新生成与验证

- 传统 PNG：从 `wled-grabber-icon-512.svg` / `wled-grabber-icon-round-512.svg` 渲染 192px，再降采样到 144/96/72/48；输出到 `mipmap-*`。
- adaptive：前景按 `×0.225 + 25.2`、通知按 `×0.10417 − 1.333` 重算 VectorDrawable 路径与渐变坐标。
- TV banner：`wled-grabber-banner.svg` 渲染 320×180 与 640×360，分别写入 `drawable/` 与 `drawable-xhdpi/`。
- 构建验证（exit 0）：
  `JAVA_HOME=.toolchains/jdk-17.0.21+9/Contents/Home ./gradlew :common:assembleDebug :mobile:assembleDebug :tv:assembleDebug`
- lint 验证（exit 0）：`:common:lintDebug`；APK 内已确认包含 adaptive foreground/background、monochrome、24dp 通知图标与 320/640 TV banner 资源。
- 视觉验收：API 26 adaptive、API 33 themed icon、圆形 mask、API 21 legacy PNG、通知栏 24dp、TV banner 320/640；检查圆形 mask 无裁切、低亮度屏幕上 Cyan 仍可辨。
- 商标：WLED 是第三方开源项目，本 app 仅作为 WLED 生态的采集端；官方 Akemi 为像素灯泡字标，与本标志形态不同。公开发布前仍应做一次商标检索，不声称官方关联。

## 8. 参考

- [技术架构 §4 四边布局与物理映射](../architecture.md#layout)
- [完整套件预览](kit-sheet.png) · [三案概念板](concepts.png) · [彩色方向](color-sheet.png) · [方案对比](before-after.png)
- WLED 官方品牌资料（仅作差异参考）：<https://github.com/wled/WLED/tree/main/images>
