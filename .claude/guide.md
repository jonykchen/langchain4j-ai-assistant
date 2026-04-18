# Claude Code 使用指南

本项目指南，帮助高效使用 Claude Code 进行开发。

## 快速开始

### 启动服务
```bash
# 后端 (端口 8082)
mvn spring-boot:run

# 前端 (端口 5173)
cd frontend && npm run dev
```

### 常用操作
```bash
# 运行测试
mvn test

# Docker 部署
docker compose up -d
```

## 与 Claude 协作的最佳实践

### 1. 提供清晰上下文

**好的提问方式：**
```
"在 frontend/src/components/MessageItem.vue 中，帮我添加代码块复制功能，
参考现有的 handleEdit 方法风格"
```

**不好的方式：**
```
"帮我加个复制功能"
```

### 2. 分步处理复杂任务

```
# 大任务分解
"1. 先分析现有 SSE 流式处理代码
 2. 设计断点续传方案
 3. 实现后端支持
 4. 更新前端适配"
```

### 3. 代码审查

Claude 生成的代码要审查：
- 是否理解每行代码
- 是否符合项目风格
- 是否处理错误情况
- 是否有安全风险

### 4. 迭代优化

```
# 第一轮：快速原型
"帮我实现基础的消息列表组件"

# 第二轮：优化完善
"消息多时滚动卡顿，用虚拟滚动优化"

# 第三轮：细节打磨
"添加消息动画过渡效果"
```

## 项目关键概念

### SSE 流式响应
- 后端返回 `Flux<String>`
- 最后发送 `[DONE]` 标记结束
- 前端使用 ReadableStream 解析

### 对话记忆
- 使用 `@MemoryId` 区分会话
- `MessageWindowChatMemory` 滑动窗口（10条）
- 思考过程用 `<thinking>` 标签包裹

### Vue 响应式
- 嵌套属性更新需要创建新数组/对象
- v-html 内容样式需要非 scoped style
- Tailwind preflight 会移除列表样式

## 常见任务模板

### 添加新 API 接口
```
"在后端添加 GET /api/history 接口，返回对话历史列表，
参考 ChatController.java 的风格，使用 record 定义响应 DTO"
```

### 添加新组件
```
"创建 Vue 组件 frontend/src/components/SettingsPanel.vue，
使用 Element Plus 组件，支持主题切换和模型参数配置"
```

### 修复 Bug
```
"SSE 流在 Safari 下中断，帮我排查并修复。
已知问题：可能和浏览器超时处理有关"
```

### 性能优化
```
"MessageList 组件渲染 1000 条消息时卡顿，
帮我分析原因并优化，优先考虑虚拟滚动"
```

## 避免常见错误

| 错误 | 正确做法 |
|------|----------|
| 直接接受所有代码 | 审查后再使用 |
| 提问过于模糊 | 提供具体上下文 |
| 一次要求太多 | 分步骤完成 |
| 不验证就提交 | 先测试再提交 |

## 相关资源

- [代码模式](./patterns.md) - 项目特有代码模式
- [调试指南](./debugging.md) - 常见问题解决
- [CLAUDE.md](../CLAUDE.md) - 完整项目文档
