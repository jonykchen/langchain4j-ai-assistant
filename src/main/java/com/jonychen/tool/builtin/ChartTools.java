package com.jonychen.tool.builtin;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.RiskLevel;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolResult;

/**
 * 图表生成工具集
 *
 * <p>根据数据生成图表配置（ECharts JSON 格式），供 DataAgent 调用。
 *
 * <h2>支持图表类型</h2>
 *
 * <ul>
 *   <li>bar - 柱状图，适合比较数据
 *   <li>line - 折线图，适合趋势分析
 *   <li>pie - 饼图，适合占比分析
 * </ul>
 *
 * @author jonychen
 */
@Component
public class ChartTools {

    private static final Logger log = LoggerFactory.getLogger(ChartTools.class);

    /**
     * 生成图表配置
     *
     * <p>返回 ECharts 兼容的图表配置 JSON，前端可直接用于渲染。
     *
     * @param chartType 图表类型：bar/line/pie
     * @param title 图表标题
     * @param xAxisData X 轴数据（柱状图/折线图）
     * @param seriesData 系列数据
     * @return 图表配置 JSON
     */
    @AgentTool(
            name = "generate_chart",
            description = "根据查询数据生成图表配置（ECharts JSON），支持 bar/line/pie 三种类型",
            category = ToolCategory.DATABASE,
            riskLevel = RiskLevel.LOW)
    public ToolResult generateChart(
            String chartType,
            String title,
            java.util.List<String> xAxisData,
            java.util.List<Object> seriesData) {

        log.info("[ChartTools] 执行 generate_chart 工具: type={}, title={}", chartType, title);

        // 参数校验
        if (chartType == null || chartType.isBlank()) {
            chartType = "bar"; // 默认柱状图
        }

        if (!isValidChartType(chartType)) {
            log.warn("[ChartTools] 不支持的图表类型: {}", chartType);
            return ToolResult.failure("不支持的图表类型: " + chartType + "，支持: bar, line, pie");
        }

        if (seriesData == null || seriesData.isEmpty()) {
            log.warn("[ChartTools] 图表数据为空");
            return ToolResult.failure("图表数据不能为空");
        }

        try {
            // 生成 ECharts 配置
            Map<String, Object> echartsConfig =
                    buildEchartsConfig(chartType, title, xAxisData, seriesData);

            log.debug("[ChartTools] 图表配置生成成功: {} 个数据点", seriesData.size());

            return ToolResult.success(
                    Map.of(
                            "chartType", chartType,
                            "echartsConfig", echartsConfig,
                            "hint", "将 echartsConfig 传入前端 ECharts 组件即可渲染"));

        } catch (Exception e) {
            log.error("[ChartTools] 图表生成失败: {}", e.getMessage(), e);
            return ToolResult.failure("图表生成失败: " + e.getMessage());
        }
    }

    /** 构建完整 ECharts 配置 */
    private Map<String, Object> buildEchartsConfig(
            String chartType,
            String title,
            java.util.List<String> xAxisData,
            java.util.List<Object> seriesData) {

        // 标题配置
        Map<String, Object> titleConfig =
                Map.of("text", title != null ? title : "", "left", "center");

        // 提示框配置
        Map<String, Object> tooltip = Map.of("trigger", "pie".equals(chartType) ? "item" : "axis");

        // 根据图表类型构建配置
        return switch (chartType) {
            case "pie" -> buildPieConfig(titleConfig, tooltip, seriesData);
            default -> buildAxisConfig(chartType, titleConfig, tooltip, xAxisData, seriesData);
        };
    }

    /** 构建饼图配置 */
    private Map<String, Object> buildPieConfig(
            Map<String, Object> titleConfig,
            Map<String, Object> tooltip,
            java.util.List<Object> seriesData) {

        // 饼图需要 {name, value} 格式的数据
        java.util.List<Map<String, Object>> pieData = new java.util.ArrayList<>();
        for (int i = 0; i < seriesData.size(); i++) {
            Object value = seriesData.get(i);
            String name = "类别" + (i + 1);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                pieData.add(mapValue);
            } else {
                pieData.add(Map.of("name", name, "value", value));
            }
        }

        Map<String, Object> series =
                Map.of(
                        "type", "pie",
                        "radius", "50%",
                        "data", pieData);

        return Map.of(
                "title", titleConfig,
                "tooltip", tooltip,
                "series", java.util.List.of(series));
    }

    /** 构建坐标系图表配置（bar/line） */
    private Map<String, Object> buildAxisConfig(
            String chartType,
            Map<String, Object> titleConfig,
            Map<String, Object> tooltip,
            java.util.List<String> xAxisData,
            java.util.List<Object> seriesData) {

        // X 轴配置
        Map<String, Object> xAxis =
                Map.of(
                        "type",
                        "category",
                        "data",
                        xAxisData != null ? xAxisData : generateDefaultXAxis(seriesData.size()));

        // Y 轴配置
        Map<String, Object> yAxis = Map.of("type", "value");

        // 系列配置
        Map<String, Object> series =
                Map.of(
                        "type", chartType,
                        "data", seriesData,
                        "smooth", "line".equals(chartType)); // 折线图平滑显示

        return Map.of(
                "title", titleConfig,
                "tooltip", tooltip,
                "xAxis", xAxis,
                "yAxis", yAxis,
                "series", java.util.List.of(series));
    }

    /** 生成默认 X 轴数据 */
    private java.util.List<String> generateDefaultXAxis(int count) {
        return java.util.stream.IntStream.range(1, count + 1).mapToObj(i -> "项" + i).toList();
    }

    /** 校验图表类型 */
    private boolean isValidChartType(String chartType) {
        return "bar".equals(chartType) || "line".equals(chartType) || "pie".equals(chartType);
    }
}
