# 开发指南

本文档帮助开发者快速搭建本地开发环境。

## 环境要求

| 工具 | 版本 | 必需 | 说明 |
|------|------|------|------|
| JDK | 17+ | ✅ | Java 开发环境 |
| Node.js | 18+ | ✅ | 前端开发 |
| Docker | 24.0+ | ✅ | 本地服务 |
| Maven | 3.8+ | ✅ | 项目构建 |
| Git | 2.0+ | ✅ | 版本控制 |
| IDE | - | 推荐 | IntelliJ IDEA / VS Code |

## 快速开始

### 1. 克隆仓库

```bash
git clone https://github.com/jonykchen/langchain4j-ai-assistant.git
cd langchain4j-ai-assistant
```

### 2. 配置环境变量

```bash
cp .env.example .env
# 编辑 .env 文件，配置必要的环境变量
```

### 3. 启动基础设施

```bash
make docker-up
# 或
docker compose -f docker-compose.dev.yml up -d
```

### 4. 安装依赖

```bash
# 后端
mvn install

# 前端
cd frontend && npm install
```

### 5. 启动应用

```bash
# 后端
make run

# 前端（另一个终端）
cd frontend && npm run dev
```

## IDE 配置

### IntelliJ IDEA

1. 安装插件:
   - Spring Boot Helper
   - Lombok
   - MyBatisX

2. 导入项目:
   - File → Open → 选择项目目录
   - 等待 Maven 导入完成

3. 运行配置:
   - 添加 Spring Boot 运行配置
   - Main class: `com.jonychen.AiApplication`
   - Active profiles: `dev`

### VS Code

1. 安装推荐扩展:
   ```bash
   # 打开命令面板 (Cmd+Shift+P)
   # 输入: Extensions: Show Recommended Extensions
   ```

2. 打开项目:
   ```bash
   code .
   ```

3. 调试配置已预设在 `.vscode/launch.json`

## 代码规范

### Java

- 遵循 Google Java Style Guide
- 使用有意义的变量和方法命名
- 添加必要的 Javadoc 注释
- 单个方法不超过 50 行

### TypeScript/Vue

- 使用 TypeScript 严格模式
- 遵循 Vue 3 Composition API 最佳实践
- 使用 `<script setup>` 语法
- 组件命名使用 PascalCase

### 提交信息

遵循 [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <subject>

[optional body]
```

类型:
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档
- `refactor`: 重构
- `test`: 测试
- `chore`: 其他

## 测试

### 单元测试

```bash
mvn test
```

### 集成测试

```bash
mvn test -Dtest="*IntegrationTest"
```

### E2E 测试

```bash
cd frontend && npm run test:e2e
```

### 性能测试

```bash
mvn gatling:test
```

## 调试

### 后端调试

在 IntelliJ IDEA 中:
1. 设置断点
2. 右键 → Debug 'AiApplication'

在 VS Code 中:
1. 使用预设的调试配置
2. F5 启动调试

### 前端调试

1. 在浏览器中打开 http://localhost:5173
2. 使用浏览器开发者工具

## 常见问题

### Q: 端口被占用？

```bash
# macOS/Linux
lsof -i :8082
kill -9 <PID>

# Windows
netstat -ano | findstr :8082
taskkill /PID <PID> /F
```

### Q: Docker 服务启动失败？

```bash
# 检查 Docker 状态
docker info

# 清理并重启
docker compose -f docker-compose.dev.yml down -v
docker compose -f docker-compose.dev.yml up -d
```

### Q: Maven 依赖下载失败？

```bash
# 清理并重新下载
mvn dependency:purge-local-repository
mvn install -U
```

## 有用的命令

```bash
# 查看所有可用命令
make help

# 格式化代码
make format

# 运行 lint 检查
make lint

# 查看服务健康状态
make health

# 备份数据库
make db-backup
```
