---
description: 检查并修复代码风格问题
allowed-tools: Bash(mvn:*)
effort: low
---

## 当前修改的文件

!`git status --short`

## Task

1. 运行 `mvn spotless:check checkstyle:check` 检查代码格式
2. 如果有违规，运行 `mvn spotless:apply` 自动修复
3. 再次检查确认通过
4. 汇报修复结果，列出修改的文件