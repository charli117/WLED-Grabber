# 06. 采集授权、生命周期与有界流控

> 模块前缀：`CAP`；建议责任人：采集/服务维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility-2026-11-16.md)；源码/决策/固定资料见其附录A/B/C。

> 2026-11-17 补充：[Android 10 TV 设备侧持续授权](../verification/wled/cap-1/android10-persistent-consent.md)仅在用户明确选择后配置。AppOps 读回不代替新会话/重启/拒绝授权测试；保留 token 不重用、前台服务及其他 API 的验收契约。

> 2026-11-17 需求增补：[默认后台与自启动](../verification/wled/cap-1/android10-background-autostart.md)。本机后台权限/白名单已设置；自启动已按受控路径实现（`bfff381`：仅 API 29 且授权与配置齐全时启动，否则降级提醒），真实冷启动与重启保持未实测，不因源码实现标作设备验收完成。

## 模块边界

common服务/Encoder(Base)/Boot入口/通知，以及共享LedStreamer

**并发编辑热点 / 合并纪律**：HyperionScreenService/Encoder/Base/BootActivity/Notification/common manifest集中由CAP修改；MOB只接入口，REL最后重命名。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="cap-1"></a>

## CAP-1 — 现代Android授权与前台服务入口

- 优先级：**P0**；前置：`FND-2`、`CFG-1`；初始状态：**待开发**
- 来源定位：[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility-2026-11-16.md#lifecycle)；[E12](../design/wled-direct-ambilight-feasibility-2026-11-16.md#e12)；[E13](../design/wled-direct-ambilight-feasibility-2026-11-16.md#e13)；[A04](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a04)。

### 1. 交付物

- 共享启动入口与服务状态机；先前台用户触发校验，再系统授权，service先startForeground后getMediaProjection，再注册callback/createVirtualDisplay。
- 补齐FGS类型/权限、PendingIntent可变性、exported/receiver约束、通知拒绝行为及boot提醒；target35运行契约进入门禁。

### 2. 关键约束（含 DRY）

- DRY：复用BootActivity.startScreenRecorder的公共入口意图，合并Toggle/mobile/TV重复授权结果转发；通知复用现有构建职责。
- 每新会话一次授权、一次getMediaProjection、一次VirtualDisplay；不缓存Intent跨进程/重启，普通/不支持平台不从 boot 自动启动录屏 FGS 或强拉 Activity；仅方案 §6.2.2 的 API 29 已完成设置且全部系统访问就绪时，经 BootActivity 新授权结果进入原前台服务路径。
- TEST模式不使用mediaProjection FGS；不请求麦克风、不绕过FLAG_SECURE；取消/拒绝不创建采集资源。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] CaptureSessionTest覆盖拒绝/重入/连续点击/重复result/空intent/创建失败/服务重建，断言调用顺序与资源计数。
- [ ] API21/26/33/34/35仪器测试：授权、通知权限拒绝、前台退出、系统撤销、boot提醒。
- [x] 检查service内部控制不导出，tile受系统权限保护，敏感授权Intent不记录日志。

**验证入口**：`:common:testDebugUnitTest --tests '*CaptureSessionTest'（另需common/手机/TV仪器测试）`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 每个成功session创建display=1；失败/拒绝创建=0；旧token重用=0。
- 受测平台无FGS/SecurityException；绕过系统许可或复用旧 token 的 boot 启动=0；API 29 受控自动路径单独验收；主线程网络调用=0。

**证据（2026-11-16，本地部分）**：`CaptureSession`纯状态机7个测试通过，覆盖拒绝/重入/连续点击/重复result/空result/投影或display失败/服务重建/旧token不重用；Android服务已改为前台化后再getMediaProjection、注册MediaProjection.Callback后再创建display、START_NOT_STICKY；manifest含FOREGROUND_SERVICE/FOREGROUND_SERVICE_MEDIA_PROJECTION/POST_NOTIFICATIONS、service exported=false且foregroundServiceType=mediaProjection；boot receiver改为通知提醒而非启动Activity。API21/26/33/34/35仪器测试与真实设备授权未执行，CAP-1保持“待验证”，不声称设备验收完成。证据见`docs/verification/wled/cap-1/`。

**证据（2026-11-17，Sony KD-55XD8005 / Android 10 / API 29 真机）**：真机首轮发现两处违反§6.2契约的缺陷——(1) `startForegroundService()` 后的配置校验早退未调用 `startForeground()`，系统抛 `RemoteServiceException` 杀死进程；(2) 服务用 `getParcelableExtra("data")` 取录屏令牌，而 `BootActivity` 以 `putExtras(data)` 写入的是系统内部 key，projection 恒为 null。修复：`startForegroundSafely()` 前置到任何早退之前；`getMediaProjection(resultCode, intent)` 与上游一致；新增 `CaptureSession.rejectStart()` 规定“未前台化不得中止启动”（先RED后GREEN），并把 `WledScreenService` 的启动/投影/display/停止全流程接入 `CaptureSession`，使原有顺序约束测试首次约束生产路径。复测证据：`dumpsys display` 去重 uniqueId=1（640×360，owner=wledgrabber）、`dumpsys media_projection`=1条TYPE_SCREEN_CAPTURE、服务 `isForeground=true`、完整session无FGS/SecurityException；端到端 WLED `/json/info` 显示 `live=true, lm=UDP, lip=10.245.0.109`（电视IP）、`fps≈35–45`；停止后 service=0、display=0、WLED退出live。JVM `:common:testDebugUnitTest` 160个用例0失败。**设备端（2026-11-17）**：新增`TvServiceForegroundContractTest`——以 `RESULT_CANCELED`（等价拒绝授权）经 `startForegroundService()` 拉起服务；若前台契约被违反，系统会抛 `RemoteServiceException` 杀死进程，测试便无法到达断言。API 33/34 TV 模拟器均通过，且拒绝启动后输出租约为 `NONE`。common 仪器 14/14 与 TV 仪器 10/10 已在 API 21/26/33/34 跑通。**仍未覆盖**：真实授权撤销/锁屏/旋转组合；`POST_NOTIFICATIONS` 撤销后启动在 API 33 模拟器上会使测试进程被系统终止，未纳入稳定回归，保持未验证。

**证据（2026-11-17，自启动实现，`bfff381`）**：`StartupPolicy`/`StartupAccess`/`StartupSetupActivity` 与新 `BootActivity` 自动路径落地——仅 API 29、`PROJECT_MEDIA`、后台启动、后台执行、电池豁免与配置全部就绪时才判定 `START_CAPTURE`，否则保留提醒；`claimBootAttempt` 以 `boot_count` 把自动尝试限制为每次真实开机一次；先发提醒兜底，服务经 `BROADCAST_FILTER` 报告开始采集后才撤销（不等于物理灯带确认）。`StartupPolicyTest` 4 项通过；startup 仪器用例已进入本轮验证，精确平台/数量及缺口见[启动验证记录](../verification/wled/startup/verification.md)；`:common:testDebugUnitTest`、`:mobile:testDebugUnitTest` 与 `:tv:assembleDebug` 通过。真实冷启动、重启保持、息屏与厂商限制未实测。

<a id="cap-2"></a>

## CAP-2 — Image关闭、resize与generation隔离

- 优先级：**P0**；前置：`CAP-1`、`SMP-1`；初始状态：**待开发**
- 来源定位：[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility-2026-11-16.md#lifecycle)；[§5 采样与颜色](../design/wled-direct-ambilight-feasibility-2026-11-16.md#sampling)；[A03](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a03)；[A04](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a04)。

### 1. 交付物

- 以PlaneView接SMP-1；每次acquire在finally关闭；reader/display/projection幂等释放。
- 处理全屏/单应用窗口、低版本方向变化及API34+ resize；同display换surface，generation拒绝旧回调。

### 2. 关键约束（含 DRY）

- DRY：复用SMP平面读取/布局计划，不保留getPixels全帧打包；所有失败走同一cleanup，不复制onStop和按钮释放逻辑。
- reader maxImages=3、应用持有Image≤1；先停旧reader再换surface/计划，不把ByteBuffer跨线程传给sender。
- 系统撤销后NEEDS_CONSENT；亮屏不能复活失效projection；同session旋转不create第二个VirtualDisplay。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] ScreenEncoderLifecycleTest注入采样抛错/acquire空/读取失败，核对每个Image恰好关闭1次。
- [ ] 连续100次旋转/resize/锁屏/撤销组合；旧generation帧和回调不得进入新计划。
- [ ] API34+单应用窗口尺寸变化，以及低版本方向交换；对照采样矩形边界。

**验证入口**：`:common:testDebugUnitTest --tests '*ScreenEncoderLifecycleTest'（另需resize仪器测试）`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- Image泄漏/双关/使用已关闭buffer均0；100次循环结束reader/display/projection/线程归零。
- 所有输出尺寸匹配当次generation；旧generation提交数0；重复createVirtualDisplay数0。

**证据（2026-11-16，本地部分）**：`GenerationGate`、`ImageLifecycle`、`ImagePlaneView`落地；5个`ScreenEncoderLifecycleTest`通过，覆盖采样异常仍关闭、acquire null/失败、旧generation拒绝、100次生命周期循环0泄漏。`HyperionScreenEncoder`改为Image finally关闭、reader身份+generation校验、reader max3、停止幂等；orientation先关旧reader再resize/setSurface。API34+ captured-content resize、100次真机旋转/锁屏/撤销与仪器测试未执行，CAP-2保持“待验证”。证据见`docs/verification/wled/cap-2/`。

**证据（2026-11-17，设备端 reader）**：新增`ImageReaderLifecycleInstrumentedTest`，用真实 `ImageReader`+`Surface` 生产者验证：20 帧连续 `acquireLatestImage`→`ImagePlaneView.from` 几何正确（320×180）→`close`，**acquired 与 closed 严格相等（无泄漏、无双重关闭）**；`maxImages=3` 时第四次获取被系统以 `IllegalStateException` 拒绝，证明服务必须先 close 再 acquire。API **21 / 33 / 34** 均 11/11 通过。另有真机/模拟器场景验证（2026-11-17）：API 26 手机模拟器 0→90→0 旋转、锁屏唤醒后 Activity 均存活、无崩溃；仍未覆盖：API 34+ 单应用窗口 resize、100 次旋转/锁屏/撤销组合。

**2026-11-17 交付复核（状态下调为开发中）**：生产 `WledScreenService` 没有任何 resize/rotation 处理——全仓无 `onCapturedContentResize`、`VirtualDisplay.Callback`、`virtualDisplay.resize/setSurface`，只在启动时 `createVirtualDisplay` 一次；`ImageLifecycle`/`GenerationGate` 也无生产调用方，服务是在 `onImageAvailable` 的 `finally` 直接关闭 Image。卡片交付物“处理全屏/单应用窗口、低版本方向变化及API34+ resize；同display换surface，generation拒绝旧回调”和验收“所有输出尺寸匹配当次generation；旧generation提交数0；重复createVirtualDisplay数0”均未实现。该卡保持**开发中**。

<a id="cap-3"></a>

## CAP-3 — 唯一输出所有者、三帧池与停止屏障

- 优先级：**P0**；前置：`CAP-2`、`SMP-2`、`NET-3`；初始状态：**待开发**
- 来源定位：[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility-2026-11-16.md#lifecycle)；[§8 设备控制与LAN安全](../design/wled-direct-ambilight-feasibility-2026-11-16.md#network)；[A03](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a03)；[A07](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a07)。

### 1. 交付物

- 实现FrameLease、3帧池、pending latest-wins、单sender与sessionEpoch；offer总是接管租约。
- 实现CAPTURE/TEST互斥与共享session所有权；停止禁止新offer、清pending、结束inFlight后黑帧/release。
- 输出结构化状态/掉帧/队列/采样/发送指标，供两端UI与QA统一读取。

### 2. 关键约束（含 DRY）

- DRY：复用NET transport/session、FND fake clock/sink，真实采集和测试图案只换帧生产者；不加第二条HTTP逐灯路径。
- producer/pending/inFlight各≤1，借不到就丢当前帧；不等待网络、不覆盖inFlight；session/代次不同直接归还。
- TEST由前台预览Activity持有输出租约，不创建MediaProjection/FGS；离开页面停止；全进程会话仲裁防止两个入口同时占设备。
- STOPPING期间不启动新session；3秒后本地释放仍报告远端未确认；任何迟到回调不能恢复已关闭捕获。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] LedStreamerTest使用慢sink与10万帧压力，标记每个缓冲版本验证无撕裂、无重复归还、pending最新。
- [x] 在编码/发送/offer/停止各阶段注入异常和并发start/test/stop，验证黑帧是本地最后颜色帧。
- [ ] 断网/HTTP超时/Activity退出/授权撤销后资源与ownership计数归零，状态不误报。

**验证入口**：`:common:testDebugUnitTest --tests '*LedStreamerTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 队列pending≤1、池占用≤3、UDP写者始终≤1；10万帧无内容撕裂或泄漏。
- 测试模式投屏请求=0；旧epoch发送=0（屏障之后）；本地停止≤3秒，设备结果保留真实UNCONFIRMED。

**证据（2026-11-16，本地部分）**：`FrameLease`、`FramePool`（3租约）、`OutputOwner`、`LedStreamer`落地；7个`LedStreamerTest`通过，覆盖10万帧无撕裂/无重复归还、pending latest-wins、慢sink停止屏障（黑帧在所有颜色帧之后）、停止超时不误报、CAPTURE/TEST互斥、旧epoch/停止后offer归还、发送失败不泄漏。真实Encoder/transport/UI接线与断网/HTTP超时/Activity退出/授权撤销仪器测试未执行，CAP-3保持“待验证”。证据见`docs/verification/wled/cap-3/`。

**证据（2026-11-17，设备端所有权）**：新增`OutputOwnershipInstrumentedTest`，在真实 Android runtime 验证：CAPTURE 与 TEST 互斥（TEST 持有期间 CAPTURE 启动被拒）、release 必须匹配 owner id 与 epoch（错误 id/错误 owner/过期 epoch 均不释放）、Activity 退出释放后重新进入可再次取得且 epoch 递增、TEST 释放后 CAPTURE 可接管。API **21 / 33 / 34** 均 9/9 通过（含既有 2 个配置/夹具用例）。另新增`WledHttpFailureInstrumentedTest`：本地 socket 接受连接但不应答时，`probe`/`readState` 均返回 `TIMEOUT` 且 probe 在 5s 内返回，`turnOff` 不误报成功；端口关闭返回 `CONNECT_FAILURE`。另有真机验证（2026-11-17）：测试图案持有 TEST 租约后 `am force-stop` 杀进程，重启服务无“output already owned”、无崩溃，证明 ownership 随进程复位。仍未覆盖：真实断网/路由变更、授权撤销。

**2026-11-17 实现完成（保持待验证）**：新增纯决策 `CaptureResizePlanner`（3项 JVM 测试：相同尺寸不重编译、旋转重建布局并递增 generation、长边预算）。服务在 API 34 `MediaProjection.Callback.onCapturedContentResize` 与所有版本的 `DisplayManager` 默认屏变化回调中，把 resize 投递到 capture 线程：重新 fit/compile 布局，在**同一** VirtualDisplay 上 `resize` + `setSurface`，递增 generation 并摘除旧 reader 回调；旧 generation 的帧被丢弃。仍缺：活动投屏下的 API34 单应用窗口 resize、连续 100 次旋转/锁屏/撤销仪器证据（需用户授权投屏），因此卡片保持待验证。
