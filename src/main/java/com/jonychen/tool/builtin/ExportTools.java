package com.jonychen.tool.builtin;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolResult;

/**
 * 数据导出工具集
 *
 * <p>将查询结果导出为 CSV 或 JSON 格式，供 DataAgent 调用。
 *
 * <h2>导出格式</h2>
 *
 * <ul>
 *   <li>csv - 逗号分隔值，适合 Excel 打开
 *   <li>json - JSON 数组，适合程序处理
 * </ul>
 *
 * @author jonychen
 */
@Component
public class ExportTools {

    private static final Logger log = LoggerFactory.getLogger(ExportTools.class);

    /**
     * 导出数据
     *
     * <p>将查询结果导出为指定格式的字符串，用户可复制保存。
     *
     * @param data 数据（List&lt;Map&gt; 格式）
     * @param format 导出格式：csv/json
     * @return 导出内容
     */
    @AgentTool(
            name = "export_data",
            description = "导出查询结果为 CSV 或 JSON 格式，返回可复制的文本",
            category = ToolCategory.DATABASE,
            riskLevel = RiskLevel.LOW)
    public ToolResult exportData(Object data, String format) {

        log.info("[ExportTools] 执行 export_data 工具: format={}", format);

        // 参数校验
        if (data == null) {
            log.warn("[ExportTools] 导出数据为空");
            return ToolResult.failure("导出数据不能为空");
        }

        if (format == null || format.isBlank()) {
            format = "csv"; // 默认 CSV
        }

        if (!"csv".equalsIgnoreCase(format) && !"json".equalsIgnoreCase(format)) {
            log.warn("[ExportTools] 不支持的导出格式: {}", format);
            return ToolResult.failure("不支持的导出格式: " + format + "，支持: csv, json");
        }

        try {
            String content;
            int size;

            if ("json".equalsIgnoreCase(format)) {
                content = convertToJson(data);
                size = content.length();
                log.debug("[ExportTools] JSON 导出完成，大小: {} 字符", size);
            } else {
                content = convertToCsv(data);
                size = content.split("\n").length;
                log.debug("[ExportTools] CSV 导出完成，{} 行", size);
            }

            return ToolResult.success(
                    Map.of(
                            "format",
                            format.toLowerCase(),
                            "content",
                            content,
                            "size",
                            size,
                            "hint",
                            "用户可复制内容并保存为文件"));

        } catch (Exception e) {
            log.error("[ExportTools] 导出失败: {}", e.getMessage(), e);
            return ToolResult.failure("导出失败: " + e.getMessage());
        }
    }

    /**
     * 转换为 JSON 格式
     *
     * <p>将数据序列化为 JSON 字符串。
     */
    private String convertToJson(Object data) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            log.error("[ExportTools] JSON 序列化失败: {}", e.getMessage());
            return String.valueOf(data);
        }
    }

    /**
     * 转换为 CSV 格式
     *
     * <p>将 List&lt;Map&gt; 数据转换为 CSV 字符串。 包含表头，对特殊字符进行转义处理。
     */
    @SuppressWarnings("unchecked")
    private String convertToCsv(Object data) {
        if (!(data instanceof List<?> rows) || rows.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        List<String> headers = null;

        // 第一行确定表头
        Object first = rows.get(0);
        if (first instanceof Map<?, ?> firstMap) {
            headers = firstMap.keySet().stream().map(String::valueOf).toList();

            // 写入表头
            sb.append(String.join(",", headers)).append("\n");

            // 写入数据行
            for (Object row : rows) {
                if (row instanceof Map<?, ?> mapRow) {
                    List<String> values =
                            headers.stream()
                                    .map(
                                            h -> {
                                                Object val = mapRow.get(h);
                                                return escapeCSV(val != null ? val.toString() : "");
                                            })
                                    .toList();
                    sb.append(String.join(",", values)).append("\n");
                }
            }
        } else {
            // 简单数组，直接输出
            for (Object row : rows) {
                sb.append(escapeCSV(String.valueOf(row))).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * CSV 字段转义
     *
     * <p>处理逗号、引号、换行符等特殊字符。
     */
    private String escapeCSV(String value) {
        if (value == null) return "";

        // 如果包含逗号、引号、换行符，需要用引号包裹
        if (value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r")) {
            // 引号转义为双引号
            value = value.replace("\"", "\"\"");
            return "\"" + value + "\"";
        }
        return value;
    }
}
