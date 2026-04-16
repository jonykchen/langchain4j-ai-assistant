# 调试指南

常见问题诊断与解决方案。

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
String result = aiService.chat(id, msg);
log.info("Chat took {}ms", System.currentTimeMillis() - start);
```

**优化方向：**
1. 减少对话记忆窗口
2. 使用流式响应
3. 缓存常用响应

## 调试工具

### Chrome DevTools
- **Network**: 查看请求/响应、SSE 流
- **Vue DevTools**: 检查组件树和状态
- **Performance**: 分析渲染性能

### 后端工具
```bash
# 查看日志
tail -f logs/spring.log

# 测试接口
curl -X POST http://localhost:8082/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"hello","conversationId":"test"}'
```
