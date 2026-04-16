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
npm run preview              # 预览生产构建
```

### Docker 部署
```bash
docker compose up -d         # 启动所有服务
docker compose logs -f       # 查看日志
docker compose down          # 停止服务
docker compose up -d --build # 重新构建并启动
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
├── style.css                    # 全局样式，Markdown 渲染样式
├── components/
│   ├── ChatInput.vue            # 消息输入组件，处理中文输入法组合事件
│   ├── MessageItem.vue          # 消息渲染组件（核心组件）
│   ├── MessageList.vue          # 消息列表容器，自动滚动到底部
│   └── Sidebar.vue              # 侧边栏，显示对话历史列表
└── views/ChatView.vue           # 主页面布局
```

**MessageItem.vue 核心功能：**
- Markdown 渲染（markdown-it）
- 代码块增强：语法高亮（highlight.js）、复制、编辑、主题切换、折叠
- 思考过程解析与折叠展示
- 消息操作：点赞/点踩、复制、重新生成

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
- `management.tracing.sampling.probability` - 链路追踪采样率

**前端配置：** `frontend/vite.config.ts`
- 开发服务器端口 3000
- 代理 `/api` 请求到后端
- 路径别名 `@` 指向 `src` 目录

**Tailwind 配置：** `frontend/tailwind.config.js`
- `corePlugins.preflight: false` - 禁用 CSS reset，避免移除列表样式

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.4.1, LangChain4j 1.13.0, WebFlux |
| 前端 | Vue 3, Vite, Pinia, Element Plus, TypeScript |
| Markdown | markdown-it, highlight.js |
| 样式 | Tailwind CSS, Scoped CSS |
| AI 模型 | 阿里云 DashScope（qwen-plus）通过 OpenAI 兼容 API |
| 流式传输 | WebFlux + SSE |
| 部署 | Docker, Docker Compose, Nginx |

## 关键技术点

### 1. Vue 响应式更新
由于 computed 对嵌套属性变化不敏感，使用独立的 `currentMessages` ref 追踪消息列表：
```ts
// 每次更新时创建新数组触发响应
currentMessages.value = [...conversation.messages]
```

### 2. scoped 样式与 v-html
scoped 样式无法作用于 `v-html` 渲染的内容，需要添加非 scoped 的 `<style>` 块：
```vue
<style scoped>
  /* 组件内部样式 */
</style>

<style>
  /* v-html 内容样式，使用更具体的选择器 */
  .message-assistant .message-content .markdown-body { ... }
</style>
```

### 3. Tailwind preflight 问题
Tailwind CSS 的 preflight 会移除列表默认样式，需要禁用：
```js
// tailwind.config.js
corePlugins: {
  preflight: false
}
```

### 4. 事件代理
动态生成的代码块按钮无法直接绑定事件，使用事件代理：
```ts
// 在父元素监听点击，通过 event.target 判断来源
<div @click="handleCodeAction">
  <button data-action="copy">复制</button>
</div>
```

### 5. SSE 解析
SSE 响应可能跨多个 chunk，需要缓冲处理：
```ts
buffer += decoder.decode(value, { stream: true })
const lines = buffer.split('\n')
buffer = lines.pop() || '' // 保留未完整的行
```

### 6. 日期解析
从 localStorage 加载的数据需要将日期字符串转换为 Date 对象：
```ts
return parsed.map(conv => ({
  ...conv,
  createdAt: new Date(conv.createdAt)
}))
```

### 7. Element Plus 图标
需要在 main.ts 中注册 `@element-plus/icons-vue` 图标组件：
```ts
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
```

## 模型配置参数

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| temperature | 创造性（0-2） | 0.7（对话）/ 0（代码） |
| maxTokens | 最大输出长度 | 4096 |
| modelName | 模型名称 | qwen-plus |

## 常见问题

### Q: 代码块没有语法高亮？
A: 检查 highlight.js 是否正确导入语言包，确保 highlight 回调返回完整 HTML。

### Q: 列表样式不显示？
A: 确保禁用了 Tailwind preflight，或在 CSS 中使用 `list-style-type: disc !important`。

### Q: 流式响应中断？
A: 检查后端连接是否超时，确保 `Flux` 正确完成并释放资源。

### Q: 对话记忆丢失？
A: 检查 `MessageWindowChatMemory` 配置，确保消息窗口大小足够（默认 10 条）。

## 扩展指南

项目提供更详细的指南文件，帮助高效开发：

| 文件 | 内容 |
|------|------|
| [.claude/guide.md](./.claude/guide.md) | Claude Code 使用方法、协作最佳实践 |
| [.claude/patterns.md](./.claude/patterns.md) | 项目特有代码模式和模板 |
| [.claude/debugging.md](./.claude/debugging.md) | 常见问题诊断与解决方案 |

## 学习路径

1. **入门** - 阅读本文档了解项目结构
2. **进阶** - 学习 [.claude/guide.md](./.claude/guide.md) 掌握 Claude 协作技巧
3. **实践** - 参考 [.claude/patterns.md](./.claude/patterns.md) 使用代码模板
4. **排错** - 遇到问题查阅 [.claude/debugging.md](./.claude/debugging.md)
