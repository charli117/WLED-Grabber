# 02. 配置快照与设备能力模型

> 模块前缀：`CFG`；建议责任人：共享配置维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility-2026-11-16.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

common/.../util/Preferences.kt、ConfigRepository/ConfigValidator、led配置模型、pref_values.xml

**并发编辑热点 / 合并纪律**：Preferences.kt、pref_values.xml、strings.xml由CFG单一维护；TV/MOB/PRE只消费，不平行新增同名key。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="cfg-1"></a>

## CFG-1 — 纯模型、集中校验与原子保存

- 优先级：**P0**；前置：`FND-2`；初始状态：**待开发**
- 来源定位：[§3 配置契约](../design/wled-direct-ambilight-feasibility-2026-11-16.md#config)；[§4 布局与映射](../design/wled-direct-ambilight-feasibility-2026-11-16.md#layout)；[A09](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a09)。

### 1. 交付物

- 实现不可变LedLayoutConfig/CaptureConfig/ValidatedConfig与schemaVersion/revision；ConfigRepository作为Preferences唯一组合入口。
- 对默认值、损坏值、数值溢出、跨字段约束返回结构化字段错误；一次edit保存整份配置，提交成功才发布revision。

### 2. 关键约束（含 DRY）

- DRY：继续复用Preferences的key/default资源规则，纯模型不调用Android Resources；TV/mobile禁止重复parseInt、范围常量和校验公式。
- 每边0..1000且总数>0；SKIP后>0；M1..65536且M≥有效数量；端口1..65535、帧率1..60、长边≤640；几何深层校验交LAY-1同一入口组合。
- 保存失败不污染旧快照；运行中保存只标记下次启动生效，禁止热改byte stride/设备/布局。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] ConfigValidatorTest覆盖空值、非法字符串、极大整数、负值、gap清空、M不足、帧率0/61及默认值。
- [x] ConfigRepositoryTest注入写失败并校验对外revision/已提交快照未变；仪器测试单次edit的可见性及commit失败后SharedPreferences内存值可能变化，验证UI无法旁路读取。
- [x] 用同一输入分别走mobile/TV适配，得到同一字段错误码。

**验证入口**：`:common:testDebugUnitTest --tests '*Config*Test'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 全部边界用例通过，非法输入崩溃0；一次保存只发布1个revision。
- M不足启动被拒绝，不能通过“强制保存”激活；Repository对外已提交快照在失败后字段不变；不声称底层SharedPreferences自动回滚。

**证据（2026-11-16）**：`ConfigValidator` 10个测试、`ConfigRepository` 7个测试、mobile/TV适配契约1个测试全部通过；`PreferencesConfigStorageInstrumentedTest` 已编译进androidTest APK（未连接设备执行）。`ConfigRepository` 只在一次`commit()`成功后发布新revision；写失败保留已提交快照，不读取底层SharedPreferences的重启内存视图。原始输出见`docs/verification/wled/cfg-1/`。

<a id="cfg-2"></a>

## CFG-2 — 区分端点、能力观察与用户配置

- 优先级：**P0**；前置：`CFG-1`；初始状态：**待开发**
- 来源定位：[§3 配置契约](../design/wled-direct-ambilight-feasibility-2026-11-16.md#config)；[§8 设备控制与LAN安全](../design/wled-direct-ambilight-feasibility-2026-11-16.md#network)；[A02](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a02)；[A06](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a06)；[A11](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a11)。

### 1. 交付物

- DeviceEndpoint分别包含HTTP地址/端口、DNRGB端口、固定DDP端口；WledDevice/Capabilities包含版本、身份、检测时间、观察count与提示字段。
- 统一字段描述/默认值和启动前确认项：通道模式、WLED Manual/offset/segment/DMX配置、有限timeout；保持物理观察值与用户覆盖值可追溯。

### 2. 关键约束（含 DRY）

- DRY：设备模型供发现、启动、设置、诊断复用；不要在三个UI重建WLED JSON DTO。
- rgbw/lc/seglc只能提示，不推导全设备物理stride；RGBW选DDP，不允许type5。只验证同构RGB/RGBW，混合/CCT不能标记已兼容。
- 手动Host始终可用，但未探测/未确认不能伪装为已验证；IP不是身份，旧IP缓存不得直接继承上次能力；发送长度等于已确认全设备数量，手动值与有效探测count不一致阻断启动，禁止截断设备尾灯。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] DeviceConfigTest覆盖HTTP非80端口、UDP自定义端口、DDP4048不被覆盖。
- [x] 能力缺字段、rgbw与用户设置冲突、身份变更、count减少、schema变更的快照测试。
- [x] 保存通道变化后旧运行快照仍为原channels，新启动才能应用。

**验证入口**：`:common:testDebugUnitTest --tests '*DeviceConfigTest'`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 三类端口100%独立；RGBW到type5的路由0。
- 缺失/冲突能力均可见；能力不足或count不足不进入READY；配置与观察时间戳不互相覆盖。

**证据（2026-11-16）**：`DeviceEndpoint`（HTTP/UDP/DDP分离）、`Capabilities`/`ChannelHint`、`WledDevice`、`ProbeResult`、`StartConfirmation`、`Transport`、`ReadyDevice`、`DeviceReadiness`、`DeviceState` 落地；11个`DeviceConfigTest`通过。覆盖HTTP非80、UDP自定义、DDP4048固定、缺失能力、RGBW冲突、身份变更、count减少、schema变更、旧快照不被通道变更修改、配置/观察时间戳独立；`Transport`无type5。androidTest APK编译通过（未连设备执行）。证据见`docs/verification/wled/cfg-2/`。

**2026-11-17 交付复核（状态下调为开发中）**：重新以生产调用方核对，`DeviceReadiness`、`StartConfirmation`、`ReadyDevice` 均无服务/UI 调用；`WledScreenService`、`BootActivity`、`WledTileService`、`TestPatternActivity` 只做 `ConfigValidator.validateForStart` 本地字段校验，开始前不探测设备、不比较观察 count/通道/MAC。因此“缺失/冲突能力均可见、能力不足或count不足不进入READY”仅在类级单测成立，不是运行行为。本轮另将四处重复的 host/count/rgbw 判断统一为 `validateForStart`，但不替代 START 前探测。该卡保持**开发中**，需接入观测+确认项后才可重新验收。

