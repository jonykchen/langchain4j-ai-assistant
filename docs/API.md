# API 文档

## 基础信息

- 基础路径: `/api`
- 协议: HTTP/1.1
- 数据格式: JSON
- 字符编码: UTF-8

## 认证

所有 API 请求需要携带 JWT Token：

```
Authorization: Bearer <token>
```

## 响应格式

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

### 错误响应

```json
{
  "code": 50206,
  "message": "所有 AI 模型均不可用",
  "data": null
}
```

## 接口列表

### 聊天接口

#### POST /api/chat

同步聊天接口

**请求体**

```json
{
  "conversationId": "uuid",
  "content": "用户消息内容",
  "stream": false
}
```

**响应**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": "AI 回复内容",
    "thinking": "思考过程",
    "modelName": "qwen-plus"
  }
}
```

#### POST /api/chat/stream

流式聊天接口（SSE）

**请求体**

```json
{
  "conversationId": "uuid",
  "content": "用户消息内容",
  "stream": true
}
```

**响应** (Server-Sent Events)

```
event: token
data: {"content": "AI"}

event: token
data: {"content": "回复"}

event: done
data: [DONE]
```

### 健康检查

#### GET /api/health/models

获取所有模型健康状态

**响应**

```json
{
  "code": 200,
  "data": {
    "dashscope": {
      "healthy": true,
      "lastCheck": "2025-06-02T10:00:00Z",
      "failureCount": 0
    },
    "zhipu": {
      "healthy": true,
      "lastCheck": "2025-06-02T10:00:00Z",
      "failureCount": 0
    }
  }
}
```

#### GET /api/health/circuit-breakers

获取所有熔断器状态

### 用户管理

#### GET /api/users/me

获取当前用户信息

#### PUT /api/users/me

更新用户信息

### 管理接口 (需要 ADMIN 角色)

#### GET /api/admin/users

获取用户列表

#### GET /api/admin/stats/summary

获取统计摘要

#### POST /api/admin/test/e2e/run

运行 E2E 测试

#### POST /api/admin/test/performance/run

运行性能测试

#### POST /api/admin/test/ai/run

运行 AI 模型测试

## 错误码

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未授权 |
| 403 | 权限不足 |
| 404 | 资源不存在 |
| 429 | 请求过快（限流） |
| 500 | 服务器内部错误 |
| 50200 | AI 服务异常 |
| 50206 | 所有模型不可用 |

## 限流规则

| 接口 | 限制 | 时间窗口 |
|------|------|----------|
| /api/chat | 20 次 | 60 秒 |
| /api/chat/stream | 30 次 | 60 秒 |
