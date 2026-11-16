# QA-1 API 与跨模块覆盖矩阵

> 日期：2026-11-16。状态：本地 JVM/build/lint 证据已收集；Android 仪器、UI 与真实设备未覆盖。QA-1 保持“待验证”。

## 设备清单

- `adb devices`：无连接设备。
- 本机 Android SDK 有 emulator 二进制，但没有可用 AVD/系统镜像；未把 JVM 结果冒充仪器结果。

## API 矩阵

| API | JVM 纯逻辑 | Android 仪器 | UI/系统行为 | 结论 |
|---|---|---|---|---|
| 21 | PASS（配置/几何/采样/报文/生命周期纯逻辑） | NOT RUN | NOT RUN | 未验证 |
| 23 | PASS（同上） | NOT RUN | NOT RUN | 未验证 |
| 26 | PASS（同上） | NOT RUN | NOT RUN | 未验证 |
| 29 | PASS（同上） | NOT RUN | NOT RUN | 未验证 |
| 33 | PASS（同上） | NOT RUN | NOT RUN（通知权限） | 未验证 |
| 34 | PASS（同上） | NOT RUN | NOT RUN（投屏撤销/resize） | 未验证 |
| 35 | PASS（同上） | NOT RUN | NOT RUN（前台类型/insets） | 未验证 |

“PASS”仅表示与平台无关的 JVM 逻辑与构建门禁通过；不能替代授权拒绝、旋转、锁屏、通知权限、后台限制和 TEST/CAPTURE 互斥的仪器测试。

## 跨模块测试台覆盖

| 覆盖点 | 证据 |
|---|---|
| 合成 274 屏幕 → 精确采样 → DNRGB → localhost UDP 捕包 | `EndToEndWledPipelineTest.synthetic274SamplesToDnrgbAndLocalhostUdpCapture` |
| RGBW 采样 → DDP datatype 0x1B → PUSH 末包 | `EndToEndWledPipelineTest.syntheticRgbwDdpCarriesRgbAndDdpDatatype` |
| 500 灯 DDP 多包 offset/length/PUSH | `EndToEndWledPipelineTest.ddpMultiPacketOffsetsAndPushAreFixed` |
| localhost `turnOff` + `readState` | `EndToEndWledPipelineTest.localhostOffAndReadbackUseTheSamePolicyClient` |
| 网络策略：公网/loopback/redirect/64KiB/超时/DNS | `WledHttpClientTest`、`WledDiscoveryTest` |
| 帧池慢消费者、latest-wins、停止黑帧 | `LedStreamerTest` |
| 授权/Image 生命周期/旧 generation | `CaptureSessionTest`、`ImageLifecycleTest`、`ScreenEncoderLifecycleTest` |
| 配置错误/整份保存/RGBW 冲突 | `ConfigValidatorTest`、`ConfigRepositoryTest`、`DeviceConfigTest` |
| 旧协议/旧身份/protobuf 产物审计 | `scripts/qa/local-regression.sh` |

## 未覆盖（保持待验证）

- API21/23/26/29/33/34/35 的仪器测试：授权拒绝、重复启动、旋转、锁屏、通知权限、后台限制、TEST/CAPTURE 互斥。
- API33+ 通知权限拒绝/API34 投屏撤销的实际进程行为。
- 真实 UDP 丢包/乱序、WLED 重启、进程 kill、息屏/亮屏。
- Sony 遥控器/D-pad/大字体。
- 物理 60fps、端到端延迟、RGBW 实机与 30 分钟稳定性。

## 复跑

```bash
bash scripts/qa/local-regression.sh
```

最近结果：Exit 0；JVM 160 通过/0 失败；三模块 lint error 0；Debug/unsigned Release 构建通过；docs 21 文件/219 链接/0 错误；APK 旧身份/protobuf 命中 0。原始输出见 `local-regression.txt` 与 `test-summary.txt`。
