# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供代码仓库的工作指导。

## 常用命令

### 后端 (Java/Spring Boot)
```bash
mvn spring-boot:run          # 启动后端服务，默认端口 8082
mvn clean package            # 构建项目
mvn test                     # 运行所有测试
mvn test -Dtest=ClassName    # 运行单个测试类
```

### 前端 (Vue 3/Vite)
```bash
cd frontend
npm install                  # 安装依赖
npm run dev                  # 启动开发服务器，默认端口 3000
npm run build                # 生产环境构建
```

## 架构说明

本项目是一个基于 LangChain4j 的 AI 聊天应用，前端采用 Vue 3 框架。后端通过 OpenAI 兼容 API 连接阿里云 DashScope（通义千问模型）。

### 后端结构
```
src/main/java/com/jonychen/
├── AiApplication.java           # Spring Boot 启动类
├── assistant/
│   └── ChatAssistant.java       # LangChain4j AI 接口（由 AiServices 自动实现）
├── config/
│   ├── AiConfig.java            # 构建 ChatAssistant Bean，配置对话记忆
│   └── CorsConfig.java          # CORS 跨域过滤器，处理 /api/**
├── controller/
│   └── ChatController.java      # REST 接口：POST /api/chat, POST /api/chat/stream
├── model/
│   ├── ChatRequest.java         # 请求 DTO（record 类型）
│   └── ChatResponse.java        # 响应 DTO（record 类型）
└── service/
    └── AiService.java           # 服务层，封装 ChatAssistant
```

**核心流程：**
- `ChatController` → `AiService` → `ChatAssistant`（LangChain4j AiServices）
- `ChatAssistant` 接口由 LangChain4j 的 `AiServices.builder()` 自动实现
- 使用 `MessageWindowChatMemory` 实现 10 条消息的滑动窗口记忆
- 通过 WebFlux SSE 实现 `Flux<String>` 流式响应

**思考过程输出：**
- 系统提示词要求 AI 用 `<thinking></thinking>` 标签包裹思考过程
- 思考过程在回答内容之前输出
- 前端解析标签并显示为可折叠区域

### 前端结构
```
frontend/src/
├── api/chat.ts                  # API 调用：sendMessage(), streamMessage()
├── stores/chat.ts               # Pinia 状态管理，支持 localStorage 持久化
├── types/index.ts               # TypeScript 类型定义
├── main.ts                      # 入口文件，注册 Element Plus 和图标
├── components/
│   ├── ChatInput.vue            # 消息输入组件，处理中文输入法组合事件
│   ├── MessageItem.vue          # 消息渲染组件，支持 Markdown、代码高亮、思考过程折叠
│   ├── MessageList.vue          # 消息列表容器，自动滚动到底部
│   └── Sidebar.vue              # 侧边栏，显示对话历史列表
└── views/ChatView.vue           # 主页面布局
```

**关键特性：**
- Vite 代理将 `/api/*` 请求转发到 `localhost:8082`
- `streamMessage()` 使用 async generator 解析 SSE 响应
- 对话历史自动持久化到 localStorage
- `currentMessages` ref 用于实时更新消息列表（解决 Vue 响应式问题）

**思考过程解析（MessageItem.vue）：**
- 解析 `<thinking>` 标签，提取思考内容
- 未完成时显示「思考中...」加载动画
- 点击可展开/折叠思考过程区域

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/chat | 同步聊天，返回 `{ reply: string }` |
| POST | /api/chat/stream | SSE 流式响应，逐字输出，最后发送 `[DONE]` |

## 配置说明

**后端配置：** `src/main/resources/application.properties`
- `langchain4j.open-ai.chat-model.*` - DashScope OpenAI 兼容 API 配置（qwen-plus 模型）
- `langchain4j.open-ai.streaming-chat-model.*` - 流式模型配置
- `server.port=8082` - 服务端口

**前端配置：** `frontend/vite.config.ts`
- 开发服务器端口 3000
- 代理 `/api` 请求到后端

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.4.1, LangChain4j 1.13.0 |
| 前端 | Vue 3, Vite, Pinia, Element Plus, TypeScript, markdown-it, highlight.js |
| AI 模型 | 阿里云 DashScope（qwen-plus）通过 OpenAI 兼容 API |
| 流式传输 | WebFlux + SSE |

## 注意事项

1. **Vue 响应式更新**：由于 computed 对嵌套属性变化不敏感，使用独立的 `currentMessages` ref 追踪消息列表，每次更新时创建新数组触发响应。

2. **日期解析**：从 localStorage 加载的数据需要将日期字符串转换为 Date 对象。

3. **SSE 解析**：不要使用 trim() 处理 data 内容，会丢失空白字符。

4. **Element Plus 图标**：需要在 main.ts 中注册 `@element-plus/icons-vue` 图标组件。
