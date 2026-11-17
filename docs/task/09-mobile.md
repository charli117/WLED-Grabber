# 09. 手机设置、授权入口与快捷磁贴

> 模块前缀：`MOB`；建议责任人：mobile模块维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility.md)；源码/决策/固定资料见其附录A/B/C。

> 2026-11-17 需求增补：[默认后台与自启动](../verification/wled/cap-1/android10-background-autostart.md)。本机后台权限/白名单已设置；自启动已按受控路径实现（`bfff381`：仅 API 29 且授权与配置齐全时启动，否则降级提醒），主界面新增启动设置入口，真实冷启动与重启保持未实测，不因源码实现标作设备验收完成。

## 模块边界

mobile SettingsActivity/MainActivity/TileService、mobile LayoutPreviewActivity与资源

**并发编辑热点 / 合并纪律**：SettingsActivity由MOB独占；公共BootActivity/通知由CAP；AndroidX迁移FND完成后再改，REL最后重命名。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="mob-1"></a>

## MOB-1 — 共享配置驱动的完整手机设置

- 优先级：**P1**；前置：`CFG-2`、`NET-1`；初始状态：**待开发**
- 来源定位：[§9 双端交互](../design/wled-direct-ambilight-feasibility.md#ui)；[§3 配置契约](../design/wled-direct-ambilight-feasibility.md#config)；[E09](../design/wled-direct-ambilight-feasibility.md#e09)。

### 1. 交付物

- 用AndroidX Preference/现有AppCompat宿主呈现完整WLED/布局设置，移除旧连接与X/Y输入。
- 复用发现/手动/错误反馈，支持草稿取消、原子保存和运行中待生效提示。

### 2. 关键约束（含 DRY）

- DRY：复用CFG描述/默认/Validator和NET解析；不继续在静态OnPreferenceChangeListener里散落新parseInt校验。
- 保持View UI，不引入Compose；共享资源R显式归属common；不把TV专属导航逻辑复制来。
- 设备观察与用户值分开显示；未验证RGBW和count冲突不能用静默fallback隐藏。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] MobileSettingsTest覆盖每个字段的合法/非法边界与整份保存/取消。
- [x] 与TV同输入fixture比较错误码、默认值、规范化Input与最终快照。
- [ ] 进程重建/损坏Preferences/键盘遮挡时不丢错误、不写半份配置。

**验证入口**：`mobile仪器测试 MobileSettingsTest；CFG共同fixture`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 首版字段覆盖率100%；mobile/TV同输入快照完全一致。
- 非法提交0，损坏配置不崩溃；热改活动session0。

**证据（2026-11-16，本地部分）**：`SettingsDraftController`与mobile全字段表单落地；4个`MobileSettingsTest`通过，覆盖整份加载/保存、非法输入不写存储、取消恢复、mobile/TV同输入错误码与最终快照一致。表单字段来自`ConfigFields.ALL`，保存经ConfigValidator/ConfigRepository原子提交；字段标签已改走共享`ConfigFieldLabels`（中英文资源）。**设备端（2026-11-17）**：新增`MobileSettingsFormTest`——遍历`ConfigFields.ALL`断言每个字段都有非空本地化标签（覆盖率100%），并在表单上验证主机与帧率标签可见（帧率需滚动到）；`ActivityScenario.recreate()` 后表单仍显示种子值 `192.0.2.11`，证明重建不丢值。API 21/33/34 均 3/3 通过。**键盘/IME（2026-11-17）**：API 26 模拟器上打开设置页并聚焦首个输入框，`dumpsys input_method` 显示 `mInputShown=true`，截图确认焦点字段位于键盘上方、未被遮挡。仍未覆盖：损坏Preferences。

<a id="mob-2"></a>

## MOB-2 — 入口、磁贴、预览与系统行为

- 优先级：**P1**；前置：`MOB-1`、`CAP-3`、`PRE-2`；初始状态：**待开发**
- 来源定位：[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility.md#lifecycle)；[§9 双端交互](../design/wled-direct-ambilight-feasibility.md#ui)；[A04](../design/wled-direct-ambilight-feasibility.md#a04)。

### 1. 交付物

- MainActivity/TileService/Toggle入口统一调用公共授权/停止路径；挂接mobile预览宿主与测试模式。
- 适配API33通知拒绝、API34授权撤销、target35 insets及锁屏/返回行为；内部组件导出最小化。

### 2. 关键约束（含 DRY）

- DRY：复用CAP公共入口、通知和状态，不在Tile持久化录屏Intent；复用PRE View/controller，不建另一套测试。
- tile只能在用户交互后引导前台授权；不能因Android限制而调用后台投屏；回到前台不自动重放旧token。
- 预览TEST无需投屏权限，离开即停止；提示未确认关灯可供用户在WLED端检查。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] MobileEntryTest覆盖主按钮/磁贴/入口解析/通知通道/进程重建：`MobileEntryTest`（主按钮可见可点）、`MobileEntriesInstrumentedTest`（磁贴服务与`_QS_TILE` 可解析、预览/测试 Activity 可解析、capture 通道与 boot 接收器注册）、`MobileSettingsFormTest`（recreate 保留值）。锁屏唤醒后 MainActivity 存活、无崩溃（API 26 模拟器）；系统撤销仍未覆盖。
- [ ] TEST模式不出现录屏弹窗，切换capture/test保持单owner。
- [~] API21/26/33/34/35上无不可用API调用：API 21/26/33/34 上 mobile 仪器均通过；API 35 部分（无 android-tv 镜像）。横竖屏 insets 未测。

**验证入口**：`mobile仪器测试 MobileEntryTest；API矩阵按QA-1/QA-2`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 所有入口使用同一授权/停止实现，重复token使用0。
- API矩阵关键流程通过或明确阻塞；内部无权限启动路径0；单设备writer≤1。

**当前进展（2026-11-16，未验收）**：进程级`OutputSessionCoordinator`已接入旧`HyperionScreenService`的CAPTURE与`TestPatternActivity`的TEST，二者互斥且旧epoch不能释放新owner；mobile菜单已接Preview/TestPatternActivity，Tile setup守卫改用`ConfigRepository`。通知拒绝提示、授权撤销、target35 insets、完整API矩阵与真机入口测试仍未完成。本卡保持“开发中”。

