# 03. 四边几何与物理LED映射

> 模块前缀：`LAY`；建议责任人：布局模块维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

common/.../led/ClassicLedLayout、LedSampleRect、CompiledLayout与对应测试

**并发编辑热点 / 合并纪律**：与SMP/PRE共享CompiledLayout接口；先完成LAY契约再并行消费，预览不得自行修改几何算法。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="lay-1"></a>

## LAY-1 — 四边几何、坐标与参考golden

- 优先级：**P0**；前置：`CFG-1`；初始状态：**待开发**
- 来源定位：[§4 布局与映射](../design/wled-direct-ambilight-feasibility.md#layout)；[E17](../design/wled-direct-ambilight-feasibility.md#e17)；[A09](../design/wled-direct-ambilight-feasibility.md#a09)。

### 1. 交付物

- 实现纯函数ClassicLedLayout与像素矩形编译，覆盖深度/Overlap/Edge Gap/四角及不同宽高比。
- 归档固定S09参考提交、许可、4位小数golden；16:9对照与非16:9有意差异分别测试。

### 2. 关键约束（含 DRY）

- DRY：复用CFG校验入口和FND平面/布局fixtures；归一化->像素只保留一份转换，采样/预览共用。
- 零灯边跳过；凸且无自交、对应边单调、有效深度/边距；裁剪后退化矩形拒绝，不用强行1像素掩盖非法配置。
- 有效小矩形round后保证一像素，半开边界；不得移植上游gap越界笔误或把Overlap解释为相邻灯宽度百分比。
- 几何准入和像素编译使用裁剪后完整精度；4位小数只用于参考表示/golden，不能把量化退化回灌活动计划。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] ClassicLedLayoutTest覆盖274/288、单边0/1、16:9/4:3/竖屏、非零Overlap/Edge Gap、梯形角点。
- [x] 拒绝交叉四边形、面积0、内缩后倒置矩形；随机10000组有效/无效配置验证边界。
- [x] 16:9合法参考配置按同精度比较；非16:9以明确手算golden验证实际宽高比。
- [x] 窄矩形：T=1000、宽1%高100%、其他边0与纵向R=1000、高1%宽100%两例，均输出1000个非空像素矩形；4位量化为0不能误拒，真实宽/高0仍拒绝。

**验证入口**：`:common:testDebugUnitTest --tests '*ClassicLedLayoutTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 所有输出0≤min<max≤1；像素矩形均在有效内容域内且非空。
- 参考配置每个坐标绝对误差≤5e-5；10000组性质测试无NaN/越界/除零。

**证据（2026-11-16）**：`ClassicLedLayout`、`LedSampleRect`、`PixelRect`、`CompiledLayout` 落地；12个`ClassicLedLayoutTest`通过，覆盖274/288、单边0/1、4:3（Edge Gap按640x480实际宽高比）、梯形角点、窄矩形、交叉/零面积/内缩退化拒绝及10000组随机性质测试。4位小数golden与S09固定提交/许可说明见`docs/verification/wled/lay-1/golden.txt`；原始输出见同目录。

<a id="lay-2"></a>

## LAY-2 — Gap/Input/Reverse与物理尾黑

- 优先级：**P0**；前置：`LAY-1`；初始状态：**待开发**
- 来源定位：[§4 布局与映射](../design/wled-direct-ambilight-feasibility.md#layout)；[A09](../design/wled-direct-ambilight-feasibility.md#a09)。

### 1. 交付物

- 编译SKIP索引列表S、BLACK掩码、物理索引映射与尾黑区域；在CompiledLayout保留逻辑槽号与物理槽号。
- 提供274/288基线与可供预览/行走像素复用的映射fixture。

### 2. 关键约束（含 DRY）

- DRY：映射只在CompiledLayout生成一次；采样、平均模式、预览与测试图案不各写gap/input/reverse。
- Input在S上floorMod；reverse在旋转之后；BLACK与尾灯在平均模式仍为黑。有效数K与物理数M分别建模。
- gap越界拒绝保存，不静默clamp用户错误；只对Input作有说明的归一化。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] LedMappingTest覆盖负Input/极大Input、gap首中尾、gap0/全长、Reverse四组合、M>K。
- [x] 274断言物理0/25/26/160/161/273对应160/135/134/0/273/161；288完整排列golden。
- [x] 随机10000组映射验证每个非gap逻辑索引恰好一次，BLACK不缩短数组，尾灯为黑。

**验证入口**：`:common:testDebugUnitTest --tests '*LedMappingTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 两套硬件fixture逐索引100%匹配；映射长度始终M。
- SKIP无gap索引、BLACK gap保留、尾灯全黑；任何有效映射无重复或遗漏。

**证据（2026-11-16）**：`LedMapping` 落地并挂入`CompiledLayout`；9个`LedMappingTest`通过，覆盖274/288锚点与完整排列、负/极大Input、gap首中尾/0/全长、Reverse四组合、M>K尾黑、BLACK gap保留，以及10000组随机映射无重复/遗漏。原始输出见`docs/verification/wled/lay-1/`。

