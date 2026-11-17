# 文档归档索引

归档只接收已彻底解决、确认过期/失效，或已被现状态文档取代的历史材料；归档文件保留归档时点内容，不在归档内继续修改业务结论。移动导致的相对链接按归档位置调整，跨文件引用统一指向归档内的历史快照。

> 归档基准：除特别注明外，归档副本为移动时的工作区内容，包含移动前尚未提交的文档更新，不保证与某个提交逐字一致；移动过程只调整相对链接，不改写业务结论。

本轮启动审查的最终证据：[2026-11-17 启动设置与修复验证快照](verification/wled/startup/final-verification-2026-11-17.md)。该快照补齐归档过程中仍在执行的测试，不修改此前中间记录。

> 2026-11-17 交付复核：任务册 [00-overview.md](task/00-overview.md) 继续作为任务状态台账，逐卡复核结论与新增证据见其 §5.2。复核后 **CFG-2、NET-3、CAP-2 为开发中，TV-1 为待验证**（生产接线缺口，不满足归档条件）；REL-1 升级为已验收。**本轮不新增归档**：未闭环的卡、证据与仍有效的产品文档均不满足“已彻底解决或确认过期/失效”的条件。本轮原始日志新增于 [verification/wled/qa-1/2026-11-17-delivery-audit/](verification/wled/qa-1/2026-11-17-delivery-audit/)。任务册是“归档但继续更新状态”的具名例外；其余归档文件仍保持归档时点内容。

当前有效文档：

- 技术规范：[docs/design/architecture.md](../design/architecture.md)
- 配置与排障：[docs/guides/configuration-and-troubleshooting.md](../guides/configuration-and-troubleshooting.md)
- 品牌与图标：[docs/design/app-icon/brand-guide.md](../design/app-icon/brand-guide.md)

| 归档文件 | 原路径 | 归档日期 | 原因 | 依据 |
|---|---|---|---|---|
| [wled-direct-ambilight-feasibility-2026-11-16.md](design/wled-direct-ambilight-feasibility-2026-11-16.md) | `docs/design/wled-direct-ambilight-feasibility.md`（784 行） | 2026-11-17 | 技术方案已重写为现状态架构规范并更名 `docs/design/architecture.md`；本快照保留重写前的定稿版本（HEAD 提交内容），供归档文档中的 E/A/S 锚点与历史引用追溯 | 技术方案定稿时点内容；重写后的现状态规范见 `docs/design/architecture.md` |
| [wled-direct-ambilight-architecture-review-premerge-2026-11-16.md](design/wled-direct-ambilight-architecture-review-premerge-2026-11-16.md) | `docs/design/wled-direct-ambilight-architecture-review.md`（合并前全文，291 行） | 2026-11-16 | 架构复核结论已实质合入技术方案，其“冲突时以本文为准”的独立规范身份失效；保留合并前全文作历史证据 | 技术方案头注；合并前文件 291 行；归档副本仅调整跨文件相对链接 |
| [wled-direct-ambilight-architecture-review.md](design/wled-direct-ambilight-architecture-review.md) | `docs/design/wled-direct-ambilight-architecture-review.md`（合并说明，9 行） | 2026-11-16 | 合并说明由归档索引取代；归档保留合并时点的入口说明 | 复核文首合并说明；正文未改，仅调整相对链接 |
| [legacy-hyperion-changelog.md](legacy-hyperion-changelog.md) | `CHANGELOG.md`（仓库根目录，64 行） | 2026-11-17 | 仅记录上游旧应用的发布历史，与当前 WLED Grabber 身份及发布状态无关 | 当前应用已不包含该后端；原文件内容未改 |
| [task/00-overview.md](task/00-overview.md) 等 12 个任务册文件 | `docs/task/` | 2026-11-17 | 开发任务分解、DAG、状态矩阵与 RED→GREEN 执行说明属于开发过程材料；产品文档只描述最终实现 | 现状态规范见 `docs/design/architecture.md`；设备与回归证据见同目录 `verification/` |
| [migration/legacy-to-wled-layout.md](migration/legacy-to-wled-layout.md) | `docs/migration/legacy-to-wled-layout.md` | 2026-11-17 | 旧配置逐字段映射、侧载与回退属于上一版本的迁移说明；当前产品不读取旧配置，也不提供旧应用回退路径 | 排障与当前参数已重写进 `docs/guides/configuration-and-troubleshooting.md` |
| [fixes/2026-11-17-completion-audit.md](fixes/2026-11-17-completion-audit.md) | `docs/fixes/2026-11-17-completion-audit.md` | 2026-11-17 | 缺陷初审、RED/GREEN 与修复提交记录属于一次性过程材料；修复结论已进入当前实现 | 修复后行为由现有测试与 `docs/design/architecture.md` 描述 |
| [verification/wled/](verification/wled/) | `docs/verification/wled/` | 2026-11-17 | 构建日志、仪器测试记录、设备授权记录与真机测量属于验收过程证据；产品文档只保留结论与支持矩阵 | 当前状态与阈值见 `docs/design/architecture.md` §11 与 `docs/guides/configuration-and-troubleshooting.md` §7 |

归档副本保留归档时点内容；需要恢复某份历史材料为活动文档时，先提供仍适用的依据，再显式移出归档并更新索引。
