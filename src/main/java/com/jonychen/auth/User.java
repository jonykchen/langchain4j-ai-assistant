package com.jonychen.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户实体类 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {

    /** 用户唯一标识 */
    @Id
    @Column(length = 64)
    private String id;

    /** 用户名（唯一） */
    @Column(nullable = false, unique = true, length = 100)
    private String username;

    /** 邮箱（唯一） */
    @Column(unique = true, length = 255)
    private String email;

    /** 昵称 */
    @Column(length = 100)
    private String nickname;

    /** 头像 URL */
    @Column(length = 500)
    private String avatar;

    /** 认证提供商 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    /** 第三方平台用户 ID */
    @Column(length = 100)
    private String providerId;

    /** 密码（BCrypt 加密存储，OAuth 用户可为空） */
    @Column(length = 100)
    private String password;

    /** 用户角色 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** 注册时间 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 乐观锁版本号 */
    @Version private Long version;

    /** 创建用户实体（工厂方法） */
    public static User create(
            String id,
            String username,
            String email,
            String nickname,
            String avatar,
            AuthProvider provider,
            String providerId) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setAvatar(avatar);
        user.setProvider(provider);
        user.setProviderId(providerId);
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
