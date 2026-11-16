# 2026-11-17 完成状态初审与待确认 TDD 边界

> **状态：初审完成，全面审查与缺陷修复未闭环。** 本轮没有新增测试或修改生产代码。用户指定的 TDD 技能要求先确认公共测试边界；以下修复须在确认后逐项执行 RED→GREEN。
> 最后复核发现时 HEAD 为 `172949a`；审查期间存在其他并行提交与 WIP，下面不是冻结发布基线。GitNexus 索引落后，仅用于定位，缺陷已核对当时当前源码，未重建索引。

## 1. 完成证据分层

- 任务总览当时有 26 张卡：11 已验收、12 待验证、2 待开发、1 延期。这里只统计文档标签，不将其升级为独立审计结论；概述与旧基线段落仍需逐模块重核。
- 当前源码的 Android 无关子集及既有测试重新编译执行，JUnit **161 项通过**；编译后与初始记录的源文件摘要比对无变化。没有把旧报错日志重新认定为现存缺陷。
- Gradle 入口受沙箱用户缓存锁写权限阻断；没有完成本轮 Android 全构建、lint、设备 UI 或硬件回归。
- 用户指定 Sony Android 10 的包级录屏授权已配置并读回 `allow`，详见[设备授权记录](../verification/wled/cap-1/android10-persistent-consent.md)。该设置不提升其他验收层级。

## 2. 尚待修复的高置信问题

| ID | 等级 | 当前源码证据 | 影响与契约 | 状态 |
|---|---|---|---|---|
| AUD-01 | P1 | `common/src/main/java/com/abrenoch/wledgrabber/common/network/wled/WledHttpClient.java:156–169` | `readStateBlocking` 只读 `/json/state`，却同时要求 `on` 与 `live`；方案 §6.3 要组合 state 的 `on` 与 info 的 `live`，现有 fixture 把两者放在 state，掩盖正常响应被判 `MISSING_FIELD` 的问题 | 已修复（2026-11-17，真机 WLED 16.0.1 复现）：`live` 缺失时回退读取 `/json/info`，新增 `liveFallsBackToInfoWhenStateOmitsIt`（先 RED 后 GREEN），详见[05-wled.md](../task/05-wled.md) |
| AUD-02 | P2 | `common/src/main/java/com/abrenoch/wledgrabber/common/capture/LedStreamer.java:191–200,208–217,237–241` | writer 读到空 pending 与设置 `writerRunning=false` 分属两次锁；中间 offer 被接受但不调度 writer，最后一帧可滞留至下一次 offer/stop | 静态确认，待可重复 RED；压力测试不能冒充确定性复现 |

AUD-01 的测试必须由 `/json/state` 和 `/json/info` 分别提供符合契约的响应；不能为了通过而继续改造 fixture 返回虚构的合并 schema。失败/超时仍不得误报释放确认，两个请求必须遵守整体停止预算。

AUD-02 的回归应观察停止生产后、会话仍运行时的最后一帧发送和租约归还，不能调用 `stop()` 清理后据此判定正常 drain 无误。修复前重新执行 GitNexus impact，核对并行改动是否已修复；如需引入确定性调度控制，先确认其公共边界，不借反射或私有字段测试。

## 3. 提议的公共测试边界（尚未经用户确认）

| 公共边界 | 覆盖的用户/调用方行为 | 不代表什么 |
|---|---|---|
| `ConfigRepository` / 设置控制器的加载、保存结果 | 配置校验、原子提交、失败保留旧快照、两端参数一致性 | 不代替 Android Preferences 落盘与真实设置 UI |
| 合成帧 → 公开布局/采样管线 → 协议报文 / DatagramSink | 几何/物理映射、RGB/RGBW、字节序和分片 | 不证明物理颜色、灯带帧率、端到端延迟 |
| `WledHttpClient.readState` 等 HTTP 公共操作 → 受控 HTTP 端点 | AUD-01 的 schema/endpoint、失败语义和整体时间预算 | 不证明物理灯已灭或其他控制器不会再点亮 |
| `LedStreamer.start/offer/stop/metrics` → FrameSink 与 FramePool | AUD-02 的排空、租约归还、停止后无旧帧；调度控制边界另行明确 | 不凭概率压力测试声称确定性覆盖特定竞态 |
| `CaptureSession` 及 TV/mobile 页面上的开始、拒绝、返回、停止操作 | 授权生命周期、重复入口、状态显示和服务交互 | 纯状态机测试不代替 SystemUI 弹窗或 Sony 重启持久性 |

执行顺序：先 AUD-01 一条失败行为测试与最小修复，再 AUD-02；其余模块继续按任务依赖逐项核对。每次修改前做 impact；小切片通过后扩展既有回归，并同步拥有该契约的技术方案、任务索引和本表。未完成的 Android/硬件证据保持待验证。
