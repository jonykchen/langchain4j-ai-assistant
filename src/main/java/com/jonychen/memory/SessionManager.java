package com.jonychen.memory;

import java.util.List;
import java.util.Optional;

/** 会话管理器接口 管理用户会话的创建、查询、更新、删除 */
public interface SessionManager {

    /**
     * 创建新会话
     *
     * @param userId 用户 ID
     * @param title 会话标题
     * @return 会话摘要信息
     */
    SessionInfo createSession(String userId, String title);

    /**
     * 获取或创建会话 如果会话不存在则创建新会话
     *
     * @param sessionId 会话 ID（可为空）
     * @param userId 用户 ID
     * @return 会话摘要信息
     */
    SessionInfo getOrCreateSession(String sessionId, String userId);

    /**
     * 获取用户所有会话
     *
     * @param userId 用户 ID
     * @return 会话列表，按更新时间倒序
     */
    List<SessionInfo> listSessions(String userId);

    /**
     * 获取会话详情
     *
     * @param sessionId 会话 ID
     * @return 会话摘要信息（如果存在）
     */
    Optional<SessionInfo> getSession(String sessionId);

    /**
     * 更新会话标题
     *
     * @param sessionId 会话 ID
     * @param title 新标题
     */
    void updateSessionTitle(String sessionId, String title);

    /**
     * 删除会话
     *
     * @param sessionId 会话 ID
     */
    void deleteSession(String sessionId);

    /**
     * 检查会话是否属于用户
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @return 是否属于该用户
     */
    boolean isSessionOwner(String sessionId, String userId);

    /**
     * 获取用户会话数量
     *
     * @param userId 用户 ID
     * @return 会话数量
     */
    long countSessions(String userId);
}
