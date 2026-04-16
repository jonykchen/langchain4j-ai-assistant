# LangChain4j AI 聊天应用

一个基于 LangChain4j 和 Vue 3 构建的 AI 聊天应用，专为 Java 开发者学习 AI Agent 开发设计。后端连接阿里云 DashScope（通义千问模型），支持流式响应和思考过程展示。

## 功能特性

- AI 对话，具备上下文记忆能力（滑动窗口 10 条）
- 流式响应，实时逐字显示 AI 回复
- **思考过程展示** - AI 推理过程可视化，支持折叠展开
- **代码块增强** - 语法高亮、复制、编辑、深色/浅色主题切换、折叠
- Markdown 渲染（标题、列表、表格、引用、代码块）
- 对话历史本地持久化（localStorage）
- 多对话管理（创建、切换、重命名、删除）
- 消息操作（点赞/点踩、复制、重新生成）
- Docker 容器化部署
- 分布式链路追踪（Zipkin）

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端 | Java, Spring Boot, LangChain4j, WebFlux | 17, 3.4.1, 1.13.0 |
| 前端 | Vue 3, Vite, Pinia, Element Plus, TypeScript | - |
| Markdown | markdown-it, highlight.js | - |
| 样式 | Tailwind CSS（preflight 禁用） | - |
| AI 模型 | 阿里云 DashScope（qwen-plus） | OpenAI 兼容 API |
| 流式传输 | Server-Sent Events (SSE) | WebFlux |
| 部署 | Docker, Docker Compose, Nginx | - |
| 可观测性 | Zipkin（分布式链路追踪） | - |

## 项目结构

```
langchain4j-demo/
├── src/main/java/com/jonychen/    # 后端代码
│   ├── AiApplication.java         # Spring Boot 启动类
│   ├── assistant/
│   │   └── ChatAssistant.java     # LangChain4j AI 接口（动态代理实现）
│   ├── config/
│   │   ├── AiConfig.java          # AI 配置（ChatMemory、AiServices）
│   │   └── CorsConfig.java        # CORS 跨域配置
│   ├── controller/
│   │   └── ChatController.java    # REST 接口（同步/流式）
│   ├── model/
│   │   ├── ChatRequest.java       # 请求 DTO（record）
│   │   └── ChatResponse.java      # 响应 DTO（record）
│   └── service/
│       └── AiService.java         # 服务层封装
├── frontend/                      # 前端代码
│   └── src/
│       ├── api/chat.ts            # API 调用（SSE 解析）
│       ├── components/
│       │   ├── ChatInput.vue      # 消息输入（中文输入法处理）
│       │   ├── MessageItem.vue    # 消息渲染（Markdown、代码块）
│       │   ├── MessageList.vue    # 消息列表（自动滚动）
│       │   └── Sidebar.vue        # 对话历史侧边栏
│       ├── stores/chat.ts         # Pinia 状态管理
│       ├── types/index.ts         # TypeScript 类型定义
│       └── views/ChatView.vue     # 主页面
├── docker-compose.yml             # Docker Compose 编排
├── backend.Dockerfile             # 后端镜像
├── frontend.Dockerfile            # 前端镜像
├── CLAUDE.md                      # 开发指南
└── README.md                      # 项目说明
```

## 快速开始

### 方式一：Docker 部署（推荐）

#### 环境要求
- Docker & Docker Compose

#### 启动服务
```bash
# 启动所有服务（后端、前端、Zipkin）
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

#### 配置 API Key
在 `src/main/resources/application.properties` 中配置：
```properties
langchain4j.open-ai.chat-model.api-key=your-api-key-here
```

获取方式：阿里云控制台 → 模型服务灵积 → API-KEY 管理

#### 启动后端
```bash
mvn spring-boot:run
```
服务地址：http://localhost:8082

#### 启动前端
```bash
cd frontend
npm install
npm run dev
```
服务地址：http://localhost:3000

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

详细的技术文档请参考：
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