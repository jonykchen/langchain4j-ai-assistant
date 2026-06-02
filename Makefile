# LangChain4j AI Assistant - Makefile
# 便捷命令集合

.PHONY: help install build run test clean docker-up docker-down docker-logs docker-clean dev

# 默认目标
.DEFAULT_GOAL := help

# ==================== 帮助信息 ====================
help: ## 显示帮助信息
	@echo "LangChain4j AI Assistant - 可用命令:"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""

# ==================== 开发命令 ====================
install: ## 安装依赖
	mvn install -DskipTests
	cd frontend && npm install

build: ## 构建项目
	mvn clean package -DskipTests
	cd frontend && npm run build

run: ## 启动后端服务
	mvn spring-boot:run -Dspring-boot.run.profiles=dev

run-frontend: ## 启动前端开发服务器
	cd frontend && npm run dev

dev: ## 启动开发环境（需要先启动 Docker）
	@echo "启动后端..."
	@mvn spring-boot:run -Dspring-boot.run.profiles=dev &

test: ## 运行所有测试
	mvn test

test-unit: ## 运行单元测试
	mvn test -Dtest="!*IntegrationTest,!*ContractTest,!*AIModelTest*"

test-integration: ## 运行集成测试
	mvn test -Dtest="*IntegrationTest"

test-e2e: ## 运行 E2E 测试
	cd frontend && npm run test:e2e

test-performance: ## 运行性能测试
	mvn gatling:test

clean: ## 清理构建产物
	mvn clean
	cd frontend && rm -rf dist node_modules/.vite

# ==================== Docker 命令 ====================
docker-up: ## 启动所有 Docker 服务
	docker compose -f docker-compose.dev.yml up -d

docker-down: ## 停止所有 Docker 服务
	docker compose -f docker-compose.dev.yml down

docker-logs: ## 查看 Docker 日志
	docker compose -f docker-compose.dev.yml logs -f --tail=100

docker-clean: ## 清理 Docker 资源（包括数据）
	docker compose -f docker-compose.dev.yml down -v --remove-orphans

docker-ps: ## 查看 Docker 服务状态
	docker compose -f docker-compose.dev.yml ps

docker-restart: ## 重启所有 Docker 服务
	docker compose -f docker-compose.dev.yml restart

# ==================== 生产部署 ====================
deploy-prod: ## 生产环境部署
	docker compose --env-file .env.prod up -d --build

deploy-down: ## 停止生产环境
	docker compose down

# ==================== 工具命令 ====================
lint: ## 运行代码检查
	mvn checkstyle:check
	cd frontend && npm run lint:check

format: ## 格式化代码
	mvn spotless:apply
	cd frontend && npm run format

db-backup: ## 备份数据库
	docker exec langchain4j-ai-assistant-postgres pg_dump -U langchain4j langchain4j > backup_$$(date +%Y%m%d_%H%M%S).sql

health: ## 检查服务健康状态
	@echo "后端健康状态:"
	@curl -s http://localhost:8082/actuator/health | jq .
	@echo "\n模型健康状态:"
	@curl -s http://localhost:8082/api/health/models | jq .

# ==================== 快速开发 ====================
dev-docker: ## 启动开发 Docker 环境
	@./dev.sh start

dev-stop: ## 停止开发环境
	@./dev.sh stop

dev-logs: ## 查看开发日志
	@./dev.sh logs
