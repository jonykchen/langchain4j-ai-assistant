---
description: 检查服务健康状态
allowed-tools: Bash(curl:*), Bash(docker:*)
effort: low
---

## Task

检查所有服务的健康状态。

步骤：
1. 检查后端服务：`curl -s http://localhost:8082/actuator/health`
2. 检查前端服务：`curl -s http://localhost:5173`
3. 检查 Docker 容器状态：`docker compose ps`
4. 检查数据库连接
5. 汇报所有服务状态