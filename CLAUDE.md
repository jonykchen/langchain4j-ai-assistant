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

本项目是一个基于 LangChain4j 的 AI 聊天应用，采用**多模型负载均衡 + 高可用架构**。前端采用 Vue 3 框架。后端支持多个 AI 模型提供者，实现故障自动转移。

### 后端结构
```
src/main/java/com/jonychen/
├── AiApplication.java           # Spring Boot 启动类
├── assistant/
│   ├── ChatAssistant.java       # LangChain4j AI 接口（由 AiServices 自动实现）
│   └── Tools.java               # AI 工具类（Function Calling）
├── config/
│   ├── AiConfig.java            # 构建 ChatAssistant Bean，配置负载均衡模型
│   ├── CorsConfig.java          # CORS 跨域过滤器
│   ├── ModelProperties.java     # 多模型配置属性
│   ├── RedisConfig.java         # Redis 配置（分布式限流）
│   ├── RateLimitProperties.java # 限流配置属性
│   ├── ResilienceConfig.java    # Resilience4j 事件监听配置
│   └── RequestLoggingAspect.java # 请求日志切面
├── controller/
│   ├── ChatController.java      # REST 接口：POST /api/chat, POST /api/chat/stream
│   └── ModelHealthController.java # 健康检查接口
├── model/
│   ├── ChatRequest.java         # 请求 DTO（record 类型）
│   ├── ChatResponse.java        # 响应 DTO（record 类型）
│   ├── ApiResponse.java         # 统一 API 响应格式
│   ├── ErrorCode.java           # 错误码枚举
│   ├── ModelProvider.java       # 模型提供者配置
│   ├── ModelHealthStatus.java   # 模型健康状态
│   ├── LoadBalancedChatModel.java       # 负载均衡同步模型
│   └── LoadBalancedStreamingChatModel.java # 负载均衡流式模型
├── service/
│   └── AiService.java           # 服务层，封装 ChatAssistant
├── ratelimit/
│   ├── DistributedRateLimiter.java      # Redis 分布式限流器
│   └── DistributedRateLimitAspect.java  # 限流切面
└── exception/
    ├── BusinessException.java           # 业务异常
    ├── AllModelsUnavailableException.java # 所有模型不可用异常
    └── GlobalExceptionHandler.java      # 全局异常处理器
```

**核心流程：**
- `ChatController` → `AiService` → `ChatAssistant`（LangChain4j AiServices）
- `ChatAssistant` 使用 `LoadBalancedChatModel` 实现多模型负载均衡
- 主模型故障时自动切换到备用模型（故障转移）
- 每个模型独立熔断器，防止雪崩效应

**高可用架构：**
- **负载均衡**：按权重随机选择模型，支持动态调整
- **故障转移**：主模型不可用时按优先级切换备用模型
- **熔断保护**：每个模型独立熔断，失败率 50% 触发熔断
- **限流保护**：支持本地限流 + Redis 分布式限流
- **健康监控**：提供模型状态、熔断器状态查询接口

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
| POST | /api/chat | 同步聊天，返回 `ApiResponse<ChatResponse>` |
| POST | /api/chat/stream | SSE 流式响应，逐字输出，事件类型 `token`，最后发送 `[DONE]` |
| GET | /api/health/models | 获取所有模型健康状态 |
| GET | /api/health/circuit-breakers | 获取所有模型熔断器状态 |
| GET | /api/health/summary | 获取综合健康状态 |

**统一响应格式：**
```json
// 成功响应
{"code": 200, "message": "success", "data": {...}}

// 失败响应
{"code": 50206, "message": "所有 AI 模型均不可用", "data": null}
```

**错误码规范：**
- 2xx: 成功
- 4xx: 客户端错误（400 参数错误，429 限流）
- 5xx: 服务端错误（500 内部错误）
- 502xx: AI 服务错误（50200 AI 异常，50206 所有模型不可用）

## 配置说明

**后端配置：** `src/main/resources/application.properties` / `application-{profile}.properties`

**多模型配置：**
```properties
# 主模型：阿里云 DashScope
model.providers.dashscope.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
model.providers.dashscope.api-key=${DASHSCOPE_API_KEY:}
model.providers.dashscope.model-name=qwen-plus
model.providers.dashscope.weight=50       # 权重（负载均衡比例）
model.providers.dashscope.priority=1      # 优先级（故障转移顺序）
model.providers.dashscope.enabled=true    # 是否启用

# 备用模型：智谱 GLM、DeepSeek、硅基流动、Ollama...
# 配置格式相同，调整 weight/priority/enabled
```

**Redis 配置（分布式限流）：**
```properties
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
```

**Resilience4j 配置：**
```properties
# 限流（每分钟 100 次）
resilience4j.ratelimiters.instances.chat.limitForPeriod=100
resilience4j.ratelimiters.instances.chat.limitRefreshPeriod=1m

# 熔断（失败率 50% 触发）
resilience4j.circuitbreaker.instances.chat.failureRateThreshold=50
resilience4j.circuitbreaker.instances.chat.waitDurationInOpenState=10s

# 重试（最多 2 次）
resilience4j.retry.instances.chat.maxAttempts=2
```

**必需的环境变量：**
- `DASHSCOPE_API_KEY`: 阿里云 DashScope API Key（主模型）
- `ZHIPU_API_KEY`: 智谱 API Key（可选，备用模型）
- `DEEPSEEK_API_KEY`: DeepSeek API Key（可选，备用模型）
- `REDIS_HOST/REDIS_PORT`: Redis 连接信息（可选，分布式限流）

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
| AI 模型 | 多模型：DashScope、智谱、DeepSeek、硅基流动、Ollama |
| 容错 | Resilience4j（熔断、限流、重试） |
| 分布式限流 | Redis + Lua 脚本（滑动窗口、令牌桶） |
| 配置中心 | Nacos（可选） |
| 监控 | Prometheus + Grafana, Micrometer |
| 链路追踪 | OpenTelemetry + Zipkin |
| 流式传输 | WebFlux + SSE |
| 部署 | Docker, Docker Compose, Nginx |

## 关键技术点

### 后端技术点

#### 1. 多模型负载均衡
按权重随机选择模型，支持动态调整权重：
```java
// 权重选择算法
int totalWeight = models.stream().mapToInt(m -> m.weight()).sum();
int random = ThreadLocalRandom.current().nextInt(totalWeight);
// 累积权重匹配
```

#### 2. 故障自动转移
主模型故障时按优先级切换备用模型：
```java
// 熔断器打开时切换
catch (CallNotPermittedException e) {
    return fallbackChat(request, failedModel);
}
```

#### 3. 模型级熔断器
每个模型独立熔断，防止雪崩：
```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .slidingWindowSize(5)
    .failureRateThreshold(50)
    .waitDurationInOpenState(Duration.ofSeconds(30))
    .build();
```

#### 4. 分布式限流
基于 Redis + Lua 脚本实现精确限流：
```java
// 滑动窗口限流
boolean allowed = rateLimiter.tryAcquireSlidingWindow(key, 100, 1, TimeUnit.MINUTES);
// 令牌桶限流
boolean allowed = rateLimiter.tryAcquireTokenBucket(key, capacity, rate);
```

#### 5. 统一异常处理
全局异常处理器返回标准化错误响应：
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handle(BusinessException e) {
        return ResponseEntity.status(getHttpStatus(e.getCode()))
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }
}
```

### 前端技术点

#### 1. Vue 响应式更新
由于 computed 对嵌套属性变化不敏感，使用独立的 `currentMessages` ref 追踪消息列表：
```ts
// 每次更新时创建新数组触发响应
currentMessages.value = [...conversation.messages]
```

#### 2. scoped 样式与 v-html
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

#### 3. Tailwind preflight 问题
Tailwind CSS 的 preflight 会移除列表默认样式，需要禁用：
```js
// tailwind.config.js
corePlugins: {
  preflight: false
}
```

#### 4. 事件代理
动态生成的代码块按钮无法直接绑定事件，使用事件代理：
```ts
// 在父元素监听点击，通过 event.target 判断来源
<div @click="handleCodeAction">
  <button data-action="copy">复制</button>
</div>
```

#### 5. SSE 解析
SSE 响应可能跨多个 chunk，需要缓冲处理：
```ts
buffer += decoder.decode(value, { stream: true })
const lines = buffer.split('\n')
buffer = lines.pop() || '' // 保留未完整的行
```

#### 6. 日期解析
从 localStorage 加载的数据需要将日期字符串转换为 Date 对象：
```ts
return parsed.map(conv => ({
  ...conv,
  createdAt: new Date(conv.createdAt)
}))
```

#### 7. Element Plus 图标
需要在 main.ts 中注册 `@element-plus/icons-vue` 图标组件：
```ts
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
```

## 模型配置参数

| 参数 | 说明 | 示例 |
|------|------|------|
| weight | 负载均衡权重（按比例分配请求） | 50 表示 50% 流量 |
| priority | 故障转移优先级（数字越小优先级越高） | 1 为主模型 |
| enabled | 是否启用该模型 | true/false |
| modelName | 模型名称 | qwen-plus, glm-4-flash, deepseek-chat |

**推荐配置：**
- 主模型：weight=50, priority=1（高流量、高优先级）
- 备用模型：weight=20-30, priority=2-4（中流量、中优先级）
- 兜底模型：weight=5, priority=5（低流量、最后兜底）

## 常见问题

### Q: 所有模型都不可用？
A: 检查 API Key 是否正确配置，查看 `/api/health/summary` 了解各模型状态。

### Q: 请求被限流？
A: 检查 Redis 连接是否正常，查看 `resilience4j.ratelimiters` 配置。

### Q: 熔断器一直打开？
A: 检查模型 API 是否正常，等待 `waitDurationInOpenState` 后自动恢复。

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
