---
description: 停止本地开发服务
allowed-tools: Bash(lsof:*), Bash(kill:*), Bash(taskkill:*), Bash(netstat:*), Bash(findstr:*)
effort: low
---

## Task

停止本地开发环境（后端 + 前端）。

步骤：
1. 查找后端进程（端口 8082）：`lsof -ti:8082` 或 `netstat -ano | findstr :8082`
2. 查找前端进程（端口 5173）：`lsof -ti:5173` 或 `netstat -ano | findstr :5173`
3. 停止所有相关进程
4. 确认端口已释放
5. 汇报停止状态