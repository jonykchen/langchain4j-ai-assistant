package com.jonychen.agent.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jonychen.agent.core.AbstractAgent;
import com.jonychen.agent.core.AgentContext;
import com.jonychen.agent.core.AgentExecutor;
import com.jonychen.agent.core.AgentMetadata;
import com.jonychen.agent.core.AgentRequest;
import com.jonychen.agent.core.ToolCallRequest;
import com.jonychen.observability.trace.AgentTraceService;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolRegistry;
import com.jonychen.tool.ToolResult;
import com.jonychen.tool.builtin.ChartTools;
import com.jonychen.tool.builtin.DatabaseTools;
import com.jonychen.tool.builtin.ExportTools;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.tool.ToolExecutor;

/**
 * 数据分析助手 Agent
 *
 * <p>负责自然语言查库、数据可视化、数据导出等数据分析任务。
 *
 * <h2>核心能力</h2>
 *
 * <ul>
 *   <li><strong>自然语言查库</strong>：将用户需求转换为 SQL 查询
 *   <li><strong>数据可视化</strong>：生成图表配置（ECharts）
 *   <li><strong>数据导出</strong>：导出为 CSV/JSON 格式
 * </ul>
 *
 * <h2>安全约束</h2>
 *
 * <ul>
 *   <li>只读查询（仅支持 SELECT）
 *   <li>表白名单机制（只能查询白名单内的表）
 *   <li>敏感字段过滤（password、api_key 等自动脱敏）
 *   <li>结果行数限制（默认 100，最大 1000）
 * </ul>
 *
 * <h2>权限要求</h2>
 *
 * <p>USER+ 角色（普通用户可用）
 *
 * @author jonychen
 */
@Component
public class DataAgent extends AbstractAgent {

    private static final Logger log = LoggerFactory.getLogger(DataAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DatabaseTools databaseTools;
    private final ChartTools chartTools;
    private final ExportTools exportTools;

    /**
     * 构造函数
     *
     * @param chatModel 聊天模型
     * @param toolRegistry 工具注册中心
     * @param traceService 追踪服务
     * @param databaseTools 数据库工具
     * @param chartTools 图表工具
     * @param exportTools 导出工具
     */
    public DataAgent(
            ChatModel chatModel,
            ToolRegistry toolRegistry,
            AgentTraceService traceService,
            DatabaseTools databaseTools,
            ChartTools chartTools,
            ExportTools exportTools) {
        super(chatModel, toolRegistry, traceService);
        this.databaseTools = databaseTools;
        this.chartTools = chartTools;
        this.exportTools = exportTools;

        log.info(
                "[DataAgent] 初始化完成，可用工具: list_tables, describe_table, execute_query, generate_chart, export_data");
    }

    @Override
    public AgentMetadata getMetadata() {
        return AgentMetadata.data();
    }

    /**
     * 判断是否应该由此 Agent 处理请求
     *
     * <p>基于关键词置信度判断：
     *
     * <ul>
     *   <li>包含 "查询"/"数据"/"统计" → 0.85
     *   <li>包含 "sql"/"图表"/"导出" → 0.9
     *   <li>包含 "表"/"库" → 0.6
     * </ul>
     *
     * @param request 执行请求
     * @return 置信度分数（0-1）
     */
    @Override
    public double canHandle(AgentRequest request) {
        String input = request.userInput().toLowerCase();

        // 高置信度关键词
        if (input.contains("sql") || input.contains("图表") || input.contains("导出")) {
            log.debug("[DataAgent] canHandle 高置信度匹配: sql/图表/导出");
            return 0.9;
        }

        // 较高置信度关键词
        if (input.contains("查询") || input.contains("数据") || input.contains("统计")) {
            log.debug("[DataAgent] canHandle 较高置信度匹配: 查询/数据/统计");
            return 0.85;
        }

        // 一般置信度关键词
        if (input.contains("表") || input.contains("库") || input.contains("分析")) {
            log.debug("[DataAgent] canHandle 一般置信度匹配: 表/库/分析");
            return 0.6;
        }

        return 0.0;
    }

    @Override
    public List<com.jonychen.tool.ToolDefinition> getAvailableTools() {
        return toolRegistry.getToolsByCategory(ToolCategory.DATABASE);
    }

    /**
     * 构建系统提示词
     *
     * <p>指导 AI 如何使用数据查询工具，包含安全约束和工作流程。
     */
    @Override
    protected String buildSystemPrompt() {
        return """
                你是一个数据分析助手，帮助用户查询和分析数据。

                ## 可用工具

                ### 数据查询工具
                - list_tables: 列出可查询的数据库表（白名单）
                - describe_table: 查看表结构，了解字段和数据类型
                - execute_query: 执行只读 SQL 查询（仅支持 SELECT）

                ### 数据可视化工具
                - generate_chart: 根据数据生成图表配置（支持 bar/line/pie）
                - export_data: 导出数据为 CSV 或 JSON 格式

                ## 数据库安全约束

                1. **只读查询**：只允许执行 SELECT 语句，禁止 INSERT/UPDATE/DELETE
                2. **表白名单**：只能查询白名单内的表，白名单表包括：
                   - users: 用户信息
                   - agent_traces: Agent 执行追踪
                   - agent_trace_spans: Agent 执行步骤详情
                   - token_usage: Token 用量统计
                   - evaluation_results: 评测结果
                   - prompt_templates: Prompt 模板
                   - agent_state_snapshots: Agent 状态快照
                3. **敏感字段过滤**：查询结果会自动过滤敏感字段（password、api_key 等）
                4. **结果行数限制**：默认返回 100 行，最大 1000 行

                ## 工作流程

                1. **理解需求**：分析用户的数据查询需求
                2. **了解数据**：使用 list_tables 或 describe_table 了解表结构
                3. **构建 SQL**：生成符合 PostgreSQL 语法的 SELECT 查询
                4. **执行查询**：调用 execute_query 执行查询
                5. **展示结果**：格式化展示查询结果
                6. **可视化（可选）**：如果用户需要图表，调用 generate_chart
                7. **导出（可选）**：如果用户需要导出，调用 export_data

                ## SQL 生成规范

                - 使用标准 PostgreSQL 语法
                - 复杂查询建议先用 describe_table 确认表结构
                - 大表查询务必添加 LIMIT
                - 使用 WHERE 条件过滤数据，避免全表扫描
                - 时间范围查询注意时区

                ## 输出格式

                - 在每个步骤前，用 <thinking></thinking> 标签说明思考过程
                - 查询结果使用表格形式展示
                - 数据分析结果给出清晰结论

                ## 错误处理

                - 如果表不在白名单，告知用户可查询的表列表
                - 如果 SQL 语法错误，尝试修正后重试
                - 如果查询超时，建议添加 LIMIT 或优化查询
                """;
    }

    /**
     * 构建执行器
     *
     * <p>注册 DatabaseTools、ChartTools、ExportTools 到执行器。
     */
    @Override
    protected AgentExecutor buildExecutor(AgentContext context) {
        // 注册工具规范
        List<dev.langchain4j.agent.tool.ToolSpecification> tools =
                List.of(
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("list_tables")
                                .description("列出可查询的数据库表，返回白名单内的所有表")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("describe_table")
                                .description("查看表结构，返回列名、类型等信息")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("execute_query")
                                .description("执行只读 SQL 查询（仅支持 SELECT），返回查询结果")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("generate_chart")
                                .description("根据数据生成图表配置（ECharts JSON），支持 bar/line/pie")
                                .build(),
                        dev.langchain4j.agent.tool.ToolSpecification.builder()
                                .name("export_data")
                                .description("导出数据为 CSV 或 JSON 格式")
                                .build());

        // 注册工具执行器
        Map<String, ToolExecutor> executors = registerToolExecutors();

        return new AgentExecutor(chatModel, buildSystemPrompt(), tools, executors);
    }

    /**
     * 注册工具执行器
     *
     * <p>每个工具对应一个 ToolExecutor，处理工具调用请求。
     */
    private Map<String, ToolExecutor> registerToolExecutors() {
        Map<String, ToolExecutor> executors = new HashMap<>();

        // list_tables 工具
        executors.put(
                "list_tables",
                (request, memoryId) -> {
                    log.info("[DataAgent] 执行 list_tables");
                    ToolResult result = databaseTools.listTables();
                    return resultToString(result);
                });

        // describe_table 工具
        executors.put(
                "describe_table",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String tableName = (String) params.get("tableName");
                    log.info("[DataAgent] 执行 describe_table: tableName={}", tableName);
                    ToolResult result = databaseTools.describeTable(tableName);
                    return resultToString(result);
                });

        // execute_query 工具
        executors.put(
                "execute_query",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String sql = (String) params.get("sql");
                    int limit =
                            params.get("limit") != null
                                    ? ((Number) params.get("limit")).intValue()
                                    : 100;
                    log.info(
                            "[DataAgent] 执行 execute_query: sql={}, limit={}",
                            truncateSql(sql),
                            limit);
                    ToolResult result = databaseTools.executeQuery(sql, limit);
                    return resultToString(result);
                });

        // generate_chart 工具
        executors.put(
                "generate_chart",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    String chartType = (String) params.getOrDefault("chartType", "bar");
                    String title = (String) params.getOrDefault("title", "");
                    @SuppressWarnings("unchecked")
                    List<String> xAxisData = (List<String>) params.get("xAxisData");
                    @SuppressWarnings("unchecked")
                    List<Object> seriesData = (List<Object>) params.get("seriesData");
                    log.info(
                            "[DataAgent] 执行 generate_chart: type={}, dataCount={}",
                            chartType,
                            seriesData != null ? seriesData.size() : 0);
                    ToolResult result =
                            chartTools.generateChart(chartType, title, xAxisData, seriesData);
                    return resultToString(result);
                });

        // export_data 工具
        executors.put(
                "export_data",
                (request, memoryId) -> {
                    Map<String, Object> params = parseArgs(request.arguments());
                    Object data = params.get("data");
                    String format = (String) params.getOrDefault("format", "csv");
                    log.info("[DataAgent] 执行 export_data: format={}", format);
                    ToolResult result = exportTools.exportData(data, format);
                    return resultToString(result);
                });

        return executors;
    }

    /** 执行工具（扩展：记录日志） */
    @Override
    protected ToolResult executeTool(ToolCallRequest toolCall, AgentContext context) {
        String toolName = toolCall.name();
        log.info("[DataAgent] 执行工具: {} params={}", toolName, toolCall.params());

        long startTime = System.currentTimeMillis();
        try {
            ToolResult result = super.executeTool(toolCall, context);
            long duration = System.currentTimeMillis() - startTime;
            log.info(
                    "[DataAgent] 工具执行完成: {} success={} duration={}ms",
                    toolName,
                    result.success(),
                    duration);
            return result;
        } catch (Exception e) {
            log.error("[DataAgent] 工具执行异常: {} - {}", toolName, e.getMessage(), e);
            return ToolResult.failure("工具执行失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    /** 解析 JSON 参数 */
    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(argumentsJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("[DataAgent] JSON 解析失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /** ToolResult 转换为字符串（供 LangChain4j 返回） */
    private String resultToString(ToolResult result) {
        if (result.success() && result.data() != null) {
            try {
                return MAPPER.writeValueAsString(result.data());
            } catch (Exception e) {
                return String.valueOf(result.data());
            }
        }
        return result.error() != null ? "Error: " + result.error() : "Success";
    }

    /** 截断 SQL 用于日志 */
    private String truncateSql(String sql) {
        if (sql == null) return null;
        return sql.length() > 100 ? sql.substring(0, 100) + "..." : sql;
    }
}
