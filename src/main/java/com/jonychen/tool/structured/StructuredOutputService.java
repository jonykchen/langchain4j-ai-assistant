package com.jonychen.tool.structured;

import com.jonychen.tool.structured.model.UserIntent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 结构化输出服务
 *
 * 使用 LLM 生成结构化输出
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructuredOutputService {

    private final ChatModel chatModel;
    private final StructuredOutputParser parser;

    /**
     * 用户意图识别 Schema
     */
    private static final OutputSchema USER_INTENT_SCHEMA = OutputSchema.object(
            "UserIntent",
            "用户意图识别结果",
            Map.of(
                    "intent", OutputSchema.PropertySchema.string("意图类型：question, command, search, calculation, conversation, unknown"),
                    "confidence", OutputSchema.PropertySchema.number("确信度，范围 0-1"),
                    "entities", OutputSchema.PropertySchema.array("提取的实体列表",
                            new OutputSchema.PropertySchema("object", "实体", null, null,
                                    Map.of(
                                            "type", OutputSchema.PropertySchema.string("实体类型"),
                                            "value", OutputSchema.PropertySchema.string("实体值"),
                                            "confidence", OutputSchema.PropertySchema.number("确信度")
                                    ))),
                    "requiresTool", OutputSchema.PropertySchema.bool("是否需要工具调用"),
                    "suggestedTool", OutputSchema.PropertySchema.string("建议使用的工具名称"),
                    "responseStrategy", OutputSchema.PropertySchema.string("响应策略：direct_response, informational_response, tool_execution")
            ),
            List.of("intent", "confidence", "requiresTool")
    );

    private String generate(String prompt) {
        return chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from(prompt))
                .build()).aiMessage().text();
    }

    /**
     * 解析用户意图
     *
     * @param userInput 用户输入
     * @return 用户意图
     */
    public UserIntent parseUserIntent(String userInput) {
        String prompt = buildIntentPrompt(userInput);

        String response = generate(prompt);

        StructuredOutputConfig config = StructuredOutputConfig.lenient(USER_INTENT_SCHEMA);
        Map<String, Object> data = parser.parse(response, config);

        return mapToUserIntent(data);
    }

    /**
     * 解析为指定类型的结构化输出
     *
     * @param prompt     提示词
     * @param schema     Schema 定义
     * @param type       目标类型
     * @return 解析结果
     */
    public <T> T parse(String prompt, OutputSchema schema, Class<T> type) {
        String response = generate(prompt);
        StructuredOutputConfig config = StructuredOutputConfig.defaultConfig(schema);
        return parser.parse(response, config, type);
    }

    /**
     * 解析为 Map
     *
     * @param prompt 提示词
     * @param schema Schema 定义
     * @return 解析结果
     */
    public Map<String, Object> parseToMap(String prompt, OutputSchema schema) {
        String response = generate(prompt);
        StructuredOutputConfig config = StructuredOutputConfig.defaultConfig(schema);
        return parser.parse(response, config);
    }

    /**
     * 构建意图识别 Prompt
     */
    private String buildIntentPrompt(String userInput) {
        return """
                分析以下用户输入，识别用户意图。

                用户输入：
                "%s"

                %s

                请输出 JSON 格式的分析结果。
                """.formatted(userInput, parser.generateSchemaPrompt(USER_INTENT_SCHEMA));
    }

    /**
     * 将 Map 转换为 UserIntent
     */
    @SuppressWarnings("unchecked")
    private UserIntent mapToUserIntent(Map<String, Object> data) {
        String intent = (String) data.getOrDefault("intent", "unknown");
        double confidence = ((Number) data.getOrDefault("confidence", 0.0)).doubleValue();
        boolean requiresTool = (Boolean) data.getOrDefault("requiresTool", false);
        String suggestedTool = (String) data.get("suggestedTool");
        String responseStrategy = (String) data.getOrDefault("responseStrategy", "direct_response");

        List<Map<String, Object>> entitiesData = (List<Map<String, Object>>) data.get("entities");
        List<UserIntent.Entity> entities = entitiesData != null
                ? entitiesData.stream()
                .map(e -> new UserIntent.Entity(
                        (String) e.get("type"),
                        (String) e.get("value"),
                        ((Number) e.getOrDefault("startIndex", 0)).intValue(),
                        ((Number) e.getOrDefault("endIndex", 0)).intValue(),
                        ((Number) e.getOrDefault("confidence", 1.0)).doubleValue()
                ))
                .toList()
                : List.of();

        List<String> subIntents = (List<String>) data.get("subIntents");
        if (subIntents == null) {
            subIntents = List.of();
        }

        return new UserIntent(intent, confidence, entities, subIntents, requiresTool, suggestedTool, responseStrategy);
    }
}