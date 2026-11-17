# 05. WLED探测、线协议与设备控制

> 模块前缀：`NET`；建议责任人：WLED网络模块维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility-2026-11-16.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

common/.../network/wled、NetworkScanner重用、network_security_config.xml

**并发编辑热点 / 合并纪律**：配置key由CFG维护；manifest由CAP/MOB/REL按顺序合并；WledSession与CAP-3联合接口评审，不各建socket writer。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="net-1"></a>

## NET-1 — 一次解析的HTTP探测与有界发现

- 优先级：**P0**；前置：`CFG-2`；初始状态：**待开发**
- 来源定位：[§8 设备控制与LAN安全](../design/wled-direct-ambilight-feasibility-2026-11-16.md#network)；[§3 配置契约](../design/wled-direct-ambilight-feasibility-2026-11-16.md#config)；[A02](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a02)；[A11](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a11)。

### 1. 交付物

- 实现WledHttpClient/ProbeResult及endpoint-specific JSON解析；mDNS、手动输入、HTTP扫描共用。
- 复用扫描排序意图，改为选定网络前缀、有界并发/超时/取消；提供清晰扫描partial与设备身份变化结果。
- 落实cleartext例外、端点LAN校验、禁redirect及响应体上限。

### 2. 关键约束（含 DRY）

- DRY：启动/发现/健康/停止读回共用client与解析器；不使用bool probe后再次获取同一info。
- /json/info读取根leds；HTTP/UDP/DDP端口独立；NSD IP是缓存而非身份。
- 候选≤256、并发≤4、整体≤15秒；请求connect/read各≤1秒、应用截止≤3秒、体≤64KiB；拒绝公网/回环/组播与带userinfo/路径的输入，网络切换重校验。低API阻塞DNS使用专用单worker，超时只失效结果、不得增开线程；占满时快速失败，迟到解析不触发网络发送。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] WledHttpClientTest用FND可控本地测试端点/注入连接，覆盖根info、聚合info、坏JSON、缺字段、非WLED、重定向、超限、慢响应；loopback仅测试注入允许。
- [x] WledDiscoveryTest覆盖NSD重复/过期/IPv6 scope、空网络、取消、scan超时及数量上界。
- [x] 端点策略测试解析后地址改变与公网目标，确保没有请求越过策略。

**验证入口**：`:common:testDebugUnitTest --tests '*WledHttpClientTest' --tests '*WledDiscoveryTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 每次probe只发1次GET且只解析一次；所有错误返回明确类型，不误标发现成功。
- 扫描并发/候选/时间上界均满足；取消后不得发布迟到结果；公网/redirect实际请求数0。

**证据（2026-11-16）**：`WledHttpClient`、`ProbeResult`、`ProbeError`、`DiscoveryCandidate`、`DiscoveryCandidateCache`、`WledDiscovery`落地；5个HttpClient测试与6个Discovery测试通过。根info单次GET/解析、聚合info/坏JSON/缺字段/非WLED/redirect/64KiB/超时、LAN/loopback策略、256候选/4并发/15秒上界、取消不发布迟到结果均有断言；证据见`docs/verification/wled/net/`。

<a id="net-2"></a>

## NET-2 — 固定固件的RGB/RGBW报文与分片

- 优先级：**P0**；前置：`CFG-2`；初始状态：**待开发**
- 来源定位：[§7 WLED协议](../design/wled-direct-ambilight-feasibility-2026-11-16.md#protocol)；[A01](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a01)；[A05](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a05)；[A13](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a13)。

### 1. 交付物

- 实现同一WledUdpTransport中的DRGB单灯、DNRGB多灯、DDP RGB/RGBW编码与DatagramSink注入。
- 保存固定v0.15.1的接收约束golden及独立包解码断言；提供274基线报文。

### 2. 关键约束（含 DRY）

- DRY：使用FND RecordingDatagramSink与一个分片计划器；RGB/RGBW只通过channels/datatype参数化，不复制socket生命周期。
- 禁type5；DNRGB每包2..489灯，单灯走type2，尾1重分；RGBW强制DDP。
- DDP sequence固定0、offset/length是字节、大端、末包PUSH；payload≤1440且像素对齐；不声称PUSH原子或UDP有ACK。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] WledPacketizerTest覆盖RGB 1/2/274/288/489/490/978/979；逐包重建完整帧验证索引无缺漏。
- [x] DDP RGB480/481及RGBW1/274/360/361/720/721，多包flags/offset/length逐字节比较。
- [x] 无效count/channels/溢出/空帧拒绝，发送中失败不重试陈旧分片；真实socket仅localhost受控测试。

**验证入口**：`:common:testDebugUnitTest --tests '*WledPacketizerTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 274RGB DNRGB826B；274RGBW DDP1106B；全部golden逐字节一致。
- DNRGB颜色包7字节数量0；type5包数量0；每个DDP帧仅最后包PUSH，重建后字节差异0。

**证据（2026-11-16）**：`WledPacketizer`、`WledUdpTransport`、`DatagramSink`落地；8个`WledPacketizerTest`通过。覆盖DRGB单灯、DNRGB 2/274/288/489/490/978/979及尾1重分、DDP RGB480/481与RGBW1/274/360/361/720/721的flags/offset/length、274RGB826B/274RGBW1106B、无type5、失败不重试；证据见`docs/verification/wled/net/`。

<a id="net-3"></a>

## NET-3 — 健康观察、统一release与恢复策略

- 优先级：**P0**；前置：`NET-1`、`NET-2`；初始状态：**待开发**
- 来源定位：[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility-2026-11-16.md#lifecycle)；[§8 设备控制与LAN安全](../design/wled-direct-ambilight-feasibility-2026-11-16.md#network)；[A07](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a07)；[A13](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a13)。

### 1. 交付物

- 实现WledSession的观察状态、后台健康探测、统一release/off/读回与明确失败结果。
- 实现网络变更/设备身份改变的端点废弃及有界恢复，提供供CAP/TEST共用的session接口。

### 2. 关键约束（含 DRY）

- DRY：复用NET-1 client/parser，所有停止原因共用一个release实现；socket生命周期只由session/streamer组合持有一次。
- LOCAL_SENDING不等于设备收帧；探测频率≤每5秒1次，10秒过期；release总≤3秒，无读回不CONFIRMED。
- 黑帧之后POST on:false/live:false/tt:0；UDP迟到和其他控制器仍可能重新点亮。未知结果不得自动off重试跨入新epoch；DDP超时来自设备有限设置。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] WledSessionTest FakeClock覆盖成功、HTTP超时、读回live仍true、设备重启、身份改变、stale观察。
- [x] 停止期间旧HTTP迟到、UDP迟到、用户连续start/stop；旧结果不能改变新session。
- [x] 进程终止无法执行release的场景验证UI/文档不承诺灭灯，恢复后需要新观察。

**验证入口**：`:common:testDebugUnitTest --tests '*WledSessionTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 只有读回on=false/live=false才CONFIRMED；其他路径100%为UNCONFIRMED/错误。
- 总控制超时≤3秒；旧epoch状态写入新epoch0；网络失败不产生无界队列或重试。

**证据（2026-11-16）**：`WledSession`、`WledControlClient`、`StateReadResult`、`FrameSender`落地；9个FakeClock测试通过。覆盖success、live/on仍true、读回失败、3秒预算、黑帧失败仍以读回为准、5秒探测频率/10秒stale、身份变化废弃端点、release中旧epoch不覆盖新session、start不自动release；证据见`docs/verification/wled/net/`。

**证据（2026-11-17，真机 10.245.0.99 / WLED 16.0.1 / 274 RGB）**：发现控制面缺少开灯能力——`WledControlClient`只有`turnOff`，WLED主电源为关时实时UDP帧不会点亮灯带（`live=true`仅表示在收实时数据）。新增`turnOn`（POST `/json/state`，body `{"on":true}`，需JSON Content-Type），`WledHttpClientTest.turnOnPostsPowerOnJson`先RED后GREEN；服务在建立采集后由后台线程开灯，不违反主线程网络约束。另在服务侧加入1秒心跳：静态画面无新帧时经同一`LedStreamer`单写者与帧池重发最后一帧，避免WLED在2秒UDP超时后退出live并回落到自身效果。真机证据：启动前`on=false`→启动后`on=true`、`lm=UDP`、`lip=10.245.0.109`（电视）；切到静态设置页后连续10秒`live=true`。未验证：停止路径release读回确认、与另一DDP源（10.245.0.121）并发抢占时的最终显示归属、QA-2时延/帧率量化门禁。

**证据（2026-11-17，读回字段兼容）**：真机WLED 16.0.1的`/json/state`**不包含`live`字段**（实时中也不含，`/json/info`才有），旧`readState`要求两者都在，导致读回恒`MISSING_FIELD`、release永远`UNCONFIRMED`。改为：`on`仍取`/json/state`，缺失`live`时回退读取`/json/info`的`live`；两者都缺才报`MISSING_FIELD`。新增JVM回归`liveFallsBackToInfoWhenStateOmitsIt`（先RED后GREEN，断言两次请求）。真机10秒测试图案用例`tenSecondPatternConfirmsRelease`因此从失败转为通过：`release CONFIRMED`，POST `{"on":false,"live":false,"tt":0}`后`/json/state`的`on=false`且`/json/info`的`live=false`。

**2026-11-17 交付复核（状态下调为开发中）**：`WledSession`、`WledControlClient`（含 `FrameSender`）仅被 `WledSessionTest` 引用，生产 `WledScreenService` 直接使用 `WledHttpClient` + `LedStreamer`，未实例化 session。因此卡片的“后台健康探测（≤5秒）”“10秒 stale”“设备身份/网络变化废弃端点并重新观察”“网络失败有界恢复”均未接入运行路径；已实现的只有服务层停止时的 black+off+读回。本轮补齐 release 结果向 UI 的广播（`SERVICE_RELEASE_KNOWN/CONFIRMED`，TV/mobile 展示），但仍不替代 `WledSession` 的观察/废弃职责。该卡保持**开发中**。

**2026-11-17 接线完成（恢复已验收）**：服务现在持有 `WledSession`：启动时用 READY 观察建立会话；`LedStreamer` 的停止屏障通过 `WledSession.release` 执行 black+off+readback 并统一映射 CONFIRMED/UNCONFIRMED；独立健康线程每 5 秒调用 `observeNow`，`ObservationMonitor` 将错误或超过 10 秒的观察判为 stale 并广播，设备身份变化时停止采集而不是继续发往新设备。回归：`WledSessionTest` 9项、`ObservationMonitorTest` 3项；API21/26/33/34 仪器回归通过。真实设备长时观察、断网恢复与迟到结果仍归 QA-2。
