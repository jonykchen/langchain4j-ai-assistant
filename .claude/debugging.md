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

### Token 刷新失败

**症状：** 登录过期后跳转登录页异常，或 Token 刷新请求 404

**诊断：**
```ts
// 检查 refresh 请求 URL 是否正确
// 错误的字符串拼接：'' + '/auth/refresh' 会得到 '/auth/refresh'
// 但如果 VITE_API_BASE_URL 未设置，import.meta.env.VITE_API_BASE_URL 是 undefined
// undefined + '/auth/refresh' 会得到 'undefined/auth/refresh' ❌
```

**解决：**
```ts
// 正确做法：先获取 baseUrl，再拼接
const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
const res = await axios.post(baseUrl + '/auth/refresh', { refreshToken })

// 使用 Vue Router 跳转而非 window.location
import router from '@/router'
router.push('/login')
```

### 管理后台加载无反馈

**症状：** 数据加载失败时页面无任何提示

**诊断：**
- 检查 `catch` 块中是否有 `ElMessage.error` 提示
- 检查 `loading` 状态是否正确设置

**解决：**
```ts
const loadResults = async () => {
  loading.value = true  // 开始加载
  try {
    results.value = await testApi.getResults()
  } catch (error) {
    console.error('加载失败', error)
    ElMessage.error('加载失败')  // 错误提示
  } finally {
    loading.value = false  // 结束加载
  }
}
```

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

## 测试管理问题

### E2E 测试结果解析为空

**症状：** E2E 测试运行完成但结果列表为空

**诊断：**
```bash
# 检查 JSON 报告文件是否存在
ls frontend/test-results/report.json

# 检查报告内容
cat frontend/test-results/report.json | head -100
```

**常见原因：**
| 原因 | 解决 |
|------|------|
| JSON 报告文件不存在 | 检查 playwright.config.ts 是否配置了 json reporter |
| Playwright 未正常完成 | 检查进程退出码和日志 |
| 报告路径不匹配 | 确认配置 `outputFile: 'test-results/report.json'` |

**解决：**
- 确保 `playwright.config.ts` 中包含 `['json', { outputFile: 'test-results/report.json' }]`
- 不传 `--reporter` 参数给 `npx playwright test`，使用配置文件中的 reporters
- 检查后端 `TestExecutionService.parseJsonReport()` 日志

### E2E 测试日志包含乱码

**症状：** 后端日志中 E2E 测试输出包含 ANSI 转义码（如颜色代码）

**解决：**
```java
// TestExecutionService 中已使用 stripAnsiCodes 剥离 ANSI 码
String cleanLine = stripAnsiCodes(line);
log.info("[E2E] {}", cleanLine);
```

### 测试任务轮询不停止

**症状：** 测试完成后轮询继续，或组件切换后定时器未清理

**诊断：**
```ts
// 检查是否有未清理的定时器
console.log('Active timeouts:', pollingTimeouts.size)
```

**解决：**
- 确保使用 `Set` 管理所有定时器
- `onUnmounted` 中清理所有定时器
- 设置最大轮询次数限制（`MAX_POLLING_ATTEMPTS = 150`）

### 管理后台页面切换后数据不刷新

**症状：** 从其他标签页切回管理后台后数据不更新

**诊断：**
```ts
// 检查 visibilitychange 事件
document.addEventListener('visibilitychange', () => {
  console.log('Visibility:', document.visibilityState)
})
```

**解决：**
- 使用 `document.visibilityState !== 'visible'` 跳过后台轮询
- 返回前台时在下一次轮询周期自动刷新

## 安全问题

### 前端 XSS 攻击

**症状：** 代码块属性值中的引号逃逸，可能导致脚本注入

**诊断：**
```ts
// 检查 data-raw 属性中是否有未转义的引号
const pre = document.querySelector('[data-raw]')
console.log('Raw attribute:', pre?.dataset.raw)
```

**常见原因：**
| 原因 | 解决 |
|------|------|
| 使用 `escapeHtml` 代替 `escapeAttr` | 属性值需额外转义引号（`"` → `&quot;`） |
| innerHTML 中直接插入用户内容 | 使用 `escapeHtml` 转义 |
| ElMessageBox 中使用 `dangerouslyUseHTMLString` | 转义后再插入 |

**解决：**
```ts
// 属性值使用 escapeAttr
return `<pre data-lang="${escapeAttr(lang)}" data-raw="${escapeAttr(code)}">...</pre>`

// HTML 内容使用 escapeHtml
ElMessageBox.alert(
  `<pre>${escapeHtml(content)}</pre>`,
  '内容',
  { dangerouslyUseHTMLString: true }
)
```

### 前端显示 NaN 或 undefined

**症状：** 管理后台数值显示为 NaN 或 undefined

**诊断：**
```ts
// 检查数据是否可能为 null/undefined
console.log('Budget data:', budget.value)
console.log('Daily used:', budget.value?.dailyUsed)
```

**常见原因：**
| 原因 | 解决 |
|------|------|
| API 返回 null 值 | 使用 `?? 0` 空值合并 |
| 数组为空时计算平均值 | 先检查 `count > 0` |
| 对象属性不存在 | 使用 `v-if` 条件渲染 |

**解决：**
```vue
<!-- 使用空值合并 -->
<span>${{ (row.totalCost ?? 0).toFixed(2) }}</span>

<!-- 条件渲染 -->
<div v-if="row.assertions">{{ row.assertions.passed }}</div>
<span v-else>-</span>

<!-- 计算属性中检查 -->
avgResponseTime: count > 0 ? Math.round(sum / count) : 0
```

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
