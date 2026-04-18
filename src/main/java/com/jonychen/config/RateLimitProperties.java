package com.jonychen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 动态限流配置
 *
 * 从 Nacos 配置中心读取限流参数，支持动态更新
 * 使用 @RefreshScope 实现配置变更时自动刷新
 */
@Configuration
@RefreshScope
public class RateLimitProperties {

    /**
     * 同步接口限流数
     */
    @Value("${rate.limit.chat.limit:20}")
    private int chatLimit = 20;

    /**
     * 同步接口限流周期（秒）
     */
    @Value("${rate.limit.chat.period:60}")
    private int chatPeriod = 60;

    /**
     * 流式接口限流数
     */
    @Value("${rate.limit.chatStream.limit:30}")
    private int chatStreamLimit = 30;

    /**
     * 流式接口限流周期（秒）
     */
    @Value("${rate.limit.chatStream.period:60}")
    private int chatStreamPeriod = 60;

    public int getChatLimit() {
        return chatLimit;
    }

    public int getChatPeriod() {
        return chatPeriod;
    }

    public int getChatStreamLimit() {
        return chatStreamLimit;
    }

    public int getChatStreamPeriod() {
        return chatStreamPeriod;
    }
}
