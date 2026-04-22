package com.jonychen.tool.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 参数校验结果
 *
 * @param valid 是否校验通过
 * @param errors 错误信息列表
 * @author jonychen
 */
public record ValidationResult(boolean valid, List<String> errors) {
    /** 创建成功的校验结果 */
    public static ValidationResult success() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /** 创建失败的校验结果 */
    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors);
    }

    /** 创建失败的校验结果（单条错误） */
    public static ValidationResult failure(String error) {
        return new ValidationResult(false, List.of(error));
    }

    /** 合并多个校验结果 */
    public ValidationResult merge(ValidationResult other) {
        if (this.valid && other.valid) {
            return success();
        }

        List<String> allErrors = new ArrayList<>(this.errors);
        allErrors.addAll(other.errors);
        return failure(allErrors);
    }

    /** 获取错误消息 */
    public String getErrorMessage() {
        return String.join("; ", errors);
    }

    /** 是否有错误 */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}
