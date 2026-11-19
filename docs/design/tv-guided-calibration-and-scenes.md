# Android TV 遥控器引导校准与观影场景

**状态（2026-11-19）：两个 TV 入口已实现，JVM/UDP 回环已验证；Android 仪器运行、物理灯效及性能验收仍为 OPEN。** 基线 `main@0963b4e`。不新增依赖、云服务或权限，mobile 不提供场景和向导入口。下表区分已验证的软件行为与仍需设备的项目；后文保留设计约束和验收目标。[现行架构](architecture.md)。

| 路径 | 本轮证据 | 尚不证明 |
|---|---|---|
| 布局编辑器 → `GuidedCalibration` → `CalibrationSolver` / `CalibrationReview` | 274 灯的「底→右」得到 `161 / reverse`；256 种小布局、18,432 个真实映射观测全部回算一致；旧发送回执和重复按键不能代替四边确认。 | 真机遥控焦点、实际接线正确。 |
| `LayoutProbeSender` → `TEST` → UDP / HTTP；`SettingsDraftController` → `ConfigRepository` | 实际本地 UDP 数据包、重复显示不熄灭、非所有者不能停止、关闭回读与仍 live 回读分开；草稿冲突、无变化不写、失败可重试。 | UDP 抵达真实灯带，或强杀后仍能关灯。 |
| 首页 → `SceneSelectionStore` → `WledScreenService` → `LedColorSampler` → `LedStreamer` | 独立偏好只发布成功选择；暂停白帧反复切 NIGHT 不累乘、RGBW 两种白模式通道最大 64；旧 epoch/revision/generation、池拒收、停止与写者 latest-wins 有 JVM 回归。 | 完整 Android 服务时序、实际切换延时、片源观感。 |

## 1. 决策及改动前基线

校准回答“**屏幕的哪一边对应哪几颗灯**”，保存的是安装结果；场景回答“**同一画面要多柔和、多暗**”，保存的是观看偏好。换场景不能改布局，校准灯、测试图案和停止黑帧也不经过场景处理。

| 现有路径 | 本次复用与缺口 |
|---|---|
| [TV 布局编辑器](../../tv/src/main/java/com/abrenoch/wledgrabber/tv/activities/LayoutEditorActivity.java) → [草稿控制器](../../common/src/main/java/com/abrenoch/wledgrabber/common/util/SettingsDraftController.java) → [配置仓库](../../common/src/main/java/com/abrenoch/wledgrabber/common/util/ConfigRepository.java) | 已有四边计数、草稿编译、整组修改和显式保存。编辑器的 `openPreview/openTestPattern` 会先保存，向导不能用这两个入口预览未提交的候选。`save` 是完整草稿提交，不是跨编辑器的比较并交换。 |
| [LedMapping](../../common/src/main/java/com/abrenoch/wledgrabber/common/led/LedMapping.java) → [LayoutProbe](../../common/src/main/java/com/abrenoch/wledgrabber/common/led/LayoutProbe.java) → [LayoutProbeSender](../../common/src/main/java/com/abrenoch/wledgrabber/common/network/wled/LayoutProbeSender.java) | 现有 `firstPhysicalLogical`、`logicalForSide`、`buildFrame` 负责 RGB/RGBW 和黑灯掩码；sender 以 `TEST` 身份占用输出，同一 key 再 `toggle` 是关灯。`STARTED` 仅是本机受理；`stop()` 也不是硬件已关的同步回执。 |
| [TV 首页](../../tv/src/main/java/com/abrenoch/wledgrabber/tv/activities/MainActivity.java) → [采集服务](../../common/src/main/java/com/abrenoch/wledgrabber/common/WledScreenService.java) → [采样器](../../common/src/main/java/com/abrenoch/wledgrabber/common/led/LedColorSampler.java) → [单写者](../../common/src/main/java/com/abrenoch/wledgrabber/common/capture/LedStreamer.java) | 采集启动时锁定 `ConfigSnapshot`；`refreshHdrCompensation` 从基础质量重建 `quality`；`keepAlive` 重发已经着色的 `lastFrame`。只写偏好、替换一个 `volatile quality` 或对上一输出反复乘系数，都不能实现正确的热切换。 |

## 2. 方向一：遥控器引导校准

**首版承诺：** 在设备和四边灯数已确定时，不要求用户理解 `Input` 或 `Reverse`，即可找到方向并验证四边。**不承诺**仅凭总灯数推断每边数量、缺口位置、墙上真实亮度或有无坏灯。首版自动求解只开放给四边都有灯、Gap 长度为 0 的 Classic 环；非零 SKIP/BLACK、缺边等保留原手动编辑与逐边验证，不偷偷改为“无缺口”。

### A. 用户看到什么

| 步骤 | 操作及结束条件 |
|---|---|
| 入口与准备 | 首次设备配置之后或「设置 → 灯带布局 → 遥控校准」进入；已有布局不强制重校。显示连接的控制器、四边数与尾灯数；不确定四边数可先回编辑器修改。若编辑器已有未保存改动，明确让用户先保存、放弃或返回，向导不代存。CAPTURE/其他 TEST 忙时不点灯、不代停采集。 |
| 找到转角 | 所有方向**按人面对电视**。先单点物理 0，允许回答“在某条边的中间”或“没看到”。左右键移动一颗，长按用于粗定位，松开后单步精调。找到任意一对跨角的相邻灯，交替显示 `p−1` 和 `p`，让用户分别选它们在哪条边，例如“底 → 右”。允许返回、重复看；“没看到”不是一个方向答案。 |
| 核验候选 | 用候选映射依次亮上、右、下、左；用户核对**整边和两端**，而非看到一颗在该边就通过。显示原值和候选值；边界错一颗可返回微调，全边长度不对则回四边数设置。任何一步都可取消。 |
| 保存或退出 | 只有四边全部确认且用户选“保存校准”才提交。其他退出都丢弃候选，保留旧设置；返回页焦点落回校准入口。配置保存成功只代表用户确认了映射，不授予采集权限，也不自动开始采集。 |

向导在原 Activity 内走 `准备 → 定位相邻两点 → 逐边核对 → 确认保存`。`GuidedCalibration` 管理原生对话框、设备复查和本次 token；`CalibrationReview` 仅管理光标序号、已发送回执和有序四边确认，`CalibrationSolver` 不依赖 Android。重新定位清空之前的四边确认，取消不写入。

### B. 用哪个观测计算 Input / Reverse

逻辑环顺序为上、右、下、左，令 `S`、`K=|S|` 与 [架构 §4.4](architecture.md#layout)一致。选择一对连续物理索引 `(p−1,p)`（均在有效环内），两次观察必须是不同的相邻边。顺时针进入新边时，`p` 对应该边**第一个**逻辑槽；逆时针时对应**最后一个**。设该槽在 `S` 中的位置是 `j`，则：

```
顺向：reverse=false，input=floorMod(j − p, K)
逆向：reverse=true ，input=floorMod(j + p + 1, K)
```

以首套 **上86 / 右49 / 下90 / 左49** 为例：用户观测物理 25 在底、26 在右。按逆向，右边末槽为 134，得到 `input=(134+26+1)%274=161`、`reverse=true`；物理 0 映射逻辑 160，在底边中段。因此“第一颗在哪个角”只能作可选提示，不能作求解依据。正向对照：同一组边数，若物理 0 在上、1 在右，右边首槽为 86，应得 `input=85, reverse=false`。

**实现接缝：** `CalibrationSolver` 是纯 Java，只接收已验证布局和两次相邻观察，返回唯一 `input/reverse` 或结构化原因（非相邻边、超界、缺失观测、布局不支持）。不创建第二套映射：候选交给现有 `LayoutDraftCompiler` / `LedMapping` 正向回算两点和四边掩码。物理光标用 `physicalToLogical(p)` 后走现有 `LayoutProbe.buildFrame`，只允许 `0≤p<K` 且不是 BLACK/tail；不能为寻找起点临时解除掩码。首版无需对每个候选编译数千次。

### C. 所有权、退出和事务

- TV 使用同一 Activity 内的向导步骤/草稿副本；焦点切换不重新建立 sender。物理输出前复用新鲜设备探测、`StartConfirmationStore` 和 `DeviceReadiness.evaluate` 的规则（设备身份、实际灯数、通道及寻址确认），不把本地 `validateForStart` 当成设备 READY。离线仍可编辑；地址或身份变动、探测失效、配置形状变化时中止向导并重新验证。不要为了点灯创建 MediaProjection。
- 给**现有** `LayoutProbeSender` 补“按本次 token 显示/替换/停止”的幂等门面；不用另建 UDP writer。保留旧编辑器“点同项熄灭”的行为，向导重复显示同一物理灯则保持点亮，遥控长按合并到最后位置。现有 worker 每 250 ms 刷新：光标停稳后由同一 writer 回报最后一次本地发送的 `(token, cursorSeq, p)`，只有当前点对的两次序号匹配才接受观察；发送失败或旧点迟到不准把屏幕新标签当成灯带新位置。该信号仍不是 UDP 收包证明，用户可重播点对。旧页面的 `stop()` 也须按拥有者/token 隔离，晚到回调不能熄灭新会话或其它 TEST；发送线程结束前不移交租约。
- 取消、退后台、屏幕关闭、错误或无操作超时（60 秒）都请求停止，执行原黑帧 → HTTP off → 尝试读回 → 释放 TEST；区分“已退出”“已释放本地租约”和“设备确认关闭”。引导输出由同一 worker 在释放后向该 token 的 listener 返回 `onReleased(confirmedOff)`，只有限时内 off 成功且 `/json/state.on=false`、`/json/info.live=false` 均成立才标设备确认；读回失败/矛盾/超时均显示未确认，不阻塞用户退出，不自动点亮重试。进程被杀无法保证清灯；页面重建不恢复发光，必须显式重新验证和点亮。
- 保存仅允许 `LED_INPUT_POSITION` 与 `LED_REVERSE` 两字段变更。向导在草稿副本写入候选，经 `LayoutDraftCompiler.compileForPreview` 复用布局验证与编译，保存时再验证完整配置。持久层提供**同一进程所有配置写入者共用**的串行提交区：重新读实际 revision、和进入向导的基线比较，在最新草稿合并两字段并一次提交。比较与写入不能分两把锁；常规完整草稿保存也要带读入时的 expectedRevision（包括启动设置的写入），以免旧编辑器稍后反向覆盖刚保存的校准。冲突不覆盖别人的更改，写失败保留旧 revision 并提示重试。保存时先封闭本轮探针输入并请求停止；结果页分别显示配置提交结果与设备关灯结果，关灯未确认不等于配置未保存。若最终值未变，不空增 revision。取消与逐边预览写入数均为 0。

## 3. 方向二：观影场景一键切换

**首版入口：** TV 首页增加「场景：当前名称」，可用方向键选中、OK 应用；返回恢复焦点，不挤掉电源/设置或改变“关于”的顺序。不依赖全局快捷键，不承诺覆盖第三方播放器：打开 Grabber 是否暂停播放取决于播放器，首版也可在播放前选择。`common` 提供能力，手机端继续用原体验。

### A. 参数及优先级

| 场景 ID | 饱和度 % | 时间平滑 %（旧样本权重） | 最终通道增益 % | 目的 |
|---|---:|---:|---:|---|
| `CUSTOM` 原设置，升级默认 | 取会话启动快照 | 取会话启动快照 | 100 | 升级前后逐字节一致；可一键回到手调效果 |
| `CINEMA` 电影柔和 | 95 | 40 | 85 | 减轻镜头切换时的颜色跳变 |
| `GAME` 游戏跟手 | 100 | 0 | 100 | 不引入额外时间平滑，不宣称降低系统/网络延迟或提高 FPS |
| `NIGHT` 深夜低亮 | 85 | 55 | 25 | 即使画面变白，也限制本机发送的通道幅值 |

数值是**首轮试看片候选**，不是视觉标定结果。固定场景只覆盖表中三项；黑边裁剪、线性平均、平均色、帧率和尺寸继承**当前会话**，HDR 继续由显示状态决定。不动主配置、控制器亮度 `bri`、WLED preset、gamma、电源、协议、RGBW white mode 或布局；不识别视频内容。手动编辑质量参数仍按原契约“下次启动生效”，固定场景不覆盖用户偏好；切回 `CUSTOM` 恢复运行会话的原值而非未生效的草稿。

### B. 同时适用于动态图和暂停画面的通路

```
Image → 现有采样域（裁剪/线性平均）→ 本代逻辑 RGB 均值〔会话内缓存〕
      → HDR 补偿 → 有效饱和度 → 现有映射/黑灯掩码与 RGB(W) 合成
      → 现有逐通道平滑 → 场景通道增益 → lastFrame → 原三帧池 / LedStreamer
```

- `LedColorSampler.samplePhysical` 已将“取均值”和“将均值渲染成物理字节”拆成可复用的两步，保留现有入口与快路径。每个逻辑槽最多一个打包 RGB 整数（`4×N` 字节，可复用 `logicalColors` 作**原始均值**，不原地覆盖）；平均色模式只存一个均值，不逐灯重复做 HDR/饱和度。只保留当代均值，不留 `Image`、屏幕像素或截图，不持久化、不上传。热身后不逐帧创建对象或数组。普通逐边模式按逻辑槽仅做一次颜色处理，按掩码写到对应物理槽。
- 场景切换时仅重置平滑历史，不清空黑边检测历史；现有 `resetQualityState()` 同时清两者，需要拆出只重置平滑的能力。用当前有效均值**立即重渲染一次**，不是拿上一帧乘亮度。比如静止白屏从 `GAME` 切 `NIGHT`，用 `(unsignedChannel*gain+50)/100` 计算后，RGB/RGBW 每个输出通道最多 64；再次按深夜不会继续变暗。100% 逐字节恒等，BLACK 与尾灯始终全通道 0。25% 是**通道增益**，不是物理照度或功耗承诺。
- `lastFrame` 是**已渲染**的心跳缓存；切换后原子换成新结果，心跳只发送新缓存，不再次应用增益或推进平滑。旋转/布局代次失效时废弃旧均值和可重发标志；无有效均值只记录选择、显示“等待首帧”，不发旧帧、不造黑帧开灯。正常停止黑帧走原停止路径，绝不经过场景渲染。

### C. 偏好、运行状态与时序

1. **一个持久选择，独立于配置快照。** `SceneSelectionStore`（独立私有偏好，串行写入 `{schema,id,selectionRevision}`，不进入 `ConfigFields.ALL`）；缺键/未知 ID 回 `CUSTOM`。相同 ID 幂等，写失败保持最后成功选择，不向服务发布；以 store 的成功提交快照为准，UI 和服务不直接读取可能已变的 SharedPreferences 内存值。磁盘写不占用 UI 或采集线程。固定预设参数定义在 `common` 的不可变表，UI 不传任意参数。未运行时只保存，“下次采集使用”；不以切场景为由 `startService`、申请授权、点灯或恢复待机会话。
2. **只向活动会话发布。** 同进程 store 向活动服务订阅者通知 `selectionRevision`；启动时订阅并取最新成功快照，按 revision 去重，避免漏掉启动交界处的选择。命令绑定本次 CAPTURE 租约单调递增的 `outputEpoch`（下文称 `sessionEpoch`，不拿毫秒时钟当唯一 ID），在 `captureHandler` 串行处理，拒绝旧 epoch/旧 revision，快速连按以最新选择为准。停止优先不是 Handler 排队优先：屏灭、授权撤销、健康检查等入口先在服务 `lock` 下关闭本 epoch 的颜色准入，再走现有 stop 屏障。耗时重渲染不持锁，最终 `offer` 和更新本地回执前在同一锁下复验 `running && !stopping`、epoch、generation 及当前选择。停止使旧回执失效，首页按活动 epoch 和单调状态序号丢弃晚到通知；不锁住网络 I/O。新会话自行读取持久选择，不消费上一会话命令。HDR 事件同样在本采集域中合成 `baseQuality + chosenScene + hdrState`，不让 `refreshHdrCompensation` 后到的基础设置覆盖场景。显示变化和旋转须区分：尺寸变化使缓存失效；纯 HDR 改变可用**补偿前**均值重渲染。
3. **回执分层。** 首页显示“已选择（未运行）”“已选择，等待画面”“已用于本机会话”；最后一项只在匹配 `epoch/revision` 的新渲染帧被既有 `LedStreamer.offer` 接纳后发布。池满保持待应用、按当前版本重试，旧回执不得覆盖更新选择。若连续选择，在 `LedStreamer.offer` 的队列锁内，新帧替换所有旧 `pending`（队列容量 1）；`drain` 在同一锁内把 `pending` 移到 `inFlight`。以**取出并标记 inFlight**为界，最多一帧旧色获准发完，即使其网络 I/O 晚于新版本本地回执才开始。新版本接纳后不再入队旧帧；由同一串行 writer 保证新颜色首次实际发送后不会在本机再发送旧颜色，不承诺撤回或控制网络乱序。保证由单 capture 域、入队前校验、latest-wins 队列及同版心跳共同实现，不重建 `VirtualDisplay`、不重置 transport、不因切换额外发 HTTP `on/off`。UI 不宣称 UDP 单帧收妥或物理灯光已改变。
4. **升级和回退。** 旧安装无迁移写入、初始 `CUSTOM`。TV 与 mobile 可替换安装且 applicationId 相同，仅 TV 包启用场景能力，mobile 包固定走原路径，即使偏好目录中残留 TV 场景也不启用。先交付离线选场景/下次启动，再开放运行中切换；热切有回归时，退回“只存下次启动”，不能退化为静默停止并重启采集。不要同时保留两个“当前场景”持久字段；运行回执是易失的，并非配置来源。

## 4. 实施顺序及验收

以下为原定验收清单，不是通过记录。已执行范围以上方证据表为准。两个功能可分别评审和发布，日常场景不依赖用户用向导重做已有布局。

| 任务 | 交付边界 | 最小验收（拟） |
|---|---|---|
| CAL-1 纯映射 | 求解、拒绝原因、候选正向验证 | `LedMappingTest`/`LayoutProbeTest` 扩展：上文两个固定例；小环遍历四边界 × 双方向 × 所有可观察偏移，回算与现有 `LedMapping` 相同。零灯边、跨非相邻边、没看到、非零 gap、尾灯、安全索引均不产生可保存候选。 |
| CAL-2 向导与安全保存 | 原编辑器里的状态机、按 token 的物理光标、原子提交 | JVM/回环：BUSY 不抢占、重复同点不熄灯、旧 token 不能 stop 新会话、丢包不伪报；取消/进后台写入 0 次；成功仅两键一次提交；模拟陈旧 revision 和写失败，原配置完好。`TvLayoutEditorTest` 补遥控焦点、Back、长按、熄屏与重建不自动亮灯。 |
| SCN-1 离线选择与采样 | 场景表、独立偏好、可重渲染的均值；未运行时不唤醒 | `LedColorSamplerTest`、`AverageColorTest`、`EndToEndWledPipelineTest`：CUSTOM 与旧 golden 字节一致；静态白的 NIGHT 最大 64、黑为 0；RGBW 两种白模式、逐灯/平均色、BLACK/tail、HDR 开关；100 次重复切换不漂移。存储失败/未知 ID、主配置 revision 和布局不动。 |
| SCN-2 热切与竞态 | 单一更新域、缓存失效、同版心跳及本地回执 | fake clock + 受控 writer：无新 Image 也重渲染；首帧前不发色；快速交错切换/心跳/旋转/HDR/息屏/授权撤销；分别卡住旧 pending/已取出的 inFlight，以及“渲染后未 offer”“offer 后未广播”两个窗口，插入 stop/新会话，断言旧帧上界和旧成功回执不复活。`TvMainActivityTest` 验证焦点与回执。场景切换新增授权请求、虚拟显示创建、额外开关灯请求均为 **0**；stop 后不得因旧回调发色。 |
| FAT 两条独立实机门槛 | Sony TV + 274 RGB；另列 RGBW 样机 | 校准：从错误方向和中段起点开始，仅用遥控完成两次，逐边肉眼全对；取消后原映射不变。场景：同一动态片段及暂停画面各往返切换 10 次，记录选择→首个新版本本地入队 p95（初始目标 ≤150 ms，限已有有效均值、无阻塞网络），肉眼确认变化另记。按[既有 30 分钟采样/发送预算](architecture.md#validation)做 A/B，测统一内容与设备；LOCAL、UDP 回环、真实光效、RGBW、物理时延分开验收。 |

### 如何复验

```sh
./gradlew :common:testDebugUnitTest :tv:testDebugUnitTest :mobile:testDebugUnitTest
./gradlew :tv:assembleDebug :mobile:assembleDebug :tv:assembleDebugAndroidTest :common:assembleDebugAndroidTest
./gradlew :common:lintDebug :tv:lintDebug :mobile:lintDebug
python3 scripts/qa/check-doc-links.py
```

2026-11-19 本地结果：`common` 254、`tv` 1、`mobile` 1 个 JVM 测试通过（0 失败/跳过）；三个模块 Android Lint 均为 0 错误，仍有警告。

TV 仪器用例在 `TvMainActivityTest`（离线选择、旧状态回执、返回）和 `TvLayoutEditorTest`（打开校准、返回、保留 TEST 租约为空）。本次无可用 Android 设备，**仅编译这些用例**；息屏、授权撤销、旋转与快速切换的整机交错、完整四边 D-pad 操作、30 分钟 A/B、p95 和 RGBW 实物均未验收。未发布 APK。

### 本轮设计核对（不是功能验收）

在 `0963b4e`，用临时 Java 驱动直接调用现有 `LedMapping.compile`：每边 1–4 灯的 256 种组合、所有偏移与两个方向，共 18,432 个可观察的跨边相邻对，反解全部还原原参数；同时检查额外两颗尾灯保持黑，以及首套 274 的物理 0/25/26 锚点。另用独立旋转/反转模型复核这些相邻对和 8 位通道增益的恒等/上界。此核对不含新向导、渲染实现、实机、时延或亮度测量。
