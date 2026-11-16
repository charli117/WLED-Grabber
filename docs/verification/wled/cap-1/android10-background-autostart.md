# Android 10 TV 后台运行设置与自启动需求增补

> 日期：2026-11-17。状态：**本机后台权限/电池优化白名单已配置并读回；应用默认行为与开机自启动尚未实现或验收**。
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

## 2. 自启动：当前实现与缺口

- 已安装包具备 `RECEIVE_BOOT_COMPLETED: granted=true`；设备 PackageManager 列出了 `.common.WledBootReceiver` 的 `BOOT_COMPLETED` 注册。
- 当前源码 `common/src/main/java/com/abrenoch/wledgrabber/common/WledBootReceiver.java:25–32` 只在 boot 偏好开启时调用 `postReminder`，不启动应用界面或采集。通知点击后才进入 `BootActivity` 并申请新会话。
- `common/src/main/res/values/pref_values.xml:23` 的源码默认值 `pref_default_boot=false`；`CaptureConfig.bootReminder()` 也明确表达提醒语义。本轮未读取/修改设备内已保存的 boot 偏好，不把源码默认值当成用户当前配置。
- 所以，收到开机广播的权限、后台白名单、录屏持续允许、用户自启动偏好、真正开机启动是不同条件；不能把前三项配置完成记为最后一项实现。

GitNexus 对 `WledBootReceiver.onReceive` 的 upstream impact 返回 `UNKNOWN`、0 个已解析 caller/process；Manifest 和设备注册证实它由 Android 系统触发，不能据图中 0 个调用者当成低风险或未使用。索引落后当前提交，代码修复前需重新核对。

## 3. 新需求与待确认公共边界

用户新增“app 允许默认后台运行及自启动”。它已纳入 CAP-1 / MOB-2 / QA-1 范围，但**开机后是进入待机还是直接开始采集/驱动灯带**尚待明确；后台系统设置已独立完成，不等待该产品决策。

后续实现须将默认偏好、显式关闭/撤销、配置或授权缺失、重复开机事件和平台不允许的启动分开处理；复用当前共享启动/验证/释放入口，不新增独立 sender，不保存或重放旧录屏 token，不用 root、全局放宽或伪造系统事件替代平台许可。

TDD 提议补充公共边界：`BOOT_COMPLETED → WledBootReceiver → 可观察启动/提醒结果`，以及配置仓库的默认值与显式关闭保存。它应覆盖默认行为、幂等与失败降级；纯 JVM/合成广播不能证明真实冷启动、Sony 的系统界面行为或其他 Android 版本兼容性。须与原先提议的公共 seams 一并经用户确认后才新增测试。

## 4. 验收分层

| 项目 | 本次状态 |
|---|---|
| 指定包后台两个 AppOps、白名单设置及独立读回 | 通过 |
| 既有录屏 allow、开机 receiver 注册和 permission 检查 | 已核对，不等于自启动已运行 |
| APK 新装默认开启及已有用户显式关闭的迁移规则 | 待实现/确认，未修改生产代码 |
| 真正开机后应用/采集自动启动 | 待行为确认、TDD 实现与冷启动实测 |
| 重启保持、后台长时运行、其他 API/厂商 | 未执行，不由本次读回外推 |

关联：[录屏持续授权](android10-persistent-consent.md)、[初审与 TDD 边界](../../../fixes/2026-11-17-completion-audit.md)。
