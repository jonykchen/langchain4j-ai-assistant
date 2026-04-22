package com.jonychen.tool.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 敏感数据脱敏器
 *
 * <p>对工具执行记录中的敏感信息进行脱敏处理
 *
 * @author jonychen
 */
@Slf4j
@Component
public class SensitiveDataMasker {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 敏感字段名模式 */
    private static final Set<String> SENSITIVE_FIELDS =
            Set.of(
                    "password",
                    "pwd",
                    "pass",
                    "secret",
                    "token",
                    "apikey",
                    "api_key",
                    "access_token",
                    "refresh_token",
                    "authorization",
                    "credential",
                    "private_key",
                    "privatekey",
                    "ssn",
                    "credit_card",
                    "creditcard",
                    "phone",
                    "mobile",
                    "email",
                    "id_card",
                    "idcard");

    /** 敏感值匹配模式 */
    private static final List<Pattern> SENSITIVE_PATTERNS =
            List.of(
                    // 手机号
                    Pattern.compile("1[3-9]\\d{9}"),
                    // 邮箱
                    Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
                    // 身份证号
                    Pattern.compile("\\d{17}[\\dXx]"),
                    // 银行卡号
                    Pattern.compile("\\d{16,19}"),
                    // API Key 风格
                    Pattern.compile("[a-zA-Z0-9]{32,}"),
                    // JWT Token 风格
                    Pattern.compile("eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*"));

    /**
     * 对 Map 进行脱敏
     *
     * @param data 原始数据
     * @return 脱敏后的数据
     */
    public Map<String, Object> mask(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();

            if (isSensitiveField(key)) {
                masked.put(entry.getKey(), maskValue(value));
            } else if (value instanceof String str) {
                masked.put(entry.getKey(), maskSensitivePatterns(str));
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                masked.put(entry.getKey(), mask(nestedMap));
            } else {
                masked.put(entry.getKey(), value);
            }
        }

        return masked;
    }

    /**
     * 对对象进行脱敏（返回 JSON 字符串）
     *
     * @param data 原始数据
     * @return 脱敏后的 JSON 字符串
     */
    public String mask(Object data) {
        if (data == null) {
            return null;
        }

        try {
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) data;
                return objectMapper.writeValueAsString(mask(map));
            } else if (data instanceof String) {
                return maskSensitivePatterns((String) data);
            } else {
                return objectMapper.writeValueAsString(data);
            }
        } catch (Exception e) {
            log.warn("Failed to mask data: {}", e.getMessage());
            return "[MASKED]";
        }
    }

    /** 判断是否为敏感字段 */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase().replace("_", "");
        return SENSITIVE_FIELDS.stream()
                .anyMatch(sensitive -> lower.contains(sensitive.replace("_", "")));
    }

    /** 对敏感值进行脱敏 */
    private String maskValue(Object value) {
        if (value == null) {
            return null;
        }

        String strValue = String.valueOf(value);

        // 完全隐藏短值
        if (strValue.length() <= 4) {
            return "****";
        }

        // 保留前后各 2 个字符
        return strValue.substring(0, 2) + "****" + strValue.substring(strValue.length() - 2);
    }

    /** 对字符串中的敏感模式进行脱敏 */
    private String maskSensitivePatterns(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String masked = text;

        // 手机号脱敏：138****1234
        masked =
                Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})").matcher(masked).replaceAll("$1****$2");

        // 邮箱脱敏：a***@example.com
        masked =
                Pattern.compile("([a-zA-Z0-9])[a-zA-Z0-9._%+-]*@([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})")
                        .matcher(masked)
                        .replaceAll("$1***@$2");

        // 身份证脱敏：320***********1234
        masked =
                Pattern.compile("(\\d{3})\\d{11}(\\d{4})")
                        .matcher(masked)
                        .replaceAll("$1***********$2");

        // 银行卡脱敏：6222****1234
        masked =
                Pattern.compile("(\\d{4})\\d{8,11}(\\d{4})").matcher(masked).replaceAll("$1****$2");

        // JWT Token 脱敏
        masked =
                Pattern.compile("eyJ[a-zA-Z0-9_-]*\\.eyJ[a-zA-Z0-9_-]*\\.[a-zA-Z0-9_-]*")
                        .matcher(masked)
                        .replaceAll("eyJ***.eyJ***.***");

        return masked;
    }

    /**
     * 对长文本进行截断
     *
     * @param text 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    public String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
