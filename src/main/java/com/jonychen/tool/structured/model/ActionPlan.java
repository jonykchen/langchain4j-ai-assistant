package com.jonychen.tool.structured.model;

import java.util.List;
import java.util.Map;

/**
 * 行动计划
 *
 * <p>用于复杂任务的分解和执行规划
 *
 * @param goal 目标描述
 * @param steps 执行步骤列表
 * @param estimatedTime 预估时间（秒）
 * @param complexity 复杂度等级
 * @param dependencies 依赖关系
 * @param fallbackPlan 备用计划
 * @author jonychen
 */
public record ActionPlan(
        String goal,
        List<Step> steps,
        int estimatedTime,
        String complexity,
        List<Dependency> dependencies,
        String fallbackPlan) {
    /** 执行步骤 */
    public record Step(
            int order,
            String action,
            String tool,
            Map<String, Object> params,
            String description,
            String expectedOutput,
            int estimatedDuration,
            String status) {}

    /** 依赖关系 */
    public record Dependency(int stepId, int dependsOn, String type) {}

    /** 复杂度枚举 */
    public enum Complexity {
        SIMPLE("simple", "简单"),
        MODERATE("moderate", "中等"),
        COMPLEX("complex", "复杂"),
        VERY_COMPLEX("very_complex", "非常复杂");

        private final String value;
        private final String description;

        Complexity(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }

    /** 状态枚举 */
    public enum StepStatus {
        PENDING("pending", "待执行"),
        RUNNING("running", "执行中"),
        COMPLETED("completed", "已完成"),
        FAILED("failed", "失败"),
        SKIPPED("skipped", "跳过");

        private final String value;
        private final String description;

        StepStatus(String value, String description) {
            this.value = value;
            this.description = description;
        }

        public String getValue() {
            return value;
        }
    }

    /** 创建简单计划 */
    public static ActionPlan simple(String goal, String action, String tool) {
        return new ActionPlan(
                goal,
                List.of(
                        new Step(
                                1,
                                action,
                                tool,
                                Map.of(),
                                action,
                                "成功执行",
                                5,
                                StepStatus.PENDING.getValue())),
                5,
                Complexity.SIMPLE.getValue(),
                List.of(),
                null);
    }

    /** 创建空计划 */
    public static ActionPlan empty() {
        return new ActionPlan("", List.of(), 0, Complexity.SIMPLE.getValue(), List.of(), null);
    }

    /** 添加步骤 */
    public ActionPlan addStep(Step step) {
        List<Step> newSteps = new java.util.ArrayList<>(steps);
        newSteps.add(step);
        return new ActionPlan(
                goal,
                newSteps,
                estimatedTime + step.estimatedDuration(),
                complexity,
                dependencies,
                fallbackPlan);
    }
}
