package com.jonychen.auth;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
    private static final String ADMIN_EMAIL = "admin@example.com";

    /** 管理员密码配置 生产环境必须通过环境变量 ADMIN_PASSWORD 设置 开发环境如未设置，将生成随机密码并输出到日志 */
    @Value("${ADMIN_PASSWORD:}")
    private String adminPassword;

    @PostConstruct
    public void init() {
        if (!userRepository.existsByUsername(ADMIN_USERNAME)) {
            // 确定密码：优先使用环境变量，否则生成随机密码
            String password = determinePassword();

            User admin = new User();
            admin.setId(UUID.randomUUID().toString());
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setEmail(ADMIN_EMAIL);
            admin.setNickname("Administrator");
            admin.setProvider(AuthProvider.CUSTOM);
            admin.setRole(UserRole.ADMIN);
            admin.setCreatedAt(LocalDateTime.now());

            userRepository.save(admin);
            log.info("管理员账户已自动创建：{}", ADMIN_USERNAME);

            // 如果是生成的随机密码，输出到日志（仅首次启动可见）
            if (!StringUtils.hasText(adminPassword)) {
                log.warn("=========================================================");
                log.warn("【安全提示】未设置 ADMIN_PASSWORD 环境变量");
                log.warn("已生成随机密码，请登录后立即修改：{}", password);
                log.warn("=========================================================");
            }
        }
    }

    /** 确定管理员密码 优先级：环境变量 > 随机生成 */
    private String determinePassword() {
        if (StringUtils.hasText(adminPassword)) {
            log.info("使用环境变量 ADMIN_PASSWORD");
            return adminPassword;
        }
        // 生成 16 位随机密码（字母数字混合）
        String randomPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.warn("未配置 ADMIN_PASSWORD，已生成随机密码");
        return randomPassword;
    }
}
