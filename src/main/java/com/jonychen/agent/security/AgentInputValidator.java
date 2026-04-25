package com.jonychen.agent.security;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Agent 输入校验器
 *
 * <p>对用户输入和工具参数进行安全校验，防止注入攻击。 核心职责：
 *
 * <ul>
 *   <li>用户输入长度和内容校验
 *   <li>工具参数基本校验
 *   <li>SQL 注入初步筛查（仅对数据库相关工具）
 *   <li>敏感词检测与过滤
 * </ul>
 *
 * <p>注意：此校验器仅提供基础防护，不能替代数据库层的权限控制。 SQL 注入的终极防线是参数化查询和只读数据库用户。
 *
 * @author jonychen
 */
@Component
public class AgentInputValidator {

    private static final Logger log = LoggerFactory.getLogger(AgentInputValidator.class);

    /** 用户输入最大长度 */
    private static final int MAX_INPUT_LENGTH = 10000;

    /** SQL 注入危险关键词（仅用于初筛，不是完整的 SQL 注入检测） */
    private static final List<Pattern> SQL_INJECTION_PATTERNS =
            List.of(
                    Pattern.compile(
                            "(?i)(\\bDROP\\s+TABLE\\b)|(\\bDELETE\\s+FROM\\b)|(\\bTRUNCATE\\s+TABLE?\\b)"),
                    Pattern.compile("(?i)(\\bINSERT\\s+INTO\\b)|(\\bUPDATE\\s+\\w+\\s+SET\\b)"),
                    Pattern.compile("(?i)(\\bALTER\\s+TABLE\\b)|(\\bCREATE\\s+TABLE\\b)"),
                    Pattern.compile("--\\s*$", Pattern.MULTILINE), // SQL 注释
                    Pattern.compile(
                            ";\\s*(DROP|DELETE|INSERT|UPDATE|ALTER|CREATE)",
                            Pattern.CASE_INSENSITIVE));

    /** 敏感词列表 */
    private static final List<String> SENSITIVE_WORDS =
            List.of(
                    // 暴力相关
                    "杀人",
                    "炸弹",
                    "爆炸",
                    "恐怖袭击",
                    "自制武器",
                    // 违法相关
                    "毒品制作",
                    "制毒方法",
                    "洗钱方法",
                    "黑客攻击教程",
                    // 自伤相关
                    "自杀方法",
                    "自残方法",
                    // 诈骗相关
                    "钓鱼攻击",
                    "社会工程学攻击",
                    "身份盗用",
                    // 系统入侵
                    "提权漏洞",
                    "后门植入",
                    "恶意代码生成");

    /** 敏感词正则（编译一次，提高性能） */
    private static final Pattern SENSITIVE_WORD_PATTERN = buildSensitiveWordPattern();

    /**
     * 校验用户输入
     *
     * @param input 用户输入
     * @throws IllegalArgumentException 输入不合法时抛出
     */
    public void validate(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("用户输入不能为空");
        }
        if (input.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("用户输入长度超过限制（最大 " + MAX_INPUT_LENGTH + " 字符）");
        }
        checkSensitiveWords(input);
    }

    /**
     * 校验工具参数
     *
     * <p>对工具参数进行基本校验，确保参数值合法。
     *
     * @param toolName 工具名称
     * @param params 工具参数
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    public void validateToolParams(String toolName, Map<String, Object> params) {
        if (params == null) {
            return;
        }

        // 对数据库相关工具进行 SQL 注入初筛
        if (isDatabaseTool(toolName)) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() instanceof String sqlValue) {
                    checkSqlInjection(toolName, entry.getKey(), sqlValue);
                }
            }
        }

        // 对所有工具参数进行敏感词检查
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() instanceof String strValue) {
                checkSensitiveWords(strValue);
            }
        }
    }

    /**
     * 检查是否包含敏感词
     *
     * @param input 输入内容
     * @return 是否包含敏感词
     */
    public boolean containsSensitiveWords(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        return SENSITIVE_WORD_PATTERN.matcher(input).find();
    }

    /**
     * 检测输入中的敏感词
     *
     * @param input 输入内容
     * @return 匹配到的敏感词列表
     */
    public List<String> detectSensitiveWords(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        return SENSITIVE_WORDS.stream().filter(input::contains).collect(Collectors.toList());
    }

    /**
     * 过滤敏感词，替换为等长星号
     *
     * @param input 输入内容
     * @return 过滤后的内容
     */
    public String filterSensitiveWords(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String filtered = input;
        for (String word : SENSITIVE_WORDS) {
            if (filtered.contains(word)) {
                String replacement = "*".repeat(word.length());
                filtered = filtered.replace(word, replacement);
            }
        }

        if (!filtered.equals(input)) {
            log.info("[AgentInputValidator] 敏感词已过滤");
        }

        return filtered;
    }

    /**
     * 检查 SQL 注入
     *
     * <p>对字符串值进行 SQL 注入初步筛查，匹配到危险模式时拒绝执行。 仅作为第一道防线，数据库层仍需使用参数化查询。
     *
     * @param toolName 工具名称
     * @param paramName 参数名
     * @param value 参数值
     * @throws IllegalArgumentException 检测到 SQL 注入风险时抛出
     */
    private void checkSqlInjection(String toolName, String paramName, String value) {
        for (Pattern pattern : SQL_INJECTION_PATTERNS) {
            if (pattern.matcher(value).find()) {
                log.warn(
                        "[AgentInputValidator] SQL 注入风险检测: tool={}, param={}, pattern={}",
                        toolName,
                        paramName,
                        pattern.pattern());
                throw new IllegalArgumentException("参数包含潜在危险的 SQL 语句，请修改后重试。参数: " + paramName);
            }
        }
    }

    /**
     * 检查敏感词
     *
     * @param input 输入内容
     * @throws IllegalArgumentException 检测到敏感词时抛出
     */
    private void checkSensitiveWords(String input) {
        List<String> detected = detectSensitiveWords(input);
        if (!detected.isEmpty()) {
            log.warn("[AgentInputValidator] 敏感词检测: found={}", detected);
            throw new IllegalArgumentException("输入包含不允许的内容，请修改后重试");
        }
    }

    /**
     * 判断是否为数据库相关工具
     *
     * @param toolName 工具名称
     * @return 是否为数据库工具
     */
    private boolean isDatabaseTool(String toolName) {
        return toolName != null
                && (toolName.contains("query")
                        || toolName.contains("sql")
                        || toolName.contains("database")
                        || toolName.contains("execute_query"));
    }

    /** 构建敏感词正则表达式 */
    private static Pattern buildSensitiveWordPattern() {
        String joined =
                SENSITIVE_WORDS.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        return Pattern.compile(joined, Pattern.CASE_INSENSITIVE);
    }
}
