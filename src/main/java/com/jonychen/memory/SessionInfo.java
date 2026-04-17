package com.jonychen.memory;

import java.time.LocalDateTime;

/**
 * 会话摘要信息
 */
public record SessionInfo(
    /**
     * 会话 ID
     */
    String sessionId,

    /**
     * 用户 ID
     */
    String userId,

    /**
     * 会话标题
     */
    String title,

    /**
     * 创建时间
     */
    LocalDateTime createdAt,

    /**
     * 最后更新时间
     */
    LocalDateTime updatedAt,

    /**
     * 消息数量
     */
    int messageCount
) {
    /**
     * 创建简单的会话摘要
     */
    public static SessionInfo of(String sessionId, String userId, String title,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new SessionInfo(sessionId, userId, title, createdAt, updatedAt, 0);
    }
}
