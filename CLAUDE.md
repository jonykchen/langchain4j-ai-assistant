# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供代码仓库的工作指导。

## 常用命令

### 后端 (Java/Spring Boot)
```bash
mvn spring-boot:run          # 启动后端服务，默认端口 8082
mvn clean package            # 构建项目
mvn test                     # 运行所有测试
mvn test -Dtest=ClassName    # 运行单个测试类
mvn gatling:test             # 运行性能测试（Gatling）
```

### 前端 (Vue 3/Vite)
```bash
cd frontend
npm install                  # 安装依赖
npm run dev                  # 启动开发服务器，默认端口 5173
npm run build                # 生产环境构建
npm run preview              # 预览生产构建
npm run test:e2e             # 运行 E2E 测试（Playwright）
npm run test:e2e:ui          # E2E 测试 UI 模式
npm run test:e2e:debug       # E2E 测试调试模式
```

### 测试执行脚本
```bash
# Windows
scripts\run-tests.bat all          # 运行全部测试
scripts\run-tests.bat unit         # 仅单元测试
scripts\run-tests.bat integration  # 仅集成测试
scripts\run-tests.bat e2e          # 仅 E2E 测试
scripts\run-tests.bat performance  # 仅性能测试
scripts\run-tests.bat ai           # 仅 AI 模型测试
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
├── admin/
│   ├── controller/AdminController.java      # 管理员 REST API
│   ├── controller/TestManagementController.java # 测试管理 REST API
│   ├── service/AdminStatisticsService.java  # 管理统计服务
│   ├── service/UserAdminService.java        # 用户管理服务
│   ├── service/TokenUsageService.java       # Token 用量服务
│   └── service/TestExecutionService.java    # 测试执行服务
├── ai/
│   ├── model/AIModelTestCase.java           # AI 测试用例定义
│   ├── model/AIModelTestResult.java         # AI 测试结果
│   └── evaluator/ResponseQualityEvaluator.java # 响应质量评估器
├── observability/                           # Agent 可观测性系统
│   ├── trace/                               # 执行追踪
│   │   ├── AgentTrace.java                  # 追踪实体
│   │   ├── AgentTraceSpan.java              # Span 实体
│   │   ├── AgentTraceService.java           # 追踪服务
│   │   ├── AgentTraceAspect.java            # 追踪切面（AOP）
│   │   └── TraceContext.java                # ThreadLocal 追踪上下文
│   ├── prompt/                              # Prompt 管理
│   │   ├── PromptTemplateEntity.java        # Prompt 模板实体
│   │   └── PromptVersionService.java        # 版本控制服务
│   ├── evaluation/                          # 评测框架
│   │   ├── EvaluationMetrics.java           # 评测指标
│   │   ├── AgentEvaluator.java              # 评测器接口
│   │   ├── EvaluationService.java           # 评测服务
│   │   └── impl/                            # 评测器实现
│   └── state/                               # 状态持久化
│       ├── AgentStateSnapshot.java          # 状态快照实体
│       ├── AgentStateService.java           # 状态持久化服务
│       └── ResumableReActAgent.java         # 可恢复 Agent
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
├── api/
│   ├── chat.ts                  # API 调用：sendMessage(), streamMessage()
│   └── admin.ts                 # 管理员 API + 测试管理 API
├── stores/chat.ts               # Pinia 状态管理，支持 localStorage 持久化
├── types/index.ts               # TypeScript 类型定义
├── main.ts                      # 入口文件，注册 Element Plus 和图标
├── style.css                    # 全局样式，Markdown 渲染样式
├── components/
│   ├── ChatInput.vue            # 消息输入组件，处理中文输入法组合事件
│   ├── MessageItem.vue          # 消息渲染组件（核心组件）
│   ├── MessageList.vue          # 消息列表容器，自动滚动到底部
│   └── Sidebar.vue              # 侧边栏，显示对话历史列表
├── views/
│   ├── ChatView.vue             # 主页面布局
│   ├── LoginView.vue            # 登录页面
│   └── admin/
│       ├── AdminLayout.vue      # 管理后台布局（含测试管理菜单）
│       ├── DashboardView.vue    # 仪表盘
│       ├── UsersView.vue        # 用户管理
│       ├── CostView.vue         # 成本监控
│       ├── TestDashboardView.vue  # 测试管理仪表盘
│       ├── E2ETestView.vue        # E2E 测试管理
│       ├── PerformanceTestView.vue # 性能测试管理
│       ├── AIModelTestView.vue    # AI 模型测试管理
│       ├── AgentTraceView.vue     # Agent 追踪管理
│       ├── PromptManagementView.vue # Prompt 版本管理
│       └── EvaluationView.vue     # Agent 评测管理
└── tests/e2e/                   # Playwright E2E 测试
    ├── playwright.config.ts     # Playwright 配置
    ├── auth.setup.ts            # 认证设置
    ├── fixtures/test-fixtures.ts # 测试夹具
    ├── pages/                   # 页面对象模型
    ├── mocks/                   # API Mock
    └── specs/                   # 测试用例
```

**MessageItem.vue 核心功能：**
- Markdown 渲染（markdown-it）
- 代码块增强：语法高亮（highlight.js）、复制、编辑、主题切换、折叠
- 思考过程解析与折叠展示
- 消息操作：点赞/点踩、复制、重新生成

## API 接口

### 核心接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/chat | 同步聊天，返回 `ApiResponse<ChatResponse>` |
| POST | /api/chat/stream | SSE 流式响应，逐字输出，事件类型 `token`，最后发送 `[DONE]` |
| GET | /api/health/models | 获取所有模型健康状态 |
| GET | /api/health/circuit-breakers | 获取所有模型熔断器状态 |
| GET | /api/health/summary | 获取综合健康状态 |

### 测试管理接口（需 ADMIN 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/admin/test/e2e/run | 运行 E2E 测试 |
| GET | /api/admin/test/e2e/status | 获取 E2E 测试状态 |
| GET | /api/admin/test/e2e/report | 获取 E2E 测试报告路径 |
| POST | /api/admin/test/performance/run | 运行性能测试 |
| GET | /api/admin/test/performance/results | 获取性能测试结果 |
| GET | /api/admin/test/performance/simulations | 获取可用模拟场景 |
| POST | /api/admin/test/ai/run | 运行 AI 模型测试 |
| GET | /api/admin/test/ai/results | 获取 AI 模型测试结果 |
| GET | /api/admin/test/ai/categories | 获取 AI 测试分类 |
| GET | /api/admin/test/job/{jobId}/status | 获取测试任务状态 |
| DELETE | /api/admin/test/job/{jobId} | 取消测试任务 |
| GET | /api/admin/test/stats/summary | 获取测试统计摘要 |

### 可观测性接口（需 ADMIN 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/admin/observability/traces | 获取 Agent 追踪列表 |
| GET | /api/admin/observability/traces/{traceId} | 获取追踪详情 |
| GET | /api/admin/observability/traces/{traceId}/spans | 获取追踪 Span 列表 |
| GET | /api/admin/observability/traces/active | 获取活跃追踪 |
| GET | /api/admin/observability/traces/statistics | 获取追踪统计 |
| GET | /api/admin/observability/prompts | 获取 Prompt 模板列表 |
| GET | /api/admin/observability/prompts/names | 获取模板名称列表 |
| GET | /api/admin/observability/prompts/{name}/versions | 获取版本历史 |
| POST | /api/admin/observability/prompts | 创建 Prompt 模板 |
| POST | /api/admin/observability/prompts/{name}/versions | 创建新版本 |
| POST | /api/admin/observability/prompts/{name}/versions/{version}/activate | 激活版本 |
| POST | /api/admin/observability/prompts/{name}/versions/{version}/promote | 推送到生产 |
| POST | /api/admin/observability/prompts/{name}/rollback/{version} | 回滚版本 |
| POST | /api/admin/observability/prompts/{name}/ab-test | 配置 A/B 测试 |
| POST | /api/admin/observability/prompts/{name}/ab-test/stop | 停止 A/B 测试 |
| POST | /api/admin/observability/evaluation/evaluate/{traceId} | 评测追踪 |
| POST | /api/admin/observability/evaluation/batch | 批量评测 |
| POST | /api/admin/observability/evaluation/report | 生成评测报告 |
| GET | /api/admin/observability/evaluation/evaluators | 获取评测器列表 |
| GET | /api/admin/observability/snapshots/session/{sessionId} | 获取会话快照 |
| GET | /api/admin/observability/snapshots/session/{sessionId}/resumable | 获取可恢复快照 |
| GET | /api/admin/observability/snapshots/statistics | 获取快照统计 |
| POST | /api/admin/observability/snapshots/cleanup | 清理过期快照 |

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

### 数据库架构

| 数据库 | 用途 | 端口 | 初始化脚本 |
|--------|------|------|-----------|
| PostgreSQL | 应用业务数据（支持 pgvector 向量检索） | 5432 | `infra/postgres/init/01-init.sql`, `02-agent-observability.sql` |
| MySQL | 仅 Nacos 配置中心元数据 | 3307 | `infra/nacos/init/01-nacos-init.sql` |
| Redis | 缓存/限流/会话 | 6379 | — |

#### Agent 可观测性数据表

| 表名 | 说明 |
|------|------|
| `agent_traces` | Agent 执行追踪记录 |
| `agent_trace_spans` | Agent 执行步骤详情 |
| `prompt_templates` | Prompt 模板版本管理 |
| `agent_state_snapshots` | Agent 状态快照（断点续传） |
| `evaluation_results` | Agent 评测结果 |

### 配置文件结构

应用配置已迁移至 Nacos，本地仅保留最小必要配置：

| 配置位置 | 说明 |
|----------|------|
| `src/main/resources/application.properties` | 仅 server.port 和环境变量说明 |
| `src/main/resources/bootstrap.yml` | Nacos 连接配置 |
| `src/main/resources/schema.sql` | 数据库 Schema（Spring Boot 启动时执行） |

Nacos 配置文件（`infra/nacos/config/`）：

| Data ID | 说明 |
|---------|------|
| `common.properties` | 跨应用共享配置（Jackson、HTTP、文件上传） |
| `langchain4j-chat.properties` | 公共配置（数据库、模型、安全、JWT、监控等） |
| `langchain4j-chat-dev.properties` | 开发环境覆盖（Redis、日志、宽松策略） |
| `langchain4j-chat-prod.properties` | 生产环境覆盖（Redis、日志、严格策略） |

配置加载优先级：
```
bootstrap.yml（Nacos 连接）
  → common.properties（共享）
  → langchain4j-chat.properties（公共）
  → langchain4j-chat-{profile}.properties（环境覆盖）
  → application.properties（本地兜底，仅 server.port）
```

### 数据库配置

```properties
# PostgreSQL（应用业务数据库，支持 pgvector 和 Row Level Security）
spring.datasource.url=jdbc:postgresql://localhost:5432/langchain4j?currentSchema=public
spring.datasource.username=langchain4j
spring.datasource.password=REDACTED_DB_PASSWORD
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

### 多模型配置

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

### 必需的环境变量

- `DASHSCOPE_API_KEY`: 阿里云 DashScope API Key（主模型）
- `DATABASE_URL`: PostgreSQL 连接 URL（默认 jdbc:postgresql://localhost:5432/langchain4j）
- `DATABASE_USERNAME`: 数据库用户名（默认 langchain4j）
- `DATABASE_PASSWORD`: 数据库密码（默认 REDACTED_DB_PASSWORD）
- `JWT_SECRET`: JWT 密钥
- `NACOS_SERVER_ADDR`: Nacos 地址（默认 localhost:8848）
- `SPRING_PROFILES_ACTIVE`: 激活环境（dev/prod，默认 dev）

可选的环境变量：
- `ZHIPU_API_KEY`: 智谱 API Key（备用模型）
- `DEEPSEEK_API_KEY`: DeepSeek API Key（备用模型）
- `SILICONFLOW_API_KEY`: 硅基流动 API Key（备用模型）
- `REDIS_HOST/REDIS_PORT`: Redis 连接信息（分布式限流）
- `GITHUB_CLIENT_ID/GITHUB_CLIENT_SECRET`: GitHub OAuth
- `GITLAB_CLIENT_ID/GITLAB_CLIENT_SECRET`: GitLab OAuth

**前端配置：** `frontend/vite.config.ts`
- 开发服务器端口 5173
- 代理 `/api` 请求到后端
- 路径别名 `@` 指向 `src` 目录

**Tailwind 配置：** `frontend/tailwind.config.js`
- `corePlugins.preflight: false` - 禁用 CSS reset，避免移除列表样式

**PostgreSQL 特性：**
- `pgvector` 扩展 - 向量相似度检索（HNSW/IVFFlat 索引）
- `JSONB` 类型 - 高效存储工具参数、元数据
- `Row Level Security` - 数据库层租户/用户隔离
- `uuid-ossp` 扩展 - UUID 生成
- `pg_trgm` 扩展 - 全文搜索

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.4.1, LangChain4j 1.13.0, WebFlux |
| 前端 | Vue 3, Vite, Pinia, Element Plus, TypeScript |
| Markdown | markdown-it, highlight.js |
| 样式 | Tailwind CSS, Scoped CSS |
| AI 模型 | 多模型：DashScope、智谱、DeepSeek、硅基流动、Ollama |
| 业务数据库 | PostgreSQL 16 + pgvector（向量检索、Row Level Security） |
| 配置数据库 | MySQL 8.0（仅 Nacos 元数据） |
| 缓存/限流 | Redis 7 |
| 配置中心 | Nacos |
| 监控 | Prometheus + Grafana, Micrometer |
| 链路追踪 | OpenTelemetry + Zipkin |
| Agent 可观测性 | 执行追踪、Prompt 管理、评测框架、状态持久化 |
| 容错 | Resilience4j（熔断、限流、重试） |
| 分布式限流 | Redis + Lua 脚本（滑动窗口、令牌桶） |
| 流式传输 | WebFlux + SSE |
| 部署 | Docker, Docker Compose, Nginx |
| E2E 测试 | Playwright 1.40+, @axe-core/playwright |
| 性能测试 | Gatling 3.10+, Scala 2.13 |
| 契约测试 | Spring Cloud Contract 4.0+ |
| 测试容器 | Testcontainers 1.19+ |
| 代码覆盖率 | JaCoCo 0.8.11 |

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

#### 6. Agent 执行追踪
AOP 自动追踪 Agent 执行过程：
```java
@Around("execution(* com.jonychen.planning.agent.ReActAgent.execute(..))")
public Object traceReActExecution(ProceedingJoinPoint joinPoint) throws Throwable {
    AgentTrace trace = traceService.startTrace(sessionId, userId, "REACT", question);
    try {
        Object result = joinPoint.proceed();
        traceService.endTraceSuccess(trace.getTraceId(), output, tokens);
        return result;
    } catch (Throwable e) {
        traceService.endTraceFailed(trace.getTraceId(), e.getMessage());
        throw e;
    }
}
```

#### 7. Prompt 版本管理
支持版本控制、A/B 测试、回滚：
```java
// 创建新版本
promptService.createVersion(name, newContent, description);
// 激活版本
promptService.activateVersion(name, version);
// 配置 A/B 测试
promptService.configureABTest(name, config);
```

#### 8. Agent 状态持久化
支持断点续传，执行中断后可恢复：
```java
// 保存检查点
stateService.saveCheckpoint(traceId, sessionId, "REACT", state, step, total);
// 从快照恢复
AgentResumeContext context = stateService.resumeFromSnapshot(snapshotId);
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

### Q: E2E 测试无法启动？
A: 确保已安装 Playwright：`cd frontend && npx playwright install`。CI 环境需 `npx playwright install --with-deps`。

### Q: 性能测试报错找不到 Scala？
A: 项目已配置 `scala-maven-plugin`，直接运行 `mvn gatling:test` 即可。

### Q: AI 模型测试超时？
A: 检查 AI API Key 是否正确，`application-ai-test.properties` 中限流配置是否宽松。

### Q: 测试管理页面打不开？
A: 需要 ADMIN 角色登录，检查 `/api/admin/test/**` 接口是否在 SecurityConfig 中配置。

### Q: Agent 追踪数据看不到？
A: 检查 `agent_traces` 表是否有数据，确认 Agent 执行时 AgentTraceAspect 切面生效。检查日志中是否有 "Started agent trace" 输出。

### Q: Prompt 模板版本管理不生效？
A: 确认 `prompt_templates` 表已创建，检查模板的 `active` 字段是否正确设置。A/B 测试需要两个版本都启用 `abTestEnabled`。

### Q: Agent 状态无法恢复？
A: 检查快照是否过期（默认 24 小时），确认 `resumable` 标志为 true。使用 `ResumableReActAgent` 替代直接调用 `ReActAgent`。

## 扩展指南

项目提供更详细的指南文件，帮助高效开发：

| 文件 | 内容 |
|------|------|
| [.claude/guide.md](./.claude/guide.md) | Claude Code 使用方法、协作最佳实践 |
| [.claude/patterns.md](./.claude/patterns.md) | 项目特有代码模式和模板 |
| [.claude/debugging.md](./.claude/debugging.md) | 常见问题诊断与解决方案 |
| [docs/测试体系技术实现方案.md](./docs/测试体系技术实现方案.md) | 测试体系完整技术方案 |
| [docs/Agent可观测性技术实现方案.md](./docs/Agent可观测性技术实现方案.md) | Agent 可观测性系统技术方案 |

## 测试体系

项目已建立完整的测试体系，覆盖从单元测试到 AI 模型测试的全链路。

### 测试类型与目录

| 测试类型 | 目录 | 运行命令 |
|---------|------|---------|
| 单元测试 | `src/test/java/` | `mvn test -Dtest="!*IntegrationTest,!*ContractTest,!*AIModelTest*"` |
| 集成测试 | `src/test/java/` | `mvn test -Dtest="*IntegrationTest"` |
| 契约测试 | `src/test/resources/contracts/` | `mvn test -Dtest="*ContractTest*"` |
| AI 模型测试 | `src/test/java/com/jonychen/ai/runner/` | `mvn test -Dtest="*AIModelTest*"` |
| 性能测试 | `src/test/scala/gatling/simulations/` | `mvn gatling:test` |
| E2E 测试 | `frontend/tests/e2e/` | `cd frontend && npx playwright test` |

### 测试配置文件

| 配置文件 | 说明 |
|---------|------|
| `src/test/resources/application-test.properties` | 单元/集成测试配置 |
| `src/test/resources/application-contract-test.properties` | 契约测试配置 |
| `src/test/resources/application-ai-test.properties` | AI 模型测试配置 |
| `src/test/resources/gatling.conf` | Gatling 性能测试配置 |
| `frontend/tests/e2e/playwright.config.ts` | Playwright E2E 测试配置 |

### 代码覆盖率

JaCoCo 已配置，运行 `mvn test` 后在 `target/site/jacoco/` 查看覆盖率报告。

## 学习路径

1. **入门** - 阅读本文档了解项目结构
2. **进阶** - 学习 [.claude/guide.md](./.claude/guide.md) 掌握 Claude 协作技巧
3. **实践** - 参考 [.claude/patterns.md](./.claude/patterns.md) 使用代码模板
4. **排错** - 遇到问题查阅 [.claude/debugging.md](./.claude/debugging.md)
