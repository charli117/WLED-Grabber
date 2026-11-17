# 2026-11-17 启动设置与审查修复最终验证快照

**结论：本轮已确认缺陷的代码与本地回归闭环；Sony 真正冷启动与实际灯带输出未验收。**

本轮执行中，另一并行任务将原 `docs/design` 技术方案、`docs/task`、`docs/fixes` 和 `docs/verification` 整理为现状态文档及归档。保留其移动与历史快照，不重建旧目录；本文件补充归档时尚在执行的最终测试结果，不改写中间 RED/GREEN 记录。

现行契约见 [技术架构 §6.2](../../../../design/architecture.md#lifecycle) 与 [配置及排障手册](../../../../guides/configuration-and-troubleshooting.md)；历史任务边界见 [任务矩阵](../../../task/00-overview.md)。

## 1. 本轮修复闭环

| 问题 / 需求 | 当前行为 | 证据 |
|---|---|---|
| 首次启动、默认后台/自启动设置 | TV/mobile 共用首次设置与重入入口；新装/缺省 boot=true，有效显式 false 保留；手动使用和 Back 取消仍可用；仅经原配置仓库保存 boot，不改其他设置 | [默认 RED](default-red-api33.txt)、[默认 GREEN](default-green-api33.txt)、最终两台设备日志 |
| 保存设置后类型崩溃 | 不再按 Boolean 读取 String-backed boot；设置完成标记与其他配置的持久化有真实 Android 回归 | [崩溃 RED](persistence-red-api33.txt)、最终设备回归 |
| 无效配置被当作关闭 | 独立读取既有 boot 字符串；仅有效 false 静默 opt-out。其他字段、boot 值或类型损坏仍提醒，全配置校验阻止自动采集；不擅自修复存储 | [坏配置 RED](invalid-config-red-api26.txt)、[坏 boot RED](corrupt-boot-red-api26.txt) |
| 服务存在被当作采集成功 | 先监听、再 GET_STATUS；只有 running=true 撤销提醒。idle/错误在自动入口保留提醒，手动入口可请求新授权 | [实际 idle service RED](idle-service-red-api33.txt)、[BootActivity 6 项 GREEN](boot-status-green-api33.txt) |
| 页面重建丢失等待状态 | 恢复原 10 秒单调时钟截止时间；等待 consent 时先恢复该阶段，不重复查询 idle 服务/请求授权。只存阶段与期限，不存授权 Intent/token | [重置超时 RED](recreate-deadline-red-api33.txt)、[重复授权 RED](manual-consent-recreate-red-api33.txt)、[6 项 GREEN](boot-status-green-api33.txt) |
| AUD-02 最后一帧滞留 | writer 退出前在同一锁内复查 pending，避免接受帧后没有 writer；不新增调度层或依赖 | [Streamer RED](streamer-red.txt)、[8 项 GREEN](streamer-green.txt)；20 次重复通过是概率压力证据，不是确定性调度证明 |
| 任务索引遗漏 | 恢复被重复锚点替换的 REL-1 行；26 个唯一任务 ID、同名审查锚点仅一个；保留该卡最后明确的“待验证”，未升级发布状态 | [结构 RED/GREEN](index-check.txt) |

AUD-01 的 HTTP state/info 契约错误已由并行修复关闭，未重复修改或归功于本次。限定代码复核最终无剩余阻断；它不是全项目/全部硬件无缺陷证明。

## 2. 最终验证结果

构建起点 HEAD `c46a877`，另含本轮未提交源码。对 **285 个源码/资源/构建输入**记录 SHA-256，最终构建期间无输入变化；设备回归后并行任务只改了 `WledScreenService` 的一行类级 Javadoc，恢复该注释后的整文件 SHA-256 与构建输入一致，未修改执行代码；详见 [统计与 APK 摘要](validation-summary.json)。文档整理和其他任务 WIP 不作为本次代码变更归属。

| 层级 | 结果 | 原始证据 |
|---|---|---|
| common JVM | **218 tests，0 failure/error/skip** | [完整强制重跑构建](final-build.txt)、[统计](validation-summary.json) |
| mobile JVM | **1 test，0 failure/error/skip** | 同上；TV unit task 为 NO-SOURCE，不计测试通过数 |
| 三模块 lint | **0 Error / 0 Fatal**；Warning 为 common 99、mobile 8、TV 55 | 同上；不声称零警告 |
| Debug 产物 | common 仪器 APK、mobile/TV APK 均成功；169 个 Gradle task 实际执行 | [构建日志](final-build.txt) |
| Android API 26 手机模拟器 | **OK (42 tests)，0 failure/skip** | [完整日志](final-api26.txt) |
| Android API 33 TV 模拟器 | **OK (42 tests)，0 failure/skip** | [完整日志](final-api33.txt) |
| 设备测试范围 | 每台包含 startup **28 项**（设置页 7、设置持久化 7、广播 8、BootActivity 6），以及既有 common 14 项 | 覆盖 ImageReader、输出租约、配置存储、loopback 网络失败和预览；不是 mobile/TV 所有应用级 UI 仪器测试 |

最终测试 APK 的 **targetSdk=35**（由并行提交 `c46a877` 对齐），`minSdk=21`。安装只针对隔离包 `com.abrenoch.wledgrabber.common.test`，使用 `adb install -r -g` 授予其声明的运行时通知权限；没有授予其 PROJECT_MEDIA 或执行真实录屏。故本轮不包含通知权限拒绝验收。ADB shell 返回码不能独立证明仪器测试成功，已逐项核对 42 个成功状态及 `OK (42 tests)`。

`BootStatusHarnessActivity` 仅在 androidTest 注册，mobile/TV 产品 Manifest 均未注册它。它只控制 Android 服务状态回复与授权请求分发，运行真实 Activity 生命周期和通知；注入 running 回执不证明实际 projection/LEDs 成功。实际 idle service 用例不依赖伪造的服务运行状态。

lint 的 `BatteryLife` 是请求电池优化豁免的分发政策提示；本项目当前为明确选择的侧载场景，不据零 error 宣称符合 Google Play 审核。未增加全局 lint 屏蔽；新页面中英资源之外沿用默认语言回退。

### 复跑命令

```sh
# 本轮使用仓库已有 JDK 17；沙箱内以可写的临时 Gradle/Android home 复用缓存和 debug key。
./gradlew --offline --no-daemon --no-watch-fs --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process --rerun-tasks \
  :common:testDebugUnitTest :mobile:testDebugUnitTest :tv:testDebugUnitTest \
  :common:lintDebug :mobile:lintDebug :tv:lintDebug \
  :common:assembleDebugAndroidTest :mobile:assembleDebug :tv:assembleDebug

# 仅针对隔离模拟器，绝不能用实际产品包代替 common.test。
adb -P 5037 -s emulator-5556 install -r -g common/build/outputs/apk/androidTest/debug/common-debug-androidTest.apk
adb -P 5037 -s emulator-5556 shell am instrument -w -r \
  -e package com.abrenoch.wledgrabber.common \
  com.abrenoch.wledgrabber.common.test/androidx.test.runner.AndroidJUnitRunner
# API26 使用 emulator-5554；实际环境应先确认该 serial 的设备身份。
```

## 3. 图分析与并行边界

BootActivity 类级 impact 涉及 mobile MainActivity、TileService 和 TV MainActivity；生命周期回调图为 UNKNOWN/有缺口，已按 Manifest、实际调用和 Android 回归核对，不将 0 caller 当作安全结论。AUD-02 的修改前风险为 CRITICAL，生产修复限定为退出锁边界。全工作区 changes 图包含其他任务 WIP，风险仍为 critical，见 [图结果](graph-changes.txt)；索引/图边界与未索引测试不能被当成完整发布证明。本轮没有提交、覆盖其他任务改动或自行重建持久索引。

文档链接最终检查为 30 文件 / 315 链接 / 0 错误；任务索引 26 个唯一 ID。本轮代码/测试/现行文档范围的 `git diff --check` 通过。全工作区检查另提示并行归档的历史评审文档一处 Markdown 双空格硬换行，未擅自修改历史格式。

## 4. 尚未关闭的真实设备验收

- Sony `10.245.0.109:5555` 定向重连后仍 `offline`，见 [连接记录](tv-connectivity.txt)。本轮未在电视安装生产 APK、停止真实采集或重启电视。
- 先前仅对指定包配置并读回 PROJECT_MEDIA、后台两个 AppOps 和电池白名单；本轮没有在电视补齐 SYSTEM_ALERT_WINDOW，不能宣称全部自动启动条件已就绪。
- 必须另验：首次设置后真实关机/开机、免二次警告的新会话、权限与偏好跨重启保持、receiver→新授权→前台服务→投射/display→实际 WLED/灯带输出；其他 API/厂商与物理时延/RGBW/长时矩阵仍保留门禁。
- “持续授权”是可撤销的设备权限，不承诺权限重置、卸载重装、恢复出厂或系统升级后永久有效。普通应用不会自行获取这一管理权限。
- 历史任务矩阵仍有 REL-1 待验证、QA-2/QA-3 阻塞项；本轮局部通过不关闭完整项目审核或发布准入。
