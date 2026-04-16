# 代码模式

本项目特有的代码模式和约定。

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
