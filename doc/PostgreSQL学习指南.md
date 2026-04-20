# PostgreSQL 学习指南（Agent 开发向）

> 本文档面向 Agent 开发者，从零基础到实战应用，包含原理讲解和项目实例。

---

## 目录

1. [第一章：PostgreSQL 简介](#第一章postgresql-简介)
2. [第二章：基础 SQL 操作](#第二章基础-sql-操作)
3. [第三章：数据类型详解](#第三章数据类型详解)
4. [第四章：索引与性能优化](#第四章索引与性能优化)
5. [第五章：高级特性](#第五章高级特性)
6. [第六章：pgvector 向量检索](#第六章pgvector-向量检索)
7. [第七章：Agent 开发实战案例](#第七章agent-开发实战案例)
8. [第八章：最佳实践与避坑指南](#第八章最佳实践与避坑指南)

---

## 第一章：PostgreSQL 简介

### 1.1 什么是 PostgreSQL？

PostgreSQL（简称 Postgres）是一个功能强大的开源关系型数据库，被誉为"世界上最先进的开源数据库"。

**核心特点：**
- **ACID 合规**：原子性、一致性、隔离性、持久性，保证数据安全
- **MVCC 架构**：多版本并发控制，读写不阻塞
- **扩展性强**：支持自定义类型、函数、索引方法
- **丰富的数据类型**：JSON、数组、几何类型、向量等

### 1.2 PostgreSQL vs MySQL

| 特性 | PostgreSQL | MySQL |
|------|-----------|-------|
| JSON 支持 | JSONB（二进制，高效查询） | JSON（文本解析） |
| 向量检索 | pgvector 原生支持 | 需要额外插件 |
| 全文搜索 | 内置，支持中文分词 | 需要第三方引擎 |
| 复杂查询 | 更强（窗口函数、CTE） | 基础支持 |
| 事务隔离 | 完整的 4 种级别 | 默认 REPEATABLE READ |

### 1.3 为什么 Agent 开发选择 PostgreSQL？

```
┌─────────────────────────────────────────────────────────────┐
│                    Agent 系统数据需求                         │
├─────────────────────────────────────────────────────────────┤
│  1. 用户会话存储 → 关系型表（users, conversations）           │
│  2. 灵活元数据   → JSONB（metadata 字段）                     │
│  3. RAG 检索     → pgvector 向量相似度搜索                    │
│  4. 审计日志     → 高效时序索引（created_at 索引）             │
│  5. 多租户隔离   → Row Level Security                        │
└─────────────────────────────────────────────────────────────┘
```

### 1.4 安装与连接

**Docker 快速启动（本项目方式）：**

```bash
# 启动 PostgreSQL 容器
docker run -d \
  --name postgres \
  -e POSTGRES_USER=langchain4j \
  -e POSTGRES_PASSWORD=REDACTED_DB_PASSWORD \
  -e POSTGRES_DB=langchain4j \
  -p 5432:5432 \
  postgres:16

# 连接数据库
docker exec -it postgres psql -U langchain4j -d langchain4j
```

**常用连接工具：**
- 命令行：`psql`
- GUI：DBeaver、DataGrip、pgAdmin
- 项目集成：Spring Boot + Spring Data JPA

---

## 第二章：基础 SQL 操作

### 2.1 数据库与 Schema

```sql
-- 查看当前数据库
SELECT current_database();

-- 创建 Schema（命名空间）
CREATE SCHEMA IF NOT EXISTS app;

-- 设置默认搜索路径
ALTER DATABASE langchain4j SET search_path TO app, public;

-- 查看 search_path
SHOW search_path;
```

**Schema 的作用：**
- 组织表结构，避免命名冲突
- 多租户架构中隔离不同客户数据
- 权限控制的粒度更细

### 2.2 创建表（CREATE TABLE）

**基本语法：**

```sql
CREATE TABLE 表名 (
    字段名 数据类型 [约束],
    ...
);
```

**项目实例 - 用户表：**

```sql
CREATE TABLE IF NOT EXISTS app.users (
    id              BIGSERIAL       PRIMARY KEY,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    email           VARCHAR(100)    NOT NULL UNIQUE,
    password        VARCHAR(255),
    nickname        VARCHAR(100),
    avatar          VARCHAR(500),
    status          VARCHAR(20)     DEFAULT 'ACTIVE',
    provider        VARCHAR(50),
    provider_id     VARCHAR(100),
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);
```

**约束类型：**

| 约束 | 说明 | 示例 |
|------|------|------|
| `PRIMARY KEY` | 主键，唯一且非空 | `id BIGSERIAL PRIMARY KEY` |
| `NOT NULL` | 不能为空 | `username VARCHAR(50) NOT NULL` |
| `UNIQUE` | 值必须唯一 | `email VARCHAR(100) UNIQUE` |
| `DEFAULT` | 默认值 | `status VARCHAR(20) DEFAULT 'ACTIVE'` |
| `REFERENCES` | 外键 | `user_id BIGINT REFERENCES users(id)` |
| `CHECK` | 自定义约束 | `CHECK (age >= 18)` |

### 2.3 插入数据（INSERT）

**单条插入：**

```sql
INSERT INTO app.users (username, email, nickname, status)
VALUES ('zhangsan', 'zhangsan@example.com', '张三', 'ACTIVE');
```

**批量插入：**

```sql
INSERT INTO app.users (username, email, nickname) VALUES
    ('lisi', 'lisi@example.com', '李四'),
    ('wangwu', 'wangwu@example.com', '王五'),
    ('zhaoliu', 'zhaoliu@example.com', '赵六');
```

**冲突处理（UPSERT）：**

```sql
-- 如果用户名冲突，则更新邮箱
INSERT INTO app.users (username, email, nickname)
VALUES ('zhangsan', 'new@example.com', '张三')
ON CONFLICT (username) 
DO UPDATE SET 
    email = EXCLUDED.email,
    updated_at = CURRENT_TIMESTAMP;
```

### 2.4 查询数据（SELECT）

**基础查询：**

```sql
-- 查询所有字段
SELECT * FROM app.users;

-- 查询指定字段
SELECT username, email, nickname FROM app.users;

-- 条件过滤
SELECT * FROM app.users WHERE status = 'ACTIVE';

-- 多条件组合
SELECT * FROM app.users 
WHERE status = 'ACTIVE' AND created_at > '2024-01-01';

-- 排序与分页
SELECT * FROM app.users 
ORDER BY created_at DESC 
LIMIT 10 OFFSET 0;
```

**LIKE 模糊查询：**

```sql
-- 查找昵称包含"张"的用户
SELECT * FROM app.users WHERE nickname LIKE '%张%';

-- 查找邮箱以 @example.com 结尾的用户
SELECT * FROM app.users WHERE email LIKE '%@example.com';
```

### 2.5 更新数据（UPDATE）

```sql
-- 更新单个字段
UPDATE app.users SET status = 'LOCKED' WHERE username = 'zhangsan';

-- 更新多个字段
UPDATE app.users 
SET 
    nickname = '新昵称',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

-- 批量更新
UPDATE app.users 
SET status = 'INACTIVE' 
WHERE last_login_at < CURRENT_TIMESTAMP - INTERVAL '90 days';
```

### 2.6 删除数据（DELETE）

```sql
-- 删除指定记录
DELETE FROM app.users WHERE id = 1;

-- 条件删除
DELETE FROM app.users WHERE status = 'INACTIVE';

-- 清空表（保留结构）
TRUNCATE TABLE app.users;
```

### 2.7 表操作（ALTER TABLE）

```sql
-- 添加字段
ALTER TABLE app.users ADD COLUMN phone VARCHAR(20);

-- 修改字段类型
ALTER TABLE app.users ALTER COLUMN phone TYPE VARCHAR(50);

-- 添加约束
ALTER TABLE app.users ADD CONSTRAINT uk_phone UNIQUE (phone);

-- 删除字段
ALTER TABLE app.users DROP COLUMN phone;

-- 重命名表
ALTER TABLE app.users RENAME TO user_account;

-- 添加字段注释
COMMENT ON COLUMN app.users.status IS '账户状态：ACTIVE-正常, LOCKED-锁定';
```

---

## 第三章：数据类型详解

### 3.1 数值类型

| 类型 | 说明 | 范围 |
|------|------|------|
| `SMALLINT` | 小整数 | -32,768 到 32,767 |
| `INTEGER` | 整数 | -2^31 到 2^31-1 |
| `BIGINT` | 大整数 | -2^63 到 2^63-1 |
| `SERIAL` | 自增整数 | 1 到 2^31-1 |
| `BIGSERIAL` | 自增大整数 | 1 到 2^63-1 |
| `DECIMAL(p,s)` | 精确小数 | 精度可配置 |

**Agent 开发中的应用：**

```sql
-- Token 消费统计：需要精确小数
CREATE TABLE token_usage_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    input_tokens    INTEGER DEFAULT 0,
    output_tokens   INTEGER DEFAULT 0,
    cost            DECIMAL(10,6) DEFAULT 0,  -- 精确到百万分位
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

### 3.2 字符串类型

| 类型 | 说明 | 最大长度 |
|------|------|----------|
| `CHAR(n)` | 定长字符串 | n |
| `VARCHAR(n)` | 变长字符串 | n |
| `TEXT` | 无限长文本 | 无限制 |

**推荐：**
- 固定长度用 `CHAR`（如 MD5 哈希：`CHAR(32)`）
- 有最大长度限制用 `VARCHAR`
- 无长度限制用 `TEXT`（如消息内容）

```sql
-- 项目实例
CREATE TABLE app.messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,  -- UUID 格式
    role            VARCHAR(20) NOT NULL,   -- system/user/assistant
    content         TEXT,                    -- 消息内容，无长度限制
    model           VARCHAR(100)             -- 模型名称
);
```

### 3.3 时间类型

| 类型 | 说明 | 时区 |
|------|------|------|
| `DATE` | 日期（年月日） | - |
| `TIME` | 时间（时分秒） | - |
| `TIMESTAMP` | 日期时间 | 无时区 |
| `TIMESTAMPTZ` | 日期时间 | 有时区 |

**Agent 开发推荐：始终使用 `TIMESTAMPTZ`**

```sql
-- 正确做法：使用时区感知类型
created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP

-- 时间计算示例
SELECT 
    created_at,
    created_at + INTERVAL '7 days' AS expire_at,
    EXTRACT(EPOCH FROM created_at) AS unix_timestamp
FROM app.users;

-- 获取当前时间
SELECT 
    CURRENT_TIMESTAMP,           -- 带时区
    CURRENT_DATE,                 -- 当前日期
    CURRENT_TIME,                 -- 当前时间
    NOW();                        -- 等同于 CURRENT_TIMESTAMP
```

### 3.4 JSON 与 JSONB（重点）

**JSON vs JSONB：**

| 特性 | JSON | JSONB |
|------|------|-------|
| 存储 | 原始文本 | 二进制 |
| 写入速度 | 快 | 慢（需解析） |
| 查询速度 | 慢（每次解析） | 快（索引支持） |
| 空格保留 | 保留 | 移除 |
| 键顺序 | 保留 | 重排 |

**推荐：查询场景用 JSONB，仅存储用 JSON**

**项目实例 - 会话元数据：**

```sql
-- 创建表
CREATE TABLE app.conversations (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    title       VARCHAR(255),
    metadata    JSONB,  -- 灵活存储扩展信息
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 插入 JSONB 数据
INSERT INTO app.conversations (id, user_id, title, metadata)
VALUES (
    'conv-001', 
    1, 
    'AI助手对话',
    '{
        "model": "qwen-plus",
        "temperature": 0.7,
        "max_tokens": 4096,
        "tags": ["开发", "AI"],
        "user_settings": {
            "language": "zh-CN",
            "response_style": "detailed"
        }
    }'
);
```

**JSONB 查询操作：**

```sql
-- 1. 提取字段（-> 返回 JSON，->> 返回文本）
SELECT 
    metadata->>'model' AS model,
    metadata->'user_settings'->>'language' AS language
FROM app.conversations;

-- 2. 条件查询
SELECT * FROM app.conversations 
WHERE metadata->>'model' = 'qwen-plus';

-- 3. JSONB 包含查询（@>）
SELECT * FROM app.conversations 
WHERE metadata @> '{"model": "qwen-plus"}';

-- 4. 检查键是否存在
SELECT * FROM app.conversations 
WHERE metadata ? 'tags';

-- 5. 数组元素查询
SELECT * FROM app.conversations 
WHERE metadata->'tags' ? '开发';

-- 6. 更新 JSONB 字段
UPDATE app.conversations 
SET metadata = metadata || '{"temperature": 0.8}'
WHERE id = 'conv-001';

-- 删除 JSONB 键
UPDATE app.conversations 
SET metadata = metadata - 'tags'
WHERE id = 'conv-001';
```

**JSONB 索引：**

```sql
-- GIN 索引（支持 @> ? ?| ?& 操作）
CREATE INDEX idx_conversations_metadata 
ON app.conversations USING GIN (metadata);

-- 特定路径索引
CREATE INDEX idx_conversations_model 
ON app.conversations ((metadata->>'model'));
```

### 3.5 数组类型

```sql
-- 创建带数组的表
CREATE TABLE app.documents (
    id          VARCHAR(36) PRIMARY KEY,
    title       VARCHAR(255),
    tags        VARCHAR(50)[],  -- 字符串数组
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- 插入数据
INSERT INTO app.documents (id, title, tags)
VALUES ('doc-001', 'PostgreSQL 指南', ARRAY['数据库', '教程', '入门']);

-- 查询数组
SELECT * FROM app.documents WHERE '数据库' = ANY(tags);

-- 数组包含（@>）
SELECT * FROM app.documents WHERE tags @> ARRAY['教程'];

-- 数组长度
SELECT title, array_length(tags, 1) AS tag_count FROM app.documents;

-- 展开数组（一行变多行）
SELECT title, unnest(tags) AS tag FROM app.documents;
```

---

## 第四章：索引与性能优化

### 4.1 索引类型

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| B-Tree | 默认类型，平衡树 | 等值、范围、排序查询 |
| Hash | 哈希索引 | 仅等值查询 |
| GiST | 通用搜索树 | 几何、全文搜索 |
| GIN | 倒排索引 | JSONB、数组、全文搜索 |
| BRIN | 块范围索引 | 大表时序数据 |

### 4.2 创建索引

```sql
-- 普通索引
CREATE INDEX idx_users_email ON app.users(email);

-- 复合索引（多列）
CREATE INDEX idx_messages_conv_time ON app.messages(conversation_id, created_at);

-- 唯一索引
CREATE UNIQUE INDEX idx_users_username ON app.users(username);

-- 部分索引（条件索引）
CREATE INDEX idx_active_users ON app.users(email) WHERE status = 'ACTIVE';

-- 表达式索引
CREATE INDEX idx_users_email_lower ON app.users(LOWER(email));
```

### 4.3 项目中的索引实践

```sql
-- 消息表：按会话和时间查询是高频操作
CREATE INDEX idx_messages_conversation ON app.messages(conversation_id);
CREATE INDEX idx_messages_created_at ON app.messages(created_at);
-- 复合索引更适合 "查询某会话的最近消息"
CREATE INDEX idx_messages_conv_time ON app.messages(conversation_id, created_at DESC);

-- Token 使用日志：按用户和时间范围查询
CREATE INDEX idx_token_usage_user_time ON app.token_usage_logs(user_id, request_time);

-- 工具审计表：多维度查询
CREATE INDEX idx_tool_audits_tool ON app.tool_execution_audits(tool_name);
CREATE INDEX idx_tool_audits_status ON app.tool_execution_audits(status);
CREATE INDEX idx_tool_audits_time ON app.tool_execution_audits(created_at);
```

### 4.4 EXPLAIN 分析查询

```sql
-- 查看执行计划
EXPLAIN SELECT * FROM app.messages WHERE conversation_id = 'conv-001';

-- 查看实际执行统计
EXPLAIN ANALYZE SELECT * FROM app.messages WHERE conversation_id = 'conv-001';

-- 输出示例
-- Index Scan using idx_messages_conversation on messages  (cost=0.29..8.31 rows=1 width=100) (actual time=0.015..0.016 rows=1 loops=1)
--   Index Cond: (conversation_id = 'conv-001'::text)
-- Planning Time: 0.050 ms
-- Execution Time: 0.035 ms
```

**关键指标：**
- `Seq Scan`：全表扫描，大数据量时需优化
- `Index Scan`：索引扫描，效率高
- `Bitmap Scan`：多位图扫描，适合多条件
- `rows`：预估行数，与实际差距大需更新统计信息

### 4.5 索引使用原则

```
┌─────────────────────────────────────────────────────────────┐
│                      索引设计原则                            │
├─────────────────────────────────────────────────────────────┤
│  1. 遵循最左前缀原则（复合索引）                              │
│     INDEX(a, b) 支持 WHERE a=? AND b=? 或 WHERE a=?           │
│     但不支持 WHERE b=?                                        │
│                                                              │
│  2. 高选择性列优先                                           │
│     性别（低选择性）不建议单独索引                            │
│     邮箱、用户名（高选择性）适合索引                          │
│                                                              │
│  3. 避免过度索引                                             │
│     索引占用空间，影响写入性能                                │
│     推荐：每表 5-10 个索引                                    │
│                                                              │
│  4. 定期维护                                                 │
│     ANALYZE 更新统计信息                                     │
│     REINDEX 重建碎片化索引                                    │
└─────────────────────────────────────────────────────────────┘
```

---

## 第五章：高级特性

### 5.1 事务与隔离级别

```sql
-- 开始事务
BEGIN;

-- 操作
INSERT INTO app.users (username, email) VALUES ('test', 'test@example.com');
INSERT INTO app.user_roles (user_id, role_id) VALUES (1, 1);

-- 提交
COMMIT;

-- 回滚
ROLLBACK;

-- 设置隔离级别
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
```

**隔离级别对比：**

| 级别 | 脏读 | 不可重复读 | 幻读 |
|------|------|-----------|------|
| READ UNCOMMITTED | ✓ | ✓ | ✓ |
| READ COMMITTED | ✗ | ✓ | ✓ |
| REPEATABLE READ | ✗ | ✗ | ✓ |
| SERIALIZABLE | ✗ | ✗ | ✗ |

### 5.2 触发器（Trigger）

**自动更新 `updated_at` 字段：**

```sql
-- 创建触发器函数
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建触发器
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON app.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- 现在更新数据时，updated_at 自动更新
UPDATE app.users SET nickname = '新昵称' WHERE id = 1;
-- updated_at 会自动设置为当前时间
```

### 5.3 存储过程与函数

```sql
-- 创建函数：统计用户 Token 消耗
CREATE OR REPLACE FUNCTION get_user_token_usage(
    p_user_id BIGINT,
    p_start_date TIMESTAMPTZ DEFAULT CURRENT_DATE,
    p_end_date TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
)
RETURNS TABLE (
    total_input BIGINT,
    total_output BIGINT,
    total_cost NUMERIC
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        COALESCE(SUM(input_tokens), 0)::BIGINT,
        COALESCE(SUM(output_tokens), 0)::BIGINT,
        COALESCE(SUM(cost), 0)::NUMERIC
    FROM app.token_usage_logs
    WHERE user_id = p_user_id
      AND request_time BETWEEN p_start_date AND p_end_date;
END;
$$ LANGUAGE plpgsql;

-- 调用函数
SELECT * FROM get_user_token_usage(1, '2024-01-01', '2024-12-31');
```

### 5.4 视图（View）

```sql
-- 创建视图：用户消费汇总
CREATE VIEW v_user_consumption AS
SELECT 
    u.id AS user_id,
    u.username,
    COUNT(l.id) AS request_count,
    COALESCE(SUM(l.input_tokens), 0) AS total_input_tokens,
    COALESCE(SUM(l.output_tokens), 0) AS total_output_tokens,
    COALESCE(SUM(l.cost), 0) AS total_cost
FROM app.users u
LEFT JOIN app.token_usage_logs l ON u.id = l.user_id
GROUP BY u.id, u.username;

-- 使用视图
SELECT * FROM v_user_consumption WHERE total_cost > 10;
```

### 5.5 行级安全（Row Level Security）

**Agent 多租户场景必备：**

```sql
-- 启用行级安全
ALTER TABLE app.conversations ENABLE ROW LEVEL SECURITY;

-- 创建策略：用户只能看到自己的会话
CREATE POLICY conversations_isolation ON app.conversations
    USING (user_id = current_setting('app.current_user_id')::BIGINT);

-- 在应用层设置当前用户
SET app.current_user_id = '1';

-- 现在查询自动过滤，只返回 user_id = 1 的数据
SELECT * FROM app.conversations;
```

---

## 第六章：pgvector 向量检索

### 6.1 向量检索原理

**为什么 Agent 开发需要向量检索？**

```
┌──────────────────────────────────────────────────────────────────┐
│                      RAG（检索增强生成）流程                       │
├──────────────────────────────────────────────────────────────────┤
│                                                                   │
│   用户提问 ──→ 文本转向量 ──→ 向量相似度搜索 ──→ 返回相关文档       │
│                    │                    │                         │
│                    ↓                    ↓                         │
│              Embedding 模型        pgvector                       │
│              (如 text-embedding      HNSW 索引                    │
│               -ada-002)             余弦相似度                    │
│                                                                   │
│   相似度高的文档 + 用户提问 ──→ LLM ──→ 生成回答                   │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

**向量相似度计算：**
- **余弦相似度**：方向相似度，范围 [-1, 1]
- **欧氏距离**：空间距离，范围 [0, ∞)
- **内积**：投影相似度

### 6.2 pgvector 安装与配置

```sql
-- 安装扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 查看版本
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
```

### 6.3 创建向量表

```sql
-- 文档分块表（RAG 核心）
CREATE TABLE IF NOT EXISTS app.document_chunks (
    id              VARCHAR(36)     PRIMARY KEY,
    document_id     VARCHAR(36)     NOT NULL,
    content         TEXT            NOT NULL,
    chunk_index     INTEGER         NOT NULL,
    token_count     INTEGER         DEFAULT 0,
    embedding       vector(1536),   -- OpenAI ada-002 是 1536 维
    metadata        JSONB,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);
```

**向量维度选择：**

| Embedding 模型 | 维度 | 适用场景 |
|---------------|------|----------|
| OpenAI text-embedding-ada-002 | 1536 | 通用 |
| OpenAI text-embedding-3-small | 1536 | 通用 |
| OpenAI text-embedding-3-large | 3072 | 高精度 |
| BGE-large-zh | 1024 | 中文 |
| m3e-base | 768 | 中文轻量 |

### 6.4 向量索引

**HNSW 索引（推荐）：**

```sql
CREATE INDEX idx_document_chunks_embedding
ON app.document_chunks
USING hnsw (embedding vector_cosine_ops)
WITH (
    m = 16,              -- 每层连接数，越大越精确，占用更多内存
    ef_construction = 64 -- 构建时搜索范围，越大构建越慢，质量越好
);
```

**IVFFlat 索引：**

```sql
CREATE INDEX idx_document_chunks_embedding_ivf
ON app.document_chunks
USING ivfflat (embedding vector_cosine_ops)
WITH (
    lists = 100  -- 聚类中心数，建议 sqrt(行数)
);
```

**索引对比：**

| 特性 | HNSW | IVFFlat |
|------|------|---------|
| 构建速度 | 慢 | 快 |
| 查询速度 | 快 | 中等 |
| 内存占用 | 较高 | 较低 |
| 精确度 | 高 | 需要 probes 调整 |
| 适用场景 | 实时查询 | 批量导入后查询 |

### 6.5 向量查询

```sql
-- 1. 余弦相似度搜索（推荐）
SELECT 
    id,
    content,
    1 - (embedding <=> '[0.1, 0.2, ...]'::vector) AS similarity
FROM app.document_chunks
ORDER BY embedding <=> '[0.1, 0.2, ...]'::vector
LIMIT 5;

-- 2. 欧氏距离搜索
SELECT id, content
FROM app.document_chunks
ORDER BY embedding <-> '[0.1, 0.2, ...]'::vector
LIMIT 5;

-- 3. 内积搜索（需要归一化向量）
SELECT id, content
FROM app.document_chunks
ORDER BY embedding <#> '[0.1, 0.2, ...]'::vector
LIMIT 5;

-- 4. 带条件的相似度搜索
SELECT 
    d.title,
    c.content,
    1 - (c.embedding <=> '[0.1, 0.2, ...]'::vector) AS similarity
FROM app.document_chunks c
JOIN app.documents d ON c.document_id = d.id
WHERE d.type = 'pdf'
  AND c.metadata->>'page' = '1'
ORDER BY c.embedding <=> '[0.1, 0.2, ...]'::vector
LIMIT 5;
```

**距离运算符：**

| 运算符 | 含义 | 适用场景 |
|--------|------|----------|
| `<=>` | 余弦距离 | 文本语义相似度 |
| `<->` | 欧氏距离 | 空间距离 |
| `<#>` | 内积 | 负内积（需要向量归一化） |

### 6.6 项目实战：RAG 文档检索

```sql
-- 1. 插入文档向量
INSERT INTO app.document_chunks (id, document_id, content, chunk_index, embedding, metadata)
VALUES (
    'chunk-001',
    'doc-001',
    'PostgreSQL 是一个功能强大的开源数据库...',
    0,
    '[0.123, -0.456, ...]'::vector,
    '{"page": 1, "source": "manual.pdf"}'
);

-- 2. 查询最相关的 5 个文档块
WITH query_embedding AS (
    SELECT '[0.11, -0.22, 0.33, ...]'::vector AS emb
)
SELECT 
    c.id,
    c.content,
    d.title,
    d.source,
    1 - (c.embedding <=> q.emb) AS similarity,
    c.metadata->>'page' AS page_number
FROM app.document_chunks c
JOIN app.documents d ON c.document_id = d.id
CROSS JOIN query_embedding q
WHERE d.status = 'active'
ORDER BY c.embedding <=> q.emb
LIMIT 5;

-- 3. 按文档去重，获取最相关的文档
SELECT DISTINCT ON (c.document_id)
    d.id AS document_id,
    d.title,
    c.content AS best_match,
    1 - (c.embedding <=> '[0.1, ...]'::vector) AS similarity
FROM app.document_chunks c
JOIN app.documents d ON c.document_id = d.id
ORDER BY c.document_id, c.embedding <=> '[0.1, ...]'::vector;
```

---

## 第七章：Agent 开发实战案例

### 7.1 完整的会话管理系统

```sql
-- 创建会话
INSERT INTO app.conversations (id, user_id, title, model, metadata)
VALUES (
    gen_random_uuid()::TEXT,
    1,
    'AI 编程助手对话',
    'qwen-plus',
    '{"temperature": 0.7, "max_tokens": 4096}'
);

-- 添加消息
INSERT INTO app.messages (conversation_id, role, content, token_count)
VALUES 
    ('conv-id', 'system', '你是一个帮助用户编程的 AI 助手', 15),
    ('conv-id', 'user', '请帮我写一个快速排序算法', 12);

-- 查询会话完整消息（按时间正序）
SELECT 
    id, role, content, token_count, created_at
FROM app.messages
WHERE conversation_id = 'conv-id'
ORDER BY created_at ASC;

-- 获取用户最近的会话列表
SELECT 
    c.id,
    c.title,
    c.model,
    c.created_at,
    (SELECT content FROM app.messages m 
     WHERE m.conversation_id = c.id 
       AND m.role = 'user'
     ORDER BY m.created_at DESC 
     LIMIT 1) AS last_message
FROM app.conversations c
WHERE c.user_id = 1
ORDER BY c.updated_at DESC
LIMIT 20;
```

### 7.2 Token 使用统计与限流

```sql
-- 记录 Token 使用
INSERT INTO app.token_usage_logs (
    user_id, conversation_id, message_id,
    model, model_provider, input_tokens, output_tokens, cost, latency_ms
) VALUES (
    1, 'conv-id', 123,
    'qwen-plus', 'dashscope', 150, 320, 0.0047, 1850
);

-- 查询用户今日消费
SELECT 
    SUM(input_tokens) AS input,
    SUM(output_tokens) AS output,
    SUM(total_tokens) AS total,
    SUM(cost) AS cost
FROM app.token_usage_logs
WHERE user_id = 1
  AND request_time::DATE = CURRENT_DATE;

-- 查询用户本月消费（按天分组）
SELECT 
    request_time::DATE AS date,
    COUNT(*) AS request_count,
    SUM(input_tokens) AS input_tokens,
    SUM(output_tokens) AS output_tokens,
    SUM(cost) AS cost
FROM app.token_usage_logs
WHERE user_id = 1
  AND request_time >= DATE_TRUNC('month', CURRENT_DATE)
GROUP BY request_time::DATE
ORDER BY date;

-- 检查用户是否超过限额
SELECT 
    u.id AS user_id,
    q.daily_token_limit,
    q.monthly_token_limit,
    COALESCE(daily_usage.used, 0) AS daily_used,
    COALESCE(monthly_usage.used, 0) AS monthly_used
FROM app.users u
JOIN app.user_quotas q ON u.id = q.user_id
LEFT JOIN (
    SELECT user_id, SUM(total_tokens) AS used
    FROM app.token_usage_logs
    WHERE request_time::DATE = CURRENT_DATE
    GROUP BY user_id
) daily_usage ON u.id = daily_usage.user_id
LEFT JOIN (
    SELECT user_id, SUM(total_tokens) AS used
    FROM app.token_usage_logs
    WHERE request_time >= DATE_TRUNC('month', CURRENT_DATE)
    GROUP BY user_id
) monthly_usage ON u.id = monthly_usage.user_id
WHERE u.id = 1;
```

### 7.3 工具执行审计

```sql
-- 记录工具执行
INSERT INTO app.tool_execution_audits (
    execution_id, tool_name, tool_category,
    user_id, conversation_id, message_id,
    input, input_hash, output, output_hash,
    status, execution_time_ms, risk_level
) VALUES (
    'exec-001',
    'web_search',
    'EXTERNAL',
    1,
    'conv-id',
    123,
    '{"query": "PostgreSQL tutorial"}',
    'a1b2c3d4',
    '{"results": [...]}',
    'e5f6g7h8',
    'success',
    1250,
    'LOW'
);

-- 查询用户工具调用历史
SELECT 
    tool_name,
    COUNT(*) AS call_count,
    AVG(execution_time_ms) AS avg_time,
    SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) AS success_count,
    SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) AS failed_count
FROM app.tool_execution_audits
WHERE user_id = 1
  AND created_at >= CURRENT_TIMESTAMP - INTERVAL '7 days'
GROUP BY tool_name
ORDER BY call_count DESC;

-- 高风险操作审计
SELECT 
    id, execution_id, tool_name, input, 
    risk_level, confirmed, confirmed_by,
    created_at
FROM app.tool_execution_audits
WHERE risk_level IN ('HIGH', 'CRITICAL')
  AND created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
ORDER BY created_at DESC;
```

### 7.4 RAG 知识库管理

```sql
-- 创建文档
INSERT INTO app.documents (id, title, source, source_type, type, status, metadata)
VALUES (
    'doc-001',
    'PostgreSQL 官方文档',
    'https://www.postgresql.org/docs/',
    'url',
    'md',
    'active',
    '{"language": "zh-CN", "version": "16"}'
);

-- 批量插入文档块（使用 UNNEST）
INSERT INTO app.document_chunks (id, document_id, content, chunk_index, embedding, metadata)
SELECT 
    gen_random_uuid()::TEXT,
    'doc-001',
    content,
    idx,
    emb,
    meta
FROM UNNEST(
    ARRAY['内容1...', '内容2...', '内容3...'],
    ARRAY[0, 1, 2],
    ARRAY['[0.1,...]'::vector, '[0.2,...]'::vector, '[0.3,...]'::vector],
    ARRAY['{"page":1}'::jsonb, '{"page":2}'::jsonb, '{"page":3}'::jsonb]
) AS t(content, idx, emb, meta);

-- 混合检索：向量 + 全文
WITH vector_results AS (
    SELECT 
        c.id,
        c.content,
        c.document_id,
        1 - (c.embedding <=> '[0.1,...]'::vector) AS vec_score
    FROM app.document_chunks c
    ORDER BY c.embedding <=> '[0.1,...]'::vector
    LIMIT 20
),
text_results AS (
    SELECT 
        c.id,
        c.content,
        c.document_id,
        ts_rank_cd(c.content_tsv, query) AS text_score
    FROM app.document_chunks c,
         websearch_to_tsquery('zh', 'PostgreSQL 索引') query
    WHERE c.content_tsv @@ query
    ORDER BY text_score DESC
    LIMIT 20
)
SELECT 
    COALESCE(v.id, t.id) AS chunk_id,
    COALESCE(v.content, t.content) AS content,
    COALESCE(v.document_id, t.document_id) AS document_id,
    COALESCE(v.vec_score, 0) AS vec_score,
    COALESCE(t.text_score, 0) AS text_score,
    COALESCE(v.vec_score, 0) * 0.7 + COALESCE(t.text_score, 0) * 0.3 AS combined_score
FROM vector_results v
FULL OUTER JOIN text_results t ON v.id = t.id
ORDER BY combined_score DESC
LIMIT 5;
```

---

## 第八章：最佳实践与避坑指南

### 8.1 表设计规范

```
┌────────────────────────────────────────────────────────────────┐
│                     表设计最佳实践                               │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. 主键选择                                                    │
│     - 自增 ID：BIGSERIAL（简单、高效）                           │
│     - UUID：VARCHAR(36) 或 UUID 类型（分布式、不暴露业务量）     │
│     - 雪花 ID：BIGINT（时序、分布式）                            │
│                                                                 │
│  2. 必要字段                                                    │
│     - created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP          │
│     - updated_at TIMESTAMPTZ（配合触发器自动更新）                │
│     - version BIGINT DEFAULT 0（乐观锁）                         │
│                                                                 │
│  3. 字段命名                                                    │
│     - 使用 snake_case：user_id, created_at                       │
│     - 避免保留字：user, order, type 等                          │
│     - 布尔字段前缀：is_active, has_permission                    │
│                                                                 │
│  4. JSONB 使用场景                                              │
│     - 扩展属性（不同类型有不同字段）                             │
│     - 无需索引的配置项                                          │
│     - 频繁变化的属性                                            │
│     - 避免过度使用，核心字段仍用独立列                           │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### 8.2 索引优化建议

```sql
-- 当前索引使用情况
SELECT 
    schemaname,
    tablename,
    indexname,
    idx_scan AS index_scans,
    idx_tup_read AS tuples_read,
    idx_tup_fetch AS tuples_fetched
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;

-- 未使用的索引
SELECT 
    schemaname || '.' || relname AS table,
    indexrelname AS index,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size,
    idx_scan AS scans
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND indexrelname NOT LIKE '%_pkey'
ORDER BY pg_relation_size(indexrelid) DESC;

-- 更新统计信息（定期执行）
ANALYZE;
```

### 8.3 连接池配置

**Spring Boot 配置（application.yml）：**

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20        # 最大连接数
      minimum-idle: 5              # 最小空闲连接
      connection-timeout: 30000    # 连接超时（ms）
      idle-timeout: 600000        # 空闲超时（ms）
      max-lifetime: 1800000       # 连接最大存活时间（ms）
      leak-detection-threshold: 60000  # 连接泄露检测
```

### 8.4 常见问题与解决

#### Q1: 查询慢怎么办？

```sql
-- 1. 使用 EXPLAIN ANALYZE 分析
EXPLAIN ANALYZE SELECT * FROM messages WHERE conversation_id = 'xxx';

-- 2. 检查是否走索引
-- 看到 Seq Scan（全表扫描）需要优化

-- 3. 创建合适索引
CREATE INDEX idx_messages_conversation ON messages(conversation_id);

-- 4. 更新统计信息
ANALYZE messages;
```

#### Q2: 向量检索精度不够？

```sql
-- 1. 增加检索数量，再过滤
SELECT * FROM document_chunks
ORDER BY embedding <=> $1
LIMIT 100;  -- 先取 100 条

-- 2. 调整 HNSW 参数
SET hnsw.ef_search = 100;  -- 默认 40，增大提高精度
SELECT * FROM document_chunks
ORDER BY embedding <=> $1
LIMIT 5;

-- 3. 使用精确搜索（验证结果）
SELECT * FROM document_chunks
ORDER BY embedding <=> $1   -- 向量距离排序
LIMIT 5;
```

#### Q3: JSONB 查询不走索引？

```sql
-- 问题：直接查询 JSONB 键值慢
SELECT * FROM conversations WHERE metadata->>'model' = 'qwen-plus';

-- 解决方案 1：创建表达式索引
CREATE INDEX idx_metadata_model ON conversations ((metadata->>'model'));

-- 解决方案 2：使用 @> 包含查询 + GIN 索引
CREATE INDEX idx_metadata_gin ON conversations USING GIN (metadata);
SELECT * FROM conversations WHERE metadata @> '{"model": "qwen-plus"}';
```

#### Q4: 连接数过多？

```sql
-- 查看当前连接
SELECT 
    pid,
    usename,
    state,
    query,
    query_start
FROM pg_stat_activity
WHERE state = 'active';

-- 终止空闲连接
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE state = 'idle'
  AND query_start < CURRENT_TIMESTAMP - INTERVAL '10 minutes';

-- 配置最大连接数（postgresql.conf）
-- max_connections = 100
```

### 8.5 数据安全

```sql
-- 1. 定期备份
pg_dump -U langchain4j -d langchain4j -F c -f backup.dump

-- 2. 恢复数据
pg_restore -U langchain4j -d langchain4j backup.dump

-- 3. 敏感数据加密
-- 使用 pgcrypto 扩展
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 加密存储
INSERT INTO secrets (id, data)
VALUES (1, pgp_sym_encrypt('secret data', 'encryption_key'));

-- 解密查询
SELECT pgp_sym_decrypt(data, 'encryption_key') FROM secrets WHERE id = 1;

-- 4. 审计日志
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    table_name  VARCHAR(100),
    operation   VARCHAR(10),
    old_data    JSONB,
    new_data    JSONB,
    user_id     BIGINT,
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
```

---

## 附录：常用命令速查

### 数据库管理

```sql
-- 查看数据库列表
\l

-- 切换数据库
\c database_name

-- 查看表列表
\dt

-- 查看表结构
\d table_name

-- 查看索引
\di

-- 查看函数
\df

-- 查看扩展
\dx
```

### 性能分析

```sql
-- 查看表大小
SELECT 
    relname AS table_name,
    pg_size_pretty(pg_total_relation_size(relid)) AS size
FROM pg_stat_user_tables
ORDER BY pg_total_relation_size(relid) DESC;

-- 查看正在执行的查询
SELECT 
    pid,
    query,
    state,
    query_start,
    EXTRACT(EPOCH FROM (now() - query_start)) AS duration_seconds
FROM pg_stat_activity
WHERE state = 'active';

-- 查看锁等待
SELECT 
    blocked_locks.pid AS blocked_pid,
    blocked_activity.query AS blocked_query,
    blocking_locks.pid AS blocking_pid,
    blocking_activity.query AS blocking_query
FROM pg_catalog.pg_locks blocked_locks
JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.locktype = blocked_locks.locktype
    AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database
    AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation
    AND blocking_locks.granted
JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
WHERE NOT blocked_locks.granted;
```

### 扩展管理

```sql
-- 安装扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 查看已安装扩展
SELECT * FROM pg_extension;

-- 升级扩展
ALTER EXTENSION vector UPDATE;
```

---

## 参考资源

- [PostgreSQL 官方文档](https://www.postgresql.org/docs/current/index.html)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [PostgreSQL 中文社区](http://www.postgres.cn/)
- [pgAdmin 官网](https://www.pgadmin.org/)

---

> 本文档基于 LangChain4j Agent 项目编写，结合实际业务场景，帮助开发者快速掌握 PostgreSQL 在 AI Agent 开发中的应用。
