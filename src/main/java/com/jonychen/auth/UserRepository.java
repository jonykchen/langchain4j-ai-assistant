package com.jonychen.auth;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    /**
     * 根据用户名查询用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据认证提供商和提供商用户 ID 查询用户
     */
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 按角色查询用户
     */
    Page<User> findByRole(UserRole role, Pageable pageable);

    /**
     * 按提供商查询用户
     */
    Page<User> findByProvider(AuthProvider provider, Pageable pageable);

    /**
     * 按用户名或邮箱模糊查询
     */
    Page<User> findByUsernameContainingOrEmailContaining(String username, String email, Pageable pageable);
}
