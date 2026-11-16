# 04. 平面采样、平均色与RGBW

> 模块前缀：`SMP`；建议责任人：图像算法维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

common/.../led/LedColorSampler、PlaneView、RGBW转换、可选颜色处理

**并发编辑热点 / 合并纪律**：ScreenEncoder由CAP维护，SMP只定义PlaneView/租约写入接口；BorderProcessor的后续改造不要夹入首版。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="smp-1"></a>

## SMP-1 — 精确积分图采样与有界工作内存

- 优先级：**P0**；前置：`LAY-2`；初始状态：**待开发**
- 来源定位：[§5 采样与颜色](../design/wled-direct-ambilight-feasibility.md#sampling)；[§11 性能与验证](../design/wled-direct-ambilight-feasibility.md#validation)；[A08](../design/wled-direct-ambilight-feasibility.md#a08)。

### 1. 交付物

- 实现PlaneView检查、可复用RGB三通道积分图及矩形平均；独立于Android Image测试。
- 实现长边640、不放大源图的尺寸策略与工作缓冲容量计算，导出采样耗时/分配计数。

### 2. 关键约束（含 DRY）

- DRY：复用FND RGBA fixtures、LAY像素矩形；getAverageColor求和思路只作为测试oracle；生产平均与逐灯共用矩形查询。
- 支持非零buffer position、不同stride、cropRect、最后一行无padding；按limit校验最后读取字节。
- O(W×H+N)；每维≤640，int累积上界须断言；不复制完整RGBA帧给发送线程，不进行每帧数组分配。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] LedColorSamplerTest与朴素oracle比较随机1000帧，含RGB纯色/灰阶/棋盘/小矩形/全屏平均。
- [x] 非零position、pixelStride>4、row padding、最后一行紧凑、非法limit/crop用例。
- [x] 采样工作缓冲10万帧复用测试，记录分配、最大尺寸与无边界溢出。

**验证入口**：`:common:testDebugUnitTest --tests '*LedColorSamplerTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 随机oracle结果逐通道误差0（统一整数舍入）；畸形帧安全丢弃不越界。
- 积分图最大3×641×641×4字节，预热后应用热路径新数组0；实机时延另由QA-2验收。

**证据（2026-11-16）**：`PlaneView`、`IntegralImage`、`LedColorSampler`、`CaptureSizing`落地；7个`LedColorSamplerTest`通过。1000帧随机oracle逐通道一致，纯色/灰阶/棋盘/小矩形/全屏平均覆盖，非零position/pixelStride>4/row padding/紧凑末行与畸形limit/crop拒绝，100000帧后积分图仍只分配3个数组，640×640积分图=4,930,572字节（<12MiB）。证据见`docs/verification/wled/net/`（原始日志与采样测试同批）。

**证据（2026-11-17，真机帧率）**：真机发现逐帧成本过高——`IntegralImage.update` 旧实现对每个像素调用3次`PlaneView.read()`，640×360时约69万次`DirectByteBuffer`绝对读取（每次一次JNI跨越），实测WLED侧有效刷新仅27–33fps。改为`PlaneView.readRow()`按行批量读取（每行一次bulk get，再在Java数组内解析RGB），`IntegralImage.update`改用行缓冲；每帧JNI跨越降到约360次。1000帧随机oracle与非零position/padding/紧凑末行用例全部保持通过，逐通道结果不变。真机（Sony KD-55XD8005 + WLED_TV 274 RGB）WLED `/json/info` 的 `leds.fps` 从27–33升到68–175（均值约74），用户在60fps配置下确认观感跟手。QA-2的p95/p99量化门禁仍未测量。

<a id="smp-2"></a>

## SMP-2 — 平均模式掩码与单方白通道合成

- 优先级：**P1**；前置：`SMP-1`、`CFG-2`；初始状态：**待开发**
- 来源定位：[§5 采样与颜色](../design/wled-direct-ambilight-feasibility.md#sampling)；[§4 布局与映射](../design/wled-direct-ambilight-feasibility.md#layout)；[A06](../design/wled-direct-ambilight-feasibility.md#a06)。

### 1. 交付物

- 实现min_subtract与preserve，RGB帧保持三通道；平均模式通过同一采样结果填充有效映射。
- 将通道模式/white mode固定在配置epoch，并暴露Manual前提说明与测试颜色向量。

### 2. 关键约束（含 DRY）

- DRY：RGBW转换仅一个helper，真实帧与测试图案复用；BLACK/尾黑复用LAY掩码，不为平均模式写第二条输出路径。
- WLED Manual与全局override确认前不能宣称RGBW已验证；默认不叠加gamma/饱和度。preserve不能标注“亮度恒定”。
- 原平均模式隐式黑边检测不继承为默认，新平均以当前有效内容域为准；自动黑边仅SMP-3显式启用。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] RgbwConversionTest覆盖黑白/纯RGB/灰阶/低亮度，255白分别得到(0,0,0,255)和(255,255,255,255)。
- [x] AverageColorTest验证所有有效灯同色、BLACK gap与多余物理灯仍为0。
- [x] RGBW输出仅4字节/灯；测试图案和真实采样相同输入得到相同转换结果。

**验证入口**：`:common:testDebugUnitTest --tests '*RgbwConversionTest' --tests '*AverageColorTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 全部转换向量逐字节一致；通道溢出0，RGB无额外GRB重排。
- 平均模式gap/尾灯泄漏亮色0；RGBW实机验收仍由QA-2阻断，单测不能替代。

**证据（2026-11-16）**：`RgbwConverter`、`WhiteMode`与`LedColorSampler.samplePhysical`落地；min_subtract/preserve的255白、纯RGB、灰阶、低亮度向量逐字节一致；平均模式与逐灯模式共用同一LAY映射/黑灯掩码，BLACK gap与物理尾灯为0；RGB无额外GRB重排。6个Rgbw/Average测试通过；证据见`docs/verification/wled/net/`。

<a id="smp-3"></a>

## SMP-3 — 可选黑边与颜色质量增强

- 优先级：**P2**；前置：`SMP-2`、`QA-2`；初始状态：**待开发**（明确延期，不阻断首版）
- 来源定位：[§5 采样与颜色](../design/wled-direct-ambilight-feasibility.md#sampling)；[§1 目标与范围](../design/wled-direct-ambilight-feasibility.md#scope)。

### 1. 交付物

- 首版发布后独立评估黑边裁剪、线性平均/饱和度与时间平滑；每个开关默认关闭。
- 用既有BorderProcessor建立回归再决定最小复用修复；提供开关对照数据与裁剪稳定策略。

### 2. 关键约束（含 DRY）

- DRY：复用PlaneView、积分图、CompiledLayout与RGBW helper；新增裁剪只改变内容域，不复制布局/协议。
- 这张卡明确延期，不是首版验收前置；禁止为了增强改首套默认颜色合同。
- 裁剪需滞回/稳定帧，停止/旋转/epoch切换清空时间滤波历史，避免旧色污染。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [ ] 全黑/闪字幕/信箱黑边/渐变场景，内容域不能坍塌为0。
- [ ] 裁剪变化后映射仍在新域内；开关关闭输出与首版逐字节一致。
- [ ] 各增强开关分别跑性能/色彩对照，回归首套p95预算。

**验证入口**：`:common:testDebugUnitTest --tests '*Border*Test' --tests '*ColorQualityTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 关闭所有增强时与首版golden差异0；边界异常崩溃0。
- 每个开关有独立对照与回退；未经实测不得默认启用。

