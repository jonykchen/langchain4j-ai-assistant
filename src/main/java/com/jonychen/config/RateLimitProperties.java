package com.jonychen.config;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import org.springframework.context.annotation.Configuration;

/**
 * 动态限流配置
 *
 * 从 Nacos 配置中心读取限流参数，支持动态更新
 */
@Configuration
public class RateLimitProperties {

    /**
     * 同步接口限流数
     */
    @NacosValue(value = "${rate.limit.chat.limit:20}", autoRefreshed = true)
    private int chatLimit = 20;

    /**
     * 同步接口限流周期（秒）
     */
    @NacosValue(value = "${rate.limit.chat.period:60}", autoRefreshed = true)
    private int chatPeriod = 60;

    /**
     * 流式接口限流数
     */
    @NacosValue(value = "${rate.limit.chatStream.limit:30}", autoRefreshed = true)
    private int chatStreamLimit = 30;

    /**
     * 流式接口限流周期（秒）
     */
    @NacosValue(value = "${rate.limit.chatStream.period:60}", autoRefreshed = true)
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
