# 代码模式

本项目特有的代码模式和约定。

## 后端模式

### 多模型配置模式

```java
// ModelProperties.java - 从 application.properties 读取多模型配置
@Configuration
@ConfigurationProperties(prefix = "model")
public class ModelProperties {
    private Map<String, ProviderConfig> providers = new HashMap<>();

    public List<ModelProvider> getEnabledProviders() {
        return providers.entrySet().stream()
            .filter(e -> e.getValue().enabled)
            .map(e -> new ModelProvider(
                e.getKey(),
                e.getValue().baseUrl,
                e.getValue().apiKey,
                e.getValue().modelName,
                e.getValue().weight,
                e.getValue().priority,
                true
            ))
            .sorted(Comparator.comparingInt(ModelProvider::priority))
            .toList();
    }
}
```

### 负载均衡模型模式

```java
// LoadBalancedChatModel.java - 多模型负载均衡 + 故障转移
public class LoadBalancedChatModel implements ChatModel {

    @Override
    public ChatResponse chat(ChatRequest request) {
        // 1. 按权重选择模型
        ModelInstance selected = selectByWeight(getAvailableModels());

        try {
            // 2. 通过熔断器执行
            return executeWithCircuitBreaker(selected, request);
        } catch (CallNotPermittedException e) {
            // 3. 熔断器打开，切换备用模型
            return fallbackChat(request, selected.provider.name());
        }
    }
}
```

### Resilience4j 注解模式

```java
// ChatController.java - 限流 + 熔断 + 重试
@PostMapping
@RateLimiter(name = "chat", fallbackMethod = "chatRateLimitFallback")
@CircuitBreaker(name = "chat", fallbackMethod = "chatCircuitBreakerFallback")
@Retry(name = "chat", fallbackMethod = "chatRetryFallback")
public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
    return ApiResponse.success(new ChatResponse(aiService.chat(request.message())));
}

// 限流降级
public ApiResponse<ChatResponse> chatRateLimitFallback(ChatRequest request, RequestNotPermitted e) {
    return ApiResponse.error(ErrorCode.RATE_LIMITED, "请求过于频繁");
}

// 熔断降级
public ApiResponse<ChatResponse> chatCircuitBreakerFallback(ChatRequest request, CallNotPermittedException e) {
    return ApiResponse.error(ErrorCode.AI_SERVICE_ERROR, "服务暂时不可用");
}
```

### 分布式限流模式

```java
// DistributedRateLimiter.java - Redis + Lua 脚本
@Component
public class DistributedRateLimiter {

    // 滑动窗口限流 Lua 脚本（原子操作）
    private static final String SLIDING_WINDOW_SCRIPT = """
        local key = KEYS[1]
        local window = tonumber(ARGV[1])
        local limit = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
        local count = redis.call('ZCARD', key)

        if count < limit then
            redis.call('ZADD', key, now, now .. '-' .. math.random())
            redis.call('PEXPIRE', key, window)
            return 1
        else
            return 0
        end
        """;

    public boolean tryAcquireSlidingWindow(String key, int limit, long period, TimeUnit unit) {
        Long result = redisTemplate.execute(script, Collections.singletonList(key), ...);
        return result != null && result == 1L;
    }
}
```

### 统一响应模式

```java
// ApiResponse.java - 统一 API 响应格式
public record ApiResponse<T>(int code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }
}

// ErrorCode.java - 错误码枚举
public enum ErrorCode {
    RATE_LIMITED(429, "请求过于频繁"),
    AI_SERVICE_ERROR(50200, "AI 服务异常"),
    ALL_MODELS_UNAVAILABLE(50206, "所有 AI 模型均不可用");
}
```

### 异常处理模式

```java
// GlobalExceptionHandler.java - 全局异常处理
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        return ResponseEntity
            .status(getHttpStatus(e.getCode()))
            .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ErrorCode.PARAM_INVALID, message));
    }
}
```

## Vue 3 组件模式

### Props + Emits 标准模板

```vue
<script setup lang="ts">
interface Props {
  message: ChatMessage
  isStreaming?: boolean
}

interface Emits {
  (e: 'like', id: string): void
  (e: 'dislike', id: string): void
  (e: 'regenerate', id: string): void
}

const props = withDefaults(defineProps<Props>(), {
  isStreaming: false
})

const emit = defineEmits<Emits>()
</script>
```

### 事件代理模式

用于动态生成的元素：

```vue
<template>
  <div class="code-block" @click="handleAction">
    <button data-action="copy" data-code="...">复制</button>
    <button data-action="edit" data-code="...">编辑</button>
  </div>
</template>

<script setup lang="ts">
const handleAction = (e: MouseEvent) => {
  const target = e.target as HTMLElement
  const action = target.dataset.action
  const code = target.dataset.code

  switch (action) {
    case 'copy':
      navigator.clipboard.writeText(code)
      break
    case 'edit':
      // 编辑逻辑
      break
  }
}
</script>
```

### 响应式数组更新

```ts
// ❌ 错误：直接修改不触发更新
messages[0] = newMsg
messages.length = 0

// ✅ 正确：创建新数组
messages.value = [...messages.value, newMsg]
messages.value = []
```

### 废弃 API 替换

```ts
// ❌ 废弃：substr 已不推荐使用
return Date.now().toString(36) + Math.random().toString(36).substr(2)

// ✅ 正确：使用 substring
return Date.now().toString(36) + Math.random().toString(36).substring(2)
```

### Vue Router 导航

```ts
// ❌ 可能导致状态问题：直接修改 location
window.location.href = '/login'

// ✅ 正确：使用 Vue Router
import router from '@/router'
router.push('/login')
```

## LangChain4j 模式

### AI 服务接口

```java
@AiService
public interface ChatAssistant {

    // 同步对话
    String chat(
        @MemoryId String conversationId,
        @UserMessage String userMessage
    );

    // 流式对话
    TokenStream chatStream(
        @MemoryId String conversationId,
        @UserMessage String userMessage
    );
}
```

### 流式响应处理

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestBody ChatRequest request) {
    return aiService.chatStream(request.conversationId(), request.message())
        .map(token -> "data: " + token + "\n\n")
        .concatWith(Flux.just("data: [DONE]\n\n"))
        .doOnError(e -> log.error("Stream error", e));
}
```

### 对话记忆配置

```java
@Bean
public ChatAssistant chatAssistant(ChatLanguageModel model) {
    return AiServices.builder(ChatAssistant.class)
        .chatLanguageModel(model)
        .chatMemoryProvider(id -> MessageWindowChatMemory.builder()
            .maxMessages(10)
            .id(id)
            .build())
        .build();
}
```

## 状态管理模式

### Pinia Store 模板

```ts
export const useChatStore = defineStore('chat', () => {
  // State
  const conversations = ref<Conversation[]>([])
  const currentId = ref<string | null>(null)

  // Getters
  const currentConversation = computed(() =>
    conversations.value.find(c => c.id === currentId.value)
  )

  // Actions
  function addMessage(content: string, role: 'user' | 'assistant') {
    const conv = currentConversation.value
    if (!conv) return

    conv.messages.push({
      id: crypto.randomUUID(),
      content,
      role,
      timestamp: new Date()
    })
  }

  return { conversations, currentId, currentConversation, addMessage }
}, {
  persist: {
    storage: localStorage,
    serializer: {
      serialize: JSON.stringify,
      deserialize: (value) => {
        const parsed = JSON.parse(value)
        // 转换日期
        return parsed.map((c: Conversation) => ({
          ...c,
          createdAt: new Date(c.createdAt),
          messages: c.messages.map(m => ({
            ...m,
            timestamp: new Date(m.timestamp)
          }))
        }))
      }
    }
  }
})
```

## SSE 前端处理模式

```ts
async function streamMessage(message: string): Promise<void> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, conversationId })
  })

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() || ''

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const data = line.slice(6)
        if (data === '[DONE]') {
          onStreamComplete()
          return
        }
        onToken(data)
      }
    }
  }
}
```

## Markdown 渲染模式

```ts
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'

const md = new MarkdownIt({
  html: true,
  linkify: true,
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

// 渲染
const htmlContent = md.render(markdownText)
```

## 样式约定

### Scoped + 全局样式组合

```vue
<style scoped>
/* 组件私有样式 */
.message-item {
  padding: 16px;
}
</style>

<style>
/* v-html 内容样式 - 需要 specificity */
.message-assistant .message-content .markdown-body pre {
  background: #1e1e1e;
  border-radius: 8px;
}
</style>
```

### Tailwind 约定

```js
// tailwind.config.js - 禁用 preflight 保留列表样式
corePlugins: {
  preflight: false
}
```

## 前端安全模式

### XSS 防护 - 属性转义

动态生成的 HTML 属性值必须转义：

```ts
/**
 * 转义 HTML 属性值中的特殊字符
 * 用于 data-xxx 属性中，确保引号不会逃逸出属性
 */
function escapeAttr(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

// 使用示例：代码块属性
const langLabel = 'java'
const rawData = 'System.out.println("Hello");'
return `<pre data-lang="${escapeAttr(langLabel)}" data-raw="${escapeAttr(rawData)}">...</pre>`
```

### HTML 内容转义

在 innerHTML/v-html 中显示用户内容时转义：

```ts
function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

// 在 ElMessageBox.alert 中显示模板内容
ElMessageBox.alert(
  `<pre style="...">${escapeHtml(version.content)}</pre>`,
  '版本内容',
  { dangerouslyUseHTMLString: true }
)
```

## 前端性能优化模式

### 轮询管理

使用 Set 管理所有定时器，确保组件卸载时清理：

```ts
// 定义定时器集合
const pollingTimeouts = new Set<ReturnType<typeof setTimeout>>()
const MAX_POLLING_ATTEMPTS = 150 // 最大轮询次数（5分钟）
let pollingAttempts = 0

// 轮询函数
const poll = async () => {
  pollingAttempts++
  if (pollingAttempts > MAX_POLLING_ATTEMPTS) {
    ElMessage.warning('轮询超时，请手动刷新查看状态')
    return
  }
  
  try {
    const status = await testApi.getJobStatus(jobId)
    if (status.status === 'running') {
      const timeout = setTimeout(poll, 2000)
      pollingTimeouts.add(timeout)
    } else {
      // 完成
    }
  } catch (e) {
    // 错误处理
  }
}

// 组件卸载时清理所有定时器
onUnmounted(() => {
  pollingTimeouts.forEach(timeout => clearTimeout(timeout))
  pollingTimeouts.clear()
})
```

### 页面可见性检测

后台页面跳过轮询以节省资源：

```ts
let refreshInterval: ReturnType<typeof setInterval> | undefined

const startPolling = () => {
  if (refreshInterval) return
  refreshInterval = setInterval(() => {
    // 页面不可见时跳过轮询，节省资源
    if (document.visibilityState !== 'visible') return
    loadActiveTraces()
    loadStatistics()
  }, 5000)
}

const stopPolling = () => {
  if (refreshInterval) {
    clearInterval(refreshInterval)
    refreshInterval = undefined
  }
}

onMounted(() => {
  loadData()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
```

### 搜索防抖

文本输入搜索使用防抖，选择器立即触发：

```ts
let searchDebounceTimer: ReturnType<typeof setTimeout> | null = null

// 搜索文本延迟 300ms
watch([searchQuery], () => {
  if (searchDebounceTimer) {
    clearTimeout(searchDebounceTimer)
  }
  searchDebounceTimer = setTimeout(() => {
    currentPage.value = 1
    loadUsers()
  }, 300)
})

// 选择器立即触发
watch([roleFilter, providerFilter], () => {
  currentPage.value = 1
  loadUsers()
})
```

## 空值安全处理模式

### 模板中的空值处理

```vue
<template>
  <!-- 使用空值合并运算符 -->
  <span>${{ (budget.dailyUsed ?? 0).toFixed(2) }}</span>
  
  <!-- 条件渲染避免 NaN -->
  <div v-if="row.assertions">
    <el-tag>{{ row.assertions.passed }} 通过</el-tag>
  </div>
  <span v-else class="text-gray-400">-</span>
  
  <!-- 百分比计算防零 -->
  <span>{{ stat.total > 0 ? Math.round(stat.passed / stat.total * 100) : 0 }}%</span>
</template>
```

### 计算属性中的空值处理

```ts
const calculateStats = () => {
  const passed = results.value.filter(r => r.passed).length
  const count = results.value.length
  return {
    passed,
    failed: count - passed,
    totalTests: count,
    // 先检查长度再计算，避免 NaN
    avgResponseTime: count > 0
      ? Math.round(results.value.reduce((sum, r) => sum + r.responseTime, 0) / count)
      : 0
  }
}
```

## 后端测试解析模式

### Playwright JSON 报告解析

从 JSON 报告文件解析测试结果，替代日志行解析：

```java
/**
 * 从 Playwright JSON 报告文件解析测试结果
 */
private List<TestResultSummary> parseJsonReport(Path jsonReportPath) {
    List<TestResultSummary> results = new ArrayList<>();
    if (!Files.exists(jsonReportPath)) {
        log.warn("[E2E] JSON report file not found: {}", jsonReportPath);
        return results;
    }

    try {
        String content = Files.readString(jsonReportPath, StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(content);
        JsonNode suites = root.get("suites");
        if (suites != null) {
            collectSpecs(suites, results);
        }
    } catch (Exception e) {
        log.error("[E2E] Failed to parse JSON report", e);
    }

    return results;
}

/**
 * 递归遍历 suites 树，提取每个 spec 的测试结果
 */
private void collectSpecs(JsonNode suites, List<TestResultSummary> results) {
    for (JsonNode suite : suites) {
        JsonNode specs = suite.get("specs");
        if (specs != null) {
            for (JsonNode spec : specs) {
                extractSpecResult(spec, results);
            }
        }
        // 递归处理嵌套 suites
        JsonNode nested = suite.get("suites");
        if (nested != null && nested.isArray()) {
            collectSpecs(nested, results);
        }
    }
}
```

### ANSI 转义码剥离

子进程输出日志需剥离 ANSI 转义码：

```java
/**
 * 剥离 ANSI 转义码（如颜色、光标移动控制字符）
 */
private String stripAnsiCodes(String text) {
    if (text == null) {
        return null;
    }
    // 匹配 ANSI 转义序列：ESC [ 或 ESC ] 开头的控制序列
    // 常见模式：\u001B[...m (颜色), \u001B[...A/K/etc (光标控制)
    return text.replaceAll("\u001B\\[[;\\d]*[ -/]*[@-~]", "");
}

// 使用示例
try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream()))) {
    String line;
    while ((line = reader.readLine()) != null) {
        String cleanLine = stripAnsiCodes(line);
        log.info("[Gatling] {}", cleanLine);
    }
}
```

## 流式模型熔断器模式

### 熔断器状态同步

流式模型通过 Handler 将请求结果同步到熔断器和健康状态：

```java
private static class FaultTolerantHandler implements StreamingChatResponseHandler {
    private final StreamingChatResponseHandler delegate;
    private final String modelName;
    private final CircuitBreaker circuitBreaker;
    private final long startTimeNanos;
    private volatile boolean completed = false;

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        completed = true;
        // 记录响应时间并上报熔断器成功
        long durationMs = TimeUnit.NANOSECONDS.toMillis(
            System.nanoTime() - startTimeNanos);
        circuitBreaker.onSuccess(durationMs, TimeUnit.MILLISECONDS);
        markSuccess(modelName);
        delegate.onCompleteResponse(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        if (!completed) {
            // 上报熔断器失败，标记模型不可用，触发故障转移
            long durationMs = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startTimeNanos);
            circuitBreaker.onError(durationMs, TimeUnit.MILLISECONDS, error);
            markFailure(modelName);
            handleFailover(request, delegate, modelName);
        }
    }
}
```

### 启动失败处理

流式模型启动失败也需标记：

```java
try {
    CircuitBreaker cb = circuitBreakers.get(selectedName);
    selected.streamingModel.chat(request, 
        new FaultTolerantHandler(handler, request, selectedName, cb));
} catch (Exception e) {
    log.warn("流式模型 {} 启动失败: {}", selectedName, e.getMessage());
    markFailure(selectedName); // 标记失败
    handleFailover(request, handler, selectedName);
}
```
