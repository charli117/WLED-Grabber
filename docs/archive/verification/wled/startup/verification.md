# 首次设置、持久化与开机启动验证

日期：2026-11-17。范围：`StartupPolicy` / `StartupAccess` / `StartupSetupActivity`、TV/mobile 入口、`WledBootReceiver` / `BootActivity`，以及初审 AUD-02 的锁边界修复。用户已确认公共测试边界。

## 验证边界

- APK 新安装/缺省配置默认开启 boot；已有明确关闭保留。首次页保存只修改 boot 字段，经现有配置仓库校验；设置完成标记单独落盘。后台/录屏访问状态始终读取系统，不把页面缓存或完成标记当成授权。
- API 29 自动路径必须同时满足设置已完成、配置可启动、PROJECT_MEDIA、后台 Activity 启动许可、后台两个 AppOps 和电池豁免。其他 API 保持手动授权或提醒；应用不自行执行管理命令。
- 真实 Android 测试使用独立 `com.abrenoch.wledgrabber.common.test`，不是电视生产包；Preferences 有隔离及包名保护。接收器测试直接调用公开 `onReceive` 并观察实际通知和 Activity 请求，不向系统注入 BOOT_COMPLETED，更不假装重启成功。
- 同一启动的 `boot_count` 持久化测试读取真实系统启动编号，不写 Global Settings。它不证明下一次物理启动的结果。
- 本轮未在 Sony 电视安装 APK、杀进程或重启；当前 `adb ... get-state` 返回 `error: device offline`（退出码 1）。系统授权读回的历史证据不证明新构建已安装或新会话/冷启动已成功。

## RED → GREEN 证据

| 切片 | 失败证据 | 修复与复验 |
|---|---|---|
| 首次默认允许开机捕获 | [API 33 真实断言 RED](default-red-api33.txt)：`bootEnabled()` 为 false | 缺省资源改为 true；[同用例 GREEN](default-green-api33.txt)，不覆盖已存 false |
| 设置保存后重建 | [API 33 崩溃 RED](persistence-red-api33.txt)：`String cannot be cast to Boolean` | `1da5401` 先改用类型化快照，本轮再独立读取既有字符串 boot，以避免其他无效字段被误判为 opt-out；不另建双份配置 |
| 无效配置错误吞掉提醒 | [API 26 RED](invalid-config-red-api26.txt) / [损坏 boot RED](corrupt-boot-red-api26.txt) | boot 选择与完整可启动性分开，仅有效 false 静默退出，无效字段不改写 |
| 服务存在不等于采集成功 | [API 33 实际 idle service RED](idle-service-red-api33.txt)：提醒 1→0 | BootActivity 先监听再查询 GET_STATUS，仅 running=true 撤销提醒；初次集成 [23 项 GREEN](startup-review-green-api33.txt) |
| 自动等待页重建重置超时 | [真实重建 RED](recreate-deadline-red-api33.txt) | 保存原单调时钟截止时间并重建监听，[2 项 GREEN](recreate-deadline-green-api33.txt) |
| 手动恢复后等待授权时重建重复请求 | [API 33 / target35 RED](manual-consent-recreate-red-api33.txt)：请求数 1→2 | 服务查询前恢复 awaiting-consent 阶段，只等待系统结果；[BootActivity 6 项 GREEN](boot-status-green-api33.txt) |
| 稀疏生产最后一帧排空 | [公开 Streamer RED](streamer-red.txt)：pending=true、inFlight=false、poolInUse=1，发送 1725/1726 | `caa1d33` 在 writer 退出锁内复查 pending；[8 项 GREEN](streamer-green.txt)，随后 20 次重复通过。压力测试是概率复现，不是确定性调度证明 |

以上保留原失败，不把修复后的历史错误当成仍存在的缺陷。完整启动回归包含对既有行为的补测，并非每个用例都声称曾 RED。

## 本轮最终本地验证

最终统计见下表和原始输出；不得把 JVM、模拟器、真实电视三层互相替代。

BootActivity 的状态/授权请求测试使用 **test-only** `BootStatusHarnessActivity`：只控制 Android `startService(GET_STATUS)` 的回复时机与 `startActivityForResult` 分发，执行实际 Activity 创建/重建、通知和生产监听代码；不替换 StartupAccess，不注入私有状态，不提供真实录屏 token。注入的 running 回执只证明回执处理，不证明实际投射或 LEDs 输出。该 harness 不进入 mobile/TV 正式 APK。

## 未关闭验收

1. Sony API 29 全部访问许可实际就绪，首次设置完成后重启，观察 receiver → BootActivity → 新授权结果 → 前台服务 → 新投射/display → WLED 实际 live/灯带输出。
2. 真机无二次敏感信息确认、权限撤销后正确回退、重启权限保持、重复入口与服务启动超时，以及长时/息屏/厂商策略。
3. API 21/23/29/34/35 等未在本轮执行的启动页或采集矩阵；不能从 API 26/33 的手动回退外推自动路径。

关联：[设计 §6.2](../../../design/wled-direct-ambilight-feasibility-2026-11-16.md#lifecycle)、[任务索引](../../../task/00-overview.md)、[后台/自启动](../cap-1/android10-background-autostart.md)、[初审关闭矩阵](../../../fixes/2026-11-17-completion-audit.md)。
