-- ==================== PostgreSQL 初始化脚本 ====================
--
-- LangChain4j Agent 工程数据库初始化
-- 支持用户管理、会话存储、Token计费、工具审计、RAG向量检索
--
-- 版本: 1.0.0
-- 更新时间: 2024-04

-- ==================== 扩展安装 ====================

-- UUID 生成
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- 密码加密
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- 向量存储（RAG）
CREATE EXTENSION IF NOT EXISTS "vector";
-- 全文搜索
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- ==================== Schema 配置 ====================

-- 创建应用 schema
CREATE SCHEMA IF NOT EXISTS app;

-- 设置默认搜索路径
ALTER DATABASE langchain4j SET search_path TO app, public;

-- ==================== 用户表 ====================

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

-- 用户表字段注释
COMMENT ON TABLE app.users IS '用户表 - 存储系统用户信息';
COMMENT ON COLUMN app.users.id IS '主键ID，自增';
COMMENT ON COLUMN app.users.username IS '用户名，唯一，用于登录';
COMMENT ON COLUMN app.users.email IS '邮箱地址，唯一，用于通知和找回密码';
COMMENT ON COLUMN app.users.password IS '密码，BCrypt加密存储，OAuth用户可为空';
COMMENT ON COLUMN app.users.nickname IS '昵称，显示名称';
COMMENT ON COLUMN app.users.avatar IS '头像URL';
COMMENT ON COLUMN app.users.status IS '账户状态：ACTIVE-正常, DISABLED-禁用, LOCKED-锁定';
COMMENT ON COLUMN app.users.provider IS 'OAuth提供者：github, gitlab, google等';
COMMENT ON COLUMN app.users.provider_id IS 'OAuth提供者的用户ID';
COMMENT ON COLUMN app.users.created_at IS '创建时间，UTC时区';
COMMENT ON COLUMN app.users.updated_at IS '更新时间，UTC时区';

-- ==================== 会话表 ====================

CREATE TABLE IF NOT EXISTS app.conversations (
    id              VARCHAR(36)     PRIMARY KEY,
    user_id         BIGINT          REFERENCES app.users(id) ON DELETE CASCADE,
    title           VARCHAR(255),
    model           VARCHAR(100),
    system_prompt   TEXT,
    temperature     DECIMAL(3,2)    DEFAULT 0.7,
    max_tokens      INTEGER         DEFAULT 4096,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- 会话表字段注释
COMMENT ON TABLE app.conversations IS '会话表 - 存储用户对话会话';
COMMENT ON COLUMN app.conversations.id IS '会话ID，UUID格式';
COMMENT ON COLUMN app.conversations.user_id IS '所属用户ID，外键关联users表';
COMMENT ON COLUMN app.conversations.title IS '会话标题，自动生成或用户设置';
COMMENT ON COLUMN app.conversations.model IS '使用的AI模型名称，如qwen-plus, gpt-4等';
COMMENT ON COLUMN app.conversations.system_prompt IS '系统提示词';
COMMENT ON COLUMN app.conversations.temperature IS '温度参数，控制输出随机性，范围0-1';
COMMENT ON COLUMN app.conversations.max_tokens IS '最大输出token数';
COMMENT ON COLUMN app.conversations.metadata IS '扩展元数据，JSON格式';
COMMENT ON COLUMN app.conversations.created_at IS '创建时间';
COMMENT ON COLUMN app.conversations.updated_at IS '更新时间';

-- ==================== 消息表 ====================

CREATE TABLE IF NOT EXISTS app.messages (
    id              BIGSERIAL       PRIMARY KEY,
    conversation_id VARCHAR(36)     NOT NULL REFERENCES app.conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20)     NOT NULL,
    content         TEXT,
    token_count     INTEGER         DEFAULT 0,
    model           VARCHAR(100),
    finish_reason   VARCHAR(50),
    metadata        JSONB,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- 消息表字段注释
COMMENT ON TABLE app.messages IS '消息表 - 存储会话中的每条消息';
COMMENT ON COLUMN app.messages.id IS '消息ID，自增主键';
COMMENT ON COLUMN app.messages.conversation_id IS '所属会话ID';
COMMENT ON COLUMN app.messages.role IS '消息角色：system-系统, user-用户, assistant-助手, tool-工具';
COMMENT ON COLUMN app.messages.content IS '消息内容，Markdown格式';
COMMENT ON COLUMN app.messages.token_count IS 'token数量，用于计费统计';
COMMENT ON COLUMN app.messages.model IS '生成此消息的模型名称';
COMMENT ON COLUMN app.messages.finish_reason IS '结束原因：stop-正常结束, length-长度限制, tool_calls-工具调用';
COMMENT ON COLUMN app.messages.metadata IS '扩展元数据，如工具调用信息等';
COMMENT ON COLUMN app.messages.created_at IS '创建时间';

-- ==================== Token使用日志表 ====================

CREATE TABLE IF NOT EXISTS app.token_usage_logs (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          REFERENCES app.users(id) ON DELETE SET NULL,
    conversation_id VARCHAR(36),
    message_id      BIGINT,
    model           VARCHAR(100),
    model_provider  VARCHAR(50),
    input_tokens    INTEGER         DEFAULT 0,
    output_tokens   INTEGER         DEFAULT 0,
    total_tokens    INTEGER         DEFAULT 0,
    cost            DECIMAL(10,6)   DEFAULT 0,
    latency_ms      BIGINT,
    status          VARCHAR(20)     DEFAULT 'success',
    error_message   TEXT,
    request_time    TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- Token日志字段注释
COMMENT ON TABLE app.token_usage_logs IS 'Token使用日志 - 记录每次API调用的token消耗和费用';
COMMENT ON COLUMN app.token_usage_logs.id IS '日志ID，自增主键';
COMMENT ON COLUMN app.token_usage_logs.user_id IS '用户ID';
COMMENT ON COLUMN app.token_usage_logs.conversation_id IS '会话ID';
COMMENT ON COLUMN app.token_usage_logs.message_id IS '关联的消息ID';
COMMENT ON COLUMN app.token_usage_logs.model IS '模型名称，如qwen-plus';
COMMENT ON COLUMN app.token_usage_logs.model_provider IS '模型提供者：dashscope, zhipu, deepseek等';
COMMENT ON COLUMN app.token_usage_logs.input_tokens IS '输入token数量';
COMMENT ON COLUMN app.token_usage_logs.output_tokens IS '输出token数量';
COMMENT ON COLUMN app.token_usage_logs.total_tokens IS '总token数量';
COMMENT ON COLUMN app.token_usage_logs.cost IS '费用，单位：美元';
COMMENT ON COLUMN app.token_usage_logs.latency_ms IS '请求延迟，毫秒';
COMMENT ON COLUMN app.token_usage_logs.status IS '状态：success-成功, error-失败, timeout-超时';
COMMENT ON COLUMN app.token_usage_logs.error_message IS '错误信息';
COMMENT ON COLUMN app.token_usage_logs.request_time IS '请求时间';

-- ==================== 工具执行审计表 ====================

CREATE TABLE IF NOT EXISTS app.tool_execution_audits (
    id                  BIGSERIAL       PRIMARY KEY,
    execution_id        VARCHAR(36)     UNIQUE NOT NULL,
    tool_name           VARCHAR(100)    NOT NULL,
    tool_category       VARCHAR(50),
    user_id             BIGINT,
    conversation_id     VARCHAR(36),
    message_id          BIGINT,
    input               TEXT,
    input_hash          VARCHAR(64),
    output              TEXT,
    output_hash         VARCHAR(64),
    status              VARCHAR(20),
    error_message       TEXT,
    execution_time_ms   BIGINT,
    retry_count         INTEGER         DEFAULT 0,
    risk_level          VARCHAR(20),
    confirmation_status VARCHAR(20),
    created_at          TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- 工具审计字段注释
COMMENT ON TABLE app.tool_execution_audits IS '工具执行审计 - 记录AI工具调用的完整信息';
COMMENT ON COLUMN app.tool_execution_audits.id IS '审计ID，自增主键';
COMMENT ON COLUMN app.tool_execution_audits.execution_id IS '执行ID，UUID格式，唯一标识一次执行';
COMMENT ON COLUMN app.tool_execution_audits.tool_name IS '工具名称';
COMMENT ON COLUMN app.tool_execution_audits.tool_category IS '工具类别：SYSTEM-系统, CUSTOM-自定义, EXTERNAL-外部';
COMMENT ON COLUMN app.tool_execution_audits.user_id IS '执行用户ID';
COMMENT ON COLUMN app.tool_execution_audits.conversation_id IS '关联会话ID';
COMMENT ON COLUMN app.tool_execution_audits.message_id IS '关联消息ID';
COMMENT ON COLUMN app.tool_execution_audits.input IS '工具输入参数，JSON格式';
COMMENT ON COLUMN app.tool_execution_audits.input_hash IS '输入参数哈希值，用于幂等检查';
COMMENT ON COLUMN app.tool_execution_audits.output IS '工具输出结果';
COMMENT ON COLUMN app.tool_execution_audits.output_hash IS '输出结果哈希值';
COMMENT ON COLUMN app.tool_execution_audits.status IS '执行状态：pending-等待, running-运行中, success-成功, failed-失败, timeout-超时';
COMMENT ON COLUMN app.tool_execution_audits.error_message IS '错误信息';
COMMENT ON COLUMN app.tool_execution_audits.execution_time_ms IS '执行耗时，毫秒';
COMMENT ON COLUMN app.tool_execution_audits.retry_count IS '重试次数';
COMMENT ON COLUMN app.tool_execution_audits.risk_level IS '风险等级：LOW-低, MEDIUM-中, HIGH-高, CRITICAL-危险';
COMMENT ON COLUMN app.tool_execution_audits.confirmation_status IS '确认状态：none-无需确认, pending-等待确认, approved-已批准, rejected-已拒绝';
COMMENT ON COLUMN app.tool_execution_audits.created_at IS '创建时间';

-- ==================== RAG文档表 ====================

CREATE TABLE IF NOT EXISTS app.documents (
    id              VARCHAR(36)     PRIMARY KEY,
    title           VARCHAR(255)    NOT NULL,
    content         TEXT,
    source          VARCHAR(500),
    source_type     VARCHAR(50),
    type            VARCHAR(50),
    status          VARCHAR(20)     DEFAULT 'active',
    file_size       BIGINT,
    file_hash       VARCHAR(64),
    chunk_count     INTEGER         DEFAULT 0,
    metadata        JSONB,
    embedding       vector(1536),
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- 文档表字段注释
COMMENT ON TABLE app.documents IS 'RAG文档表 - 存储用于检索增强生成的文档';
COMMENT ON COLUMN app.documents.id IS '文档ID，UUID格式';
COMMENT ON COLUMN app.documents.title IS '文档标题';
COMMENT ON COLUMN app.documents.content IS '文档原始内容';
COMMENT ON COLUMN app.documents.source IS '文档来源URL或路径';
COMMENT ON COLUMN app.documents.source_type IS '来源类型：upload-上传, url-URL导入, api-API导入';
COMMENT ON COLUMN app.documents.type IS '文档类型：txt, md, pdf, docx等';
COMMENT ON COLUMN app.documents.status IS '状态：active-有效, deleted-已删除, processing-处理中';
COMMENT ON COLUMN app.documents.file_size IS '文件大小，字节';
COMMENT ON COLUMN app.documents.file_hash IS '文件哈希值，MD5或SHA256';
COMMENT ON COLUMN app.documents.chunk_count IS '分块数量';
COMMENT ON COLUMN app.documents.metadata IS '扩展元数据，JSON格式';
COMMENT ON COLUMN app.documents.embedding IS '文档摘要向量，1536维';
COMMENT ON COLUMN app.documents.created_at IS '创建时间';
COMMENT ON COLUMN app.documents.updated_at IS '更新时间';

-- ==================== RAG文档分块表 ====================

CREATE TABLE IF NOT EXISTS app.document_chunks (
    id              VARCHAR(36)     PRIMARY KEY,
    document_id     VARCHAR(36)     NOT NULL REFERENCES app.documents(id) ON DELETE CASCADE,
    content         TEXT            NOT NULL,
    chunk_index     INTEGER         NOT NULL,
    token_count     INTEGER         DEFAULT 0,
    embedding       vector(1536),
    metadata        JSONB,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- 文档分块字段注释
COMMENT ON TABLE app.document_chunks IS 'RAG文档分块表 - 存储文档分割后的文本块及向量';
COMMENT ON COLUMN app.document_chunks.id IS '分块ID，UUID格式';
COMMENT ON COLUMN app.document_chunks.document_id IS '所属文档ID';
COMMENT ON COLUMN app.document_chunks.content IS '分块文本内容';
COMMENT ON COLUMN app.document_chunks.chunk_index IS '分块序号，从0开始';
COMMENT ON COLUMN app.document_chunks.token_count IS 'token数量';
COMMENT ON COLUMN app.document_chunks.embedding IS '文本向量，1536维，用于相似度检索';
COMMENT ON COLUMN app.document_chunks.metadata IS '扩展元数据，如页码、段落信息等';
COMMENT ON COLUMN app.document_chunks.created_at IS '创建时间';

-- ==================== 角色与权限表 ====================

CREATE TABLE IF NOT EXISTS app.roles (
    id              BIGSERIAL       PRIMARY KEY,
    name            VARCHAR(50)     NOT NULL UNIQUE,
    description     VARCHAR(255),
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.roles IS '角色表 - 存储系统角色';
COMMENT ON COLUMN app.roles.id IS '角色ID';
COMMENT ON COLUMN app.roles.name IS '角色名称：ADMIN, USER, VIP等';
COMMENT ON COLUMN app.roles.description IS '角色描述';
COMMENT ON COLUMN app.roles.created_at IS '创建时间';

CREATE TABLE IF NOT EXISTS app.user_roles (
    user_id         BIGINT          NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    role_id         BIGINT          NOT NULL REFERENCES app.roles(id) ON DELETE CASCADE,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE app.user_roles IS '用户角色关联表';
COMMENT ON COLUMN app.user_roles.user_id IS '用户ID';
COMMENT ON COLUMN app.user_roles.role_id IS '角色ID';
COMMENT ON COLUMN app.user_roles.created_at IS '分配时间';

-- ==================== API Key表 ====================

CREATE TABLE IF NOT EXISTS app.api_keys (
    id              VARCHAR(36)     PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES app.users(id) ON DELETE CASCADE,
    name            VARCHAR(100)    NOT NULL,
    key_hash        VARCHAR(128)    NOT NULL,
    key_prefix      VARCHAR(8)      NOT NULL,
    permissions     JSONB,
    rate_limit      INTEGER         DEFAULT 100,
    daily_quota     INTEGER         DEFAULT 10000,
    used_quota      INTEGER         DEFAULT 0,
    status          VARCHAR(20)     DEFAULT 'active',
    expires_at      TIMESTAMPTZ,
    last_used_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.api_keys IS 'API Key表 - 存储用户的API访问密钥';
COMMENT ON COLUMN app.api_keys.id IS 'API Key ID，UUID格式';
COMMENT ON COLUMN app.api_keys.user_id IS '所属用户ID';
COMMENT ON COLUMN app.api_keys.name IS 'Key名称，用户自定义';
COMMENT ON COLUMN app.api_keys.key_hash IS 'Key哈希值，SHA256加密存储';
COMMENT ON COLUMN app.api_keys.key_prefix IS 'Key前缀，用于展示（如sk-xxx的前8位）';
COMMENT ON COLUMN app.api_keys.permissions IS '权限配置，JSON格式';
COMMENT ON COLUMN app.api_keys.rate_limit IS '速率限制，每分钟请求数';
COMMENT ON COLUMN app.api_keys.daily_quota IS '每日配额，token数';
COMMENT ON COLUMN app.api_keys.used_quota IS '已使用配额';
COMMENT ON COLUMN app.api_keys.status IS '状态：active-有效, disabled-禁用, expired-已过期';
COMMENT ON COLUMN app.api_keys.expires_at IS '过期时间';
COMMENT ON COLUMN app.api_keys.last_used_at IS '最后使用时间';
COMMENT ON COLUMN app.api_keys.created_at IS '创建时间';

-- ==================== 索引创建 ====================

-- 消息表索引
CREATE INDEX IF NOT EXISTS idx_messages_conversation ON app.messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON app.messages(created_at);
CREATE INDEX IF NOT EXISTS idx_messages_role ON app.messages(role);

-- Token日志索引
CREATE INDEX IF NOT EXISTS idx_token_usage_user ON app.token_usage_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_token_usage_time ON app.token_usage_logs(request_time);
CREATE INDEX IF NOT EXISTS idx_token_usage_model ON app.token_usage_logs(model_provider);
CREATE INDEX IF NOT EXISTS idx_token_usage_conversation ON app.token_usage_logs(conversation_id);

-- 工具审计索引
CREATE INDEX IF NOT EXISTS idx_tool_audits_tool ON app.tool_execution_audits(tool_name);
CREATE INDEX IF NOT EXISTS idx_tool_audits_user ON app.tool_execution_audits(user_id);
CREATE INDEX IF NOT EXISTS idx_tool_audits_time ON app.tool_execution_audits(created_at);
CREATE INDEX IF NOT EXISTS idx_tool_audits_status ON app.tool_execution_audits(status);

-- 文档分块索引
CREATE INDEX IF NOT EXISTS idx_document_chunks_doc ON app.document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_document_chunks_index ON app.document_chunks(document_id, chunk_index);

-- 向量索引（HNSW算法，用于相似度检索）
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
ON app.document_chunks
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 文档索引
CREATE INDEX IF NOT EXISTS idx_documents_status ON app.documents(status);
CREATE INDEX IF NOT EXISTS idx_documents_type ON app.documents(type);

-- API Key索引
CREATE INDEX IF NOT EXISTS idx_api_keys_user ON app.api_keys(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_status ON app.api_keys(status);
CREATE INDEX IF NOT EXISTS idx_api_keys_prefix ON app.api_keys(key_prefix);

-- ==================== Agent 审计日志表 ====================

CREATE TABLE IF NOT EXISTS app.agent_audit_logs (
    id              BIGSERIAL       PRIMARY KEY,
    event_id        VARCHAR(36)     UNIQUE NOT NULL,
    trace_id        VARCHAR(36),
    user_id         VARCHAR(64),
    agent_name      VARCHAR(50),
    event_type      VARCHAR(30)     NOT NULL,
    timestamp       TIMESTAMPTZ     NOT NULL,
    client_ip       VARCHAR(50),
    user_agent      VARCHAR(500),
    details         JSONB,
    created_at      TIMESTAMPTZ     DEFAULT CURRENT_TIMESTAMP
);

-- Agent 审计日志索引
CREATE INDEX IF NOT EXISTS idx_audit_trace ON app.agent_audit_logs(trace_id);
CREATE INDEX IF NOT EXISTS idx_audit_user ON app.agent_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_type_time ON app.agent_audit_logs(event_type, timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_created ON app.agent_audit_logs(created_at);

-- Agent 审计日志字段注释
COMMENT ON TABLE app.agent_audit_logs IS 'Agent审计日志 - 记录Agent执行的完整审计信息';
COMMENT ON COLUMN app.agent_audit_logs.id IS '审计ID，自增主键';
COMMENT ON COLUMN app.agent_audit_logs.event_id IS '事件ID，唯一标识';
COMMENT ON COLUMN app.agent_audit_logs.trace_id IS '追踪ID，关联执行';
COMMENT ON COLUMN app.agent_audit_logs.user_id IS '用户ID';
COMMENT ON COLUMN app.agent_audit_logs.agent_name IS 'Agent名称';
COMMENT ON COLUMN app.agent_audit_logs.event_type IS '事件类型：EXECUTION_START, EXECUTION_END, TOOL_CALL等';
COMMENT ON COLUMN app.agent_audit_logs.timestamp IS '事件时间戳';
COMMENT ON COLUMN app.agent_audit_logs.client_ip IS '客户端IP';
COMMENT ON COLUMN app.agent_audit_logs.user_agent IS '客户端User-Agent';
COMMENT ON COLUMN app.agent_audit_logs.details IS '事件详情，JSON格式';
COMMENT ON COLUMN app.agent_audit_logs.created_at IS '创建时间';

-- ==================== 触发器 ====================

-- 更新时间触发器函数
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为需要的表创建触发器
CREATE TRIGGER trigger_users_updated_at
    BEFORE UPDATE ON app.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trigger_conversations_updated_at
    BEFORE UPDATE ON app.conversations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trigger_documents_updated_at
    BEFORE UPDATE ON app.documents
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- ==================== 初始数据 ====================

-- 插入默认角色
INSERT INTO app.roles (name, description) VALUES
    ('ADMIN', '系统管理员，拥有所有权限'),
    ('USER', '普通用户'),
    ('VIP', 'VIP用户，拥有更高配额')
ON CONFLICT (name) DO NOTHING;

-- 插入默认管理员用户（密码：REDACTED_ADMIN_PASSWORD，BCrypt加密）
INSERT INTO app.users (username, email, password, nickname, status)
VALUES ('admin', 'admin@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', 'Administrator', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

-- 为管理员分配ADMIN角色
INSERT INTO app.user_roles (user_id, role_id)
SELECT u.id, r.id FROM app.users u, app.roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN'
ON CONFLICT DO NOTHING;

-- ==================== 权限授权 ====================

GRANT ALL PRIVILEGES ON SCHEMA app TO langchain4j;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA app TO langchain4j;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA app TO langchain4j;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA app TO langchain4j;

-- Schema 注释
COMMENT ON SCHEMA app IS 'LangChain4j Agent 应用 Schema - 生产级AI工程数据模型';
