# LangChain4j AI 聊天应用

一个基于 LangChain4j 和 Vue 3 构建的 AI 聊天应用，后端连接阿里云 DashScope（通义千问模型）。

## 功能特性

- 支持 AI 对话，具备上下文记忆能力
- 支持流式响应，实时显示 AI 回复
- **思考过程展示** - AI 在回答前展示推理过程，支持折叠展开
- 对话历史本地持久化
- 支持 Markdown 渲染和代码高亮
- 响应式界面设计

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.4.1, LangChain4j 1.13.0 |
| 前端 | Vue 3, Vite, Pinia, Element Plus, TypeScript |
| AI 模型 | 阿里云 DashScope（qwen-plus） |
| 流式传输 | WebFlux + SSE |
| 部署 | Docker, Docker Compose, Nginx |
| 可观测性 | Zipkin（分布式链路追踪） |

## 项目结构

```
langchain4j-demo/
├── src/main/java/com/jonychen/    # 后端代码
│   ├── AiApplication.java         # 启动类
│   ├── assistant/                 # AI 接口定义
│   ├── config/                    # 配置类
│   ├── controller/                # REST 控制器
│   ├── model/                     # 数据模型
│   └── service/                   # 业务服务
├── frontend/                      # 前端代码
│   └── src/
│       ├── api/                   # API 调用
│       ├── components/            # Vue 组件
│       ├── stores/                # Pinia 状态管理
│       ├── types/                 # TypeScript 类型
│       └── views/                 # 页面视图
├── docker-compose.yml             # Docker Compose 编排
├── backend.Dockerfile             # 后端镜像构建
├── frontend.Dockerfile            # 前端镜像构建
└── pom.xml                        # Maven 配置
```

## 快速开始

### 方式一：Docker 部署（推荐）

#### 环境要求

- Docker & Docker Compose

#### 启动服务

```bash
# 启动所有服务（后端、前端、Zipkin 追踪）
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f

# 停止服务
docker compose down

# 重新构建并启动
docker compose up -d --build
```

#### 服务地址

| 服务 | 地址 |
|------|------|
| 前端应用 | http://localhost:3000 |
| 后端 API | http://localhost:8082 |
| Zipkin 追踪 | http://localhost:9411 |

### 方式二：本地开发

#### 环境要求

- JDK 17+
- Node.js 18+
- Maven 3.6+

#### 配置

1. 在 `src/main/resources/application.properties` 中配置 DashScope API Key：

```properties
langchain4j.open-ai.chat-model.api-key=your-api-key-here
```

#### 启动后端

```bash
mvn spring-boot:run
```

后端服务将在 http://localhost:8082 启动。

#### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器将在 http://localhost:3000 启动。

## 功能说明

### 思考过程展示

AI 在回答问题前会先展示思考过程（用 `<thinking>` 标签包裹），用户可以：

- 点击「思考过程」区域展开/折叠查看
- 流式响应时显示「思考中...」加载状态
- 思考完成后自动显示最终回答

示例输出：
```
<thinking>
用户问的是关于 Vue 响应式的问题...
首先需要分析 computed 和 ref 的区别...
</thinking>

这是最终的回答内容...
```

### 对话记忆

系统自动保存最近 10 条消息的上下文，AI 能够理解之前的对话内容。

### 本地持久化

对话历史自动保存到浏览器 localStorage，刷新页面不会丢失。

### 分布式链路追踪

项目集成了 Zipkin 用于链路追踪，可观测请求的完整调用链路：

- 访问 http://localhost:9411 打开 Zipkin UI
- 查看每个请求的耗时、调用关系等信息
- 用于排查性能问题和调试

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/chat | 同步聊天接口 |
| POST | /api/chat/stream | SSE 流式聊天接口 |

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

## 开发指南

详细的项目结构和开发指南请参考 [CLAUDE.md](./CLAUDE.md)。

## 许可证

MIT License
