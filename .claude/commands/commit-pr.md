---
description: 提交代码变更并创建 PR
allowed-tools: Bash(git:*), Bash(gh:*), Bash(curl:*), Bash(source:*), Bash(cat:*), Bash(grep:*)
effort: low
---

## Context

- 当前状态: !`git status --short`
- 当前分支: !`git branch --show-current`
- 最近提交: !`git log --oneline -5`
- 远程地址: !`git remote get-url origin 2>/dev/null || echo "no remote"`
- 环境配置: !`cat .env 2>/dev/null | grep GITEE_ || echo "no GITEE_ config in .env"`

## Task

完成 Git 工作流：提交并创建 PR。

步骤：
1. 检查当前分支，如果在 main/master 则创建新分支
2. 查看变更内容：`git diff HEAD`
3. 添加相关文件到暂存区（避免 .env 等敏感文件）
4. 创建 commit，消息格式遵循项目规范
5. 推送到远程：`git push -u origin <branch>`
6. 创建 PR：根据远程仓库选择方式
   - **Gitee**: 从 `.env` 读取配置，用 curl 调用 Gitee API v5 创建 PR
   - **GitHub**: 使用 `gh pr create`
7. 汇报 PR 地址

## Gitee PR 创建方式

当远程地址包含 `gitee.com` 时，使用以下方式创建 PR：

1. 从项目根目录 `.env` 文件读取配置：
   - `GITEE_TOKEN`: 私人令牌
   - `GITEE_OWNER`: 仓库所有者
   - `GITEE_REPO`: 仓库名

   读取方式：
   ```bash
   source .env
   echo $GITEE_TOKEN
   echo $GITEE_OWNER
   echo $GITEE_REPO
   ```

2. 如果 `.env` 中缺少 `GITEE_TOKEN` 等配置，提示用户在 `.env` 中添加：
   ```
   GITEE_TOKEN=你的私人令牌
   GITEE_OWNER=仓库所有者
   GITEE_REPO=仓库名
   ```

3. 用 curl 创建 PR：
   ```bash
   source .env
   curl -s -X POST "https://gitee.com/api/v5/repos/${GITEE_OWNER}/${GITEE_REPO}/pulls" \
     -H "Content-Type: application/json" \
     -d "{
       \"access_token\": \"${GITEE_TOKEN}\",
       \"title\": \"PR标题\",
       \"head\": \"源分支\",
       \"base\": \"目标分支(默认master)\",
       \"body\": \"PR描述\"
     }"
   ```

4. 从响应中提取 `html_url` 作为 PR 链接

5. PR 标题使用最新 commit 消息，描述收集分支所有 commit

## 注意

- `.env` 文件已在 `.gitignore` 中，不会被提交
- 不要将 GITEE_TOKEN 硬编码在命令文件中
