package com.jonychen.auth;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jonychen.model.ApiResponse;
import com.jonychen.model.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 用户控制器 处理用户信息相关请求 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /** 获取当前用户信息 */
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> getCurrentUser(Principal principal) {
        if (principal == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), "未登录");
        }

        String userId = principal.getName();
        User user =
                userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));

        return ApiResponse.success(UserInfoVO.from(user));
    }

    /** 更新用户信息 */
    @PutMapping("/me")
    public ApiResponse<UserInfoVO> updateCurrentUser(
            Principal principal, @RequestBody UpdateUserRequest request) {
        if (principal == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), "未登录");
        }

        String userId = principal.getName();
        User user =
                userRepository.findById(userId).orElseThrow(() -> new RuntimeException("用户不存在"));

        // 更新允许修改的字段
        if (request.nickname() != null) {
            user.setNickname(request.nickname());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar());
        }

        userRepository.save(user);

        return ApiResponse.success(UserInfoVO.from(user));
    }

    // ========== 内部 DTO 类 ==========

    /** 更新用户信息请求 */
    public record UpdateUserRequest(String nickname, String avatar) {}
}
