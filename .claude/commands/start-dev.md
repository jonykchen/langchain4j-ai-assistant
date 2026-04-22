---
description: 启动本地开发环境
allowed-tools: Bash(mvn:*), Bash(npm:*), Bash(npx:*), Bash(lsof:*), Bash(kill:*), Bash(curl:*)
effort: low
---

## 当前状态

- 后端端口 8082: !`lsof -ti:8082 2>/dev/null || netstat -ano | findstr :8082 || echo "未占用"`
- 前端端口 5173: !`lsof -ti:5173 2>/dev/null || netstat -ano | findstr :5173 || echo "未占用"`

## Task

启动本地开发环境（后端 + 前端）。

步骤：
1. 如果端口被占用，先停止占用进程
2. 启动后端服务：`mvn spring-boot:run -Dcheckstyle.skip=true -Dpmd.skip=true`
3. 等待后端启动完成（约 30 秒）
4. 进入 frontend 目录，启动前端：`npm run dev`
5. 验证两个服务都正常运行
6. 汇报启动状态和访问地址