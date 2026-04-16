# 03 - CLAUDE.md 配置指南

> 学会配置项目说明文件，让 Claude 更好地理解你的项目。

## 什么是 CLAUDE.md？

CLAUDE.md 是项目根目录下的说明文件，Claude Code 启动时会自动读取它。它的作用是：

- 让 Claude 理解项目架构
- 提供常用命令参考
- 说明编码规范
- 避免重复解释项目背景

## 创建 CLAUDE.md

### 方法一：使用命令

```bash
/init
```

Claude 会分析项目并自动生成。

### 方法二：手动创建

在项目根目录创建 `CLAUDE.md` 文件。

## 推荐结构

```markdown
# 项目名称

简短描述项目是做什么的。

## 常用命令

### 后端
\`\`\`bash
mvn spring-boot:run    # 启动后端服务
mvn test               # 运行测试
\`\`\`

### 前端
\`\`\`bash
npm run dev            # 启动开发服务器
npm run build          # 构建生产版本
\`\`\`

## 架构说明

描述项目的核心架构：

\`\`\`
src/main/java/com/example/
├── controller/    # REST 接口
├── service/       # 业务逻辑
├── repository/    # 数据访问
└── model/         # 数据模型
\`\`\`

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17, Spring Boot 3.4 |
| 前端 | Vue 3, TypeScript |
| 数据库 | MySQL 8.0 |

## 编码规范

- 使用中文注释
- 遵循阿里巴巴 Java 开发手册
- 所有 public 方法需要 Javadoc

## 注意事项

- 配置文件在 application.yml
- 测试数据库使用 H2
- 敏感信息在 .env 文件中
```

## 实际示例

以下是一个完整示例：

```markdown
# 电商后台管理系统

基于 Spring Boot + Vue 的电商后台管理系统，提供商品、订单、用户管理功能。

## 常用命令

\`\`\`bash
# 后端
mvn spring-boot:run          # 启动后端 (端口 8080)
mvn test                     # 运行测试
mvn test -Dtest=UserTest     # 运行单个测试

# 前端
cd frontend && npm run dev   # 启动前端 (端口 3000)
npm run build                # 构建生产版本

# Docker
docker compose up -d         # 启动所有服务
docker compose logs -f       # 查看日志
\`\`\`

## 架构说明

\`\`\`
后端结构：
src/main/java/com/example/
├── controller/     # REST 接口层
├── service/        # 业务逻辑层
├── repository/     # 数据访问层 (MyBatis-Plus)
├── entity/         # 数据库实体
├── dto/            # 数据传输对象
├── config/         # 配置类
└── util/           # 工具类

前端结构：
frontend/src/
├── views/          # 页面组件
├── components/     # 通用组件
├── api/            # API 调用
├── stores/         # Pinia 状态
└── router/         # 路由配置
\`\`\`

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.4, MyBatis-Plus |
| 前端框架 | Vue 3, Element Plus |
| 数据库 | MySQL 8.0, Redis |
| 认证 | JWT |
| 部署 | Docker, Nginx |

## API 规范

- 统一响应格式：`{ code, message, data }`
- 认证接口：`/api/auth/*`
- 业务接口：`/api/v1/*`
- 需要认证的接口在 Header 添加 `Authorization: Bearer <token>`

## 编码规范

### Java
- 类名：大驼峰 (UserService)
- 方法名：小驼峰 (getUserById)
- 常量：全大写下划线 (MAX_RETRY_COUNT)
- 所有 public 方法添加 Javadoc

### 前端
- 组件名：大驼峰 (UserList.vue)
- 变量名：小驼峰 (userList)
- 使用 TypeScript 类型定义

## 环境配置

| 环境 | 配置文件 | 数据库 |
|------|----------|--------|
| 开发 | application-dev.yml | localhost:3306 |
| 测试 | application-test.yml | test-db:3306 |
| 生产 | application-prod.yml | prod-db:3306 |

切换环境：`spring.profiles.active=dev`

## 注意事项

1. 不要提交 .env 文件
2. 数据库迁移使用 Flyway
3. 代码提交前运行 `mvn test`
4. 敏感操作需要记录日志
\`\`\`
```

## 最佳实践

### DO ✅

- 描述项目的核心概念和术语
- 列出常用命令，节省每次查询的时间
- 说明关键文件的位置
- 记录重要的编码约定

### DON'T ❌

- 写太多细节，保持简洁
- 包含敏感信息（密码、密钥）
- 描述临时状态（正在开发的分支）
- 重复 README 的全部内容

## 小结

现在你知道如何：
- 创建 CLAUDE.md 文件
- 组织项目说明的结构
- 编写有用的项目上下文

下一步：学习《进阶指南/01-工作原理深度解析》
