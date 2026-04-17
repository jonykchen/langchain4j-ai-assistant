package com.jonychen.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 基于 Redis 的会话管理器实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSessionManager implements SessionManager {

    private final StringRedisTemplate redisTemplate;

    @Value("${app.session.ttl:7d}")
    private String sessionTtl;

    // Redis Key 前缀
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user:sessions:";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public SessionInfo createSession(String userId, String title) {
        String sessionId = generateSessionId();
        LocalDateTime now = LocalDateTime.now();

        SessionInfo session = new SessionInfo(
                sessionId,
                userId,
                title != null ? title : "新对话",
                now,
                now,
                0
        );

        // 保存会话元数据
        saveSession(session);

        // 添加到用户会话列表
        addToUserSessionList(userId, sessionId);

        log.info("创建会话: sessionId={}, userId={}, title={}", sessionId, userId, title);

        return session;
    }

    @Override
    public SessionInfo getOrCreateSession(String sessionId, String userId) {
        if (sessionId != null) {
            Optional<SessionInfo> existing = getSession(sessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        return createSession(userId, null);
    }

    @Override
    public List<SessionInfo> listSessions(String userId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;
        Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);

        if (sessionIds == null || sessionIds.isEmpty()) {
            return Collections.emptyList();
        }

        return sessionIds.stream()
                .map(this::getSession)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt())) // 按更新时间倒序
                .collect(Collectors.toList());
    }

    @Override
    public Optional<SessionInfo> getSession(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return Optional.empty();
        }

        try {
            SessionInfo session = objectMapper.readValue(json, SessionInfo.class);
            return Optional.of(session);
        } catch (JsonProcessingException e) {
            log.error("解析会话数据失败: sessionId={}", sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public void updateSessionTitle(String sessionId, String title) {
        String key = SESSION_KEY_PREFIX + sessionId;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            log.warn("会话不存在，无法更新标题: sessionId={}", sessionId);
            return;
        }

        try {
            SessionInfo session = objectMapper.readValue(json, SessionInfo.class);
            SessionInfo updated = new SessionInfo(
                    session.sessionId(),
                    session.userId(),
                    title,
                    session.createdAt(),
                    LocalDateTime.now(),
                    session.messageCount()
            );

            saveSession(updated);
            log.info("更新会话标题: sessionId={}, title={}", sessionId, title);

        } catch (JsonProcessingException e) {
            log.error("更新会话标题失败: sessionId={}", sessionId, e);
        }
    }

    @Override
    public void deleteSession(String sessionId) {
        // 先获取会话信息以获取 userId
        Optional<SessionInfo> sessionOpt = getSession(sessionId);

        // 删除会话数据
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.delete(key);

        // 从用户会话列表中移除
        sessionOpt.ifPresent(session -> {
            String userSessionsKey = USER_SESSIONS_KEY_PREFIX + session.userId();
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
        });

        // 删除会话消息
        String messagesKey = SESSION_KEY_PREFIX + sessionId + ":messages";
        redisTemplate.delete(messagesKey);

        log.info("删除会话: sessionId={}", sessionId);
    }

    @Override
    public boolean isSessionOwner(String sessionId, String userId) {
        return getSession(sessionId)
                .map(session -> session.userId().equals(userId))
                .orElse(false);
    }

    @Override
    public long countSessions(String userId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForSet().size(userSessionsKey);
        return size != null ? size : 0;
    }

    /**
     * 更新会话的消息数量
     */
    public void updateMessageCount(String sessionId, int messageCount) {
        getSession(sessionId).ifPresent(session -> {
            SessionInfo updated = new SessionInfo(
                    session.sessionId(),
                    session.userId(),
                    session.title(),
                    session.createdAt(),
                    LocalDateTime.now(),
                    messageCount
            );
            saveSession(updated);
        });
    }

    /**
     * 更新会话最后更新时间
     */
    public void touchSession(String sessionId) {
        getSession(sessionId).ifPresent(session -> {
            SessionInfo updated = new SessionInfo(
                    session.sessionId(),
                    session.userId(),
                    session.title(),
                    session.createdAt(),
                    LocalDateTime.now(),
                    session.messageCount()
            );
            saveSession(updated);
        });
    }

    // ========== 私有方法 ==========

    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void saveSession(SessionInfo session) {
        String key = SESSION_KEY_PREFIX + session.sessionId();
        try {
            String json = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, json, parseTtl(), TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("序列化会话数据失败: sessionId={}", session.sessionId(), e);
        }
    }

    private void addToUserSessionList(String userId, String sessionId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId;
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, parseTtl(), TimeUnit.SECONDS);
    }

    private long parseTtl() {
        // 解析 TTL 配置，支持 s/m/h/d 后缀
        String ttl = sessionTtl.trim().toLowerCase();
        long multiplier = 1;

        if (ttl.endsWith("d")) {
            multiplier = 24 * 60 * 60;
            ttl = ttl.substring(0, ttl.length() - 1);
        } else if (ttl.endsWith("h")) {
            multiplier = 60 * 60;
            ttl = ttl.substring(0, ttl.length() - 1);
        } else if (ttl.endsWith("m")) {
            multiplier = 60;
            ttl = ttl.substring(0, ttl.length() - 1);
        } else if (ttl.endsWith("s")) {
            ttl = ttl.substring(0, ttl.length() - 1);
        }

        try {
            return Long.parseLong(ttl) * multiplier;
        } catch (NumberFormatException e) {
            return 7 * 24 * 60 * 60; // 默认 7 天
        }
    }
}
