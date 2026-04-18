# LangChain4j Agent 工程

一个生产级 AI Agent 开发工程，基于 LangChain4j + Spring Boot + Vue 3 构建。支持多模型负载均衡、故障自动转移、完整可观测性链路。

> 📖 **[快速开始指南](./docs/QUICK_START.md)** - 完整的启动文档和服务访问地址

## 核心特性

### AI 能力
- **多模型负载均衡** - 权重分配 + 故障转移
- **流式响应** - SSE 实时输出
- **思考过程展示** - AI 推理可视化
- **上下文记忆** - 滑动窗口策略

### Agent 工程
- **工具调用** - Function Calling + 审计日志
- **RAG 检索** - 文档分块 + 向量检索
- **Planning Agent** - ReAct / Plan-Execute 模式

### 工程化
- **高可用架构** - 熔断 + 限流 + 重试
- **分布式限流** - Redis + Lua 脚本
- **完整可观测性** - Prometheus + Grafana + Zipkin
- **配置中心** - Nacos 动态配置
- **一键启动** - Docker Compose 编排

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.4, LangChain4j 1.13, WebFlux |
| 前端 | Vue 3, Vite, Pinia, Element Plus, TypeScript |
| AI 模型 | DashScope, 智谱, DeepSeek, 硅基流动, Ollama |
| 数据库 | PostgreSQL, Redis |
| 可观测性 | Prometheus, Grafana, Zipkin |
| 配置中心 | Nacos |
| 容错 | Resilience4j（熔断、限流、重试） |
| 部署 | Docker, Docker Compose |

## 项目结构

```
langchain4j-demo/
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
│   ├── postgres/init/             # 数据库初始化
│   ├── nacos/init/                # Nacos 初始化
│   ├── prometheus/                # Prometheus 配置
│   └── grafana/                   # Grafana 配置
├── data/                          # 本地数据（gitignore）
├── docker-compose.dev.yml         # 开发环境编排
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
git clone <repository-url>
cd langchain4j-demo

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

# 启动基础服务 + 可选服务（如 Ollama CPU 版 + 向量数据库）
# docker compose -f docker-compose.dev.yml --profile cpu --profile vector up -d

# 停止所有服务
docker compose -f docker-compose.dev.yml down

# 查看服务状态
docker compose -f docker-compose.dev.yml ps

# 查看日志
docker compose -f docker-compose.dev.yml logs -f --tail=100

# 查看指定服务日志（如 redis）
docker compose -f docker-compose.dev.yml logs -f --tail=100 redis

# 重置环境（清除所有数据）
docker compose -f docker-compose.dev.yml down -v --remove-orphans
Remove-Item -Recurse -Force .\data
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
| Grafana | http://localhost:3001 | 监控面板 |
| Nacos | http://localhost:8848/nacos | 配置中心 |
| Zipkin | http://localhost:9411 | 链路追踪 |
| Adminer | http://localhost:8080 | 数据库管理 |

## 功能详解

### 思考过程展示
AI 在回答前展示推理过程（`<thinking>` 标签），用户可：
- 点击「思考过程」展开/折叠查看
- 流式响应时显示「思考中...」加载状态
- 完成后自动显示最终回答

### 代码块增强
- **语法高亮** - 支持 Java、JavaScript、Python、SQL 等主流语言
- **复制** - 一键复制代码到剪贴板
- **编辑** - 弹窗编辑代码内容
- **主题切换** - 深色/浅色模式
- **折叠** - 长代码块可折叠

### 对话记忆
- 滑动窗口记忆（MessageWindowChatMemory）
- 保留最近 10 条消息上下文
- 防止超出模型 token 限制

### 本地持久化
- 对话历史保存到 localStorage
- 页面刷新不丢失数据
- 支持多对话管理

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/chat | 同步聊天，返回完整回复 |
| POST | /api/chat/stream | SSE 流式聊天，逐字返回 |

### 请求示例
```bash
# 同步请求
curl -X POST http://localhost:8082/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下自己"}'

# 流式请求
curl -X POST http://localhost:8082/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请介绍一下自己"}'
```

### SSE 响应格式
```
event: token
data: 你

event: token
data: 好

event: done
data: [DONE]
```

## 开发指南

- **[快速开始指南](./docs/QUICK_START.md)** - 环境搭建、服务地址、配置说明
- **[CLAUDE.md](./CLAUDE.md)** - 架构说明、关键技术点、常见问题

### 核心技术要点
1. **Vue 响应式陷阱** - 使用 `currentMessages` ref + 新数组触发更新
2. **scoped 样式限制** - v-html 内容需要非 scoped 样式
3. **Tailwind preflight** - 禁用避免移除列表样式
4. **事件代理** - 动态生成元素的事件处理
5. **SSE 解析** - 缓冲处理跨 chunk 的消息

## 学习资源

本项目代码包含详细注释，适合学习：
- **LangChain4j** - Java AI Agent 开发框架
- **AiServices** - 动态代理模式实现 AI 接口
- **WebFlux** - 响应式编程与流式传输
- **SSE** - Server-Sent Events 协议
- **Pinia** - Vue 3 状态管理
- **Event Delegation** - 事件代理模式

## 许可证

MIT License