# LangChain4j Agent 工程 - 快速开始指南

本文档提供完整的开发环境启动指南，帮助你在任何电脑上一键启动项目。

---

## 目录

- [环境要求](#环境要求)
- [快速启动](#快速启动)
- [服务访问地址](#服务访问地址)
- [配置说明](#配置说明)
- [常见问题](#常见问题)
- [开发工作流](#开发工作流)

---

## 环境要求

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| Docker Desktop | 4.0+ | 容器运行环境 |
| JDK | 17+ | Java 开发环境 |
| Maven | 3.8+ | 项目构建工具 |
| Node.js | 18+ | 前端开发环境（可选） |

---

## 快速启动

### 1. 克隆项目

```bash
git clone <repository-url>
cd langchain4j-demo
```

### 2. 配置环境变量

**Linux / macOS：**

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑 .env 文件，填入你的 API Keys
# 必填项：DASHSCOPE_API_KEY
```

**Windows（PowerShell）：**

```powershell
# 复制环境变量模板
copy .env.example .env

# 编辑 .env 文件，填入你的 API Keys
# 必填项：DASHSCOPE_API_KEY
```

### 3. 启动基础设施

**Linux / macOS：**

```bash
# 方式一：使用启动脚本
./dev.sh start

# 方式二：使用 Makefile
make dev-docker

# 方式三：直接使用 Docker Compose
docker compose -f docker-compose.dev.yml up -d
```

**Windows（PowerShell）：**

```powershell
# 首次使用：创建数据目录
New-Item -ItemType Directory -Force -Path data/postgres,data/redis,data/nacos,data/nacos-db,data/prometheus,data/grafana

# 启动基础设施
docker compose -f docker-compose.dev.yml up -d

# 启动基础服务 + 可选服务（如 Ollama CPU 版）
# docker compose -f docker-compose.dev.yml --profile cpu up -d

# 查看服务状态
docker compose -f docker-compose.dev.yml ps

# 查看日志
docker compose -f docker-compose.dev.yml logs -f --tail=100

# 停止所有服务
docker compose -f docker-compose.dev.yml down
```

### 4. 启动后端应用

```bash
# 方式一：Maven
mvn spring-boot:run

# 方式二：使用 Makefile
make run

# 方式三：指定配置文件
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5. 启动前端（可选）

```bash
cd frontend
npm install
npm run dev
```

---

## 服务访问地址

### 基础设施服务

| 服务 | 地址 | 用户名/密码 | 说明 |
|------|------|-------------|------|
| **PostgreSQL** | `localhost:5432` | `langchain4j` / `REDACTED_DB_PASSWORD` | 主数据库 |
| **Redis** | `localhost:6379` | 无密码 | 分布式缓存/限流 |
| **Nacos** | http://localhost:8848/nacos | `nacos` / `nacos` | 配置中心 |

### 可观测性服务

| 服务 | 地址 | 用户名/密码 | 说明 |
|------|------|-------------|------|
| **Zipkin** | http://localhost:9411 | 无 | 链路追踪 |
| **Prometheus** | http://localhost:9090 | 无 | 指标收集 |
| **Grafana** | http://localhost:3001 | `admin` / `REDACTED_ADMIN_PASSWORD` | 可视化面板 |

### 管理工具

| 服务 | 地址 | 用户名/密码 | 说明 |
|------|------|-------------|------|
| **Adminer** | http://localhost:8080 | - | 数据库管理 |
| **Redis Commander** | http://localhost:8081 | - | Redis 管理 |

### 应用服务

| 服务 | 地址 | 说明 |
|------|------|------|
| **后端 API** | http://localhost:8082 | Spring Boot 应用 |
| **H2 Console** | http://localhost:8082/h2-console | 内存数据库控制台（开发模式） |
| **前端** | http://localhost:5173 | Vue 3 开发服务器 |

---

## 详细服务说明

### PostgreSQL 数据库（应用业务数据）

**连接信息：**
```
Host: localhost
Port: 5432
Database: langchain4j
Username: langchain4j
Password: REDACTED_DB_PASSWORD
JDBC URL: jdbc:postgresql://localhost:5432/langchain4j
```

**使用 Adminer 管理：**
1. 访问 http://localhost:8080
2. 选择 "PostgreSQL" 作为数据库类型
3. 输入连接信息后登录

**命令行连接：**
```bash
docker exec -it langchain4j-postgres psql -U langchain4j -d langchain4j
```

**扩展支持：**
- `pgvector` - 向量存储和相似度检索
- `uuid-ossp` - UUID 生成
- `pg_trgm` - 全文搜索

**数据表结构：**
| 表名 | 说明 |
|------|------|
| `app.users` | 用户表 |
| `app.conversations` | 会话表 |
| `app.messages` | 消息表 |
| `app.token_usage_logs` | Token 使用日志 |
| `app.tool_execution_audits` | 工具执行审计 |
| `app.documents` | RAG 文档表 |
| `app.document_chunks` | RAG 文档分块表（含向量） |
| `app.roles` | 角色表 |
| `app.user_roles` | 用户角色关联表 |
| `app.api_keys` | API Key 表 |

---

### MySQL 数据库（仅 Nacos 元数据）

> **注意**：MySQL 仅存储 Nacos 配置中心的元数据，应用业务数据使用 PostgreSQL。

**连接信息：**
```
Host: localhost
Port: 3307
Database: nacos
Username: nacos
Password: REDACTED_NACOS_PASSWORD
JDBC URL: jdbc:mysql://localhost:3307/nacos
```

**使用 Adminer 管理：**
1. 访问 http://localhost:8080
2. 选择 "MySQL" 作为数据库类型
3. 输入连接信息后登录

---

### Redis

**连接信息：**
```
Host: localhost
Port: 6379
Password: 无
```

**使用 Redis Commander 管理：**
1. 访问 http://localhost:8081
2. 自动连接到本地 Redis

**命令行连接：**
```bash
docker exec -it langchain4j-redis redis-cli
```

**主要用途：**
- 分布式限流
- 会话缓存
- 对话历史缓存
- 幂等性检查

---

### Nacos 配置中心

**访问地址：** http://localhost:8848/nacos

**默认账号：** `nacos` / `nacos`

**配置命名空间：**
| 命名空间 | 说明 |
|----------|------|
| `dev` | 开发环境 |
| `prod` | 生产环境 |

**常用配置：**
- `langchain4j-chat.properties` - 应用主配置
- 限流规则动态更新
- 熔断器参数动态调整

**配置示例：**
```properties
# 限流配置
rate.limit.chat.limit=20
rate.limit.chat.period=60

# 熔断器配置
resilience4j.circuitbreaker.instances.chat.slidingWindowSize=20
resilience4j.circuitbreaker.instances.chat.failureRateThreshold=30
```

---

### Zipkin 链路追踪

**访问地址：** http://localhost:9411

**功能：**
- 查看请求链路
- 分析调用耗时
- 排查性能问题

**使用方式：**
1. 访问 Zipkin UI
2. 点击 "Run Query" 查看最近请求
3. 点击具体 trace 查看详细调用链

---

### Prometheus 监控

**访问地址：** http://localhost:9090

**监控指标：**
| 指标类型 | 说明 |
|----------|------|
| JVM 内存 | 堆内存、非堆内存使用情况 |
| HTTP 请求 | 请求量、响应时间、错误率 |
| AI 模型 | 调用量、延迟、Token 消耗 |
| Resilience4j | 熔断器状态、限流计数 |
| Redis | 连接数、命中率 |
| PostgreSQL | 连接数、查询性能 |

**常用查询：**
```promql
# HTTP 请求 QPS
sum(rate(http_server_requests_seconds_count[5m]))

# P95 响应时间
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))

# JVM 堆内存使用
jvm_memory_used_bytes{area="heap"}
```

---

### Grafana 可视化

**访问地址：** http://localhost:3001

**默认账号：** `admin` / `REDACTED_ADMIN_PASSWORD`

**预置仪表板：**
- LangChain4j Dashboard - 应用概览
- JVM Dashboard - JVM 监控
- Redis Dashboard - Redis 监控
- PostgreSQL Dashboard - 数据库监控

**添加数据源：**
数据源已自动配置，包括：
- Prometheus（默认）
- Redis
- PostgreSQL
- Zipkin

---

### Adminer 数据库管理

**访问地址：** http://localhost:8080

**支持的数据库：**

| 数据库 | 类型 | 用途 | 连接信息 |
|--------|------|------|----------|
| PostgreSQL | 主数据库 | 业务数据 | Server: `postgres`, User: `langchain4j`, Password: `REDACTED_DB_PASSWORD` |
| MySQL | 辅助数据库 | Nacos 元数据 | Server: `nacos-db`, User: `nacos`, Password: `REDACTED_NACOS_PASSWORD` |

**连接 PostgreSQL（业务数据）：**
```
System: PostgreSQL
Server: postgres
Username: langchain4j
Password: REDACTED_DB_PASSWORD
Database: langchain4j
```

**连接 MySQL（Nacos 元数据）：**
```
System: MySQL
Server: nacos-db
Username: nacos
Password: REDACTED_NACOS_PASSWORD
Database: nacos
```

---

### Redis Commander

**访问地址：** http://localhost:8081

**功能：**
- 可视化查看 Redis 数据
- 执行 Redis 命令
- 管理键值对

---

## 可选服务

### Ollama 本地模型

**启动（Linux / macOS）：**
```bash
# CPU 版本
./dev.sh start --profile cpu

# GPU 版本（需要 NVIDIA GPU）
./dev.sh start --profile gpu
```

**启动（Windows PowerShell）：**
```powershell
# CPU 版本
docker compose -f docker-compose.dev.yml --profile cpu up -d

# GPU 版本（需要 NVIDIA GPU + NVIDIA Container Toolkit）
docker compose -f docker-compose.dev.yml --profile gpu up -d
```

**访问地址：** `http://localhost:11434`

**拉取模型：**
```bash
docker exec -it langchain4j-ollama ollama pull qwen2.5:7b
```

**API 调用：**
```bash
curl http://localhost:11434/api/generate -d '{
  "model": "qwen2.5:7b",
  "prompt": "Hello"
}'
```

---

### Qdrant 向量数据库

**启动（Linux / macOS）：**
```bash
./dev.sh start --profile vector
```

**启动（Windows PowerShell）：**
```powershell
docker compose -f docker-compose.dev.yml --profile vector up -d
```

**访问地址：**
- REST API: http://localhost:6333
- Dashboard: http://localhost:6333/dashboard

**使用示例：**
```bash
# 创建集合
curl -X PUT http://localhost:6333/collections/documents \
  -H 'Content-Type: application/json' \
  -d '{"vectors": {"size": 1536, "distance": "Cosine"}}'
```

---

### RabbitMQ 消息队列

**启动（Linux / macOS）：**
```bash
./dev.sh start --profile mq
```

**启动（Windows PowerShell）：**
```powershell
docker compose -f docker-compose.dev.yml --profile mq up -d
```

**访问地址：**
- AMQP: `localhost:5672`
- Management UI: http://localhost:15672

**默认账号：** `admin` / `REDACTED_ADMIN_PASSWORD`

---

### Milvus 分布式向量库

**启动（Linux / macOS）：**
```bash
./dev.sh start --profile milvus
```

**启动（Windows PowerShell）：**
```powershell
docker compose -f docker-compose.dev.yml --profile milvus up -d
```

**访问地址：**
- gRPC: `localhost:19530`
- HTTP: `localhost:9091`

---

## 配置说明

### 环境变量

创建 `.env` 文件（参考 `.env.example`）：

```properties
# ==================== AI 模型 API Keys ====================
# 必填：阿里云 DashScope
DASHSCOPE_API_KEY=your-api-key

# 可选：备用模型
ZHIPU_API_KEY=your-api-key
DEEPSEEK_API_KEY=your-api-key
SILICONFLOW_API_KEY=your-api-key

# ==================== 数据库 ====================
POSTGRES_USER=langchain4j
POSTGRES_PASSWORD=REDACTED_DB_PASSWORD

# ==================== JWT ====================
JWT_SECRET=your-256-bit-secret-key-here

# ==================== 监控 ====================
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=REDACTED_ADMIN_PASSWORD
```

### 应用配置文件

| 文件 | 说明 |
|------|------|
| `application.properties` | 主配置文件 |
| `application-dev.properties` | 开发环境配置 |
| `application-prod.properties` | 生产环境配置 |

### 切换环境

```bash
# 开发环境（默认）
mvn spring-boot:run

# 生产环境
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

---

## 常用命令

### Docker 管理

**Linux / macOS：**

```bash
# 查看服务状态
docker compose -f docker-compose.dev.yml ps

# 查看日志
docker compose -f docker-compose.dev.yml logs -f [服务名]

# 停止所有服务
docker compose -f docker-compose.dev.yml down

# 重启服务
docker compose -f docker-compose.dev.yml restart [服务名]

# 重置环境（删除所有数据）
docker compose -f docker-compose.dev.yml down -v
rm -rf data/
```

**Windows（PowerShell）：**

```powershell
# 查看服务状态
docker compose -f docker-compose.dev.yml ps

# 查看日志
docker compose -f docker-compose.dev.yml logs -f [服务名]

# 停止所有服务
docker compose -f docker-compose.dev.yml down

# 重启服务
docker compose -f docker-compose.dev.yml restart [服务名]

# 重置环境（删除所有数据）
docker compose -f docker-compose.dev.yml down -v
Remove-Item -Recurse -Force .\data
```

### 使用启动脚本（仅 Linux / macOS）

```bash
./dev.sh start          # 启动服务
./dev.sh stop           # 停止服务
./dev.sh restart        # 重启服务
./dev.sh status         # 查看状态
./dev.sh logs redis     # 查看 Redis 日志
./dev.sh reset          # 重置环境
```

> Windows 用户请直接使用 `docker compose` 命令，参考上方「Docker 管理」章节。

### 使用 Makefile

```bash
make dev-docker         # 启动 Docker 基础设施
make run                # 启动后端应用
make dev                # 启动完整开发环境
make logs               # 查看日志
make db-psql            # 连接 PostgreSQL
make db-redis           # 连接 Redis
```

---

## 常见问题

### Q: 服务启动失败？

检查 Docker 是否运行：
```bash
docker info
```

查看具体服务日志：
```bash
docker compose -f docker-compose.dev.yml logs [服务名]
```

### Q: 端口被占用？

检查端口占用：
```bash
# Linux / macOS
lsof -i :8082

# Windows（PowerShell）
netstat -ano | findstr :8082
```

修改端口（在 `.env` 中）：
```properties
SERVER_PORT=8083
```

### Q: 数据库连接失败？

等待 PostgreSQL 完全启动：
```bash
docker compose -f docker-compose.dev.yml ps postgres
```

确保状态为 `healthy`。

### Q: Nacos 启动慢？

Nacos 依赖 MySQL，需要等待 MySQL 启动完成后才会开始初始化。通常需要 30-60 秒。

### Q: 如何导入已有的数据？

数据目录 `data/` 包含所有持久化数据，复制整个目录到新项目即可恢复数据。

---

## 开发工作流

### 推荐的启动顺序

```
1. 启动 Docker 基础设施
   ├─ Linux/macOS:  ./dev.sh start
   └─ Windows:      docker compose -f docker-compose.dev.yml up -d

2. 等待服务就绪（约 30 秒）
   └─> Nacos 需要 MySQL 先启动

3. 启动后端应用
   └─> mvn spring-boot:run

4. 启动前端（可选）
   └─> cd frontend && npm run dev
```

### 端口使用总览

| 端口 | 服务 | 协议 | 说明 |
|------|------|------|------|
| 5173 | 前端 | HTTP | Vue 3 开发服务器 |
| 3001 | Grafana | HTTP | 监控面板 |
| 3307 | MySQL | TCP | Nacos 元数据库 |
| 5432 | PostgreSQL | TCP | 应用业务数据库 |
| 6379 | Redis | TCP | 缓存/限流 |
| 8080 | Adminer | HTTP | 数据库管理 |
| 8081 | Redis Commander | HTTP | Redis 管理 |
| 8082 | 后端 API | HTTP | Spring Boot 应用 |
| 8848 | Nacos | HTTP | 配置中心 |
| 9090 | Prometheus | HTTP | 指标收集 |
| 9121 | Redis Exporter | HTTP | Redis 指标导出 |
| 9187 | Postgres Exporter | HTTP | PostgreSQL 指标导出 |
| 9411 | Zipkin | HTTP | 链路追踪 |
| 11434 | Ollama | HTTP | 本地大模型 |
| 15672 | RabbitMQ Management | HTTP | 消息队列管理 |
| 19530 | Milvus | gRPC | 分布式向量库 |
| 6333 | Qdrant | HTTP | 向量数据库 |

---

## 附录

### 项目结构

```
langchain4j-demo/
├── data/                      # Docker 数据卷（本地持久化）
│   ├── postgres/              # PostgreSQL 数据（业务数据）
│   ├── redis/                 # Redis 数据
│   ├── nacos/                 # Nacos 日志
│   ├── nacos-db/              # Nacos MySQL 数据
│   ├── prometheus/            # Prometheus 数据
│   ├── grafana/               # Grafana 配置
│   └── ...
├── docs/                      # 文档目录
│   └── QUICK_START.md         # 本文档
├── frontend/                  # 前端项目
├── infra/                     # 基础设施配置
│   ├── postgres/init/         # PostgreSQL 初始化脚本
│   ├── nacos/init/            # Nacos 初始化 SQL（含预置配置）
│   ├── nacos/config/          # Nacos 配置文件模板
│   ├── mysql/conf/            # MySQL 配置（仅 Nacos 使用）
│   ├── prometheus/            # Prometheus 配置
│   └── grafana/               # Grafana 配置
├── src/main/java/             # Java 源代码
├── src/main/resources/        # 配置文件
│   ├── application.properties # 本地配置（仅端口）
│   ├── bootstrap.yml          # Nacos 连接配置
│   └── schema.sql             # 数据库 Schema（Spring Boot 自动执行）
├── docker-compose.dev.yml     # 开发环境编排
├── docker-compose.yml         # 生产环境编排
├── dev.sh                     # 快速启动脚本
├── Makefile                   # 便捷命令
├── .env.example               # 环境变量模板
└── README.md                  # 项目说明
```

### 相关文档

- [项目架构说明](../CLAUDE.md)
- [代码模式指南](../.claude/patterns.md)
- [调试指南](../.claude/debugging.md)

---

**最后更新：** 2026-04
