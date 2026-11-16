# 10. 唯一后端切换、命名迁移与发布文档

> 模块前缀：`REL`；建议责任人：集成维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

全模块旧协议调用、proto/build依赖、包名/manifest/action/资源、README和迁移说明

**并发编辑热点 / 合并纪律**：这是全仓最高并发编辑热点；待FND/CFG/CAP/NET/PRE/TV/MOB合并后独立单PR执行，冻结其他源码修改。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="rel-1"></a>

## REL-1 — 原子切换WLED并移除旧协议/品牌

- 优先级：**P0**；前置：`TV-2`、`MOB-2`、`CAP-3`；初始状态：**待开发**
- 来源定位：[§10 构建与迁移](../design/wled-direct-ambilight-feasibility.md#build-migration)；[附录D 变更台账](../design/wled-direct-ambilight-feasibility.md#change-ledger)；[A10](../design/wled-direct-ambilight-feasibility.md#a10)；[A12](../design/wled-direct-ambilight-feasibility.md#a12)。

### 1. 交付物

- 切换唯一WLED运行入口，删除Hyperion.java/HyperionThread.java/HyperionProto.proto及临时protobuf插件/依赖/生成代码。
- 统一改类名、包目录、namespace/applicationId、actions/channel/tile/resource/测试引用；扫描全部语言values-*，消除活动UI旧品牌。
- 记录mobile/tv同ID替代APK与版本码来源，保留合法版权/来源例外清单。

### 2. 关键约束（含 DRY）

- DRY：一份命名映射/版本来源驱动三模块；使用GitNexus rename预览处理符号，逐项检查manifest/XML/反射，禁止仅全局字符串替换。
- 修改前逐符号impact，HIGH/CRITICAL先警示；提交前detect_changes完整不截断。图UNKNOWN要补文本证据，不当安全证明。
- 不保留旧后端开关/适配器/隐藏分支；历史报告和LICENSE不做零命中式删除；不移动用户仓库目录、不发布。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] 用入口/依赖/APK检查确认正式产物无protobuf和旧网络类、无旧后端菜单。
- [x] 三个模块build/test/lint；旧新包名引用残留逐条核对例外清单，所有翻译资源编译。
- [x] 安装前检查两APK manifest的package/版本/启动器/服务/权限，确认新旧应用身份区分。

**验证入口**：`:common:testDebugUnitTest :mobile:testDebugUnitTest :tv:testDebugUnitTest :mobile:assembleDebug :tv:assembleDebug :mobile:assembleRelease :tv:assembleRelease`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 活动源与产物旧协议类/依赖/后端开关0；未解释的旧品牌残留0。
- Debug与unsigned Release均可构建，引用/manifest错误0；所有例外具名且合法。

**证据（2026-11-16，本地未验收）**：唯一入口改为`WledScreenService`，旧`HyperionScreenService/Encoder/Thread/proto`与protobuf插件/依赖已删除；包目录、namespace、applicationId、service/action/channel、主题与活动UI字符串迁移到`com.abrenoch.wledgrabber`；多语言`values-*`活动品牌清理；Debug+unsigned Release+三模块lint通过；156 JVM测试通过；APK内旧身份/protobuf命中0；merged manifest为`com.abrenoch.wledgrabber`、versionCode 1012/2012、versionName 1.0。未安装、未签名、未发布；真机采集/关灯读回与Sony/RGBW验收未执行。翻译完整度仍有缺口（未翻译项回退默认文案，已用lint抑制并记录），不阻塞构建但不声称翻译全覆盖。REL-1保持“待验证”。证据见`docs/verification/wled/rel-1/`。2026-11-17：应用图标、自适应图标（含 Android 13 monochrome）、24dp 通知图标与 TV banner 已按[品牌与图标规范](../design/app-icon/brand-guide.md)替换；三模块 Debug 构建与 `:common:lintDebug` 通过（exit 0）。真机 launcher/themed icon/TV banner 显示待验收。

<a id="rel-2"></a>

## REL-2 — 配置重建、故障排查与侧载说明

- 优先级：**P1**；前置：`QA-1`；初始状态：**待开发**
- 来源定位：[§10 构建与迁移](../design/wled-direct-ambilight-feasibility.md#build-migration)；[§8 设备控制与LAN安全](../design/wled-direct-ambilight-feasibility.md#network)；[§6 并发与生命周期](../design/wled-direct-ambilight-feasibility.md#lifecycle)。

### 1. 交付物

- 交付旧字段->新配置映射、274参数、RGBW Manual/DDP前提、网络/停止未确认排障及隐私说明。
- README入口更新，列API/固件/灯带支持矩阵、mobile/tv替代APK、侧载/回退与未验证项，版本号与产物一致。

### 2. 关键约束（含 DRY）

- DRY：链接架构基线/任务矩阵作为参数和验收SSOT，不复制一份不同版本的wire规范。
- 明确旧X/Y不能推导四边，旧priority无WLED等价，旧端口不能迁移；新旧ID可共装但须停旧发送。
- 只发颜色不保存/上传屏幕图像，不申请音频；LAN无认证/HTTP明文与关灯不保证写清。签名/真机安装/发布需后续明确授权。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] 按文档从空配置走274设置，逐项核对与CFG/LAY fixture一致。
- [x] 链接检查、命令可发现性检查、版本/包名/权限与APK metadata对照。
- [x] 断网停止、通知拒绝、RGBW变黑案例能定位到明确检查步骤。

**验证入口**：`文档链接/来源审计 + APK metadata对照（不执行发布）`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 旧Host/Port/Priority/X/Y/帧率/平均色/开机行为均有映射或“不迁移”解释。
- 失效链接0；所有未测试组合标注未验证；签名/发布完成的虚假表述0。

**证据（2026-11-16）**：`docs/migration/legacy-to-wled-layout.md`交付；链接检查19个文档、217处链接0错误；旧字段映射/274参数/Input/Reverse/DDP/auto-white/RELEASE_UNCONFIRMED排障全部覆盖；README与合并APK manifest的package/versionCode/versionName/权限已对照（仍为旧身份`com.abrenoch.hyperiongrabber`，签名/发布/硬件未完成）。**2026-11-17 复核**：本地回归 `scripts/qa/local-regression.sh` 通过——25 个文档、241 处链接、0 错误；`docs/migration/legacy-to-wled-layout.md` 仍覆盖旧 Host/Port/Priority/X/Y/帧率/平均色/开机行为的映射或“不迁移”解释；QA-1 已验收（JVM 187/0、lint 0 error、Debug+Release 构建）。三项独立测试全部勾选，REL-2 验收通过。证据见`docs/verification/wled/rel-2/` 与 `docs/verification/wled/qa-1/`。

