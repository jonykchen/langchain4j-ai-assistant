---
description: 提交代码变更并创建 PR
allowed-tools: Bash(git:*), Bash(gh:*)
effort: low
---

## Context

- 当前状态: !`git status --short`
- 当前分支: !`git branch --show-current`
- 最近提交: !`git log --oneline -5`

## Task

完成 Git 工作流：提交并创建 PR。

步骤：
1. 检查当前分支，如果在 main 则创建新分支
2. 查看变更内容：`git diff HEAD`
3. 添加相关文件到暂存区
4. 创建 commit，消息格式遵循项目规范
5. 推送到远程：`git push -u origin <branch>`
6. 创建 PR：`gh pr create`
7. 汇报 PR 地址