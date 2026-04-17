package com.jonychen.auth;

import java.time.LocalDateTime;

/**
 * 用户信息 VO（View Object）
 */
public record UserInfoVO(
    String id,
    String username,
    String email,
    String nickname,
    String avatar,
    String role,
    String provider,
    LocalDateTime createdAt,
    LocalDateTime lastLoginAt
) {
    /**
     * 从 User 实体创建 UserInfoVO
     */
    public static UserInfoVO from(User user) {
        if (user == null) {
            return null;
        }
        return new UserInfoVO(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getNickname(),
            user.getAvatar(),
            user.getRole().name(),
            user.getProvider().getDisplayName(),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }
}
