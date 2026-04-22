---
description: 本地 Docker 部署
allowed-tools: Bash(docker:*)
effort: medium
---

## Task

使用 Docker Compose 进行本地部署。

步骤：
1. 检查 Docker 是否运行：`docker info`
2. 查看当前容器状态：`docker compose ps`
3. 如果需要重新部署：
   - 停止现有容器：`docker compose down`
   - 重新构建并启动：`docker compose up -d --build`
4. 查看启动日志：`docker compose logs -f`
5. 等待所有服务健康后汇报状态