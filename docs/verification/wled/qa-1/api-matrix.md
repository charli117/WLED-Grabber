# QA-1 API 与跨模块覆盖矩阵

> 日期：2026-11-17。状态：本地 JVM/build/lint 证据已收集；Sony 真机（API 29）与 API 34 TV AVD 的仪器测试已运行；API 21/26/33/35 待镜像下载后补跑。

## 设备清单

- Sony KD-55XD8005（`BRAVIA 4K UR3`，Android 10 / API 29，serial `c0eabd001408547`），adb over network `10.245.0.109:5555`。
- Android TV AVD `wled-tv-api34`（`sdk_google_atv64_arm64`，API 34，arm64-v8a）。

## API 矩阵

| API | JVM 纯逻辑 | Android 仪器 | UI/系统行为 | 结论 |
|---|---|---|---|---|
| 21 | PASS（配置/几何/采样/报文/生命周期纯逻辑） | **PASS**：common 28/28、mobile 10（Tile/通知渠道按 API 跳过）（`api21-phone` 5.0.2） | 入口、预览D-pad/触摸、设置表单重建 | API 21 覆盖 |
| 23 | PASS（同上） | 未建镜像 | 未建镜像 | 未验证 |
| 26 | PASS（同上） | **PASS**：common 41/41、mobile 8/8（`api26-phone` 8.0.0） | 入口、预览、设置表单重建、所有权 | API 26 覆盖 |
| 29 | PASS（同上） | **PASS**：tv 8/8（含真实 WLED 探测/读回/测试图案 release CONFIRMED、宿主租约释放）；common 2/2（新增用例前） | **PASS**：采集授权、前台服务、`display=1`、语言/设置 UI、静态画面心跳、点亮校验 | API 29 覆盖 |
| 33 | PASS（同上） | **PASS**：common 41/41、mobile 8/8、tv 32（29+3跳过）（`wled-tv-api33` 13） | TV 引导步骤/设置/测试图案/租约生命周期 | API 33 覆盖 |
| 34 | PASS（同上） | **PASS**：common 38/38、mobile 8/8、tv 32（29+3跳过）（`wled-tv-api34` 14） | 入口/设置/TV 流程/租约生命周期 | API 34 覆盖 |
| 35 | PASS（同上） | **PARTIAL**：common 2/2、mobile 1/1；tv 5/7（`api35-phone` AVD） | 无 android-tv 镜像，TvSetupUiTest 2 项不适用 | 部分覆盖 |

“PASS”仅表示该平台上的对应测试类别通过；未运行的类别保持待验证。

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
| 采样时延/发送间隔（真机 QA-2） | `docs/verification/wled/qa-2/device-metrics-2026-11-17.txt` |

## 未覆盖（保持待验证）

- API21/23/26/33/35 的仪器测试：授权拒绝、重复启动、旋转、锁屏、通知权限、后台限制、TEST/CAPTURE 互斥。
- RGBW 样机两模式、端到端物理时延（需光感/高速相机）。
