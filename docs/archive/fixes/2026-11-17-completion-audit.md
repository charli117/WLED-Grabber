# 2026-11-17 完成状态审查与 TDD 修复记录

> **状态：已确认的局部缺陷已修复；完整设备/发布验收仍未闭环。** 用户已确认下述公共测试边界，并补充“首次设置持久保存、开机后自动采集驱动灯带”。按小切片执行 RED→GREEN；不把局部通过表述为整个项目无缺陷。
> 初审发现时 HEAD 为 `172949a`；后续修复已进入 `bfff381`、`caa1d33`、`1da5401` 等并行提交。审查期间存在其他任务与 WIP，不归功或覆盖它们；每次修复重新核对当前源码。GitNexus 索引落后，风险结果仅作有边界的调用图证据，未自行重建索引。

> 后续更正：AUD-01 的 `live` 缺失回退是当时的修复，不是当前读回契约；两个端点矛盾时的修复与证据统一见[待机修复报告](../../fixes/2026-11-18-tv-standby.md)。

## 1. 完成证据分层

- 初审时任务总览有 26 张卡：11 已验收、12 待验证、2 待开发、1 延期。恢复遗漏索引行后的当前标签为 23 已验收、REL-1 待验证、QA-2 待验证/BLOCKED、QA-3 待开发/BLOCKED。这是任务文档已有证据的汇总，不是本轮独立认证全部 23 张卡；新增自启动始终单独验收。
- 初审 Android 无关子集 JUnit **161 项通过**是历史基线。后续新增回归与 Android 验证分开记录；不把旧报错重新认定为当前缺陷。
- 原 Gradle 缓存锁阻断已通过临时可写 `GRADLE_USER_HOME`（复用现有缓存，离线构建）解决，未修改用户全局缓存。后续构建、lint、真实 Android instrumentation 与设备限制见[启动验证记录](../verification/wled/startup/verification.md)。
- 用户指定 Sony Android 10 的包级录屏授权已配置并读回 `allow`，详见[设备授权记录](../verification/wled/cap-1/android10-persistent-consent.md)。该设置不提升其他验收层级。

## 2. 逐项关闭矩阵

| ID | 等级 | 初审源码位置（修复前） | 影响与契约 | 状态 |
|---|---|---|---|---|
| AUD-01 | P1 | `common/src/main/java/com/abrenoch/wledgrabber/common/network/wled/WledHttpClient.java:156–169` | `readStateBlocking` 只读 `/json/state`，却同时要求 `on` 与 `live`；方案 §6.3 要组合 state 的 `on` 与 info 的 `live`，现有 fixture 把两者放在 state，掩盖正常响应被判 `MISSING_FIELD` 的问题 | 已修复（2026-11-17，真机 WLED 16.0.1 复现）：`live` 缺失时回退读取 `/json/info`，新增 `liveFallsBackToInfoWhenStateOmitsIt`（先 RED 后 GREEN），详见[05-wled.md](../task/05-wled.md) |
| AUD-02 | P2 | `common/src/main/java/com/abrenoch/wledgrabber/common/capture/LedStreamer.java:191–200,208–217,237–241` | writer 读到空 pending 与设置 `writerRunning=false` 分属两次锁；中间 offer 被接受但不调度 writer，最后一帧可滞留至下一次 offer/stop | 已修复（`caa1d33`）：退出 writer 前在同一锁内重新检查 pending。公开 `offer`→sink/租约测试在修复前捕获最后一帧滞留，修复后 8 项通过及 20 次重复通过；仍是概率压力测试，不冒充确定性调度覆盖 |

独立复核补充两项 P2：① BootActivity 将服务“存在”当作启动成功，实际 idle service 测试已 RED（提醒被错误撤销）；改为先监听再请求公开 GET_STATUS，只有真实 running=true 才撤销，idle 自动入口保留提醒、手动入口可重新申请授权。② 配置加载失败被误读为用户 opt-out，损坏其他字段/boot 类型的实际测试已 RED；改为独立读取既有字符串 boot，只有有效 false 静默关闭，全配置校验继续阻止无效配置自动采集。两项初次集成 API 33 的启动包 23 项 GREEN；随后重建等待时限、manual idle → consent → recreate 的重复授权均取得额外 RED，补存原截止时间与等待阶段后 BootActivity 6 项 GREEN（API 33 / target 35）。不由此宣称真机完整路径通过。

新增启动持久化缺陷：`StartupAccess.bootEnabled()` 原先按 Boolean 读取配置仓库写入的 String，保存设置后重建会抛 `ClassCastException`。Android instrumentation 已实际 RED；并行提交 `1da5401` 先改为读取 `ConfigRepository` 类型化快照；本次随后修正其对无效配置的 opt-out 混淆，保留字符串类型安全及现有配置仓库的写入约束。默认开启、显式关闭、配置其他字段不变的回归见启动验证记录。

AUD-01 的测试由 `/json/state` 和 `/json/info` 分别提供符合契约的响应；不能为了通过而继续改造 fixture 返回虚构的合并 schema。失败/超时仍不得误报释放确认，两个请求必须遵守整体停止预算。

AUD-02 的回归观察停止生产后、会话仍运行时的最后一帧发送和租约归还，不能调用 `stop()` 清理后据此判定正常 drain 无误。修复前 impact 报告 `CRITICAL`（capture/test-pattern 多条调用链），已说明风险并将生产变更限定在锁边界。没有反射、私有状态注入或测试专用调度 API；原始 [RED](../verification/wled/startup/streamer-red.txt) / [GREEN](../verification/wled/startup/streamer-green.txt) 保留。

## 3. 用户已确认的公共测试边界

| 公共边界 | 覆盖的用户/调用方行为 | 不代表什么 |
|---|---|---|
| `ConfigRepository` / 设置控制器的加载、保存结果 | 配置校验、原子提交、失败保留旧快照、两端参数一致性 | 不代替 Android Preferences 落盘与真实设置 UI |
| 合成帧 → 公开布局/采样管线 → 协议报文 / DatagramSink | 几何/物理映射、RGB/RGBW、字节序和分片 | 不证明物理颜色、灯带帧率、端到端延迟 |
| `WledHttpClient.readState` 等 HTTP 公共操作 → 受控 HTTP 端点 | AUD-01 的 schema/endpoint、失败语义和整体时间预算 | 不证明物理灯已灭或其他控制器不会再点亮 |
| `LedStreamer.start/offer/stop/metrics` → FrameSink 与 FramePool | AUD-02 的排空、租约归还、停止后无旧帧；调度控制边界另行明确 | 不凭概率压力测试声称确定性覆盖特定竞态 |
| `CaptureSession` 及 TV/mobile 页面上的开始、拒绝、返回、停止操作 | 授权生命周期、重复入口、状态显示和服务交互 | 纯状态机测试不代替 SystemUI 弹窗或 Sony 重启持久性 |
| 首次启动设置 → 实际 Preferences → 重建后用户选择；开机广播 → 提醒/启动结果 | 默认开启、显式关闭保留、未授权不伪完成、D-pad/返回、同一次启动仅一次尝试、未完成设置不拉起采集 | API 26/33 的降级验证不代表 API 29 全授权自动路径或 Sony 冷启动通过 |

## 4. 本轮交付与未关闭项

- 首次打开引导和设置重入复用共享 `StartupAccess`/配置仓库；系统授权每次重读，默认值不覆盖已有显式关闭，保存仅修改 boot 字段。手动模式和取消保留，后台白名单不取消用户停止/息屏/撤销的释放。
- 仅 API 29 且设置已完成、配置有效及全部系统访问就绪，才从 `WledBootReceiver` 经 `BootActivity` 请求新授权结果并走原服务；普通应用不会自行授予 PROJECT_MEDIA，也不存储 token。
- 未关闭：Sony 真正冷启动、启动后实际灯带输出、权限撤销与重启保持、其他 API 的完整采集矩阵、长时硬件指标。电视最新 ADB 状态为 `offline`；本轮未安装生产 APK 或重启电视。
- 任务卡的既有“已验收”仅适用于其原证据，不扩展到本次新增自启动；其余模块/硬件仍按任务依赖核对。详见[验证记录](../verification/wled/startup/verification.md)。

## 5. 索引完整性修复

发现 `20d4adb` 将 REL-1 任务行误替换为重复的完成审查锚点，导致总览仅剩 25 个任务 ID、同名锚点出现两次。已恢复最后明确的 REL-1 行与“待验证”状态，不升级其验收结论；结构复验为 26 个唯一任务 ID、该锚点仅一次，见[检查记录](../verification/wled/startup/index-check.txt)。
