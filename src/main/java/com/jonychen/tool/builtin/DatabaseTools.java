package com.jonychen.tool.builtin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolParam;
import com.jonychen.tool.ToolResult;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.StatementVisitorAdapter;
import net.sf.jsqlparser.statement.alter.Alter;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.drop.Drop;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.statement.update.Update;

/**
 * 数据库工具集
 *
 * <p>提供安全的 SQL 查询能力，供 DataAgent 调用。
 *
 * <h2>安全机制</h2>
 *
 * <ol>
 *   <li><strong>SQL AST 白名单校验</strong>：使用 JSqlParser 解析 SQL 语法树，只允许 SELECT 语句，禁止
 *       INSERT/UPDATE/DELETE/DROP/ALTER 等写操作
 *   <li><strong>表白名单</strong>：递归检查 FROM、JOIN、子查询中的表是否在白名单内
 *   <li><strong>敏感字段过滤</strong>：查询结果自动过滤敏感字段（password、api_key 等）
 *   <li><strong>结果行数限制</strong>：自动添加 LIMIT，最大 1000 行
 * </ol>
 *
 * <h2>工具列表</h2>
 *
 * <ul>
 *   <li>{@code listTables()} - 列出可查询的数据库表
 *   <li>{@code describeTable(tableName)} - 查看表结构
 *   <li>{@code executeQuery(sql, limit)} - 执行只读 SQL 查询
 * </ul>
 *
 * @author jonychen
 */
@Component
public class DatabaseTools {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTools.class);

    /** 默认白名单表（可通过配置覆盖） */
    private static final Set<String> DEFAULT_ALLOWED_TABLES =
            Set.of(
                    "users",
                    "agent_traces",
                    "agent_trace_spans",
                    "token_usage",
                    "evaluation_results",
                    "prompt_templates",
                    "agent_state_snapshots");

    /** 默认敏感字段（可通过配置覆盖） */
    private static final Set<String> DEFAULT_SENSITIVE_COLUMNS =
            Set.of(
                    "password",
                    "api_key",
                    "secret",
                    "token",
                    "access_token",
                    "refresh_token",
                    "credential",
                    "private_key");

    /** 查询结果最大行数上限 */
    private static final int MAX_ROWS_LIMIT = 1000;

    /** 默认查询行数限制 */
    private static final int DEFAULT_ROWS_LIMIT = 100;

    /** 允许查询的表白名单（统一转小写，确保大小写不敏感匹配） */
    private final Set<String> allowedTables;

    /** 敏感字段黑名单（统一转小写） */
    private final Set<String> sensitiveColumns;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 构造函数
     *
     * @param jdbcTemplate JDBC 模板
     * @param allowedTablesConfig 配置的表白名单（逗号分隔）
     * @param sensitiveColumnsConfig 配置的敏感字段（逗号分隔）
     */
    public DatabaseTools(
            JdbcTemplate jdbcTemplate,
            @Value("${agent.data.allowed-tables:#{null}}") String allowedTablesConfig,
            @Value("${agent.data.sensitive-columns:#{null}}") String sensitiveColumnsConfig) {
        this.jdbcTemplate = jdbcTemplate;

        // 初始化表白名单：配置优先，否则使用默认值
        if (allowedTablesConfig != null && !allowedTablesConfig.isBlank()) {
            this.allowedTables = parseConfigToSet(allowedTablesConfig);
            log.info("[DatabaseTools] 使用自定义表白名单: {}", this.allowedTables);
        } else {
            this.allowedTables = new HashSet<>(DEFAULT_ALLOWED_TABLES);
            log.info("[DatabaseTools] 使用默认表白名单: {}", this.allowedTables);
        }

        // 初始化敏感字段黑名单：配置优先，否则使用默认值
        if (sensitiveColumnsConfig != null && !sensitiveColumnsConfig.isBlank()) {
            this.sensitiveColumns = parseConfigToSet(sensitiveColumnsConfig);
            log.info("[DatabaseTools] 使用自定义敏感字段: {}", this.sensitiveColumns);
        } else {
            this.sensitiveColumns = new HashSet<>(DEFAULT_SENSITIVE_COLUMNS);
            log.info("[DatabaseTools] 使用默认敏感字段: {}", this.sensitiveColumns);
        }
    }

    /**
     * 列出可查询的数据库表
     *
     * <p>返回白名单内的所有表，供 Agent 了解可查询的数据范围。
     *
     * @return 可查询表列表
     */
    @AgentTool(
            name = "list_tables",
            description = "列出可查询的数据库表，只允许查询白名单内的表",
            category = ToolCategory.DATABASE,
            riskLevel = RiskLevel.LOW)
    public ToolResult listTables() {
        log.info("[DatabaseTools] 执行 list_tables 工具");

        List<Map<String, Object>> tables = new ArrayList<>();
        for (String tableName : allowedTables) {
            tables.add(Map.of("name", tableName, "queryable", true));
        }

        log.debug("[DatabaseTools] list_tables 返回 {} 个表", tables.size());
        return ToolResult.success(
                Map.of(
                        "tables",
                        tables,
                        "total",
                        tables.size(),
                        "hint",
                        "只允许查询白名单内的表，使用 describe_table 查看表结构"));
    }

    /**
     * 查看表结构
     *
     * <p>返回表的列信息，自动过滤敏感字段的详细信息（不暴露字段类型等）。
     *
     * @param tableName 表名称
     * @return 表结构信息
     */
    @AgentTool(
            name = "describe_table",
            description = "查看表结构，返回列名、类型、是否可空等信息",
            category = ToolCategory.DATABASE,
            riskLevel = RiskLevel.LOW)
    public ToolResult describeTable(
            @ToolParam(name = "tableName", description = "表名称", required = true) String tableName) {

        log.info("[DatabaseTools] 执行 describe_table 工具: tableName={}", tableName);

        // 校验表是否在白名单内
        if (tableName == null || tableName.isBlank()) {
            log.warn("[DatabaseTools] describe_table 参数错误: tableName 为空");
            return ToolResult.failure("表名称不能为空");
        }

        String normalizedTableName = tableName.toLowerCase();
        if (!allowedTables.contains(normalizedTableName)) {
            log.warn("[DatabaseTools] describe_table 拒绝访问: 表 {} 不在白名单内", tableName);
            return ToolResult.failure("表不在白名单中，允许查询的表: " + String.join(", ", allowedTables));
        }

        try {
            // 查询表结构（PostgreSQL 使用 information_schema）
            String sql =
                    """
                    SELECT column_name, data_type, is_nullable, column_default
                    FROM information_schema.columns
                    WHERE table_name = ?
                    ORDER BY ordinal_position
                    """;

            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql, normalizedTableName);

            // 过滤敏感字段并脱敏处理
            List<Map<String, Object>> filteredColumns = new ArrayList<>();
            for (Map<String, Object> col : columns) {
                String columnName = (String) col.get("column_name");
                String normalizedColumnName = columnName.toLowerCase();

                if (sensitiveColumns.contains(normalizedColumnName)) {
                    // 敏感字段：标记为敏感，不返回详细信息
                    filteredColumns.add(
                            Map.of(
                                    "column_name",
                                    columnName,
                                    "data_type",
                                    "***",
                                    "is_nullable",
                                    col.get("is_nullable"),
                                    "sensitive",
                                    true,
                                    "hint",
                                    "敏感字段，值已脱敏"));
                    log.debug("[DatabaseTools] describe_table 脱敏处理字段: {}", columnName);
                } else {
                    filteredColumns.add(
                            Map.of(
                                    "column_name", columnName,
                                    "data_type", col.get("data_type"),
                                    "is_nullable", col.get("is_nullable"),
                                    "column_default", col.get("column_default")));
                }
            }

            log.info(
                    "[DatabaseTools] describe_table 返回 {} 列（含 {} 个敏感字段）",
                    filteredColumns.size(),
                    filteredColumns.stream().filter(c -> c.containsKey("sensitive")).count());

            return ToolResult.success(
                    Map.of(
                            "tableName", normalizedTableName,
                            "columns", filteredColumns,
                            "totalColumns", filteredColumns.size()));

        } catch (Exception e) {
            log.error("[DatabaseTools] describe_table 执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("查询表结构失败: " + e.getMessage());
        }
    }

    /**
     * 执行只读 SQL 查询
     *
     * <h3>安全校验流程</h3>
     *
     * <ol>
     *   <li>SQL AST 解析，只允许 SELECT 语句
     *   <li>递归检查 FROM、JOIN、子查询中的表是否在白名单
     *   <li>自动添加 LIMIT（如未指定）
     *   <li>执行查询并过滤敏感字段
     * </ol>
     *
     * @param sql SELECT 查询语句
     * @param limit 返回行数限制（默认 100，最大 1000）
     * @return 查询结果
     */
    @AgentTool(
            name = "execute_query",
            description = "执行只读 SQL 查询（仅支持 SELECT），返回查询结果",
            category = ToolCategory.DATABASE,
            riskLevel = RiskLevel.LOW)
    public ToolResult executeQuery(
            @ToolParam(name = "sql", description = "SELECT 查询语句", required = true) String sql,
            @ToolParam(name = "limit", description = "返回行数限制，默认100，最大1000", defaultValue = "100")
                    int limit) {

        log.info(
                "[DatabaseTools] 执行 execute_query 工具: sql={}, limit={}",
                truncateForLog(sql),
                limit);

        // 参数校验
        if (sql == null || sql.isBlank()) {
            log.warn("[DatabaseTools] execute_query 参数错误: SQL 为空");
            return ToolResult.failure("SQL 语句不能为空");
        }

        // 步骤 1: SQL AST 安全校验
        ValidationResult astValidation = validateSqlAst(sql);
        if (!astValidation.valid()) {
            log.warn("[DatabaseTools] execute_query SQL 安全校验失败: {}", astValidation.errors());
            return ToolResult.failure("SQL 安全校验失败: " + String.join(", ", astValidation.errors()));
        }
        log.debug("[DatabaseTools] execute_query SQL AST 校验通过");

        // 步骤 2: 处理 LIMIT
        String processedSql = processLimit(sql, limit);
        log.debug("[DatabaseTools] execute_query 处理后的 SQL: {}", truncateForLog(processedSql));

        // 步骤 3: 执行查询
        try {
            long startTime = System.currentTimeMillis();
            List<Map<String, Object>> results = jdbcTemplate.queryForList(processedSql);
            long executionTimeMs = System.currentTimeMillis() - startTime;

            log.info(
                    "[DatabaseTools] execute_query 执行成功: 返回 {} 行, 耗时 {}ms",
                    results.size(),
                    executionTimeMs);

            // 步骤 4: 过滤敏感字段
            List<Map<String, Object>> filteredResults = filterSensitiveData(results);

            return ToolResult.success(
                    Map.of(
                            "rows",
                            filteredResults,
                            "count",
                            filteredResults.size(),
                            "truncated",
                            filteredResults.size() >= Math.min(limit, MAX_ROWS_LIMIT),
                            "executionTimeMs",
                            executionTimeMs),
                    Map.of("sql", processedSql));

        } catch (Exception e) {
            log.error("[DatabaseTools] execute_query 执行失败: {}", e.getMessage(), e);
            return ToolResult.failure("查询执行失败: " + e.getMessage());
        }
    }

    // ==================== 安全校验方法 ====================

    /**
     * SQL AST 安全校验
     *
     * <p>使用 JSqlParser 解析 SQL 语法树，进行以下检查：
     *
     * <ol>
     *   <li>只允许 SELECT 语句，禁止 INSERT/UPDATE/DELETE/DROP/ALTER
     *   <li>递归检查 FROM、JOIN、子查询中的表是否在白名单
     *   <li>禁止危险操作（如 INTO OUTFILE）
     * </ol>
     *
     * @param sql SQL 语句
     * @return 校验结果
     */
    private ValidationResult validateSqlAst(String sql) {
        try {
            Statement statement = CCJSqlParserUtil.parse(sql);
            log.debug("[DatabaseTools] SQL AST 解析成功: {}", statement.getClass().getSimpleName());

            // 危险节点检测结果
            AtomicBoolean hasDangerousNode = new AtomicBoolean(false);
            List<String> dangerousNodes = new ArrayList<>();

            // 使用访问者模式遍历 AST
            statement.accept(
                    new StatementVisitorAdapter() {
                        @Override
                        public void visit(Select select) {
                            // SELECT 语句需要进一步检查表白名单
                            // JSqlParser 4.9: Select 本身就是 SelectBody
                            validateSelect(select, dangerousNodes, hasDangerousNode);
                        }

                        @Override
                        public void visit(Delete delete) {
                            dangerousNodes.add("检测到 DELETE 节点");
                            hasDangerousNode.set(true);
                            log.warn("[DatabaseTools] SQL 安全校验: 检测到 DELETE 节点");
                        }

                        @Override
                        public void visit(Update update) {
                            dangerousNodes.add("检测到 UPDATE 节点");
                            hasDangerousNode.set(true);
                            log.warn("[DatabaseTools] SQL 安全校验: 检测到 UPDATE 节点");
                        }

                        @Override
                        public void visit(Insert insert) {
                            dangerousNodes.add("检测到 INSERT 节点");
                            hasDangerousNode.set(true);
                            log.warn("[DatabaseTools] SQL 安全校验: 检测到 INSERT 节点");
                        }

                        @Override
                        public void visit(Drop drop) {
                            dangerousNodes.add("检测到 DROP 节点");
                            hasDangerousNode.set(true);
                            log.warn("[DatabaseTools] SQL 安全校验: 检测到 DROP 节点");
                        }

                        @Override
                        public void visit(Alter alter) {
                            dangerousNodes.add("检测到 ALTER 节点");
                            hasDangerousNode.set(true);
                            log.warn("[DatabaseTools] SQL 安全校验: 检测到 ALTER 节点");
                        }
                    });

            if (hasDangerousNode.get()) {
                return ValidationResult.failure(dangerousNodes);
            }

            return ValidationResult.success();

        } catch (JSQLParserException e) {
            log.warn("[DatabaseTools] SQL AST 解析失败: {}", e.getMessage());
            return ValidationResult.failure(List.of("SQL 语法解析失败: " + e.getMessage()));
        }
    }

    /**
     * 校验 SELECT 语句（JSqlParser 4.9 版本）
     *
     * <p>递归检查 FROM、JOIN、WHERE 子查询中的表是否在白名单。
     *
     * <p>注意：JSqlParser 4.9 移除了 SelectBody 接口，Select 本身包含查询信息。
     */
    private void validateSelect(
            Select select, List<String> dangerousNodes, AtomicBoolean hasDangerousNode) {

        // 处理 PlainSelect（普通 SELECT）
        if (select instanceof PlainSelect plainSelect) {
            // 检查 FROM 子句
            FromItem fromItem = plainSelect.getFromItem();
            validateFromItem(fromItem, dangerousNodes, hasDangerousNode);

            // 检查 JOIN 子句
            if (plainSelect.getJoins() != null) {
                for (Join join : plainSelect.getJoins()) {
                    validateFromItem(join.getRightItem(), dangerousNodes, hasDangerousNode);
                }
            }

            // 检查 WHERE 子句中的子查询（如 IN、EXISTS）
            Expression where = plainSelect.getWhere();
            if (where != null) {
                validateExpression(where, dangerousNodes, hasDangerousNode);
            }

            // 检查 WITH 子句（CTE）
            if (plainSelect.getWithItemsList() != null) {
                for (WithItem withItem : plainSelect.getWithItemsList()) {
                    // JSqlParser 4.9: WithItem.getSelect() 返回子查询
                    Select withSelect = withItem.getSelect();
                    if (withSelect != null) {
                        validateSelect(withSelect, dangerousNodes, hasDangerousNode);
                    }
                }
            }
        }
        // 其他 SELECT 类型（如 SetOperation UNION）暂不支持，可扩展
    }

    /**
     * 校验表达式中的子查询
     *
     * <p>递归遍历表达式树，查找 ParenthesedSelect（子查询）并校验其表名。
     *
     * <p>支持的场景：IN (SELECT ...)、EXISTS (SELECT ...)、= (SELECT ...) 等
     */
    private void validateExpression(
            Expression expression, List<String> dangerousNodes, AtomicBoolean hasDangerousNode) {

        expression.accept(
                new ExpressionVisitorAdapter() {
                    @Override
                    public void visit(ParenthesedSelect parenthesedSelect) {
                        // 发现子查询，递归校验
                        Select subSelect = parenthesedSelect.getSelect();
                        if (subSelect != null) {
                            validateSelect(subSelect, dangerousNodes, hasDangerousNode);
                        }
                    }

                    @Override
                    public void visit(Select select) {
                        // Select.accept(ExpressionVisitor) 会调用 visit(Select)
                        // 需要检查是否是 ParenthesedSelect 子类
                        if (select instanceof ParenthesedSelect parenthesedSelect) {
                            visit(parenthesedSelect);
                        } else {
                            // 其他 Select 类型（如 PlainSelect），直接校验
                            validateSelect(select, dangerousNodes, hasDangerousNode);
                        }
                    }

                    @Override
                    public void visit(
                            net.sf.jsqlparser.expression.operators.relational.InExpression inExpr) {
                        // IN 表达式需要显式检查右侧（可能是子查询）
                        Expression rightExpr = inExpr.getRightExpression();
                        if (rightExpr != null) {
                            rightExpr.accept(this);
                        }
                        // 左侧表达式也需要检查
                        Expression leftExpr = inExpr.getLeftExpression();
                        if (leftExpr != null) {
                            leftExpr.accept(this);
                        }
                    }

                    @Override
                    public void visit(
                            net.sf.jsqlparser.expression.operators.relational.ExistsExpression
                                    existsExpr) {
                        // EXISTS 表达式检查右侧（子查询）
                        Expression rightExpr = existsExpr.getRightExpression();
                        if (rightExpr != null) {
                            rightExpr.accept(this);
                        }
                    }

                    @Override
                    protected void visitBinaryExpression(
                            net.sf.jsqlparser.expression.BinaryExpression binaryExpr) {
                        // 二元表达式递归检查左右两侧
                        Expression left = binaryExpr.getLeftExpression();
                        Expression right = binaryExpr.getRightExpression();
                        if (left != null) left.accept(this);
                        if (right != null) right.accept(this);
                    }
                });
    }

    /**
     * 校验 FROM 项（表或子查询）
     *
     * <p>递归处理子查询，防止通过子查询绕过表白名单。
     */
    private void validateFromItem(
            FromItem fromItem, List<String> dangerousNodes, AtomicBoolean hasDangerousNode) {

        if (fromItem instanceof Table table) {
            // 检查表是否在白名单
            String tableName = table.getName();
            if (tableName != null && !allowedTables.contains(tableName.toLowerCase())) {
                String error = "表不在白名单: " + tableName;
                dangerousNodes.add(error);
                hasDangerousNode.set(true);
                log.warn("[DatabaseTools] SQL 安全校验: {}", error);
            } else {
                log.debug("[DatabaseTools] SQL 安全校验: 表 {} 通过白名单检查", tableName);
            }

        } else if (fromItem instanceof ParenthesedSelect parenthesedSelect) {
            // JSqlParser 4.9: SubSelect 重命名为 ParenthesedSelect
            Select subSelect = parenthesedSelect.getSelect();
            if (subSelect != null) {
                validateSelect(subSelect, dangerousNodes, hasDangerousNode);
            }
        }
    }

    /**
     * 处理 LIMIT 子句
     *
     * <p>如果 SQL 未指定 LIMIT，自动添加；如果已指定但超过上限，截断到上限。
     */
    private String processLimit(String sql, int requestedLimit) {
        int effectiveLimit = Math.min(Math.max(requestedLimit, 1), MAX_ROWS_LIMIT);

        try {
            Statement stmt = CCJSqlParserUtil.parse(sql);
            if (stmt instanceof Select select) {
                // JSqlParser 4.9: 直接检查 Select 类型
                if (select instanceof PlainSelect plainSelect) {
                    if (plainSelect.getLimit() == null) {
                        // 未指定 LIMIT，自动添加
                        Limit limit = new Limit();
                        limit.setRowCount(new LongValue(effectiveLimit));
                        plainSelect.setLimit(limit);
                        log.debug("[DatabaseTools] 自动添加 LIMIT: {}", effectiveLimit);
                        return plainSelect.toString();
                    } else {
                        // 已有 LIMIT，检查是否超过上限
                        long existingLimit =
                                ((LongValue) plainSelect.getLimit().getRowCount()).getValue();
                        if (existingLimit > MAX_ROWS_LIMIT) {
                            plainSelect.getLimit().setRowCount(new LongValue(MAX_ROWS_LIMIT));
                            log.debug("[DatabaseTools] LIMIT 超过上限，截断到 {}", MAX_ROWS_LIMIT);
                            return plainSelect.toString();
                        }
                    }
                }
            }
        } catch (JSQLParserException e) {
            log.warn("[DatabaseTools] LIMIT 处理解析失败，使用原始 SQL: {}", e.getMessage());
        }

        return sql;
    }

    /**
     * 过滤敏感数据
     *
     * <p>遍历查询结果，移除敏感字段的值。
     */
    private List<Map<String, Object>> filterSensitiveData(List<Map<String, Object>> data) {
        return data.stream()
                .map(
                        row -> {
                            Map<String, Object> filtered = new LinkedHashMap<>();
                            row.forEach(
                                    (key, value) -> {
                                        if (sensitiveColumns.contains(key.toLowerCase())) {
                                            filtered.put(key, "***");
                                            log.trace("[DatabaseTools] 过滤敏感字段: {}", key);
                                        } else {
                                            filtered.put(key, value);
                                        }
                                    });
                            return filtered;
                        })
                .toList();
    }

    // ==================== 辅助方法 ====================

    /** 解析逗号分隔的配置字符串为 Set（统一转小写） */
    private Set<String> parseConfigToSet(String config) {
        if (config == null || config.isBlank()) {
            return Set.of();
        }
        return Set.of(config.split(",")).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    /** 截断 SQL 用于日志输出 */
    private String truncateForLog(String sql) {
        if (sql == null) return null;
        return sql.length() > 200 ? sql.substring(0, 200) + "..." : sql;
    }

    /** 校验结果 */
    private record ValidationResult(boolean valid, List<String> errors) {
        static ValidationResult success() {
            return new ValidationResult(true, List.of());
        }

        static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }
}
