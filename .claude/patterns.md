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
