# 01. 构建现代化与共享测试基座

> 模块前缀：`FND`；建议责任人：构建维护者。  
> 状态权威：[00-overview.md](00-overview.md)；本册编制时全部待开发。功能实现/设备验收尚未发生。
> 唯一技术来源：[最终技术方案](../design/wled-direct-ambilight-feasibility-2026-11-16.md)；源码/决策/固定资料见其附录A/B/C。

## 模块边界

根构建文件、三个模块构建、AndroidX/ViewBinding机械迁移、测试基座

**并发编辑热点 / 合并纪律**：build.gradle/settings.gradle/gradle.properties、common/variables.gradle、三个模块build.gradle、全项目import与R引用；FND完成前不要并行包重命名或UI替换。

**格式模板**：[01-foundation.md](01-foundation.md)；统一采用交付物、关键约束、独立测试与量化验收三段式。下列测试类/文件均为计划新增或改造，不代表当前已存在。执行时每张卡先通过公共行为写RED，再实现GREEN；纯文档卡用可失败的结构/链接检查替代代码单测。

<a id="fnd-1"></a>

## FND-1 — 锁定兼容工具链与可编译过渡

- 优先级：**P0**；前置：无；初始状态：**待开发**
- 来源定位：[§10 构建与迁移](../design/wled-direct-ambilight-feasibility-2026-11-16.md#build-migration)；[附录D 变更台账](../design/wled-direct-ambilight-feasibility-2026-11-16.md#change-ledger)；[E15](../design/wled-direct-ambilight-feasibility-2026-11-16.md#e15)；[A10](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a10)。

### 1. 交付物

- 固定AGP8.7.2/Gradle8.9/JDK17/Kotlin2.1.21候选；生成gradle/libs.versions.toml，校验wrapper来源/校验和，仓库声明收敛settings.gradle。
- 记录AndroidX与测试依赖的实际锁定版本、AAR minSdk/compileSdk和传递图；保留源码可编译所需的临时旧协议生成链，列入REL-1删除台账。

### 2. 关键约束（含 DRY）

- DRY：复用common/variables.gradle的集中版本意图，改为单一版本目录；三个模块不再各声明AGP/protobuf版本。
- 维持common/mobile/tv结构；min21、compile35、最终target35；中间分支不发布。不得因旧调用未删除而提前删protobuf，也不采用“最新版本”或动态版本号。
- JDK17缺失时记录环境阻塞，不修改用户全局JDK、不私自安装；依赖变更仅限已批准的现代化替换，不引入新运行框架。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] 在空缓存或受控CI解析依赖，归档JDK/Gradle/AGP/Kotlin版本与仓库访问结果。
- [x] 三个模块编译；核对旧proto调用仍可解析，确认没有因移除生成器而缺类。
- [x] 检查版本目录无重复版本源，依赖树中无jcenter-only来源。

**验证入口**：`:common:compileDebugJavaWithJavac :mobile:assembleDebug :tv:assembleDebug`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- 三个模块Debug编译均退出0；所有新增/升级依赖为精确版本。
- root+三模块jcenter声明0；受控构建使用JDK17；临时协议依赖100%登记。

**证据（2026-11-16）**：`docs/verification/wled/fnd-1/` 保存工具链、依赖图、AAR minSdk元数据与构建/单测原始输出。执行命令：`JAVA_HOME=.toolchains/jdk-17.0.21+9/Contents/Home ./gradlew :common:compileDebugJavaWithJavac :mobile:assembleDebug :tv:assembleDebug :common:testDebugUnitTest :mobile:testDebugUnitTest :tv:testDebugUnitTest`，退出0；临时保留 protobuf-gradle-plugin 0.9.4 + protoc 3.25.5 + javalite 3.25.5，待REL-1删除。

<a id="fnd-2"></a>

## FND-2 — AndroidX、ViewBinding与共享fixture

- 优先级：**P0**；前置：`FND-1`；初始状态：**待开发**
- 来源定位：[§2 架构与DRY](../design/wled-direct-ambilight-feasibility-2026-11-16.md#architecture)；[§11 性能与验证](../design/wled-direct-ambilight-feasibility-2026-11-16.md#validation)；[E16](../design/wled-direct-ambilight-feasibility-2026-11-16.md#e16)；[A10](../design/wled-direct-ambilight-feasibility-2026-11-16.md#a10)。

### 1. 交付物

- 替换Support、ButterKnife及不可获取依赖，处理common资源的显式R引用；保留Views/Leanback，固定支持API21的组件版本。
- 创建common/src/test/java/.../fixtures与common/src/test/resources/fixtures：RGBA平面、274/288布局、设备JSON、FakeClock、RecordingDatagramSink、延迟/失败控制端点；Android测试读取同一份fixture资源而不复制文件。
- 建立纯JVM与Android仪器测试入口，记录历史lint问题及其CAP/MOB任务归属，不用全局ignore掩盖。

### 2. 关键约束（含 DRY）

- DRY：所有后续模块复用本卡fixture、fake clock和recording sink；fixture新增按需扩展单一来源。
- AndroidX迁移和包名迁移分开；本卡保留旧包名，REL-1统一改名。保持test/prod界面隔离，禁止把测试socket实现带进正式APK。
- 仅编译基座与已有行为测试完成不意味着target35运行验收；授权/服务问题由CAP-1关闭。Konfetti非核心依赖若删除必须记录UI行为变化。

### 3. 独立测试与验收

**独立测试**（完成后勾选并附命令/原始证据）：

- [x] 运行现有ExampleUnitTest以及新增fixture自测（padding/非零buffer位置/时间推进/数据包捕获）。
- [x] 关闭Jetifier后编译三个模块；检查依赖树Support传递依赖为0。
- [x] 手机/TV布局编译，确认ButterKnife绑定已迁移，common R显式引用可解析。

**验证入口**：`:common:testDebugUnitTest :mobile:testDebugUnitTest :mobile:assembleDebug :tv:assembleDebug`。Gradle入口在FND基座完成后执行；仪器/硬件测试必须记录设备与退出码，不能由JVM通过推定。

**验收标准（量化）**：

- Debug编译与已有/fixture单测全绿；活动代码中的Support依赖与ButterKnife绑定0。
- 每类fixture仅一个共享来源；minSdk仍21；历史lint错误逐条分配且最终QA-1零错误门禁不变。

**证据（2026-11-16）**：AndroidX/ViewBinding迁移完成，ButterKnife删除，TV的NetworkScan/ScanResult改用ViewBinding；`common/src/sharedTest`同时供JVM与androidTest使用，JSON资源无复制。执行 `JAVA_HOME=.toolchains/jdk-17.0.21+9/Contents/Home ./gradlew :common:testDebugUnitTest :mobile:testDebugUnitTest :tv:testDebugUnitTest :mobile:assembleDebug :tv:assembleDebug :common:assembleDebugAndroidTest`，退出0；15个fixture测试与ExampleUnitTest通过；androidTest APK已编译（未连接设备执行）。依赖、lint与构建原始输出见`docs/verification/wled/fnd-2/`。

