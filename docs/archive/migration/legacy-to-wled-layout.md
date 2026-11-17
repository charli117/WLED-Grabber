# 旧 Hyperion 配置迁移到 WLED 直连

> 文档状态：2026-11-16 交付；实现状态：核心模块本地完成，REL-1 已在本地切换到 WLED 并删除旧后端（待真机/QA 验证），未签名、未安装、未发布；硬件验收未完成。
> 参数、协议与验收的唯一规范：[WLED 直连流光溢彩技术方案](../design/wled-direct-ambilight-feasibility-2026-11-16.md)；任务状态见[任务总纲](../task/00-overview.md)。

## 1. 迁移原则

- 不后台读取旧应用偏好，不把旧 Host/Port 自动当作 WLED 配置。
- 旧字段只做“映射 / 不迁移”解释；旧 X/Y 只能说明旧采集缩放，**不能推导四边灯数**。
- 新配置由用户在界面重建；保存后下一次启动生效，运行中的会话不被热改。
- 新应用可以保持旧 Hyperion 应用共存，但同一时间只能有一个发送者控制灯带。

## 2. 旧字段映射

| 旧字段/能力 | 新版本处理 |
|---|---|
| 服务器 Host / Port | **不迁移**。重新输入或发现 WLED 的 host、HTTP 端口、UDP realtime 端口；DDP 固定 4048，不能复用旧 19445 |
| Priority | **不迁移**。WLED 首版不使用 Hyperion priority；删除该字段 |
| Reconnect / Reconnect Delay | **不迁移**。使用受限健康观察和显式会话恢复，不模拟旧 TCP 重连 |
| X LED Count / Y LED Count | **不迁移为灯数**。只影响旧应用采集缩放；按实际 Top/Bottom/Left/Right 重建 |
| Frame Rate | 保留能力；新配置独立保存，首套验收需显式选择 60，不能用默认 30 代替门禁 |
| Average Color | 保留能力；平均模式仍执行 Gap/尾黑，不再隐式检测黑边 |
| Grab on Boot | 改为**受控自启动**：仅 API 29 且录屏/后台/电池授权与配置齐全时经 BootActivity 申请授权，其余情况为开机提醒；不自动复用旧录屏令牌 |
| 旧服务器布局 | 用四边数量、Gap、Input、Reverse、Depth、Overlap、Edge Gap、四角重建 |
| 实际接线 | 用物理索引/侧别预览和三种测试图案确认，不凭旧 X/Y 或起点直觉推断 |

## 3. 首套 274 参数

来源：[技术方案 §4.5](../design/wled-direct-ambilight-feasibility-2026-11-16.md#layout)。

- Top=86、Bottom=90、Left=49、Right=49、Gap=0、Input=161、Reverse=true
- H Depth=8%、V Depth=5%、Overlap=0、Edge Gap=0
- 四角默认：TL(0,0)、TR(100,0)、BR(100,100)、BL(0,100)
- 物理映射：`slot(p)=floorMod(160-p,274)`；物理 0/25/26/160/161/273 → 逻辑 160/135/134/0/273/161
- 物理数量必须与 WLED 观察 count 一致；不足时阻断启动，不截断尾灯。

## 4. RGBW 前提

- RGBW 首版使用 **DDP**；DNRGB 不得承载 RGBW，也不使用 type5。
- WLED 的 auto-white 必须为 **Manual**，全局 override 不得改回自动；否则 `(0,0,0,255)` 可能被重算成全黑。
- 选择 `min_subtract` 或 `preserve` 后，用白色图案确认；没有 RGBW 样机时不得声称已验证。
- 首套 WS2815 是 RGB，不能当作 RGBW 验收。

## 5. 排障

### 5.1 发现/连接失败

1. 确认手机/TV 与 WLED 在同一可信 LAN；可手动输入 host/IP。
2. 检查 HTTP 端口（默认 80）、UDP realtime 端口（默认 21324）与 DDP 4048 是否独立配置。
3. 若探测超时/拒绝/响应体超限，界面显示明确原因；不会把 UDP 写出当成设备可达。
4. 确认 WLED realtime offset=0、DMX 起始地址=1、main-segment-only 关闭，且没有额外 LED map 或总线反向；否则逐灯寻址会错位。
5. 设备身份（MAC/版本/count）变化后需重新确认，不沿用旧 IP 的缓存能力。

### 5.2 关灯未确认

- 停止流程：停止新帧 → 清 pending → 等 inFlight → 同一 writer 发黑 → HTTP `on:false/live:false/tt:0` → 读回。
- 只有读回 `on=false` 且 `live=false` 才显示 `RELEASE_CONFIRMED`；超时/断网/身份变化显示 `RELEASE_UNCONFIRMED`。
- 进程被杀时不保证释放；设备有限 realtime timeout 可能恢复原效果。迟到 UDP、其他控制器或 WLED Sync 仍可能重新点亮。
- 不要在未确认时自动把 off 请求重放到下一个会话。

### 5.3 RGBW 变黑

1. 确认 WLED auto-white=Manual 且 override 未被改回自动。
2. 确认使用 DDP，而非 DNRGB/type5。
3. 用白色测试图案检查 W 通道；仍异常时记录固件版本、灯带类型与 WLED 设置，按未验证处理。

### 5.4 通知/授权

- API33+ 通知权限被拒时不崩溃，但前台通知不可见；下次前台提示。
- 投屏授权被撤销/会话失效后显示 `NEEDS_CONSENT`，不自动重启旧授权。
- 开机默认只发用户可见提醒；仅 API 29 且录屏/后台/电池授权与配置齐全时经 `BootActivity` 申请授权，其余情况不后台强拉 Activity。
- 受控自启的 ADB AppOps、电池白名单与调用链见[Android 10 后台/自启动记录](../verification/wled/cap-1/android10-background-autostart.md)。

## 6. 隐私与安全边界

- 应用只发送逐灯颜色，不保存/上传屏幕图像；不申请麦克风/音频。
- LAN HTTP 为明文且无认证；可信局域网内的其他控制器仍可能控制 WLED。
- 不承诺 DRM/受保护内容可采集；FLAG_SECURE 内容不被绕过。
- 签名、真机安装、侧载与发布需单独授权；本文档不构成发布完成声明。

## 7. 支持矩阵（截至 2026-11-17）

| 项目 | 状态 |
|---|---|
| Android API21–35 | API 21/26/29/33/34 已覆盖仪器测试；API 35 部分覆盖；API 23 与完整设备矩阵未验证 |
| WLED 固件 | v0.15.1 源码基线；QA-2 真机为 WLED 16.0.1；其他版本按显式兼容试验 |
| RGB / RGBW | RGB 已在 274 真机验证；RGBW 代码路径已实现，实机未验收 |
| 274 灯 | 首套目标；软件侧采样/发送门禁达标；物理 60fps/端到端时延未验收 |
| 288 参考布局 | 数学/fixture 覆盖；非首套硬件 |
| 500/1000/2000 灯 | 未验证 |
| ESP8266 | 保留入口/警告，未继承 ESP32 性能结论 |
| Sony KD55XD8005 | 目标设备；采集与停止读回已验证；遥控器/大字体/物理 60fps 待 QA-2 |

## 8. APK、侧载与回退

- 2026-11-16 REL-1 本地切换后 applicationId 为 `com.abrenoch.wledgrabber`；旧应用 `com.abrenoch.hyperiongrabber` 可共装，但必须先停止旧发送者。
- mobile/tv 同 ID，输出的是**替代 APK**，彼此不能共装；旧/新应用是两个不同身份。
- 当前 versionCode：mobile 1012、tv 2012；versionName 1.0。发布版本号与签名未确定；不得把 Debug/unsigned Release 构建当发布产物。
- 侧载步骤：保留旧 APK/分支 → 停止旧发送 → 安装新 APK → 重建 WLED 配置 → 预览/测试图案确认接线。
- 回退：停止新发送 → 恢复旧 APK/分支；新旧 ID 可共存，但同一时间只能有一个发送者；旧配置不会自动迁移。

## 9. 未验证与禁止表述

- 未验证：Sony 物理 60fps 与端到端时延、RGBW 样机、500/1000/2000 灯配置、完整 API 矩阵、签名/发布。WLED 实机报文与停止读回已在 QA-2 目标设备验证（`RELEASE_CONFIRMED`）；其他固件/硬件组合仍未验证。
- 禁止表述：“已全部完成”“保证关灯”“支持所有 WLED 版本”“RGBW 已验证”“60fps 已达标”。
- 实现/证据状态以[任务总纲进度矩阵](../task/00-overview.md)为准。
