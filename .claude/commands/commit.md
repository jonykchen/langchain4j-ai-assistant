---
description: 提交代码变更并推送
allowed-tools: Bash(git:*), Bash(gh:*)
effort: low
---

## Context

- 当前状态: !`git status --short`
- 当前分支: !`git branch --show-current`
- 最近提交: !`git log --oneline -3`

## Task

完成 Git 提交并推送。

步骤：
1. 检查当前分支，如果在 main/master 且有变更，提示创建新分支或确认直接提交
2. 查看变更内容：`git diff HEAD`
3. 添加相关文件到暂存区（个人私有项目，.env 需提交）
4. 创建 commit，消息格式遵循项目规范
5. 推送到远程：`git push`
6. 汇报提交结果

## Commit 消息格式

遵循 Conventional Commits 规范：
- `feat:` 新功能
- `fix:` 修复 bug
- `docs:` 文档变更
- `chore:` 构建/工具/依赖变更
- `refactor:` 重构
- `test:` 测试相关
- `style:` 代码格式
