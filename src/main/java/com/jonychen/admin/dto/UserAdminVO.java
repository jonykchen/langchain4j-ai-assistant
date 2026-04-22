package com.jonychen.admin.dto;

import java.time.LocalDateTime;

/**
 * 用户管理视图对象
 *
 * @param id 用户ID
 * @param username 用户名
 * @param email 邮箱
 * @param nickname 昵称
 * @param avatar 头像
 * @param role 角色
 * @param provider 登录方式
 * @param createdAt 创建时间
 * @param lastLoginAt 最后登录时间
 * @param todayTokens 今日 Token
 * @param todayCost 今日费用
 * @param dailyTokenLimit 日 Token 限额
 * @param monthlyTokenLimit 月 Token 限额
 * @author jonychen
 */
public record UserAdminVO(
        String id,
        String username,
        String email,
        String nickname,
        String avatar,
        String role,
        String provider,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        long todayTokens,
        double todayCost,
        int dailyTokenLimit,
        int monthlyTokenLimit) {}
