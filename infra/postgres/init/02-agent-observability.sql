-- ============================================================
-- Agent 可观测性系统数据库 Schema
-- 创建时间: 2024
-- 说明: 包含 Agent 追踪、Prompt 管理、状态持久化、评测框架相关表
-- ============================================================

-- 1. Agent 追踪表
-- 存储 Agent 执行的整体追踪信息
CREATE TABLE IF NOT EXISTS app.agent_traces (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(36) UNIQUE NOT NULL,
    session_id VARCHAR(36),
    user_id VARCHAR(50),
    agent_type VARCHAR(50) NOT NULL,
    goal TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    execution_time_ms BIGINT,
    iterations INTEGER DEFAULT 0,
    final_output TEXT,
    error_message TEXT,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    total_tokens BIGINT DEFAULT 0,
    spans JSONB DEFAULT '[]'::jsonb,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.agent_traces IS 'Agent 执行追踪记录';
COMMENT ON COLUMN app.agent_traces.trace_id IS '追踪 ID（全局唯一）';
COMMENT ON COLUMN app.agent_traces.session_id IS '会话 ID';
COMMENT ON COLUMN app.agent_traces.user_id IS '用户 ID';
COMMENT ON COLUMN app.agent_traces.agent_type IS 'Agent 类型：REACT, PLAN_EXECUTE, MULTI_AGENT';
COMMENT ON COLUMN app.agent_traces.goal IS '任务目标/问题';
COMMENT ON COLUMN app.agent_traces.status IS '执行状态：RUNNING, COMPLETED, FAILED, CANCELLED';
COMMENT ON COLUMN app.agent_traces.iterations IS '迭代次数';
COMMENT ON COLUMN app.agent_traces.final_output IS '最终输出';
COMMENT ON COLUMN app.agent_traces.error_message IS '错误信息';

-- 索引
CREATE INDEX IF NOT EXISTS idx_trace_session ON app.agent_traces(session_id);
CREATE INDEX IF NOT EXISTS idx_trace_status ON app.agent_traces(status);
CREATE INDEX IF NOT EXISTS idx_trace_start_time ON app.agent_traces(start_time DESC);
CREATE INDEX IF NOT EXISTS idx_trace_user ON app.agent_traces(user_id);
CREATE INDEX IF NOT EXISTS idx_trace_type ON app.agent_traces(agent_type);

-- 2. Agent Span 表
-- 存储 Agent 执行过程中的每个步骤详情
CREATE TABLE IF NOT EXISTS app.agent_trace_spans (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(36) NOT NULL,
    span_id VARCHAR(36) NOT NULL,
    parent_span_id VARCHAR(36),
    type VARCHAR(50) NOT NULL,
    name VARCHAR(200),
    input TEXT,
    output TEXT,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    duration_ms BIGINT,
    success BOOLEAN DEFAULT true,
    error TEXT,
    prompt_tokens BIGINT DEFAULT 0,
    completion_tokens BIGINT DEFAULT 0,
    attributes JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_span_trace FOREIGN KEY (trace_id)
        REFERENCES app.agent_traces(trace_id) ON DELETE CASCADE
);

COMMENT ON TABLE app.agent_trace_spans IS 'Agent 执行步骤 Span';
COMMENT ON COLUMN app.agent_trace_spans.type IS 'Span 类型：THOUGHT, ACTION, OBSERVATION, LLM_CALL, TOOL_EXECUTE, PLANNING';
COMMENT ON COLUMN app.agent_trace_spans.parent_span_id IS '父 Span ID（用于嵌套调用）';
COMMENT ON COLUMN app.agent_trace_spans.duration_ms IS '执行时间（毫秒）';
COMMENT ON COLUMN app.agent_trace_spans.attributes IS '扩展属性';

-- 索引
CREATE INDEX IF NOT EXISTS idx_span_trace ON app.agent_trace_spans(trace_id);
CREATE INDEX IF NOT EXISTS idx_span_type ON app.agent_trace_spans(type);
CREATE INDEX IF NOT EXISTS idx_span_start_time ON app.agent_trace_spans(start_time);

-- 3. Prompt 模板表
-- 存储版本化的 Prompt 模板
CREATE TABLE IF NOT EXISTS app.prompt_templates (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    version VARCHAR(20) NOT NULL,
    description VARCHAR(500),
    content TEXT NOT NULL,
    variables JSONB DEFAULT '[]'::jsonb,
    tags VARCHAR(500),
    active BOOLEAN DEFAULT false,
    production BOOLEAN DEFAULT false,
    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- A/B 测试配置
    ab_test_enabled BOOLEAN DEFAULT false,
    ab_test_variant_name VARCHAR(50),
    ab_test_traffic_percentage DECIMAL(5,2) DEFAULT 0,
    ab_test_baseline_version VARCHAR(20),

    -- 使用统计
    total_uses BIGINT DEFAULT 0,
    success_count BIGINT DEFAULT 0,
    failure_count BIGINT DEFAULT 0,
    avg_response_time DECIMAL(10,2),
    avg_token_usage DECIMAL(10,2),

    CONSTRAINT uk_prompt_name_version UNIQUE(name, version)
);

COMMENT ON TABLE app.prompt_templates IS 'Prompt 模板版本管理';
COMMENT ON COLUMN app.prompt_templates.name IS '模板名称';
COMMENT ON COLUMN app.prompt_templates.version IS '版本号（语义化版本）';
COMMENT ON COLUMN app.prompt_templates.active IS '是否为当前激活版本';
COMMENT ON COLUMN app.prompt_templates.production IS '是否为生产环境版本';
COMMENT ON COLUMN app.prompt_templates.ab_test_enabled IS '是否启用 A/B 测试';
COMMENT ON COLUMN app.prompt_templates.ab_test_traffic_percentage IS 'A/B 测试流量百分比';

-- 索引
CREATE INDEX IF NOT EXISTS idx_prompt_name ON app.prompt_templates(name);
CREATE INDEX IF NOT EXISTS idx_prompt_active ON app.prompt_templates(active) WHERE active = true;
CREATE INDEX IF NOT EXISTS idx_prompt_production ON app.prompt_templates(production) WHERE production = true;

-- 4. Agent 状态快照表
-- 存储 Agent 执行状态的持久化快照，用于断点续传
CREATE TABLE IF NOT EXISTS app.agent_state_snapshots (
    id BIGSERIAL PRIMARY KEY,
    snapshot_id VARCHAR(36) UNIQUE NOT NULL,
    trace_id VARCHAR(36),
    session_id VARCHAR(36),
    agent_type VARCHAR(50),
    current_step_index INTEGER DEFAULT 0,
    total_steps INTEGER DEFAULT 0,
    internal_state JSONB DEFAULT '{}'::jsonb,
    execution_history JSONB DEFAULT '[]'::jsonb,
    snapshot_type VARCHAR(20) DEFAULT 'CHECKPOINT',
    resumable BOOLEAN DEFAULT true,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.agent_state_snapshots IS 'Agent 状态快照';
COMMENT ON COLUMN app.agent_state_snapshots.snapshot_type IS '快照类型：CHECKPOINT, ERROR, PAUSE, STEP_COMPLETE';
COMMENT ON COLUMN app.agent_state_snapshots.resumable IS '是否可恢复';
COMMENT ON COLUMN app.agent_state_snapshots.expires_at IS '过期时间';

-- 索引
CREATE INDEX IF NOT EXISTS idx_snapshot_session ON app.agent_state_snapshots(session_id);
CREATE INDEX IF NOT EXISTS idx_snapshot_resumable ON app.agent_state_snapshots(resumable) WHERE resumable = true;
CREATE INDEX IF NOT EXISTS idx_snapshot_expires ON app.agent_state_snapshots(expires_at);
CREATE INDEX IF NOT EXISTS idx_snapshot_trace ON app.agent_state_snapshots(trace_id);

-- 5. 评测结果表
-- 存储 Agent 执行的评测结果
CREATE TABLE IF NOT EXISTS app.evaluation_results (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(36) NOT NULL,
    evaluator_name VARCHAR(50) NOT NULL,
    passed BOOLEAN NOT NULL,
    score DECIMAL(5,4) NOT NULL,
    details JSONB DEFAULT '{}'::jsonb,
    issues JSONB DEFAULT '[]'::jsonb,
    recommendation TEXT,
    evaluated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app.evaluation_results IS 'Agent 评测结果';
COMMENT ON COLUMN app.evaluation_results.evaluator_name IS '评测器名称';
COMMENT ON COLUMN app.evaluation_results.passed IS '是否通过评测';
COMMENT ON COLUMN app.evaluation_results.score IS '评测得分（0-1）';
COMMENT ON COLUMN app.evaluation_results.issues IS '问题列表（JSON 数组）';

-- 索引
CREATE INDEX IF NOT EXISTS idx_eval_trace ON app.evaluation_results(trace_id);
CREATE INDEX IF NOT EXISTS idx_eval_name ON app.evaluation_results(evaluator_name);
CREATE INDEX IF NOT EXISTS idx_eval_passed ON app.evaluation_results(passed);

-- 6. 为 token_usage_logs 表添加 trace_id 字段（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'app' AND table_name = 'token_usage_logs' AND column_name = 'trace_id'
    ) THEN
        ALTER TABLE app.token_usage_logs ADD COLUMN trace_id VARCHAR(36);
        CREATE INDEX IF NOT EXISTS idx_token_trace ON app.token_usage_logs(trace_id);
    END IF;
END $$;

-- 7. 触发器：自动更新 updated_at
CREATE OR REPLACE FUNCTION app.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE OR REPLACE TRIGGER update_prompt_templates_updated_at
    BEFORE UPDATE ON app.prompt_templates
    FOR EACH ROW
    EXECUTE FUNCTION app.update_updated_at_column();
