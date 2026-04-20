# ==================== LangChain4j Agent 工程开发 Makefile ====================
#
# 使用方式：
#   make [命令]
#
# 快速开始：
#   make init    - 初始化开发环境
#   make dev     - 启动 Docker 基础设施 + 本地应用

.PHONY: help init dev dev-docker dev-local stop restart status logs reset clean test build run db-backup db-restore db-sync

# 默认目标
help:
	@echo "==================== LangChain4j Agent 开发命令 ===================="
	@echo ""
	@echo "环境管理:"
	@echo "  make init         初始化开发环境（创建 .env 和 data 目录）"
	@echo "  make dev          启动完整开发环境（Docker + 后端）"
	@echo "  make dev-docker   仅启动 Docker 基础设施"
	@echo "  make dev-local    仅启动本地应用（需 Docker 已运行）"
	@echo "  make stop         停止所有 Docker 服务"
	@echo "  make restart      重启 Docker 服务"
	@echo "  make status       查看服务状态"
	@echo "  make logs         查看所有日志"
	@echo "  make reset        重置环境（清除所有数据）"
	@echo ""
	@echo "构建与测试:"
	@echo "  make build        构建项目"
	@echo "  make test         运行测试"
	@echo "  make clean        清理构建产物"
	@echo ""
	@echo "运行:"
	@echo "  make run          启动后端应用（端口 8082）"
	@echo "  make run-dev      以开发模式启动（启用热重载）"
	@echo ""
	@echo "可选服务:"
	@echo "  make dev-ollama   启动基础服务 + Ollama (CPU)"
	@echo "  make dev-vector   启动基础服务 + Qdrant 向量数据库"
	@echo "  make dev-mq       启动基础服务 + RabbitMQ"
	@echo "  make dev-all      启动所有服务"
	@echo ""
	@echo "数据库:"
	@echo "  make db-psql      连接 PostgreSQL"
	@echo "  make db-redis     连接 Redis"
	@echo "  make db-backup    备份 PG业务+Nacos配置为种子 (git 共享)"
	@echo "  make db-restore   从种子恢复 PG+Nacos 数据"
	@echo "  make db-sync      备份 + git 提交 (同步给团队)"
	@echo ""
	@echo "前端:"
	@echo "  make frontend     启动前端开发服务器"
	@echo ""

# ==================== 环境管理 ====================

# 初始化环境
init:
	@echo "初始化开发环境..."
	@if [ ! -f .env ]; then cp .env.example .env && echo "已创建 .env 文件"; else echo ".env 已存在"; fi
	@mkdir -p data/{postgres,redis,nacos,nacos-db,prometheus,grafana,ollama,qdrant,etcd,minio,milvus,rabbitmq}
	@echo "已创建 data 目录结构"
	@echo "请编辑 .env 文件填入你的 API Keys"

# 启动 Docker 基础设施
dev-docker:
	@./dev.sh start

# 启动本地应用
dev-local:
	@echo "启动后端应用..."
	@export JAVA_HOME=/usr/local/opt/openjdk@17 && mvn spring-boot:run

# 启动完整开发环境
dev: dev-docker
	@sleep 10
	@echo "等待服务就绪..."
	@export JAVA_HOME=/usr/local/opt/openjdk@17 && mvn spring-boot:run

# 停止所有服务
stop:
	@./dev.sh stop

# 重启服务
restart:
	@./dev.sh restart

# 查看状态
status:
	@./dev.sh status

# 查看日志
logs:
	@./dev.sh logs

# 重置环境
reset:
	@./dev.sh reset

# ==================== 可选服务 ====================

dev-ollama:
	@./dev.sh start --profile cpu

dev-vector:
	@./dev.sh start --profile vector

dev-mq:
	@./dev.sh start --profile mq

dev-all:
	@./dev.sh start --profile cpu --profile vector --profile mq

# ==================== 构建与测试 ====================

# 清理
clean:
	@export JAVA_HOME=/usr/local/opt/openjdk@17 && mvn clean

# 构建
build:
	@export JAVA_HOME=/usr/local/opt/openjdk@17 && mvn clean package -DskipTests

# 测试
test:
	@export JAVA_HOME=/usr/local/opt/openjdk@17 && mvn test

# ==================== 运行 ====================

# 启动应用
run:
	@export JAVA_HOME=/usr/local/opt/openjdk@17 && mvn spring-boot:run

# 开发模式启动（热重载）
run-dev:
	@export JAVA_HOME=/usr/local/opt/openjdk@17 && mvn spring-boot:run -Dspring-boot.run.profiles=dev

# ==================== 数据库 ====================

# ==================== 数据库工具（跨环境数据共享） ====================

# 备份当前数据为种子文件 → 提交 Git → 团队共享
db-backup:
	@./scripts/db/db-backup.sh --seed

# 从种子文件恢复数据（新环境 pull 代码后执行）
db-restore:
	@./scripts/db/db-restore.sh

# 一键同步：备份 + git commit + push
db-sync: db-backup
	@echo ""
	@echo "==> 提交种子数据到 Git..."
	@git add infra/postgres/init/02-seed-data.sql infra/nacos/init/03-nacos-config-seed.sql 2>/dev/null; \
	(git diff --cached --quiet || \
		git commit -m "sync: update database & nacos seed data $(shell date +%Y-%m-%d)")
	@echo "OK! 请执行 git push 同步给团队"

# 连接 PostgreSQL
db-psql:
	@docker exec -it langchain4j-postgres psql -U langchain4j -d langchain4j

# 连接 Redis
db-redis:
	@docker exec -it langchain4j-redis redis-cli

# ==================== 前端 ====================

frontend:
	@cd frontend && npm install && npm run dev