# 调试指南

常见问题诊断与解决方案。

## 后端问题

### 所有模型不可用

**症状：** 返回错误码 50206 "所有 AI 模型均不可用"

**诊断：**
```bash
# 检查模型健康状态
curl http://localhost:8082/api/health/summary

# 检查熔断器状态
curl http://localhost:8082/api/health/circuit-breakers
```

**常见原因：**
| 原因 | 解决 |
|------|------|
| API Key 未配置 | 检查环境变量 DASHSCOPE_API_KEY |
| API Key 无效 | 验证 API Key 是否有效 |
| 网络问题 | 检查代理设置或网络连接 |
| 熔断器全部打开 | 等待恢复或重启服务 |

### 请求被限流

**症状：** 返回错误码 429 "请求过于频繁"

**诊断：**
```bash
# 检查限流配置
curl http://localhost:8082/actuator/ratelimiters

# 检查 Redis 连接
redis-cli ping
```

**解决：**
- 检查 `resilience4j.ratelimiters` 配置
- 检查 Redis 连接是否正常
- 调整 `limitForPeriod` 参数

### 熔断器一直打开

**症状：** 日志显示熔断器状态变为 OPEN

**诊断：**
```java
// 查看熔断器事件
circuitBreaker.getEventPublisher()
    .onStateTransition(event -> LOG.warn("状态变化: {}", event))
    .onError(event -> LOG.warn("调用失败: {}", event));
```

**解决：**
- 检查模型 API 是否正常
- 调整 `failureRateThreshold` 阈值
- 调整 `waitDurationInOpenState` 恢复时间
- 调用 `/api/health/circuit-breakers` 查看状态

### API 调用失败

**检查：**
```properties
# 启用详细日志
logging.level.com.jonychen=DEBUG
logging.level.dev.langchain4j=DEBUG
logging.level.io.github.resilience4j=DEBUG
```

**常见错误：**
| 错误 | 原因 | 解决 |
|------|------|------|
| 401 Unauthorized | API Key 无效 | 检查环境变量配置 |
| 429 Too Many Requests | API 限流 | 降低请求频率或更换模型 |
| Connection timeout | 网络问题 | 检查代理或超时配置 |
| All models unavailable | 所有模型不可用 | 检查各模型配置 |

### 模型切换频繁

**症状：** 日志显示大量故障转移

**诊断：**
```java
// 查看切换次数指标
curl http://localhost:8082/actuator/metrics/model_failover_total
```

**解决：**
- 调整模型权重，减少低质量模型权重
- 调整熔断器阈值
- 检查网络稳定性

## 前端问题

### 响应式失效

**症状：** 数据变化但视图不更新

**诊断：**
```ts
// 检查是否直接修改了响应式对象
console.log('原始对象:', toRaw(reactiveObj))

// 检查 computed 依赖
watchEffect(() => {
  console.log('依赖变化:', someRef.value)
})
```

**常见原因：**
| 原因 | 解决 |
|------|------|
| 直接修改数组索引 | `arr.value = [...arr.value]` |
| 直接修改数组长度 | `arr.value = []` 或 `arr.splice(0)` |
| reactive 重新赋值 | 使用 ref 或 Object.assign |
| 添加新属性 | `obj.value = { ...obj.value, newProp }` |

### SSE 流中断

**症状：** 流式响应中途停止

**诊断：**
```ts
// 添加详细日志
reader.read().then(({ done, value }) => {
  console.log('done:', done, 'value length:', value?.length)
})
```

**检查项：**
1. 浏览器网络超时设置
2. 后端 Flux 是否正确完成
3. 代理/网关超时配置

**解决：**
```properties
# application.properties
spring.mvc.async.request-timeout=300000
server.netty.connection-timeout=300000
```

### 代码高亮失效

**症状：** 代码块无语法高亮

**检查：**
```ts
// 1. 语言包是否注册
console.log('支持的语言:', hljs.listLanguages())

// 2. 渲染输出是否正确
const html = md.render('```java\npublic class A {}\n```')
console.log('渲染结果:', html)
```

**解决：**
```ts
// 确保导入语言包
import hljs from 'highlight.js'
import 'highlight.js/lib/languages/java'
import 'highlight.js/lib/languages/typescript'
```

### 样式不生效

**症状：** CSS 规则被覆盖或不显示

**检查：**
1. 浏览器 DevTools → Elements → Styles
2. 查看 computed 样式
3. 检查 scoped 属性

**v-html 内容样式：**
```vue
<!-- scoped 不生效 -->
<style scoped>
.content pre { }  /* ❌ 不生效 */
</style>

<!-- 需要非 scoped -->
<style>
.content pre { }  /* ✅ 生效 */
</style>
```

## 后端问题

### 对话记忆丢失

**诊断：**
```java
// 添加日志
log.info("Conversation ID: {}", conversationId);
log.info("Memory messages: {}", chatMemory.messages());
```

**检查项：**
1. `@MemoryId` 是否正确传递
2. `ChatMemoryProvider` 是否正确配置
3. 会话 ID 是否一致

### 流式响应错误

**诊断：**
```java
Flux<String> flux = aiService.chatStream(id, msg)
    .doOnSubscribe(s -> log.info("Stream started"))
    .doOnNext(token -> log.debug("Token: {}", token))
    .doOnComplete(() -> log.info("Stream complete"))
    .doOnError(e -> log.error("Error: {}", e.getMessage()))
    .doOnCancel(() -> log.warn("Stream cancelled"));
```

### API 调用失败

**检查：**
```properties
# 启用详细日志
logging.level.com.jonychen=DEBUG
logging.level.dev.langchain4j=DEBUG
```

**常见错误：**
| 错误 | 原因 | 解决 |
|------|------|------|
| 401 Unauthorized | API Key 无效 | 检查配置 |
| 429 Too Many Requests | 限流 | 添加重试 |
| Connection timeout | 网络问题 | 检查代理 |

## 性能问题

### 前端卡顿

**诊断：**
```ts
// 使用 Performance API
performance.mark('render-start')
// ... 渲染代码
performance.mark('render-end')
performance.measure('render', 'render-start', 'render-end')
console.log(performance.getEntriesByName('render'))
```

**优化方向：**
1. 虚拟滚动 (vue-virtual-scroller)
2. 防抖/节流
3. 懒加载组件
4. 减少 computed 复杂度

### 后端慢请求

**诊断：**
```java
// 添加计时
long start = System.currentTimeMillis();
String result = aiService.chat(msg);
log.info("Chat took {}ms", System.currentTimeMillis() - start);

// 查看 Prometheus 指标
curl http://localhost:8082/actuator/metrics/http.server.requests
```

**优化方向：**
1. 减少对话记忆窗口大小
2. 使用流式响应避免超时
3. 调整模型超时配置
4. 启用 Nacos 动态配置热更新

### Redis 连接问题

**症状：** 分布式限流失效

**诊断：**
```bash
# 检查 Redis 连接
redis-cli -h localhost -p 6379 ping

# 查看连接数
redis-cli info clients
```

**解决：**
- 检查 `spring.data.redis.*` 配置
- 检查网络连通性
- Redis 异常时自动降级为本地限流

## 调试工具

### Chrome DevTools
- **Network**: 查看请求/响应、SSE 流
- **Vue DevTools**: 检查组件树和状态
- **Performance**: 分析渲染性能

### 后端工具

**健康检查接口：**
```bash
# 模型状态
curl http://localhost:8082/api/health/models

# 熔断器状态
curl http://localhost:8082/api/health/circuit-breakers

# 综合状态
curl http://localhost:8082/api/health/summary
```

**Actuator 端点：**
```bash
# 所有端点
curl http://localhost:8082/actuator

# 健康检查
curl http://localhost:8082/actuator/health

# Prometheus 指标
curl http://localhost:8082/actuator/prometheus

# 限流器状态
curl http://localhost:8082/actuator/ratelimiters

# 熔断器状态
curl http://localhost:8082/actuator/circuitbreakers
```

**日志分析：**
```bash
# 查看日志
tail -f logs/spring.log

# 测试接口
curl -X POST http://localhost:8082/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"hello"}'
```

**Prometheus + Grafana：**
- 访问 Grafana 仪表盘查看 AI 服务指标
- 监控模型切换次数、熔断器状态
- 设置告警规则
