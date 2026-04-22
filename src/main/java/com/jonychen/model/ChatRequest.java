package com.jonychen.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 聊天请求 DTO
 *
 * @param message 用户消息内容
 */
public record ChatRequest(
        @NotBlank(message = "消息内容不能为空") @Size(max = 10000, message = "消息长度不能超过10000字符")
                String message) {}
