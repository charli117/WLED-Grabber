# 07. 共用布局预览与测试图案

> 模块前缀：`PRE`；建议责任人：共享View维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

common/.../views/LedLayoutPreviewView、共享测试图案控制器；双端仅宿主

**并发编辑热点 / 合并纪律**：CompiledLayout归LAY、输出归CAP、两端Activity由TV/MOB建壳；不要在View中直接访问Preferences/socket。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="pre-1"></a>

## PRE-1 — 共享预览View与索引可读性

- 优先级：**P1**；前置：`LAY-2`、`CFG-2`；初始状态：**待开发**
- 来源定位：[§9 双端交互](../design/wled-direct-ambilight-feasibility.md#ui)；[§4 布局与映射](../design/wled-direct-ambilight-feasibility.md#layout)；[§2 架构与DRY](../design/wled-direct-ambilight-feasibility.md#architecture)。

### 1. 交付物

- 实现LedLayoutPreviewView：四角、边界、采样矩形、逻辑/物理索引、gap/尾黑与选中位置。
- 展示验证错误及实际映射顺序；支持触摸与D-pad焦点/放大或索引选择，不依赖小字挤满屏幕。

### 2. 关键约束（含 DRY）

- DRY：只读同一个CompiledLayout/共享错误描述；预览不自己算四边，不解析Preferences，不发网络。
- TV/mobile主题由宿主提供，绘制坐标与布局采样域一致；274/2000等大布局只改变展示密度，不改真实索引。
- 缺截图资产时以技术方案§4参数/公式为规格，不编造已比对的参考图。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] LedLayoutPreviewTest验证274/288、SKIP/BLACK、Reverse、竖屏与错误布局的绘制模型。
- [ ] 仪器测试检查键盘/D-pad选中物理0/最后灯，并在resize后保持选中身份。
- [ ] TV大字体与mobile小屏下焦点/索引/按钮可见，不被insets遮挡。

**验证入口**：`:common:testDebugUnitTest --tests '*LedLayoutPreviewTest'（另需View仪器测试）`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 所有绘制矩形与CompiledLayout误差≤1显示像素；索引golden匹配率100%。
- 核心参数与选择入口均可非触摸访问，焦点陷阱0。

**证据（2026-11-16，本地部分）**：`PreviewModel`与`LedLayoutPreviewView`落地；8个`LedLayoutPreviewTest`通过，覆盖274/288、SKIP/BLACK、Reverse、竖屏、梯形角点、尾黑、选中越界与错误模型。View只读同一`CompiledLayout`/映射，不解析Preferences、不发网络；触摸与D-pad选择、TV大字体/mobile小屏/insets仪器测试未执行，PRE-1保持“待验证”。证据见`docs/verification/wled/pre-1/`。

**证据（2026-11-17，设备端 View）**：新增`LedLayoutPreviewInstrumentedTest`，在真实 Android runtime 验证：D-pad 左右可连续走到物理 273 与 0 并正确钳位、resize（重新 setLayout）后保持用户选中的物理索引 161、错误状态阻断 D-pad 选择（保持 -1）。API **21 / 33 / 34** 均通过。另有`touchSelectsTheNearestPhysicalLed`：给 View 真实尺寸后，顶部点击选中一个物理灯，底部点击选中另一个，证明触摸按最近矩形映射。仍未覆盖：TV 大字体/insets、选中索引的像素级 golden。

<a id="pre-2"></a>

## PRE-2 — 共用四边色与行走像素测试

- 优先级：**P1**；前置：`PRE-1`、`CAP-3`；初始状态：**待开发**
- 来源定位：[§9 双端交互](../design/wled-direct-ambilight-feasibility.md#ui)；[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility.md#lifecycle)；[§2 架构与DRY](../design/wled-direct-ambilight-feasibility.md#architecture)。

### 1. 交付物

- 共享纯色/四边色/行走像素图案，显示当前物理索引与测试剩余时间；默认测试10秒可取消。
- 测试前检查当前采集状态并要求先停止；退出/后台/取消走同一release结果展示。

### 2. 关键约束（含 DRY）

- DRY：复用LAY映射、SMP RGBW helper、CAP租约和NET传输；删除旧TestTask式独立HTTP着色逻辑。
- 不请求录屏授权，不强改WLED亮度/auto-white/segment配置；TEST与CAPTURE互斥。
- 停止测试不自动恢复旧capture；需要新用户启动与授权。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] LedPatternTest逐索引验证274/288与gap/尾黑，不用真实网络。
- [ ] 慢网络下开始测试/取消/返回/旋转/Activity停止，输出所有者与租约恰好释放。
- [ ] RGBW图案复用同一转换向量；错误/超时显示UNCONFIRMED而不是成功Toast。

**验证入口**：`:common:testDebugUnitTest --tests '*LedPatternTest'（另需宿主生命周期测试）`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 一个循环访问每个可映射物理灯恰好1次；gap/尾黑泄漏0。
- TEST超时后停止路径被调用1次；投屏弹窗0；并发输出写者≤1。

**证据（2026-11-16，本地部分）**：`LedTestPattern`、`TestPatternSession`、`TestPatternRunner`、`TestPatternActivity`落地；6个`LedPatternTest`与3个`TestPatternRunnerTest`通过，覆盖274/288四边色、SKIP/BLACK与尾黑、行走像素逐物理灯恰好1次、RGBW向量、10秒/取消/捕获互斥、三帧池流控与黑帧最后发送。TEST宿主已通过同一`WledHttpClient`执行`turnOff`+`readState`，只有off/live均false才显示CONFIRMED；否则UNCONFIRMED。宿主慢网络/退出/旋转/Activity停止与仪器测试未完成，PRE-2保持“待验证”。证据见`docs/verification/wled/test-host/`。

