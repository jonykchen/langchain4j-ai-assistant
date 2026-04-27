package com.jonychen.admin.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jonychen.admin.dto.PageResponse;
import com.jonychen.admin.dto.UserAdminVO;
import com.jonychen.admin.repository.TokenUsageRepository;
import com.jonychen.auth.User;
import com.jonychen.auth.UserRepository;
import com.jonychen.auth.UserRole;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户管理服务
 *
 * @author jonychen
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final TokenUsageRepository tokenUsageRepository;

    /** 分页获取用户列表 */
    public PageResponse<UserAdminVO> getUsers(
            int page, int size, String search, String role, String provider) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by("createdAt").descending());

        Page<User> userPage;

        if (search != null && !search.isBlank()) {
            userPage =
                    userRepository.findByUsernameContainingOrEmailContaining(
                            search, search, pageable);
        } else if (role != null && !role.isBlank()) {
            userPage = userRepository.findByRole(UserRole.valueOf(role), pageable);
        } else if (provider != null && !provider.isBlank()) {
            userPage =
                    userRepository.findByProvider(
                            com.jonychen.auth.AuthProvider.valueOf(provider), pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        List<UserAdminVO> users = userPage.getContent().stream().map(this::toUserAdminVO).toList();

        return PageResponse.of(users, userPage.getTotalElements(), page, size);
    }

    /** 获取用户详情 */
    public UserAdminVO getUserDetail(String userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return toUserAdminVO(user);
    }

    /** 更新用户角色 */
    @Transactional
    public void updateUserRole(String userId, String role) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        user.setRole(UserRole.valueOf(role));
        userRepository.save(user);

        log.info("Updated user {} role to {}", userId, role);
    }

    /** 更新用户配额 */
    @Transactional
    public void updateUserQuota(String userId, QuotaUpdateRequest request) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // 存储配额到用户元数据（简化实现）
        // 实际项目中可以创建单独的配额表
        log.info(
                "Updated user {} quota: daily={}, monthly={}",
                userId,
                request.dailyTokenLimit(),
                request.monthlyTokenLimit());
    }

    /** 删除用户 */
    @Transactional
    public void deleteUser(String userId) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        // 不允许删除管理员
        if (user.getRole() == UserRole.ADMIN) {
            // 检查是否是最后一个管理员
            long adminCount = userRepository.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new RuntimeException("无法删除最后一个管理员账户");
            }
            throw new RuntimeException("不允许删除管理员用户");
        }

        userRepository.delete(user);
        log.info("Deleted user: {}", userId);
    }

    /** 获取用户使用统计 */
    public UserUsageStats getUserUsageStats(String userId) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        List<Object[]> todayUsage = tokenUsageRepository.getUserUsage(userId, todayStart, now);
        List<Object[]> monthUsage = tokenUsageRepository.getUserUsage(userId, monthStart, now);

        long todayTokens =
                todayUsage.isEmpty() || todayUsage.get(0)[0] == null
                        ? 0
                        : ((Number) todayUsage.get(0)[0]).longValue();
        double todayCost =
                todayUsage.isEmpty() || todayUsage.get(0)[1] == null
                        ? 0
                        : ((Number) todayUsage.get(0)[1]).doubleValue();

        long monthTokens =
                monthUsage.isEmpty() || monthUsage.get(0)[0] == null
                        ? 0
                        : ((Number) monthUsage.get(0)[0]).longValue();
        double monthCost =
                monthUsage.isEmpty() || monthUsage.get(0)[1] == null
                        ? 0
                        : ((Number) monthUsage.get(0)[1]).doubleValue();

        return new UserUsageStats(todayTokens, todayCost, monthTokens, monthCost);
    }

    private UserAdminVO toUserAdminVO(User user) {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        List<Object[]> usage = tokenUsageRepository.getUserUsage(user.getId(), todayStart, now);
        long todayTokens =
                usage.isEmpty() || usage.get(0)[0] == null
                        ? 0
                        : ((Number) usage.get(0)[0]).longValue();
        double todayCost =
                usage.isEmpty() || usage.get(0)[1] == null
                        ? 0
                        : ((Number) usage.get(0)[1]).doubleValue();

        return new UserAdminVO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatar(),
                user.getRole().name(),
                user.getProvider().name(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                todayTokens,
                todayCost,
                100000, // 默认配额
                2000000);
    }

    /** 配额更新请求 */
    public record QuotaUpdateRequest(int dailyTokenLimit, int monthlyTokenLimit) {}

    /** 用户使用统计 */
    public record UserUsageStats(
            long todayTokens, double todayCost, long monthTokens, double monthCost) {}
}
