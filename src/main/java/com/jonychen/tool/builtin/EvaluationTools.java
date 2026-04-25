package com.jonychen.tool.builtin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.jonychen.tool.AgentTool;
import com.jonychen.tool.ToolCategory;
import com.jonychen.tool.ToolParam;
import com.jonychen.tool.ToolResult;

/**
 * Prompt 评测工具集
 *
 * <p>提供 Prompt 版本评测和比较的功能：
 *
 * <ul>
 *   <li>evaluate_prompt - 评估单个 Prompt 的效果
 *   <li>compare_versions - 对比两个 Prompt 版本的效果差异
 * </ul>
 *
 * @author jonychen
 */
@Component
public class EvaluationTools {

    /**
     * 评估单个 Prompt
     *
     * <p>根据测试用例评估 Prompt 的响应质量，返回评分和详细分析。
     *
     * @param promptTemplate Prompt 模板内容
     * @param testInput 测试输入
     * @param criteria 评测标准（可选）
     * @return 评测结果
     */
    @AgentTool(
            name = "evaluate_prompt",
            description = "评估 Prompt 效果，返回响应质量评分和改进建议",
            category = ToolCategory.PROMPT)
    public ToolResult evaluatePrompt(
            @ToolParam(name = "promptTemplate", description = "Prompt 模板内容") String promptTemplate,
            @ToolParam(name = "testInput", description = "测试输入内容") String testInput,
            @ToolParam(
                            name = "criteria",
                            description = "评测标准（accuracy/clarity/completeness）",
                            required = false)
                    String criteria) {

        if (promptTemplate == null || promptTemplate.isBlank()) {
            return ToolResult.failure("Prompt 模板不能为空");
        }

        if (testInput == null || testInput.isBlank()) {
            return ToolResult.failure("测试输入不能为空");
        }

        // 评测维度
        String[] dimensions =
                criteria != null && !criteria.isBlank()
                        ? criteria.split(",")
                        : new String[] {"accuracy", "clarity", "completeness"};

        // 模拟评测结果（实际应调用 LLM 进行评测）
        Map<String, Object> scores = new HashMap<>();
        double totalScore = 0;
        int scoreCount = 0;

        for (String dimension : dimensions) {
            String dim = dimension.trim().toLowerCase();
            double score = evaluateDimension(dim, promptTemplate, testInput);
            scores.put(dim, score);
            totalScore += score;
            scoreCount++;
        }

        double avgScore = scoreCount > 0 ? totalScore / scoreCount : 0;

        // 生成改进建议
        List<String> suggestions = generateSuggestions(scores, avgScore);

        Map<String, Object> result = new HashMap<>();
        result.put("totalScore", Math.round(avgScore * 100) / 100.0);
        result.put("scores", scores);
        result.put("suggestions", suggestions);
        result.put("promptLength", promptTemplate.length());
        result.put("hasPlaceholder", promptTemplate.contains("{"));

        return ToolResult.success(result);
    }

    /**
     * 比较两个 Prompt 版本
     *
     * <p>对比两个 Prompt 版本在相同测试用例上的表现差异。
     *
     * @param promptV1 第一个 Prompt 版本
     * @param promptV2 第二个 Prompt 版本
     * @param testInput 测试输入
     * @return 对比结果
     */
    @AgentTool(
            name = "compare_versions",
            description = "对比两个 Prompt 版本的效果差异，返回性能对比和推荐版本",
            category = ToolCategory.PROMPT)
    public ToolResult compareVersions(
            @ToolParam(name = "promptV1", description = "第一个 Prompt 版本内容") String promptV1,
            @ToolParam(name = "promptV2", description = "第二个 Prompt 版本内容") String promptV2,
            @ToolParam(name = "testInput", description = "测试输入内容") String testInput) {

        if (promptV1 == null || promptV1.isBlank()) {
            return ToolResult.failure("Prompt V1 不能为空");
        }

        if (promptV2 == null || promptV2.isBlank()) {
            return ToolResult.failure("Prompt V2 不能为空");
        }

        if (testInput == null || testInput.isBlank()) {
            return ToolResult.failure("测试输入不能为空");
        }

        // 分别评测两个版本
        double scoreV1 = evaluateOverall(promptV1, testInput);
        double scoreV2 = evaluateOverall(promptV2, testInput);

        // 对比分析
        double improvement = scoreV2 - scoreV1;
        double improvementPercent =
                scoreV1 > 0 ? (improvement / scoreV1) * 100 : (scoreV2 > 0 ? 100 : 0);

        Map<String, Object> result = new HashMap<>();
        result.put("v1Score", Math.round(scoreV1 * 100) / 100.0);
        result.put("v2Score", Math.round(scoreV2 * 100) / 100.0);
        result.put("improvement", Math.round(improvement * 100) / 100.0);
        result.put("improvementPercent", Math.round(improvementPercent * 10) / 10.0);
        result.put("recommended", improvement >= 0 ? "v2" : "v1");
        result.put(
                "summary",
                String.format(
                        "V2 相比 V1 %s %.1f%%",
                        improvement >= 0 ? "提升" : "下降", Math.abs(improvementPercent)));

        // 版本特征对比
        result.put("v1Length", promptV1.length());
        result.put("v2Length", promptV2.length());
        result.put("v1HasPlaceholder", promptV1.contains("{"));
        result.put("v2HasPlaceholder", promptV2.contains("{"));

        return ToolResult.success(result);
    }

    /** 评估单个维度的分数 */
    private double evaluateDimension(String dimension, String prompt, String input) {
        // 基于启发式规则的评分（实际应调用 LLM 或专门的评测模型）
        return switch (dimension) {
            case "accuracy" -> {
                // 检查 Prompt 是否包含明确的指令
                double baseScore = 70.0;
                if (prompt.toLowerCase().contains("准确")
                        || prompt.toLowerCase().contains("correct")) {
                    baseScore += 10;
                }
                if (prompt.contains("{") && prompt.contains("}")) {
                    baseScore += 5; // 有占位符结构化更好
                }
                yield Math.min(baseScore + Math.random() * 15, 100);
            }
            case "clarity" -> {
                // 检查 Prompt 是否清晰
                double baseScore = 75.0;
                if (prompt.length() < 500) {
                    baseScore += 10; // 简洁
                }
                if (prompt.split("\n").length > 3) {
                    baseScore += 5; // 有结构
                }
                yield Math.min(baseScore + Math.random() * 10, 100);
            }
            case "completeness" -> {
                // 检查 Prompt 是否完整
                double baseScore = 70.0;
                if (prompt.length() > 100) {
                    baseScore += 10;
                }
                if (prompt.contains("步骤") || prompt.contains("step")) {
                    baseScore += 10;
                }
                if (prompt.contains("注意")
                        || prompt.contains("注意")
                        || prompt.toLowerCase().contains("note")) {
                    baseScore += 5;
                }
                yield Math.min(baseScore + Math.random() * 15, 100);
            }
            default -> 70.0 + Math.random() * 20;
        };
    }

    /** 评估整体分数 */
    private double evaluateOverall(String prompt, String input) {
        double accuracy = evaluateDimension("accuracy", prompt, input);
        double clarity = evaluateDimension("clarity", prompt, input);
        double completeness = evaluateDimension("completeness", prompt, input);
        return (accuracy + clarity + completeness) / 3;
    }

    /** 生成改进建议 */
    private List<String> generateSuggestions(Map<String, Object> scores, double avgScore) {
        java.util.ArrayList<String> suggestions = new java.util.ArrayList<>();

        if (avgScore < 60) {
            suggestions.add("分数较低，建议重新设计 Prompt 结构");
        } else if (avgScore < 80) {
            suggestions.add("分数中等，可考虑增加更明确的指令");
        }

        if (scores.containsKey("accuracy") && (Double) scores.get("accuracy") < 75) {
            suggestions.add("准确性不足，建议添加准确性要求或示例");
        }

        if (scores.containsKey("clarity") && (Double) scores.get("clarity") < 75) {
            suggestions.add("清晰度不足，建议简化表达或增加结构化格式");
        }

        if (scores.containsKey("completeness") && (Double) scores.get("completeness") < 75) {
            suggestions.add("完整性不足，建议补充更多上下文或约束条件");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("表现良好，继续保持");
        }

        return suggestions;
    }
}
