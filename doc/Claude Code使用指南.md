# Claude Code v2.1.116+ 开发者完整使用指南

> 基于 Claude Code 源码仓库整理，版本 2.1.116+

---

## 一、Slash 命令完整速查表

### 1.1 会话管理命令

| 命令 | 作用 | 使用场景 |
|------|------|---------|
| `/clear` | 清除当前会话上下文 | 切换任务、话题偏移太远时重置 |
| `/compact` | 压缩对话历史，释放上下文窗口 | 对话太长接近上下文限制时 |
| `/recap` | 返回会话时提供上下文回顾 | 恢复旧会话时快速了解上下文 |
| `/rewind` 或 `/undo` | 撤销最近的操作 | 需要回退时使用 |
| `/cost` | 显示当前会话 token 消耗统计 | 关注成本时查看 |
| `/resume` | 恢复之前的会话 | 继续之前的工作 |

### 1.2 模型与性能命令

| 命令 | 作用 | 使用场景 |
|------|------|---------|
| `/model` | 切换模型（opus/sonnet/haiku） | 选择适合任务的模型 |
| `/effort` | 设置模型努力程度（low/medium/high/xhigh/max） | 平衡速度与智能度 |
| `/fast` | 切换快速模式（Opus 4.6 加速输出） | 需要快速响应时 |

**Effort 级别说明**：
- `low`：快速响应，适合简单任务
- `medium`：默认平衡
- `high`：更深思熟虑（Pro/Max 用户 Opus/Sonnet 4.6 默认值）
- `xhigh`：Opus 4.7 专属，介于 high 和 max 之间
- `max`：最深度思考

### 1.3 项目配置命令

| 命令 | 作用 | 使用场景 |
|------|------|---------|
| `/init` | 生成 CLAUDE.md 项目文档 | 新项目首次接入 |
| `/config` | 打开配置界面 | 修改设置、主题、编辑器模式 |
| `/doctor` | 诊断当前会话问题 | 排查问题时使用 |
| `/memory` | 管理持久化记忆 | 让 Claude 记住偏好 |
| `/permissions` | 管理权限设置 | 查看或修改权限 |
| `/status` | 显示当前状态信息 | 检查配置来源 |

### 1.4 开发工作流命令

| 命令 | 作用 | 使用场景 |
|------|------|---------|
| `/review` | 审查当前分支的代码变更或 PR | 提交前代码审查 |
| `/security-review` | 安全审查当前分支变更 | 上线前安全检查 |
| `/ultrareview` | 云端多代理并行代码审查 | 深度审查 PR |
| `/simplify` | 审查代码复用性和质量 | 改完代码后检查 |
| `/commit` | 自动生成 commit 并提交 | Git 提交 |
| `/commit-push-pr` | 完整 Git 工作流（分支→提交→推送→PR） | 一键完成 PR |

### 1.5 效率优化命令

| 命令 | 作用 | 使用场景 |
|------|------|---------|
| `/fewer-permission-prompts` | 自动将常用只读命令加入权限白名单 | 减少权限弹窗 |
| `/less-permission-prompts` | 同上（别名） | 同上 |
| `/copy` | 交互式选择并复制代码块 | 复制代码 |

### 1.6 高级功能命令

| 命令 | 作用 | 使用场景 |
|------|------|---------|
| `/loop` | 定时循环执行命令 | 持续监控、轮询状态 |
| `/remote-control` | 桥接到 claude.ai/code | 从浏览器/手机继续 |
| `/terminal-setup` | 配置终端滚动敏感度 | VS Code/Cursor/Windsurf 全屏滚动优化 |
| `/tui` | 切换到无闪烁渲染模式 | 终端显示优化 |
| `/focus` | 切换专注视图 | 隐藏冗余信息 |
| `/powerup` | 交互式学习 Claude Code 功能 | 新手教程 |

### 1.7 团队协作命令

| 命令 | 作用 | 使用场景 |
|------|------|---------|
| `/team-onboarding` | 生成本地使用指南，帮助队友上手 | 团队知识传递 |
| `/agents` | 查看运行的子代理和代理库 | 管理子代理 |

### 1.8 其他命令

| 命令 | 作用 |
|------|------|
| `/help` | 显示帮助信息 |
| `/bug` | 报告 Bug |
| `/feedback` | 提交反馈 |
| `/debug` | 调试当前会话 |
| `/theme` | 切换主题（含 Auto 自动匹配终端） |
| `/reload-plugins` | 重载插件并自动安装依赖 |

### 1.9 已移除的命令

| 命令 | 状态 | 替代方案 |
|------|------|---------|
| `/tag` | v2.1.94 移除 | 无 |
| `/vim` | v2.1.94 移除 | 用 `/config` → Editor mode 切换 |
| `/proactive` | v2.1.105 起为 `/loop` 别名 | 用 `/loop` |

---

## 二、Skills 系统详解

### 2.1 什么是 Skills？

Skills 是**自动激活的能力模块**，通过 `.claude/commands/` 目录下的 Markdown 文件定义。当触发条件匹配时自动加载上下文，无需手动调用。

### 2.2 内置 Skills 列表

以下 Skills 已内置，可直接使用：

| Skill | 触发方式 | 作用 |
|-------|---------|------|
| `update-config` | `/update-config` | 修改 settings.json（权限、环境变量、钩子） |
| `keybindings-help` | `/keybindings-help` | 自定义键盘快捷键 |
| `simplify` | `/simplify` | 代码质量审查 |
| `fewer-permission-prompts` | `/fewer-permission-prompts` | 减少权限弹窗 |
| `loop` | `/loop 5m /foo` | 定时循环执行 |
| `claude-api` | 自动触发 | Claude API 开发辅助 |
| `init` | `/init` | 初始化 CLAUDE.md |
| `review` | `/review` | 审查 PR |
| `security-review` | `/security-review` | 安全审查 |

### 2.3 创建自定义 Skill/命令

**文件结构**：
```
.claude/
├── commands/
│   ├── fix-style.md      # /fix-style 命令
│   ├── run-tests.md      # /run-tests 命令
│   └── deploy.md         # /deploy 命令
```

**基本格式**：
```markdown
---
description: 修复代码风格问题
allowed-tools: Bash(mvn:*), Bash(npm:*)
effort: high
---

检查并修复代码格式问题：
1. 运行 mvn spotless:check checkstyle:check
2. 如有违规，运行 mvn spotless:apply
3. 重新检查确认通过
```

### 2.4 Frontmatter 配置项

| 字段 | 说明 | 示例 |
|------|------|------|
| `description` | 命令描述，显示在命令列表 | `"修复代码风格"` |
| `allowed-tools` | 限制可用的工具 | `Bash(mvn:*), Bash(git:*)` |
| `effort` | 覆盖模型努力程度 | `high`, `max` |
| `paths` | 匹配的文件路径 glob 列表 | `["src/**/*.java", "frontend/**"]` |
| `disallowedTools` | 禁用的工具列表 | `["WebSearch", "WebFetch"]` |
| `maxTurns` | 最大执行轮数 | `10` |
| `mcpServers` | 命令专用的 MCP 服务器配置 | 见 MCP 章节 |

### 2.5 动态上下文注入

使用 `!` 反引号语法在命令执行时注入动态内容：

```markdown
---
description: 提交并推送
allowed-tools: Bash(git:*), Bash(gh pr create:*)
---

## Context

- 当前状态: !`git status`
- 当前分支: !`git branch --show-current`
- 最近提交: !`git log --oneline -5`

## Task

1. 创建 commit
2. 推送到远程
3. 创建 PR
```

### 2.6 参数接收

使用 `$ARGUMENTS` 占位符接收用户输入：

```markdown
---
description: 运行指定测试并修复
---

运行测试 $ARGUMENTS，分析失败原因并修复代码。
```

使用：`/fix-test ChatServiceTest` → `$ARGUMENTS` 被替换为 `ChatServiceTest`

### 2.7 自动触发条件

在 Skill 文件头部定义自动触发规则：

```markdown
TRIGGER when: code imports `anthropic`/`@anthropic-ai/sdk`
SKIP: file imports `openai`/other-provider SDK
```

### 2.8 Skill 目录变量

- `${CLAUDE_SKILL_DIR}` - 引用 Skill 自身目录路径

---

## 三、Agents 子代理系统

### 3.1 什么是 Agents？

Agents 是 Claude Code 的**子代理系统**，可并行执行任务、隔离上下文。Claude 会根据任务复杂度自动派发，也可以手动请求使用特定类型。

### 3.2 可用的 Agent 类型

| Agent 类型 | 专长 | 工具权限 | 何时自动派发 |
|-----------|------|---------|-------------|
| **Explore** | 代码库探索、搜索 | 只读工具 | 广泛搜索代码时 |
| **Plan** | 架构设计、方案规划 | 只读 + 搜索 | 复杂功能规划时 |
| **claude-code-guide** | Claude Code 使用问题 | 只读 + WebSearch | 查询功能用法时 |
| **general-purpose** | 通用任务 | 全部工具 | 需要写代码的任务 |

### 3.3 Agent Frontmatter 配置

在 Agent 定义文件中可配置：

```markdown
---
description: 代码探索代理
effort: high
maxTurns: 20
disallowedTools: ["WebSearch"]
initialPrompt: "开始探索代码库"
---

[Agent 的系统提示词...]
```

| 字段 | 说明 |
|------|------|
| `initialPrompt` | 自动提交的第一轮提示 |
| `effort` | 模型努力程度 |
| `maxTurns` | 最大执行轮数 |
| `disallowedTools` | 禁用的工具 |

### 3.4 手动请求使用 Agent

```
# 让 Claude 使用 Explore Agent
"用 Explore Agent 搜索项目中所有限流相关代码"

# 并行派发多个 Agent
"同时修复 A 和 B 两个问题"

# 请求 Plan 模式
"先设计 WebSocket 通知的实现方案，确认后再执行"
```

### 3.5 Worktree 隔离模式

Agent 可在 Git Worktree 中隔离工作：

```
# 请求在 worktree 中工作
"在 worktree 里实验新的缓存方案"

# Claude 会：
# 1. 创建独立 worktree
# 2. 在其中修改代码
# 3. 完成后你选择保留或删除
```

### 3.6 查看运行中的 Agent

```
/agents    # 显示运行中的子代理和代理库
```

- **Running 标签页**：显示正在运行的子代理
- **Library 标签页**：显示可用代理，可运行或查看

---

## 四、Hooks 钩子系统

### 4.1 什么是 Hooks？

Hooks 是在特定事件触发时自动执行的脚本或命令，配置在 `settings.json` 中，由框架层执行。

### 4.2 所有可用的事件类型

| 事件 | 触发时机 | 典型用途 |
|------|---------|---------|
| `PreToolUse` | 执行工具调用之前 | 拦截危险命令、验证输入 |
| `PostToolUse` | 执行工具调用之后 | 自动格式化、运行 lint |
| `PreCompact` | 压缩上下文之前 | 阻止或修改压缩行为 |
| `PostCompact` | 压缩上下文之后 | 压缩后处理 |
| `SessionStart` | 会话开始时 | 注入上下文、设置输出风格 |
| `SessionEnd` | 会话结束时 | 清理资源 |
| `UserPromptSubmit` | 用户提交提示时 | 修改用户输入 |
| `Stop` | Claude 停止响应时 | 自动提交、运行测试 |
| `StopFailure` | API 错误导致停止时 | 错误处理 |
| `Notification` | 发送通知时 | 自定义通知方式 |
| `CwdChanged` | 工作目录改变时 | 环境重置 |
| `FileChanged` | 文件改变时 | 自动重载 |
| `TaskCreated` | 任务创建时 | 任务跟踪 |
| `WorktreeCreate` | Worktree 创建时 | 初始化配置 |
| `Elicitation` | 弹出交互对话框时 | 拦截对话框 |
| `ElicitationResult` | 对话框返回结果时 | 处理结果 |
| `PermissionDenied` | 权限被拒绝时 | 记录审计日志 |

### 4.3 Hook 配置格式

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "command": "python3 /path/to/hook.py"
          }
        ]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "npx eslint --fix $FILE_PATH"
          }
        ]
      }
    ],
    "Stop": [
      {
        "matcher": "",
        "if": "Bash(git status:*)",
        "hooks": [
          {
            "type": "command",
            "command": "mvn spotless:apply -q"
          }
        ]
      }
    ]
  }
}
```

### 4.4 Hook 输入输出

**输入（stdin JSON）**：
```json
{
  "tool_name": "Bash",
  "tool_input": {
    "command": "rm -rf /"
  }
}
```

**Exit Code 语义**：
| 退出码 | 含义 |
|--------|------|
| `0` | 允许继续执行 |
| `1` | 显示警告给用户，但继续执行 |
| `2` | 阻止操作，显示错误给 Claude |

**返回 JSON 阻止操作**：
```json
{
  "decision": "block",
  "reason": "危险命令被阻止"
}
```

**返回 JSON 修改工具输入**：
```json
{
  "permissionDecision": "allow",
  "updatedInput": {
    "command": "rm -rf ./safe-directory"
  }
}
```

### 4.5 Hook 条件过滤

使用 `if` 字段进行条件匹配：

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "if": "Bash(rm:*)",
        "hooks": [
          {
            "type": "command",
            "command": "echo 'Warning: rm command detected'"
          }
        ]
      }
    ]
  }
}
```

### 4.6 使用 /update-config 配置 Hook

```
/update-config
```

然后按提示添加 Hook 配置。

---

## 五、Memory 记忆系统

### 5.1 记忆类型

| 类型 | 用途 | 示例 |
|------|------|------|
| `user` | 用户角色、偏好、知识 | "我是后端开发者，对前端不太熟" |
| `feedback` | 协作反馈（做/不做） | "不要自动添加注释" |
| `project` | 项目上下文、进度 | "正在做限流功能重构" |
| `reference` | 外部系统资源指针 | "Bug 追踪在 Linear 的 INGEST 项目" |

### 5.2 记忆存储结构

```
~/.claude/projects/<project-id>/memory/
├── MEMORY.md              # 索引文件（自动加载，限 200 行/25KB）
├── user_role.md           # 用户偏好
├── feedback_style.md      # 协作反馈
└── project_progress.md    # 项目进度
```

### 5.3 记忆文件格式

```markdown
---
name: 语言偏好
description: 用户要求始终使用中文交流
type: user
---

用户要求始终使用中文进行所有交流。
```

### 5.4 Feedback/Project 类型结构

```markdown
---
name: 代码风格偏好
type: feedback
---

不要自动添加 JavaDoc 注释。

**Why:** 项目已有足够的代码自文档化，多余的注释反而造成干扰。

**How to apply:** 编写新代码时不需要添加注释，除非逻辑非常复杂需要解释原因。
```

### 5.5 管理记忆

```
# 让 Claude 记住
记住：我偏好用中文交流，代码注释用英文
记住：修改代码前先规划，确认后再执行

# 查看记忆
/memory

# 删除记忆
记住但要忘记：XXX
```

### 5.6 配置选项

```json
{
  "autoMemoryDirectory": "/custom/path/memory"
}
```

---

## 六、Plugins 插件系统

### 6.1 官方插件列表

| 插件名 | 功能 | 主要命令 |
|--------|------|---------|
| **agent-sdk-dev** | Claude Agent SDK 应用开发 | `/new-sdk-app` |
| **claude-opus-4-5-migration** | 迁移代码到 Opus 4.5 | 自动触发 |
| **code-review** | 自动化 PR 代码审查 | `/code-review` |
| **commit-commands** | Git 工作流自动化 | `/commit`, `/commit-push-pr`, `/clean_gone` |
| **explanatory-output-style** | 教育性输出风格 | 自动激活 |
| **feature-dev** | 7 阶段功能开发工作流 | `/feature-dev` |
| **frontend-design** | 前端设计指导 | 自动触发 |
| **hookify** | 简化 Hook 创建 | `/hookify`, `/hookify:list` |
| **learning-output-style** | 交互式学习模式 | 自动激活 |
| **plugin-dev** | 插件开发工具包 | `/plugin-dev:create-plugin` |
| **pr-review-toolkit** | PR 审查工具集（6 个维度） | `/pr-review-toolkit:review-pr` |
| **ralph-wiggum** | 自引用循环迭代开发 | `/ralph-loop` |
| **security-guidance** | 安全模式检测警告 | 自动触发 |

### 6.2 安装插件

```
# 安装官方插件
claude plugin install <plugin-name>

# 从 URL 安装
claude plugin install https://github.com/user/plugin.git

# 列出已安装插件
claude plugin list

# 更新插件
claude plugin update <plugin-name>

# 重载插件（自动安装缺失依赖）
/reload-plugins
```

### 6.3 插件目录结构

```
my-plugin/
├── .claude-plugin/
│   └── plugin.json       # 插件清单
├── commands/             # 自定义命令
│   └── my-command.md
├── agents/               # 自定义代理
│   └── my-agent.md
├── skills/               # 自定义技能
│   └── my-skill/
│       └── SKILL.md
├── hooks/                # 插件钩子
│   └── hooks.json
└── bin/                  # 可执行文件
    └── my-script.sh
```

### 6.4 plugin.json 清单

```json
{
  "name": "my-plugin",
  "version": "1.0.0",
  "description": "我的自定义插件",
  "author": "your@email.com",
  "commands": ["commands/"],
  "agents": ["agents/"],
  "skills": ["skills/"],
  "hooks": "hooks/hooks.json",
  "userConfig": {
    "apiKey": {
      "type": "string",
      "description": "API Key",
      "sensitive": true
    }
  }
}
```

---

## 七、MCP 服务器集成

### 7.1 什么是 MCP？

Model Context Protocol (MCP) 允许 Claude Code 连接外部工具和数据源。

### 7.2 配置 MCP 服务器

在 `settings.json` 中：

```json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "ghp_xxx"
      }
    },
    "postgres": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres", "postgresql://localhost:5432/mydb"]
    },
    "filesystem": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-filesystem", "/path/to/allowed/dir"]
    }
  }
}
```

### 7.3 常用 MCP 服务器

| 服务器 | 功能 | 安装命令 |
|--------|------|---------|
| `server-github` | GitHub PR/Issue 操作 | `npx -y @modelcontextprotocol/server-github` |
| `server-postgres` | 直接查询 PostgreSQL | `npx -y @modelcontextprotocol/server-postgres` |
| `server-filesystem` | 文件系统访问 | `npx -y @modelcontextprotocol/server-filesystem` |
| `server-brave-search` | Brave 搜索 | `npx -y @modelcontextprotocol/server-brave-search` |

### 7.4 MCP 工具结果持久化

在工具返回中添加 `_meta` 注释，可将结果持久化到磁盘（最大 500KB）：

```json
{
  "_meta": {
    "anthropic/maxResultSizeChars": 500000
  }
}
```

### 7.5 MCP OAuth 支持

MCP 支持 OAuth 认证，遵循 RFC 9728 Protected Resource Metadata 发现授权服务器。

---

## 八、权限系统

### 8.1 权限配置结构

```json
{
  "permissions": {
    "allow": [
      "Bash(mvn:*)",
      "Bash(npm run:*)",
      "Bash(git:*)",
      "Bash(docker compose:*)",
      "WebSearch",
      "WebFetch(domain:github.com)",
      "Read(//path/**)"
    ],
    "deny": [
      "Bash(rm -rf:*)"
    ],
    "ask": [
      "Bash"
    ]
  }
}
```

### 8.2 权限规则语法

| 模式 | 说明 |
|------|------|
| `Bash(mvn:*)` | 允许所有 mvn 子命令 |
| `Bash(git commit:*)` | 允许特定子命令 |
| `Read(//path/**)` | 允许读取特定路径 |
| `WebFetch(domain:github.com)` | 允许特定域名 |
| `WebSearch` | 允许网络搜索 |
| `mcp__*` | 允许所有 MCP 工具 |

### 8.3 权限配置层级

```
Enterprise/MDM 管理设置（最高优先级，无法覆盖）
    ↓
项目 .claude/settings.local.json
    ↓
用户 ~/.claude/settings.json
    ↓
运行时权限提示
```

### 8.4 企业管理设置

```json
{
  "permissions": {
    "disableBypassPermissionsMode": "disable"
  },
  "strictKnownMarketplaces": [],
  "allowManagedPermissionRulesOnly": true,
  "allowManagedHooksOnly": true
}
```

### 8.5 Sandbox 沙箱配置

```json
{
  "sandbox": {
    "enabled": true,
    "autoAllowBashIfSandboxed": false,
    "allowUnsandboxedCommands": false,
    "excludedCommands": [],
    "network": {
      "allowUnixSockets": [],
      "allowAllUnixSockets": false,
      "allowLocalBinding": false,
      "allowedDomains": [],
      "deniedDomains": ["malicious-site.com"]
    }
  }
}
```

---

## 九、高效开发工作流

### 9.1 项目初始化清单

```
# 1. 启动 Claude Code
claude

# 2. 初始化项目文档
/init

# 3. 减少权限弹窗
/fewer-permission-prompts

# 4. 记住偏好
记住：我偏好中文交流，代码注释用英文
记住：修改代码前先规划，确认后再执行
```

### 9.2 日常开发流程

```
┌────────────────────────────────────────────────────────┐
│  需求描述                                              │
│  ↓                                                     │
│  复杂功能 → Claude 自动进入 Plan 模式 → 你确认方案      │
│  ↓                                                     │
│  Claude 编码实现                                       │
│  ↓                                                     │
│  /simplify      →  代码质量审查                        │
│  /review        →  变更审查                            │
│  /security-review → 安全检查                           │
│  ↓                                                     │
│  /commit-push-pr → 一键提交 PR                         │
└────────────────────────────────────────────────────────┘
```

### 9.3 实用提示词技巧

**1. 明确任务范围**
```
# 好的示例
在 ChatController 中添加一个 GET /api/chat/history 端点，
返回当前用户的最近 20 条对话记录，需要分页支持。

# 避免
加个聊天历史功能
```

**2. 先规划再执行**
```
我想给限流系统加上按用户维度的限流，请先分析现有代码，
设计实现方案，确认后再动手修改。
```

**3. 利用并行能力**
```
同时做以下三件事：
1. 修复 PerformanceTestView.vue 中的空值显示问题
2. 给 PlanningView.vue 添加表单验证
3. 更新 E2E 测试的 mock 数据
```

**4. 上下文管理**
```
# 对话太长时
/compact 保留关于限流功能的讨论

# 切换完全不同的任务时
/clear
```

**5. 使用自定义命令**
```
/fix-style           # 用自定义命令修复代码格式
/run-tests ChatServiceTest  # 运行指定测试
```

### 9.4 /loop 循环任务

```bash
# 每 5 分钟检查部署状态
/loop 5m 检查 docker compose ps 的服务状态

# 持续监控日志
/loop 2m 检查后端日志中是否有新的 ERROR
```

> **注意**：循环任务在会话关闭后停止，7 天后自动过期。

### 9.5 Remote Control 远程控制

```
/remote-control
```

这会将当前会话桥接到 claude.ai/code，你可以从浏览器或手机继续对话。

---

## 十、键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| `Enter` | 发送消息 |
| `Shift+Enter` | 换行 |
| `Escape` | 取消当前操作 |
| `Ctrl+C` | 中断 Claude 响应 |
| `Ctrl+L` | 清屏 |
| `↑/↓` | 浏览历史消息 |
| `Tab` | 自动补全 |
| `Ctrl+O` | 切换详细模式 |

> 使用 `/keybindings-help` 自定义快捷键。

---

## 十一、常见问题

### Q: 对话太长 Claude 变慢？
使用 `/compact` 压缩上下文，或 `/clear` 重新开始。

### Q: 权限弹窗太多？
运行 `/fewer-permission-prompts`，或手动添加到 settings.json。

### Q: 如何让 Claude 记住偏好？
直接说"记住：XXX"。

### Q: Skill 和 Agent 区别？
- **Skill**：自动激活的能力模块，预定义的上下文模板
- **Agent**：子代理，可并行执行任务，有独立上下文窗口

### Q: Hooks 和 Memory 区别？
- **Hooks**：框架层自动化，"每次 X 时执行 Y"
- **Memory**：影响 Claude 决策，但不触发自动行为

### Q: 如何切换模型？
`/model opus`、`/model sonnet`、`/model haiku`

### Q: 如何设置努力程度？
`/effort` 打开交互式滑块，或 `/effort high` 直接设置。

### Q: 子代理卡住怎么办？
子代理 10 分钟无响应会自动失败并报错。

---

## 十二、版本更新要点

### v2.1.116 主要更新
- `/resume` 大会话恢复提速 67%
- MCP 启动优化，多 stdio 服务器并发连接
- Thinking spinner 显示进度（"still thinking"等）
- `/config` 搜索支持匹配选项值
- `/doctor` 可在 Claude 响应时打开
- Sandbox 安全增强，阻止对关键系统目录的 rm 操作

### v2.1.117 主要更新
- 支持 `CLAUDE_CODE_FORK_SUBAGENT=1` 启用 Fork 子代理
- Agent frontmatter `mcpServers` 支持
- `/model` 选择跨重启持久化
- `/resume` 提供压缩旧会话选项
- macOS/Linux 原生构建使用嵌入式 `bfs` 和 `ugrep` 替代 Glob/Grep 工具

---

## 十三、本项目自定义命令

本项目已预配置以下自定义命令，拉取代码后可直接使用：

| 命令 | 说明 |
|------|------|
| `/fix-style` | 检查并修复代码格式（mvn spotless + checkstyle） |
| `/fix-test <测试名>` | 运行指定测试并分析失败原因 |
| `/run-e2e` | 运行 Playwright E2E 测试 |
| `/run-performance` | 运行 Gatling 性能测试 |
| `/check-health` | 检查服务健康状态 |
| `/deploy-local` | 本地 Docker 部署 |
| `/start-dev` | 启动本地开发环境 |
| `/stop-dev` | 停止本地开发服务 |
| `/commit-pr` | 提交代码并创建 PR |
