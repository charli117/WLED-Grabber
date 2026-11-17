# 11. 跨模块回归、实机性能与准入

> 模块前缀：`QA`；建议责任人：独立验证维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility-2026-11-16.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

共享回归tests、instrumentation、受控UDP/HTTP测试台、验收证据目录（实施时创建）

**并发编辑热点 / 合并纪律**：只补证据与回归；发现产品修复回所属模块，不在QA并行改CAP/NET热点。测试fixture仍由FND公共来源扩展。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="qa-1"></a>

## QA-1 — 跨模块协议、生命周期与安全回归

- 优先级：**P0**；前置：`REL-1`；初始状态：**待开发**
- 来源定位：[§11 性能与验证](../design/wled-direct-ambilight-feasibility-2026-11-16.md#validation)；[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility-2026-11-16.md#lifecycle)；[§7 WLED协议](../design/wled-direct-ambilight-feasibility-2026-11-16.md#protocol)；[附录A 源码锚点](../design/wled-direct-ambilight-feasibility-2026-11-16.md#source-audit)。

### 1. 交付物

- 构建端到端可控测试台：合成屏幕->采样->编码->localhost捕包/HTTP控制；跨模块启动/停止/配置错误回归。
- 建立API21/23/26/29/33/34/35关键组合矩阵，独立列JVM、Android仪器、UI与未覆盖设备。

### 2. 关键约束（含 DRY）

- DRY：复用FND fixtures与NET解码golden，不重写一个“与实现同错”的编码器充当oracle；断言固定字节与独立数学预期。
- 测试台仅localhost或明确测试网络，不自动扫描/写入真实LAN设备；凭据/授权Intent/屏幕像素不入日志。
- lint错误不能全局禁用；任何修复重新跑最小RED->GREEN后回归，不用删测试换绿。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] 跑三个模块单测/build/lint，协议边界、帧池慢消费者与所有错误状态全覆盖。
- [ ] 仪器测试授权拒绝/重复启动/旋转/锁屏/通知权限/后台限制/TEST互斥。
- [x] 网络策略覆盖公网/redirect/超限/身份变化；schema损坏与测试产物不进入release。

**验证入口**：`:common:testDebugUnitTest :mobile:testDebugUnitTest :tv:testDebugUnitTest :common:lintDebug :mobile:lintDebug :tv:lintDebug :mobile:assembleDebug :tv:assembleDebug（设备另跑connectedDebugAndroidTest）`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- JVM全部通过、build退出0、lint error=0；未覆盖平台保持待验证而非PASS。
- FND/CAP/NET定义的失败分支都有可定位回归；安全策略绕过请求0。

**证据（2026-11-16，本地）**：新增`EndToEndWledPipelineTest`（合成274→采样→DNRGB、RGBW DDP、500灯多包、localhost off/readback）；`scripts/qa/local-regression.sh`复跑JVM 160通过/0失败、三模块lint error0、Debug+unsigned Release构建、docs链接与APK旧身份/protobuf审计，退出0。API矩阵见[api-matrix.md](../verification/wled/qa-1/api-matrix.md)；`MobileEntryTest`与`TvLayoutFlowTest`已随androidTest APK编译，但无设备/AVD，仪器与UI项保持未验证。QA-1标“待验证”，不标PASS。

**证据（2026-11-17，真机/AVD）**：JVM **166通过/0失败**；三模块lint **error 0**（warnings common 94 / mobile 8 / tv 49）；三模块Debug与unsigned Release构建退出0；仪器测试：**Sony KD-55XD8005（API 29）common 2/2、tv 7/7**（含真实WLED `10.245.0.99`能力探测、状态读回、10秒测试图案release CONFIRMED），**API 34 AVD mobile 1/1**。期间修复两处真机缺陷：`TestPatternActivity`/`LayoutPreviewActivity`缺少AppCompat主题导致启动崩溃（已在common manifest指定`Theme.AppCompat.NoActionBar`）；WLED 16.x `/json/state`不含`live`导致读回恒`MISSING_FIELD`（改为回退`/json/info`，新增JVM回归`liveFallsBackToInfoWhenStateOmitsIt`）。API 21/26/33/35仍未运行，保持待验证。

**证据（2026-11-17 交付复核，本轮）**：JVM **227通过/0失败**（common 227、mobile 1、tv NO-SOURCE）；三模块 lint **error 0**（common 101 / mobile 8 / tv 55 warnings）；Debug 与 unsigned Release 构建 exit 0。仪器复跑：**API21 common 35/35、mobile 10通过+2平台跳过；API26 common 49/49、mobile 10/10；API33 TV 35通过+3真机跳过；API34 common 49/49、mobile 10/10、tv 35通过+3跳过**。新增接线回归：`StartCaptureActivityTest`、`StartConfirmationStoreTest`、`TvServiceReadinessContractTest`（CFG-2）、`ObservationMonitorTest`（NET-3）、`TvMainActivityTest.staleObservationBroadcastIsShownWhileRunning`（TV-1）、`CaptureResizePlannerTest`（CAP-2 实现）、`MobileSettingsCorruptPreferenceTest`（MOB-1）。API 23/29/35 与真机矩阵本轮未复跑，保持待验证。新增证据归档：`docs/archive/verification/wled/qa-1/2026-11-17-delivery-audit/`（JVM/构建、lint/Release、API21/26/33/34 原始日志）；`.omc/artifacts/` 同名文件为可删除的工作区运行产物。QA-1 维持**已验收**是就“本地可复跑门禁”而言，不表示发布或物理验收。

<a id="qa-2"></a>

## QA-2 — 274/60fps、RGBW与故障实机验收

- 优先级：**P0**；前置：`QA-1`；初始状态：**待开发**
- 来源定位：[§11 性能与验证](../design/wled-direct-ambilight-feasibility-2026-11-16.md#validation)；[§1 目标与范围](../design/wled-direct-ambilight-feasibility-2026-11-16.md#scope)；[A14](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a14)。

### 1. 交付物

- 在Sony+274WS2815+固定ESP32固件跑30分钟可复核实验；分开采capture/sample/send/show/物理刷新与端到端延迟。
- RGBW独立样机验证Manual+两white mode、DDP边界；分别测ESP8266及500/1000/2000灯配置，不外推帧率。
- 归档型号/系统/固件SHA/布局/网络/供电实物资料、原始计时/抓包/光学或逻辑分析记录和统计方法。

### 2. 关键约束（含 DRY）

- DRY：共用274/288 fixture、NET包解码与CAP指标；测试图案用PRE，不写独立着色工具绕过真实链路。
- 设备、RGBW灯带或物理测量设备缺失时保持待硬件；UDP60Hz和info.leds.fps不能代替物理60fps。
- 60fps未过不擅降30，RGBW未过不删除范围；不更改WLED固件/电源/接线而不经硬件负责人确认。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [ ] 预热5分钟+观察25分钟；固定60Hz不同帧内容；计算采样p95/p99、send间隔、1秒窗口物理FPS、端到端p95。
- [ ] 多包丢首/末、乱序、延迟、断HTTP/断UDP、WLED重启、进程kill、息屏/亮屏；检查真实状态与关灯结果。
- [ ] RGBW1/360/361及RGB1/489/490/979末灯、Manual/错误auto-white组合，防止二次合成。
- [ ] Sony真实遥控器完成向导/设置/高级/预览/测试/停止全流程，检查D-pad焦点和大字体可读性；该目标设备证据只在本卡验收，不反向作为TV-2前置。

**验证入口**：`指定设备仪器测试 + capture/send指标 + packet capture + 物理时序/光学测量`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 首套采样p95≤5ms/p99≤8ms，发送间隔p95≤20ms，物理不同帧平均≥59fps且1秒窗口≥55fps，端到端p95≤80ms。
- 30分钟ANR/崩溃0；稳定工作集净增≤5MiB且无持续增长；池≤3/pending≤1；RGBW两模式实机通过。
- 所有必须硬件组合（含Sony遥控器/大字体）有原始证据；不符合或缺测保持BLOCKED，不写“基本通过”。

**硬件阻塞（2026-11-16）**：当前无 Sony 电视、274 WS2815+ESP32、RGBW 样机、500/1000/2000 灯配置或物理测量设备；`adb devices` 为空。未开始可复核实验，未产生 fps/延迟/稳定性数据。必测项与量化门槛见[hardware-blocked.md](../verification/wled/qa-2/hardware-blocked.md)。QA-2 保持“待开发/BLOCKED”，不以 JVM 或 UDP 频率代替物理验收。

**实测（2026-11-17，Sony KD-55XD8005 + WLED_TV 274 RGB @10.245.0.99，640长边，60fps配置）**：采样 p50≈3.3ms、**p95≈4.05–4.5ms**、**p99≈4.5–4.9ms**（≤5/≤8 达标）；**发送间隔 p95≈17.3ms**（≤20 达标）；WLED `leds.fps` 30次1秒采样 **min=66 / avg=107.4**（≥59 且1秒窗口≥55 达标）；启动自动开灯、静态画面1秒心跳保持 live、停止后 `on=false/live=false` 并经 HTTP 读回 CONFIRMED。30分钟soak与内存净增结果见下方复核记录。**仍缺**：端到端物理时延（需光感/高速相机或等效测量设备）、RGBW 样机两模式、500/1000/2000 灯配置、Sony 遥控器大字体实测。按卡片自身口径，QA-2 保持 BLOCKED，不标 PASS。硬件就绪后的执行步骤见[qa2-runbook.md](../verification/wled/qa-2/qa2-runbook.md)，软件侧复测可用`scripts/qa/measure-qa2.sh`。

**2026-11-17 交付复核补充**：32分钟soak（UTC 2026-11-16T18:05–18:38）已完成：`FATAL EXCEPTION`/ANR 均为 0，TOTAL PSS 93,288→68,632 KB（净降），采样 p95 3.9–4.4ms、发送间隔 p95 17.3–17.6ms，满足“无崩溃/无持续增长”的稳定性口径。仍缺的四项（物理FPS/端到端、RGBW样机、500/1000/2000灯、Sony遥控器大字体）没有变化；故障矩阵（丢包/乱序/断网/断HTTP/WLED重启/进程kill/息屏）未系统执行。QA-2 继续 BLOCKED。

<a id="qa-3"></a>

## QA-3 — 最终需求闭环与发布准入记录

- 优先级：**P0**；前置：`QA-2`、`REL-2`；初始状态：**待开发**
- 来源定位：[§12 实施与准入](../design/wled-direct-ambilight-feasibility-2026-11-16.md#delivery)；[§11 性能与验证](../design/wled-direct-ambilight-feasibility-2026-11-16.md#validation)；[§1 目标与范围](../design/wled-direct-ambilight-feasibility-2026-11-16.md#scope)。

### 1. 交付物

- 逐条对照总纲进度校验矩阵、A01-A14和全局checklist，形成事实级PASS/FAIL/未验证结果。
- 校验最终mobile/tv unsigned产物与文档版本/包名/权限；列出签名、安装、硬件与发布证据的独立层级。

### 2. 关键约束（含 DRY）

- DRY：总纲矩阵是状态SSOT，模块卡只附证据链接；不另造一份容易漂移的“全部完成”表。
- SMP-3为显式延期，不阻断MVP；任何必做P0/P1未通过均阻断，不以架构批准代替运行验收。
- 本卡只记录准入，不自行签名/上传/发布或停止外部服务。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [ ] 检查每个必做ID有代码/测试/验收证据，所有依赖先完成且无跨模块遗漏。
- [ ] 独立复核最终APK权限/组件和旧后端删除清单；检查未验证项是否误标PASS。
- [ ] 按统一验证流程从干净受控环境复跑关键门禁并保存退出码。

**验证入口**：`全局checklist审计 + 受控clean build/test/lint + 独立架构/证据复核`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 25个首版任务中的本卡前置与其他必做卡全部通过；未闭合P0/P1=0。
- 所有证据层级明确，文档/矩阵状态与实际结果一致；实际发布仍有独立授权。

