# Android 10 TV 持续录屏授权：设备设置与证据边界

> 日期：2026-11-17。状态：**指定应用 AppOps 设置及读回通过；新会话免弹窗、进程重建和重启保持待实测**。
> 这是用户明确要求的设备管理配置，不是应用自动提权功能，也不代表 CAP-1、QA-1 或 QA-2 已完成。

## 1. 设备与授权范围

- 用户指定 ADB：`adb -P 5037 connect 10.245.0.109:5555`。
- 实测设备：Sony `BRAVIA 4K UR3`；Android 10 / API 29；当前用户 `0`。
- 目标应用：`com.abrenoch.wledgrabber`，实测 `versionCode=2012`、`versionName=1.0`、`targetSdk=35`。
- 同机有旧包 `com.abrenoch.hyperiongrabber`；本次没有修改旧包权限。
- 该权限允许目标应用以后申请屏幕采集时不再逐次询问，可能采集屏幕上的敏感内容；仅在设备所有者明确选择持续允许后配置。

## 2. 本次操作与读回

先确认当前 Android 用户以及目标包已安装，随后只修改该用户、该包的 `PROJECT_MEDIA`：

```sh
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops get --user 0 com.abrenoch.wledgrabber PROJECT_MEDIA
# 操作前：PROJECT_MEDIA: default
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops set --user 0 com.abrenoch.wledgrabber PROJECT_MEDIA allow
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops get --user 0 com.abrenoch.wledgrabber PROJECT_MEDIA
# 操作后及后续独立读回：PROJECT_MEDIA: allow
```

恢复逐次确认的可撤销操作（**本次未执行**）：

```sh
adb -P 5037 -s 10.245.0.109:5555 shell cmd appops set --user 0 com.abrenoch.wledgrabber PROJECT_MEDIA default
```

没有 root、修改 SystemUI、关闭全局安全提示、伪造点击、缓存/重放授权 Intent 或 token；没有发出停止采集、强停进程、安装、卸载或重启命令。首次观察存在该包投射，后续观察投射为 `null`、前台为 `LayoutSettingsActivity`；这是两个时点的观测，不能据此宣称会话一直存活，也不推断状态变化原因。

## 3. 固定版本源码与调用链

下列 AOSP 官方镜像固定在 `android-10.0.0_r1`，2026-11-17 已取回正文核对：

1. [MediaProjectionPermissionActivity.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/packages/SystemUI/src/com/android/systemui/media/MediaProjectionPermissionActivity.java#L85-L90)：`hasProjectionPermission(uid, package)` 成立时直接设置成功结果并结束确认 Activity。
2. [同文件的 getMediaProjectionIntent](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/packages/SystemUI/src/com/android/systemui/media/MediaProjectionPermissionActivity.java#L192-L198)：仍调用 `createProjection(...)` 生成新的投射 token，不是重放旧授权。
3. [MediaProjectionManagerService.java](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-10.0.0_r1/services/core/java/com/android/server/media/projection/MediaProjectionManagerService.java#L257-L270)：`OP_PROJECT_MEDIA == MODE_ALLOWED` 是 `hasProjectionPermission` 的通过条件之一；同文件永久授权分支也通过 `setMode(OP_PROJECT_MEDIA, ..., MODE_ALLOWED)` 设置这一状态。

应用仍遵循 `用户启动 → createScreenCaptureIntent → 系统授权判定 → 新结果 → 前台服务 → 新 MediaProjection/单 VirtualDisplay`。设备授权只改变系统是否显示确认页，不改变应用生命周期、前台服务、安全释放或停止后的重新建会话约束。普通应用不可自行调用受 `MANAGE_MEDIA_PROJECTION` 保护的管理接口。

AOSP 基线不能代替 Sony 固件行为验收；本次没有把 Android 10 的机制外推到其他 API、其他 TV 固件或未来升级。所谓“持续允许”指可撤销的设备权限配置，**不承诺卸载重装、权限重置、恢复出厂或系统升级后永久有效**。

## 4. 尚未完成的验收

| 验收项 | 本次结果 |
|---|---|
| 当前用户/目标包识别，`default → allow`，独立读回 | 通过 |
| 应用停止后新建采集会话，不再出现敏感信息确认 | 未执行；需在用户同意的电视/灯带测试窗口验证 |
| 打开应用/返回设置不误创建第二个采集会话 | 未执行 UI 回归；不能由 AppOps 读回代替 |
| 杀进程、设备重启后权限及新会话行为保持 | 未执行 |
| 撤销后恢复系统确认，且应用正确处理拒绝 | 未执行 |
| API 21/23/26/33/34/35、其他厂商 | 未执行；保留原授权流程与验收门禁 |

## 5. 同轮审查基线（不是新增 TDD 修复）

- 从当时工作区重新编译 Android 无关生产代码及既有 sharedTest/test，以 JDK 17、JUnit 4.13.2 执行：**161 tests passed**；随后源文件摘要比对无变化。证据位于本机 `/private/tmp/wled-audit-jvm.QPVbiA/`，不是持久 CI 或 Android 设备证明。
- 标准 Gradle 单测入口因沙箱禁止写 `/Users/luowei/.gradle/.../gradle-8.9-bin.zip.lck` 未能启动；纯 JVM 替代验证不覆盖资源、Manifest、Android 编译、lint、安装或 UI。
- 工作区在审查期间有其他并行提交/改动，未覆盖或归功于本次。新增测试仍须先按用户指定的 TDD 技能确认公共测试边界；本记录不代表全面审查/代码修复已闭环。
