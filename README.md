# LangChain4j AI 聊天应用

一个基于 LangChain4j 和 Vue 3 构建的 AI 聊天应用，后端连接阿里云 DashScope（通义千问模型）。

## 功能特性

- 支持 AI 对话，具备上下文记忆能力
- 支持流式响应，实时显示 AI 回复
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
└── pom.xml                        # Maven 配置
```

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- Maven 3.6+

### 配置

1. 在 `src/main/resources/application.properties` 中配置 DashScope API Key：

```properties
langchain4j.open-ai.chat-model.api-key=your-api-key-here
```

### 启动后端

```bash
mvn spring-boot:run
```

后端服务将在 http://localhost:8082 启动。

### 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器将在 http://localhost:3000 启动。

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