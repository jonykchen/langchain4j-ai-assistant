package com.jonychen.model;

/**
 * 模型提供者配置
 *
 * <p>定义一个大模型提供者的完整配置信息，用于负载均衡和故障转移。
 *
 * @param name 提供者名称（如 dashscope, zhipu, deepseek）
 * @param baseUrl API 基础地址
 * @param apiKey API Key（从环境变量注入）
 * @param modelName 模型名称
 * @param weight 权重（负载均衡时使用，总和应为 100）
 * @param priority 优先级（故障转移顺序，数字越小优先级越高）
 * @param enabled 是否启用
 */
public record ModelProvider(
        String name,
        String baseUrl,
        String apiKey,
        String modelName,
        int weight,
        int priority,
        boolean enabled) {

    /** 创建一个禁用的模型提供者（用于占位） */
    public static ModelProvider disabled(String name) {
        return new ModelProvider(name, "", "", "", 0, Integer.MAX_VALUE, false);
    }

    /** 检查配置是否有效 */
    public boolean isValid() {
        return enabled
                && apiKey != null
                && !apiKey.isBlank()
                && baseUrl != null
                && !baseUrl.isBlank()
                && modelName != null
                && !modelName.isBlank();
    }
}
