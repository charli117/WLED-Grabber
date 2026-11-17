# WLED Grabber

Repository: <https://github.com/charli117/WLED-Grabber>

[English](#english) | [简体中文](#zh-cn)

<a id="english"></a>

## English

WLED Grabber is an Android screen grabber that sends the colors of the screen directly to a single [WLED](https://wled.me/) controller. The app captures the screen, samples it locally through a four-side Classic layout with per-LED averaging, and streams the result over UDP/DDP. No server or other middleware is involved.

> **Status (2026-11-17): under active development. No signed APK has been released, and no release admission has been granted.**

### Features

- **Four-sided Classic layout**: independent Top/Right/Bottom/Left counts, clockwise logical slots, and no extra corner LEDs.
- **Physical mapping controls**: SKIP/BLACK gap, Input, Reverse, horizontal/vertical depth, overlap, edge gap, and four independent corner positions.
- **Exact per-LED sampling**: integer rectangle averaging over the captured frame (long edge capped at 640 px, no sparse sampling). Average-color mode still honors gap and tail-black masks.
- **RGB and RGBW**: `min_subtract` and `preserve` white synthesis. RGBW requires DDP and WLED auto-white set to Manual.
- **WLED protocols**: DRGB/DNRGB for RGB and DDP for RGBW; DDP is also available for RGB. Type-5/DNRGBW is not used.
- **Device setup**: NSD discovery, a bounded local scan, and manual host/port entry. Control and readback use `/json/info` and `/json/state`.
- **Shared UI**: complete mobile settings with a Quick Settings tile; TV-first Leanback setup with a real-time layout editor; shared preview and three test patterns (four-side colors, walking pixel, solid color).
- **Controlled background start**: on Android 10 (API 29) devices with the documented permissions, boot can request one capture session through `BootActivity`; otherwise boot only posts a visible reminder.
- **Deterministic stop**: a single stop barrier sends a black frame, turns WLED off over HTTP, and reads the device state back. The release state is `CONFIRMED` only after readback; otherwise it is `UNCONFIRMED`.

**Not in scope**: cloud/remote access, multi-device sync groups, audio capture, DRM bypass, 2D matrix/PWM layouts, screen-content recognition, WLED firmware modifications, and Google Play distribution.

### Device prerequisites

- **WLED real-time addressing**: offset 0, DMX start address 1, main-segment-only off, and no extra LED map or reversed bus.
- **RGBW**: DDP plus WLED auto-white set to Manual; the global override must not be set back to automatic.
- **Controlled boot on Android 10**: first-time setup completed, the documented ADB app-ops, a background-start capability (overlay or device owner), and battery whitelisting. See the [configuration and troubleshooting guide](docs/guides/configuration-and-troubleshooting.md).

### Compatibility and acceptance status

| Area | Status (2026-11-17) |
|---|---|
| Android | minSdk 21, compileSdk/targetSdk 35. Instrumented coverage includes API 21/26/29/33/34, with partial coverage on API 35; API 23 and the full device matrix are not yet covered. |
| WLED firmware | Verified against the v0.15.1 source baseline; target-device tests ran on WLED 16.0.1. Other versions and custom builds require explicit compatibility testing. |
| RGB | Implemented. On the first 274-LED target (Sony TV + WLED), software-side sampling p95 ≈ 4.05–4.5 ms and send interval p95 ≈ 17.3 ms. Physical refresh rate and end-to-end latency are not yet measured. |
| RGBW | Implemented with DDP, Manual auto-white, and both white modes. No RGBW strip has been accepted on hardware. |
| LED counts | 274 LEDs is the first target. 500/1000/2000-LED configurations are unverified. |
| ESP8266 | Configuration entry and performance warning are retained; ESP32 performance conclusions are not inherited. |
| Target hardware | Sony KD-55XD8005 (Android 10 TV) + ESP32-WROOM-32D + 274 × WS2815 RGB. Remote-control, large-font, and physical-output acceptance is pending. |

Software-side evidence covers the layout, sampling, protocol, lifecycle, and the target TV's capture and release paths. Release admission is still blocked by missing physical FPS/end-to-end measurements, RGBW hardware, larger LED configurations, and the remaining device/API matrix. A successful build does not imply signing, installation, or release.

### Build

Requirements: JDK 17 and Android SDK 35.

```sh
scripts/build-client.sh -m all -v both
```

Debug APKs (debug key) and unsigned release APKs are written to `dist/`. Signing, installation, and publishing are separate, explicitly authorized steps. The `mobile` and `tv` APKs share the same applicationId, so they are alternative packages and cannot be installed side by side.

### Documentation

The design and configuration documents are currently in Chinese.

- [Technical architecture](docs/design/architecture.md)
- [Configuration and troubleshooting](docs/guides/configuration-and-troubleshooting.md)
- [Brand and icon specification](docs/design/app-icon/brand-guide.md)

### Privacy and security

- Only per-LED color values and WLED discovery/control requests leave the device. Screen images are never stored or uploaded, and the app does not request microphone or audio capture.
- LAN traffic uses cleartext HTTP/UDP without authentication. Other devices on the same LAN may control WLED.
- DRM/`FLAG_SECURE` content is not captured or bypassed.
- If the process is killed, lights-off is not guaranteed; the app reports `UNCONFIRMED` when it cannot confirm the device state.

### License

Copyright © 2026 Charli117. WLED Grabber is distributed under the [MIT License](LICENSE.txt). WLED is an independent open-source project; this app is an unofficial capture client and is not affiliated with the WLED project.

<a id="zh-cn"></a>

## 简体中文

WLED Grabber 是一个 Android 屏幕采集应用，将画面颜色直接发送给局域网内的单台 [WLED](https://wled.me/) 控制器：应用采集屏幕，在本地按四边 Classic 布局做逐灯平均，再通过 UDP/DDP 输出。整个过程不依赖服务器或其他中间层。

> **状态（2026-11-17）：仍在开发中。尚未发布签名 APK，也未取得发布准入。**

### 功能

- **四边 Classic 布局**：上/右/下/左独立灯数，逻辑槽位顺时针排列，不额外增加四角灯。
- **物理映射控制**：SKIP/BLACK Gap、Input、Reverse、水平/垂直深度、Overlap、Edge Gap、四角独立位置。
- **精确逐灯采样**：对采集帧做整数矩形平均（长边上限 640 px，不做稀疏抽样）。平均色模式仍执行 Gap 与尾灯置黑。
- **RGB 与 RGBW**：支持 `min_subtract` 与 `preserve` 两种白通道合成。RGBW 必须使用 DDP，且 WLED auto-white 必须设为 Manual。
- **WLED 协议**：RGB 使用 DRGB/DNRGB，RGBW 使用 DDP；RGB 也可选 DDP。不使用 type-5/DNRGBW。
- **设备配置**：NSD 发现、有界本地扫描、手动填写 host/端口。控制与读回通过 `/json/info` 和 `/json/state` 完成。
- **共享 UI**：手机端完整设置与快捷设置磁贴；TV 优先的 Leanback 设置与实时布局编辑器；共享预览与三种测试图案（四边色、行走像素、整带纯色）。
- **受控后台启动**：Android 10（API 29）且具备文档所述权限时，开机可经 `BootActivity` 申请一次采集会话；其余情况只发送用户可见提醒。
- **确定性停止**：统一停止屏障发送黑帧、通过 HTTP 关闭 WLED 并读回设备状态。只有读回确认后释放状态才为 `CONFIRMED`，否则为 `UNCONFIRMED`。

**不在范围**：云端/远程访问、多设备同步组、音频采集、DRM 绕过、2D 矩阵/PWM 布局、屏幕内容识别、WLED 固件修改、Google Play 上架。

### 设备前提

- **WLED 实时寻址**：offset=0、DMX 起始地址=1、关闭 main-segment-only，且没有额外 LED map 或总线反向。
- **RGBW**：使用 DDP，WLED auto-white 设为 Manual，且全局 override 不得改回自动。
- **Android 10 受控自启**：已完成首次设置，具备文档中的 ADB AppOps、后台启动能力（悬浮窗或 device owner）与电池优化白名单。详见[配置与排障手册](docs/guides/configuration-and-troubleshooting.md)。

### 兼容性与验收状态

| 项目 | 状态（2026-11-17） |
|---|---|
| Android | minSdk 21，compileSdk/targetSdk 35。仪器测试覆盖 API 21/26/29/33/34，API 35 为部分覆盖；API 23 与完整设备矩阵尚未覆盖。 |
| WLED 固件 | 以 v0.15.1 源码为基线核验；目标设备实测为 WLED 16.0.1。其他版本与定制固件需显式兼容试验。 |
| RGB | 已实现。首套 274 灯（Sony TV + WLED）软件侧采样 p95 ≈ 4.05–4.5 ms、发送间隔 p95 ≈ 17.3 ms；物理刷新率与端到端时延尚未测量。 |
| RGBW | 已实现 DDP、Manual auto-white 与两种白模式；尚无 RGBW 灯带硬件验收。 |
| 灯数 | 首套目标 274 灯；500/1000/2000 灯配置未验证。 |
| ESP8266 | 保留配置入口与性能警告；不继承 ESP32 性能结论。 |
| 目标硬件 | Sony KD-55XD8005（Android 10 TV）+ ESP32-WROOM-32D + 274 × WS2815 RGB；遥控器、大字体与物理输出验收待完成。 |

软件侧证据覆盖布局、采样、协议、生命周期，以及目标电视的采集与释放路径。发布准入仍因缺少物理刷新率/端到端时延测量、RGBW 硬件、更大灯数配置及剩余设备/API 矩阵而阻塞。构建成功不代表已完成签名、安装或发布。

### 构建

构建要求：JDK 17、Android SDK 35。

```sh
scripts/build-client.sh -m all -v both
```

Debug APK（debug 签名）与未签名 Release APK 输出到 `dist/`。签名、安装与发布是独立的显式授权步骤。`mobile` 与 `tv` 使用相同 applicationId，属于替代安装包，不能共存安装。

### 文档

- [技术架构](docs/design/architecture.md)
- [配置与排障手册](docs/guides/configuration-and-troubleshooting.md)
- [品牌与图标规范](docs/design/app-icon/brand-guide.md)

### 隐私与安全

- 只有逐灯颜色值与 WLED 发现/控制请求会离开设备。不保存或上传屏幕图像，不申请麦克风/音频采集。
- 局域网通信使用明文 HTTP/UDP 且无认证；同一局域网内的其他设备也可能控制 WLED。
- 不采集、不绕过 DRM/`FLAG_SECURE` 内容。
- 进程被杀时不保证关灯；无法确认设备状态时应用显示 `UNCONFIRMED`。

### 许可

版权所有 © 2026 Charli117。WLED Grabber 采用 [MIT License](LICENSE.txt) 发布。WLED 是独立的开源项目；本应用是非官方采集客户端，与 WLED 项目无隶属关系。
