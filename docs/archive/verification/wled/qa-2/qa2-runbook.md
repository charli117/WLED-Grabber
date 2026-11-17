# QA-2 执行手册（硬件就绪后按此跑）

> 当前状态：软件侧门禁已实测达标；物理 FPS、端到端时延、RGBW 与多灯数配置待硬件。
> 本手册只在硬件到位后执行，任何一项缺失都保持 BLOCKED，不写“基本通过”。

## 0. 前置

- Sony KD-55XD8005 已开启网络调试并 `adb connect <TV-IP>:5555`。
- WLED 固件、灯数与总线记录在案（当前真实灯带 `10.245.0.99`，274 RGB，WLED 16.0.1）。
- 物理测量设备就位：光感/光电二极管 + 示波器，或可复核的高速摄影（≥240fps），用于物理 FPS 与端到端时延。

## 1. 软件侧指标（每次配置变更都要复测）

```bash
# 1) 部署并启动采集（需在电视上按电源键并接受录屏授权）
adb -s <TV-IP>:5555 install -r -g dist/wled-grabber-tv-1.0-2012-debug.apk
adb -s <TV-IP>:5555 shell monkey -p com.abrenoch.wledgrabber \
    -c android.intent.category.LEANBACK_LAUNCHER 1
# 按电源键 + 立即开始

# 2) 采样 30 秒，写证据到 docs/verification/wled/qa-2/
scripts/qa/measure-qa2.sh <TV-IP>:5555 <WLED-IP> 30
```

判定（与 `docs/task/11-validation.md` 一致）：

| 指标 | 门槛 | 来源 |
|---|---|---|
| 采样 p95 / p99 | ≤5ms / ≤8ms | `qa2 sampling_us` 行 |
| 发送间隔 p95 | ≤20ms | `qa2 interval_us` 行 |
| 物理不同帧平均 / 1 秒窗口 | ≥59fps / ≥55fps | **光感或高速相机**,不能用 `info.leds.fps` |
| 端到端 p95 | ≤80ms | **光感/示波器或高速相机** |

## 2. 物理测量步骤（唯一能关闭 QA-2 的部分）

1. 屏幕显示固定 60Hz 测试内容（棋盘或行走像素），保证每秒 ≥60 次不同帧。
2. 光感对准灯带首/末灯，示波器记录两次颜色切换的时间差；或高速相机同时拍屏幕与灯带。
3. 记录 30 分钟：物理 FPS 的平均与 1 秒窗口最小值、端到端时延分布（p50/p95/p99）。
4. 原始波形/视频 + 统计方法一并存入 `docs/verification/wled/qa-2/`。

## 3. RGBW（需 RGBW 样机）

1. WLED 的 auto-white 设为 Manual、全局 override 未改回自动。
2. 分别验证 `min_subtract` 与 `preserve`：255 白、纯 RGB、灰阶、低亮度、1/360/361 灯边界。
3. 记录末灯与白通道是否可见；无样机则保持未验收。

## 4. 多灯数配置（需 500/1000/2000 灯带）

每种灯数分别执行第 1-2 节，并单独发布支持矩阵，不把 274 的结论外推。

## 5. 故障与稳定性

```bash
# 32 分钟 soak（已在 2026-11-17 执行过一次）
adb -s <TV-IP>:5555 shell "dumpsys meminfo com.abrenoch.wledgrabber" > /tmp/qa2-mem-start.txt
sleep 1920
adb -s <TV-IP>:5555 shell "dumpsys meminfo com.abrenoch.wledgrabber" > /tmp/qa2-mem-end.txt
```

- 断网/HTTP 超时/授权撤销/进程被杀：分别执行并核对 ownership 计数归零、状态不误报。
- 门槛：ANR/崩溃 0；稳定工作集净增 ≤5MiB 且无持续增长。

## 6. 完成后

1. 把证据文件加入 `docs/verification/wled/qa-2/`。
2. 更新 `docs/task/00-overview.md` §5 的 QA-2 行与本文件状态。
3. 只有全部门禁都有原始证据时，QA-2 才标“已验收”；随后才能进入 QA-3。
