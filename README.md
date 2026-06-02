# LangChain4j AI Assistant

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/)
[![Vue](https://img.shields.io/badge/Vue-3-brightgreen.svg)](https://vuejs.org/)
[![GitHub Stars](https://img.shields.io/github/stars/jonykchen/langchain4j-ai-assistant?style=social)](https://github.com/jonykchen/langchain4j-ai-assistant/stargazers)
[![GitHub Issues](https://img.shields.io/github/issues/jonykchen/langchain4j-ai-assistant)](https://github.com/jonykchen/langchain4j-ai-assistant/issues)
[![GitHub Forks](https://img.shields.io/github/forks/jonykchen/langchain4j-ai-assistant?style=social)](https://github.com/jonykchen/langchain4j-ai-assistant/network/members)

一个生产级 AI 助手平台，基于 LangChain4j + Spring Boot + Vue 3 构建。支持多模型负载均衡、故障自动转移、完整可观测性链路。

[English](#english) | [简体中文](#简体中文)

> 📖 **[快速开始指南](./docs/QUICK_START.md)** - 完整的启动文档和服务访问地址

## 项目截图

| 聊天界面 | 管理后台 |
|:---:|:---:|
| ![聊天界面](docs/screenshots/chat.png) | ![管理后台](docs/screenshots/admin.png) |

| 模型监控 | Agent 追踪 |
|:---:|:---:|
| ![模型监控](docs/screenshots/monitoring.png) | ![Agent追踪](docs/screenshots/tracing.png) |

> 📸 截图即将更新

## 核心特性

### AI 能力
- **多模型负载均衡** - 权重分配 + 故障转移
- **流式响应** - SSE 实时输出
- **思考过程展示** - AI 推理可视化
- **上下文记忆** - 滑动窗口策略

### Agent 工程
- **工具调用** - Function Calling + 审计日志
- **RAG 检索** - 文档分块 + 向量检索（pgvector）
- **Planning Agent** - ReAct / Plan-Execute 模式

### 工程化
- **高可用架构** - 熔断 + 限流 + 重试
- **分布式限流** - Redis + Lua 脚本
- **完整可观测性** - Prometheus + Grafana + Zipkin
- **配置中心** - Nacos 动态配置
- **一键启动** - Docker Compose 编排

## 数据架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         数据存储分层架构                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌───────────────────────────────┐  ┌───────────────────────────┐  │
│  │  MySQL (仅 Nacos 元数据)       │  │  PostgreSQL (应用业务)    │  │
│  │  端口: 3307 (dev)              │  │  端口: 5432               │  │
│  │  ┌─────────────────────────┐   │  │  ┌─────────────────────┐  │  │
│  │  │ nacos.config_info       │   │  │  │ users               │  │  │
│  │  │ nacos.tenant_info       │   │  │  │ token_usage_logs   │  │  │
│  │  │ nacos.users             │   │  │  │ tool_execution_... │  │  │
│  │  └─────────────────────────┘   │  │  │ documents          │  │  │
│  └───────────────────────────────┘  │  │  │ document_chunks    │  │  │
│                                      │  │  │ (pgvector 1536维)  │  │  │
│  ┌───────────────────────────────┐  │  │  │ conversations      │  │  │
│  │  Redis (热数据/缓存)           │  │  │  │ messages           │  │  │
│  │  端口: 6379                    │  │  │  │ api_keys           │  │  │
│  │  会话 │ 限流 │ Token 缓存      │  │  └─────────────────────┘  │  │
│  └───────────────────────────────┘  └───────────────────────────┘  │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │  Nacos 配置中心 (端口: 8848)                                    │  │
│  │  langchain4j-chat.properties (公共)                            │  │
│  │  langchain4j-chat-dev.properties / langchain4j-chat-prod.properties │
│  │  common.properties (跨应用共享)                                  │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.4, LangChain4j 1.13, WebFlux |
| 前端 | Vue 3, Vite, Pinia, Element Plus, TypeScript |
| AI 模型 | DashScope, 智谱, DeepSeek, 硅基流动, Ollama |
| 主数据库 | PostgreSQL 16 + pgvector |
| 配置数据库 | MySQL 8.0 (仅 Nacos) |
| 缓存/限流 | Redis 7 |
| 可观测性 | Prometheus, Grafana, Zipkin |
| 配置中心 | Nacos |
| 容错 | Resilience4j（熔断、限流、重试） |
| 部署 | Docker, Docker Compose |

## 项目结构

```
langchain4j-ai-assistant/
├── src/main/java/com/jonychen/    # 后端代码
│   ├── assistant/                 # AI 助手接口
│   ├── config/                    # 配置类
│   ├── controller/                # REST 控制器
│   ├── model/                     # 数据模型
│   ├── service/                   # 业务服务
│   ├── planning/                  # Agent 规划模块
│   ├── rag/                       # RAG 检索增强
│   ├── tool/                      # 工具调用
│   └── ratelimit/                 # 分布式限流
├── frontend/                      # Vue 3 前端
├── docs/                          # 文档
│   └── QUICK_START.md             # 快速开始指南
├── infra/                         # 基础设施配置
│   ├── postgres/init/             # PostgreSQL 初始化（业务数据）
│   ├── nacos/init/                # Nacos 初始化（配置预置）
│   ├── nacos/config/              # Nacos 配置文件模板
│   ├── mysql/conf/                # MySQL 配置（仅 Nacos）
│   ├── prometheus/                # Prometheus 配置
│   └── grafana/                   # Grafana 配置
├── data/                          # 本地数据（gitignore）
├── docker-compose.dev.yml         # 开发环境编排
├── docker-compose.yml             # 生产环境编排
├── dev.sh                         # 快速启动脚本
├── Makefile                       # 便捷命令
├── .env.example                   # 环境变量模板
└── CLAUDE.md                      # 开发指南
```

## 快速开始

> 详细文档请查看 **[快速开始指南](./docs/QUICK_START.md)**

### 1. 环境准备

```bash
# 克隆项目
git clone https://github.com/jonykchen/langchain4j-ai-assistant.git
cd langchain4j-ai-assistant

# 配置环境变量
cp .env.example .env
# 编辑 .env 填入 DASHSCOPE_API_KEY
```

### 2. 启动基础设施

**Linux / macOS：**

```bash
# 一键启动
./dev.sh start

# 或使用 Makefile
make dev-docker
```

**Windows（PowerShell）：**

```powershell
# 首次使用：创建 .env 文件和数据目录
copy .env.example .env
New-Item -ItemType Directory -Force -Path data/postgres,data/redis,data/nacos,data/nacos-db,data/prometheus,data/grafana

# 启动基础设施
docker compose -f docker-compose.dev.yml up -d

# 停止所有服务
docker compose -f docker-compose.dev.yml down
```

### 3. 启动应用

```bash
# 启动后端
mvn spring-boot:run

# 启动前端（可选）
cd frontend && npm run dev
```

### 服务访问

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端应用 | http://localhost:5173 | Vue 3 前端 |
| 后端 API | http://localhost:8082 | Spring Boot |
| PostgreSQL | localhost:5432 | 业务数据库 |
| MySQL | localhost:3307 | Nacos 数据库 |
| Redis | localhost:6379 | 缓存/限流 |
| Nacos | http://localhost:8848/nacos | 配置中心 |
| Grafana | http://localhost:3001 | 监控面板 |
| Zipkin | http://localhost:9411 | 链路追踪 |

## 开发指南

- **[快速开始指南](./docs/QUICK_START.md)** - 环境搭建、服务地址、配置说明
- **[CLAUDE.md](./CLAUDE.md)** - 架构说明、关键技术点、常见问题

### 核心配置

应用配置已迁移至 Nacos，本地仅保留服务端口：

| 配置位置 | 说明 |
|----------|------|
| `infra/nacos/config/` | Nacos 配置模板（首次部署需导入） |
| `src/main/resources/application.properties` | 仅 server.port |
| `src/main/resources/bootstrap.yml` | Nacos 连接配置 |

### 数据库初始化

| 数据库 | 初始化脚本 |
|--------|-----------|
| PostgreSQL | `infra/postgres/init/01-init.sql` |
| MySQL (Nacos) | `infra/nacos/init/01-nacos-init.sql` |

## 贡献指南

欢迎贡献代码、报告问题或提出建议！请查看 [CONTRIBUTING.md](./CONTRIBUTING.md) 了解详情。

### 如何贡献
- 🍴 Fork 项目并创建 PR
- 🐛 [报告 Bug](https://github.com/jonykchen/langchain4j-ai-assistant/issues/new?template=bug_report.md)
- 💡 [建议新功能](https://github.com/jonykchen/langchain4j-ai-assistant/issues/new?template=feature_request.md)
- 📖 改进文档

## 🗺️ 路线图

### v1.1 (计划中)
- [ ] 多租户支持
- [ ] 更多 AI 模型接入（Claude, GPT-4）
- [ ] Agent 工作流可视化编辑器
- [ ] 移动端适配

### v1.0 (当前版本)
- [x] 多模型负载均衡
- [x] Agent 可观测性
- [x] RAG 文档检索
- [x] 完整测试体系

## 🤝 贡献者

感谢所有为项目做出贡献的开发者！

[![Contributors](https://contrib.rocks/image?repo=jonykchen/langchain4j-ai-assistant)](https://github.com/jonykchen/langchain4j-ai-assistant/graphs/contributors)

## 📊 项目统计

![Repo Size](https://img.shields.io/github/repo-size/jonykchen/langchain4j-ai-assistant)
![Commit Activity](https://img.shields.io/github/commit-activity/m/jonykchen/langchain4j-ai-assistant)
![Last Commit](https://img.shields.io/github/last-commit/jonykchen/langchain4j-ai-assistant)

## 📄 许可证

本项目采用 [MIT License](LICENSE) 开源协议。

## 🙏 致谢

- [LangChain4j](https://github.com/langchain4j/langchain4j) - Java AI 开发框架
- [Spring Boot](https://spring.io/projects/spring-boot) - 后端框架
- [Vue.js](https://vuejs.org/) - 前端框架
- [Element Plus](https://element-plus.org/) - UI 组件库

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/jonykchen">Jony Chen</a>
</p>

<p align="center">
  <a href="https://github.com/jonykchen/langchain4j-ai-assistant">⬆️ 返回顶部</a>
</p>

---

## English

A production-ready AI assistant platform built with LangChain4j + Spring Boot + Vue 3. Features multi-model load balancing, automatic failover, and complete observability.

### Key Features
- **Multi-model Load Balancing** - Weight-based distribution with automatic failover
- **Streaming Response** - SSE real-time output
- **Agent Observability** - Execution tracing, prompt management, evaluation
- **RAG Support** - Document chunking + vector search with pgvector
- **High Availability** - Circuit breaker + rate limiting + retry

### Quick Start
```bash
git clone https://github.com/jonykchen/langchain4j-ai-assistant.git
cd langchain4j-ai-assistant
cp .env.example .env
# Edit .env and add your API key
./dev.sh start  # Linux/macOS
mvn spring-boot:run
```

See [Quick Start Guide](./docs/QUICK_START.md) for detailed instructions.

### License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
