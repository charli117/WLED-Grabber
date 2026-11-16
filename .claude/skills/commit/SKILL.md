---
argument-hint: [--no-verify] [--style=simple|full] [--type=feat|fix|docs|style|refactor|perf|test|chore|ci|build|revert]
description: 创建格式规范的 Conventional Commits 提交；标题与正文默认中文
---

# Claude Command: Commit

创建符合 Conventional Commits 规范的提交。**标题描述、正文、脚注说明一律使用中文（简体中文）**，仅 `type(scope)` 等机器标识保留英文——详见[语言规则](#语言规则中文优先)。

## 用法

基本用法：
```
/commit
```

带选项：
```
/commit --no-verify
/commit --style=full
/commit --style=full --type=feat
```

## 命令选项

- `--no-verify`：跳过 pre-commit 检查（lint、build、generate:docs）
- `--style=simple|full`：
  - `simple`（默认）：简洁单行提交信息
  - `full`：包含正文与脚注的详细说明
- `--type=<type>`：指定提交类型（覆盖自动检测结果）

## 执行步骤

1. **Pre-commit 检查**（除非 `--no-verify`）：
   - `pnpm lint` — 确保代码质量
   - `pnpm build` — 验证构建通过
   - `pnpm generate:docs` — 更新文档

2. **文件暂存**：
   - 用 `git status` 检查已暂存文件
   - 若无已暂存文件，自动 `git add` 所有修改/新增文件

3. **变更分析**：
   - 通过 `git diff` 理解变更内容
   - 检测是否存在多个逻辑变更需要拆分
   - 在合适时建议原子提交

4. **生成提交信息**：
   - 遵循 Conventional Commits 规范
   - 添加对应 emoji 前缀
   - full 模式下生成详细正文/脚注
   - **所有人类可读叙述使用中文**（见下文）

## 语言规则（中文优先）

生成提交信息时严格遵守：

- **标题行**：`<emoji> <type>[scope]:` 保留英文（Conventional Commits 规范），**冒号后的描述必须用中文**，使用祈使语气（「添加」「修复」「更新」，而非「已添加」）。
- **正文（full 模式）**：全部使用中文；引用日志、错误信息、API 名称等必须保留英文原文时例外。
- **脚注**：Issue 引用（`Closes #123`）、hash、路径等机器标识保留原样；解释性文字使用中文；`BREAKING CHANGE:` 关键词保留英文（工具链兼容），冒号后说明用中文。
- **禁止**在有清晰中文表达的情况下默认使用英文。

### 反模式（必须避免）

- 全英文标题如 `feat: add login` → 应写 `feat: 添加登录流程`
- 无必要地中英混用（如「添加 JWT token validation」）→ 应写「添加 JWT 校验」，除非该术语在仓库中普遍以英文使用

## Conventional Commits 格式

### 简洁风格（默认）
```
<emoji> <type>[可选 scope]: <中文描述>
```
示例：`✨ feat(auth): 添加 JWT 令牌校验`

### 完整风格
```
<emoji> <type>[可选 scope]: <中文描述>

<中文正文>

<中文脚注>
```

示例：
```
✨ feat(auth): 添加 JWT 令牌校验

实现 JWT 校验中间件，负责：
- 校验签名与过期时间
- 从载荷提取用户声明
- 将用户上下文写入请求对象
- 处理刷新令牌轮换

通过要求受保护路由均完成鉴权，提升整体安全性。

BREAKING CHANGE: 所有需鉴权的接口现要求 Bearer 令牌
Closes: #123
```

## 提交类型与 Emoji

| 类型 | Emoji | 含义 | 使用场景 |
|------|-------|------|----------|
| `feat` | ✨ | 新功能 | 新增功能 |
| `fix` | 🐛 | Bug 修复 | 修复问题 |
| `docs` | 📝 | 文档 | 仅文档变更 |
| `style` | 🎨 | 代码风格 | 格式调整、补分号等 |
| `refactor` | ♻️ | 重构 | 非修复也非新增功能 |
| `perf` | ⚡️ | 性能 | 性能优化 |
| `test` | ✅ | 测试 | 补充测试 |
| `chore` | 🔧 | 维护 | 构建流程或工具变更 |
| `ci` | 👷 | CI/CD | CI 配置变更 |
| `build` | 📦 | 构建 | 构建系统变更 |
| `revert` | ⏪ | 回滚 | 撤回之前的提交 |

## 正文编写指南（完整风格）

正文应当：
- 说明**做了什么**和**为什么**（而非怎么做）
- 多项变更使用列表
- 包含变更动机
- 对比前后行为差异
- 引用相关 issue 或决策
- 每行不超过 72 字符

正文示例：
```
此前用户资料接口允许未认证访问，存在安全风险。

本提交增加鉴权中间件：
- 在受保护路由上校验 JWT
- 实现刷新令牌逻辑
- 增加限流以降低暴力破解风险
- 记录鉴权失败便于监控

实现遵循 OAuth 2.0 常见实践，整体提升应用安全。
```

## 脚注编写指南（完整风格）

脚注包含：
- **破坏性变更**：以 `BREAKING CHANGE:` 开头
- **Issue 引用**：`Closes:`、`Fixes:`、`Refs:`
- **共同作者**：`Co-authored-by: name <email>`
- **评审引用**：`Reviewed-by:`、`Approved-by:`

脚注示例：
```
BREAKING CHANGE: 将 config.auth 重命名为 config.authentication
Closes: #123, #124
Co-authored-by: Jane Doe <jane@example.com>
```

## Scope 编写指南

Scope 应当：
- 用名词描述代码库的某个区域
- 在项目中保持一致
- 简短且有意义

常用 scope：
- `api`、`auth`、`ui`、`db`、`config`、`deps`
- 组件名：`button`、`modal`、`header`
- 模块名：`parser`、`compiler`、`validator`

## 提交拆分策略

检测到以下情况时自动建议拆分：
1. **混合类型**：同一提交中包含功能 + 修复
2. **多个关注点**：不相关的变更
3. **大范围**：跨多个模块的变更
4. **文件模式**：源码 + 测试 + 文档混在一起
5. **依赖项**：依赖更新与功能变更混合

## 最佳实践

### 应做：
- ✅ **标题描述用中文**（祈使语气：添加/修复/更新，避免「已添加」作标题）
- ✅ 使用现在时、祈使语气
- ✅ 首行控制在 50 字符以内（最多 72 字符）
- ✅ 标题末尾不加句号
- ✅ 标题与正文之间空一行
- ✅ 正文解释做了什么、为什么（而非怎么做）
- ✅ 引用 issue 和破坏性变更

### 不应做：
- ❌ 有清晰中文表达时默认使用英文
- ❌ 一次提交混入多个逻辑变更
- ❌ 标题中包含实现细节
- ❌ 使用过去时（中文标题用「修复 xxx」而非「已修复 xxx」）
- ❌ 提交过大难以 review
- ❌ 提交有问题的代码（WIP 除外）
- ❌ 包含敏感信息

## 示例

### 简洁风格示例
```bash
✨ feat: 添加用户注册流程
🐛 fix: 修复事件处理器中的内存泄漏
📝 docs: 更新 API 端点文档
♻️ refactor: 简化鉴权逻辑
⚡️ perf: 优化数据库查询性能
🔧 chore: 更新构建依赖
```

### 完整风格示例
```bash
✨ feat(auth): 实现 OAuth2 认证流程

新增完整 OAuth2 认证，支持多身份提供商（Google、GitHub、Microsoft）。
实现遵循 RFC 6749，包含：

- 带 PKCE 的授权码流程
- 刷新令牌轮换
- 基于 scope 的权限
- 基于 Redis 的会话管理
- 按客户端限流

在兼容既有 JWT 方案的前提下，为用户提供安全的单点登录能力。

BREAKING CHANGE: /api/auth 相关接口现要求传入 client_id
Closes: #456, #457
Refs: RFC-6749, RFC-7636
```

## 工作流程

1. **更新 GitNexus 索引**：运行 `npx gitnexus analyze --embeddings` 确保知识图谱为最新（必须在变更分析之前执行，避免索引包含未提交内容）
2. 分析变更，确定提交类型与 scope
3. 检查是否需要拆分为多个提交
4. **文档索引同步**：若提交涉及 `docs/` 下文件的增删，同步更新 `docs/README.md` 索引
5. 对每个提交：
   - 暂存对应文件
   - 按风格设置生成提交信息（**标题/正文/脚注均使用中文**）
   - full 模式下生成详细正文与脚注
   - 执行 `git commit --author='罗威 <luowei@cvte.com>'`
6. 输出提交变更摘要

## 注意事项

- **文档索引同步**：提交涉及 `docs/` 下文件增删时，**必须**同步更新 `docs/README.md` 索引
- **提交信息默认中文**；`type`/`scope` 保留英文以兼容 Conventional Commits 规范
- 默认使用 `simple` 风格，适合日常快速提交
- 以下场景使用 `full` 风格：
  - 破坏性变更
  - 复杂功能
  - 需要解释的 Bug 修复
  - 影响多个系统的变更
- 工具会自动检测是否适合使用 full 风格并给出建议
- 确认前务必检查生成的提交信息
- Pre-commit 检查有助于维护代码质量