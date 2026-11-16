# QA-2 硬件验收记录

> 日期：2026-11-17。状态：**BLOCKED（部分门禁已实测，物理测量与RGBW仍缺）**；不以 `info.leds.fps` 代替物理验收。

## 当前可用硬件

- Sony KD-55XD8005（BRAVIA 4K UR3，Android 10 / API 29，`10.245.0.109:5555`）与遥控器。
- WLED 真实灯带：`10.245.0.99`（WLED_TV，16.0.1，274 颗 RGB）。
- 24 颗测试板：`10.245.0.138`。
- API 34 Android TV AVD（`wled-tv-api34`）。

## 已实测（2026-11-17，640长边/60fps/274灯）

- 采样 p50≈3.3ms、**p95≈4.05–4.5ms**、**p99≈4.5–4.9ms**（≤5/≤8 达标）。
- **发送间隔 p95≈17.3ms**（≤20 达标）。
- WLED `leds.fps` 30次1秒采样 min=66 / avg=107.4（仅设备刷新计数，**不作为物理门禁证据**）。
- 自动开灯、静态画面1秒心跳保持 `live=true`、停止后 `on=false/live=false` 并经 HTTP 读回 CONFIRMED。
- 原始日志：`docs/verification/wled/qa-2/device-metrics-2026-11-17.txt`。

## 仍缺失/未执行

- 物理不同帧 FPS（≥59 平均、≥55/1秒窗口）与端到端 p95≤80ms：需光感/高速相机或等效测量设备。
- RGBW 独立样机两模式（min_subtract/preserve）与 WLED auto-white Manual 核验。
- 500/1000/2000 灯与 ESP8266 配置。
- Sony 遥控器大字体与 D-pad 全流程实测（TV-2 仪器已覆盖部分）。
- 30 分钟预热+观察的稳定性与内存净增：本轮 soak 结果见 `docs/verification/wled/qa-2/soak-2026-11-17.txt`（完成后填入）；未完成前不计入通过。
- 故障注入：多包丢首/末、乱序、延迟、断 HTTP/UDP、WLED 重启、进程 kill、息屏/亮屏。

## 量化门槛（不变）

- 采样 p95 ≤ 5ms、p99 ≤ 8ms；发送间隔 p95 ≤ 20ms。
- 物理不同帧平均 ≥ 59fps，1 秒窗口 ≥ 55fps；端到端 p95 ≤ 80ms。
- 30 分钟 ANR/崩溃 0；稳定工作集净增 ≤ 5MiB 且无持续增长；池 ≤ 3、pending ≤ 1。
- 任一硬件/测量缺失或不符合，保持 BLOCKED，不写“基本通过”。

## 结论

软件侧采样/发送时延门禁已在真机达标；物理 FPS、端到端时延与 RGBW 因测量设备/样机缺失仍 BLOCKED。QA-2 不标 PASS。
