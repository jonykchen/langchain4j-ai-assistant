package com.jonychen.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 管理员账户初始化器 启动时自动创建 admin 用户（如果不存在） */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "123456";
    private static final String ADMIN_EMAIL = "admin@example.com";

    @PostConstruct
    public void init() {
        if (!userRepository.existsByUsername(ADMIN_USERNAME)) {
            User admin = new User();
            admin.setId(UUID.randomUUID().toString());
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setEmail(ADMIN_EMAIL);
            admin.setNickname("Administrator");
            admin.setProvider(AuthProvider.CUSTOM);
            admin.setRole(UserRole.ADMIN);
            admin.setCreatedAt(LocalDateTime.now());

            userRepository.save(admin);
            log.info("管理员账户已自动创建：{}", ADMIN_USERNAME);
        }
    }
}
