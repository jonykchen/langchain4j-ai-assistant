# 项目截图

此目录用于存放项目截图，用于 README 和文档展示。

## 需要的截图

### 1. chat.png - 聊天界面
**内容**: 展示主要聊天功能
- 对话界面
- 流式输出效果
- 思考过程折叠展示
- Markdown 渲染效果

**尺寸**: 1200x800px 或 16:10 比例

### 2. admin.png - 管理后台
**内容**: 展示管理后台功能
- 仪表盘统计
- 用户管理
- 成本监控

**尺寸**: 1200x800px

### 3. monitoring.png - 模型监控
**内容**: 展示监控功能
- Grafana 仪表盘
- Prometheus 指标
- 模型健康状态

**尺寸**: 1200x800px

### 4. tracing.png - Agent 追踪
**内容**: 展示 Agent 执行追踪
- 执行步骤列表
- Span 详情
- Token 使用统计

**尺寸**: 1200x800px

## 截图要求

1. **格式**: PNG 格式
2. **分辨率**: 至少 1200px 宽
3. **内容**: 不要包含敏感信息（API Key、真实数据等）
4. **主题**: 使用暗色主题截图效果更好

## 如何截图

1. 启动项目：
   ```bash
   make docker-up
   make run
   cd frontend && npm run dev
   ```

2. 访问：
   - 前端: http://localhost:5173
   - 管理后台: http://localhost:5173/admin
   - Grafana: http://localhost:3001

3. 使用浏览器开发者工具切换设备模拟器获取最佳尺寸
