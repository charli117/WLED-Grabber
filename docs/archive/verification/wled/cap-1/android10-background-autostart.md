# Android 10 TV 后台运行设置与自启动需求增补

> 日期：2026-11-17。状态：**本机后台权限/电池优化白名单已配置并读回；开机自启动已按受控路径实现（提交 `bfff381`），真实冷启动与重启保持未实测**。
> 范围：用户指定 Sony `BRAVIA 4K UR3`，Android 10 / API 29，ADB `10.245.0.109:5555`，用户 `0`，仅 `com.abrenoch.wledgrabber`。

## 1. 本机后台权限：已执行

操作前两个后台 AppOps 均为 `No operations. Default mode: allow`；电池优化 user 白名单只有旧包 `com.abrenoch.hyperiongrabber`，不含新版包。确认用户与目标包后，执行：

```sh
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops set --user 0 com.abrenoch.wledgrabber RUN_IN_BACKGROUND allow
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops set --user 0 com.abrenoch.wledgrabber RUN_ANY_IN_BACKGROUND allow
adb -P 5037 -s 10.245.0.109:5555 shell dumpsys deviceidle whitelist +com.abrenoch.wledgrabber
```

即时及独立再次读回：

```text
RUN_IN_BACKGROUND: allow
RUN_ANY_IN_BACKGROUND: allow
user,com.abrenoch.wledgrabber,10138
```

之前配置的 `PROJECT_MEDIA: allow` 保持；旧包的白名单不变。没有关闭全局 Doze、修改其他应用、重启电视、强停进程、伪造开机广播或新启动采集。设置后一次观察存在本包投射，随后观察为 `null`；不能由时点观测推断连续运行或归因状态变化。

恢复本次变更前语义的命令（**未执行**）：

```sh
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops set --user 0 com.abrenoch.wledgrabber RUN_IN_BACKGROUND default
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops set --user 0 com.abrenoch.wledgrabber RUN_ANY_IN_BACKGROUND default
adb -P 5037 -s 10.245.0.109:5555 shell dumpsys deviceidle whitelist -com.abrenoch.wledgrabber
```

这是已连接电视的系统管理配置，不是 APK 在所有设备上自动获取了这些权限；也不是永不被回收、屏幕关闭后仍采集或重启后自动采集的证明。原有用户停止/息屏/授权撤销释放行为不因后台白名单而取消。

## 2. 自启动：实现现状（已实现，冷启动未实测）

实现前源码在 `WledBootReceiver` 中只调用 `postReminder`，且 `pref_default_boot=false`。提交 `bfff381` 之后：

- `StartupPolicy`（纯策略）按平台版本、`PROJECT_MEDIA`、后台启动（悬浮窗或 device owner）、后台执行（两个 AppOps）、电池豁免五项授权决定 `START_CAPTURE` / `REMIND`；只有 API 29 自动路径经过审计，其余版本保持手动采集。
- `StartupAccess` 只持久化用户选择（boot 偏好，以及 `startup_setup` 中的完成标记与开机尝试）；系统授权每次实时读取，不做缓存。`claimBootAttempt` 用 `Settings.Global` 的 `boot_count` 保证每次真实开机只尝试一次。
- `WledBootReceiver` 在偏好开启且录屏服务未运行时调用 `StartupAccess.status().onBoot(...)`：配置与授权齐全则先发提醒作为兜底，再以 `EXTRA_AUTOMATIC_BOOT` 启动 `BootActivity`；其余情况维持提醒。服务报告已开始采集后（`BROADCAST_FILTER` 成功）撤销提醒；这不代表物理灯带确认输出，出错或 10 秒超时则保留提醒。
- `BootActivity` 自动路径在配置缺失、授权缺失或用户取消时降级为提醒。服务存在时先监听后 GET_STATUS，不当作成功；仅 running=true 撤销提醒。页面重建恢复等待 consent 阶段或原 10 秒服务截止时间，不重置等待、不重放 token、不重复申请授权；idle 的手动入口仍可重新请求新授权。
- `StartupSetupActivity` 展示实时授权状态，可复制管理员 ADB 命令但自身不执行；用户未完成设置时，主界面入口会引导进入。
- 源码默认值 `pref_default_boot=true` 只影响新装/缺省配置，设备内已保存的偏好不被改写。

仍未验证：真实开机冷启动、重启保持、息屏与厂商限制下的实际推流，以及其他 API 版本。设置页文案也明确说明它不启动采集、不能证明冷启动。

GitNexus 对 `WledBootReceiver.onReceive` 的 upstream impact 返回 `UNKNOWN`、0 个已解析 caller/process；Manifest 和设备注册证实它由 Android 系统触发，不能据图中 0 个调用者当成低风险或未使用。

## 3. 已确认需求与公共测试边界

用户新增“app 允许默认后台运行及自启动”。它已纳入 CAP-1 / MOB-2 / QA-1 范围。开机后的目标行为已于实现时确定：授权与配置齐全则直接开始采集，否则降级为提醒（见 §2）；后台系统设置不依赖该决策。

实现已把默认偏好、显式关闭/撤销、配置或授权缺失、重复开机事件和平台不允许的启动分开处理；复用共享启动/验证/释放入口，不新增独立 sender，不保存或重放旧录屏 token，不用 root、全局放宽或伪造系统事件替代平台许可。

公共边界测试：`StartupPolicyTest` 4 项 JVM 用例覆盖“授权齐全启动 / 显式关闭优先 / 缺配置或缺任一授权降级提醒 / 其他版本不继承 API 29 例外”；仪器用例 `StartupSetupActivityTest`（7 项）与 `StartupSettingsInstrumentedTest`（7 项）覆盖默认开启、显式关闭与完成标记持久化、缺授权不可持久化为自动设置、不支持平台不展示授权入口、D-pad 与返回取消。另外补充接收器/真实通知（8 项，含损坏配置仍提醒、显式关闭仍静默）和 BootActivity（6 项，含实际 idle 服务、受控状态回执、两种重建等待）的公开边界测试。模拟器实际执行结果、原始日志与缺口见[启动验证记录](../startup/verification.md)。纯 JVM/合成广播不能证明真实冷启动、Sony 的系统界面行为或其他 Android 版本兼容性。

## 4. 验收分层

| 项目 | 本次状态 |
|---|---|
| 指定包后台两个 AppOps、白名单设置及独立读回 | 通过 |
| 既有录屏 allow、开机 receiver 注册和 permission 检查 | 已核对；receiver 已改按 §2 受控路径启动 |
| APK 新装默认开启及已有用户显式关闭的迁移规则 | 已实现：新装默认 `true`，已保存偏好不改写；设置页写回用户选择 |
| 真正开机后应用/采集自动启动 | 已实现受控路径；真机冷启动与重启保持未实测 |
| 重启保持、后台长时运行、其他 API/厂商 | 未执行，不由本次读回外推 |

最新设备状态：2026-11-17 `adb -P 5037 -s 10.245.0.109:5555 get-state` 为 `offline`。本轮未对电视配置 `SYSTEM_ALERT_WINDOW`、安装生产 APK 或执行重启，不能据已有 PROJECT_MEDIA/后台白名单读回声称全套自启动条件已齐。

关联：[录屏持续授权](android10-persistent-consent.md)、[初审与 TDD 边界](../../../fixes/2026-11-17-completion-audit.md)。
