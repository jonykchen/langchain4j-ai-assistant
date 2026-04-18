package com.jonychen.auth;

import com.jonychen.model.ApiResponse;
import com.jonychen.model.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 认证控制器
 * 处理登录、OAuth 回调、Token 刷新等请求
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Validated
public class AuthController {

    private final OAuth2UserService oAuth2UserService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    /**
     * 获取 GitHub 授权 URL
     */
    @GetMapping("/github/url")
    public ApiResponse<String> getGitHubAuthUrl(
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        if (state == null || state.isBlank()) {
            state = generateState();
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = getBaseUrl(request) + "/auth/github/callback";
        }

        String authUrl = oAuth2UserService.getGitHubAuthorizationUrl(redirectUri, state);
        return ApiResponse.success(authUrl);
    }

    /**
     * GitHub OAuth 回调
     */
    @GetMapping("/github/callback")
    public ApiResponse<OAuthCallbackResponse> githubCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String redirectUri
    ) {
        try {
            TokenResponse token = oAuth2UserService.handleGitHubCallback(code, redirectUri);
            User user = userRepository.findById(jwtTokenProvider.getUserIdFromToken(token.accessToken()))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            return ApiResponse.success(new OAuthCallbackResponse(token, UserInfoVO.from(user)));
        } catch (Exception e) {
            log.error("GitHub OAuth 回调处理失败", e);
            return ApiResponse.error(ErrorCode.OAUTH_FAILED.getCode(), "GitHub 登录失败: " + e.getMessage());
        }
    }

    /**
     * 获取 GitLab 授权 URL
     */
    @GetMapping("/gitlab/url")
    public ApiResponse<String> getGitLabAuthUrl(
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false) String state,
            HttpServletRequest request
    ) {
        if (state == null || state.isBlank()) {
            state = generateState();
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = getBaseUrl(request) + "/auth/gitlab/callback";
        }

        String authUrl = oAuth2UserService.getGitLabAuthorizationUrl(redirectUri, state);
        return ApiResponse.success(authUrl);
    }

    /**
     * GitLab OAuth 回调
     */
    @GetMapping("/gitlab/callback")
    public ApiResponse<OAuthCallbackResponse> gitlabCallback(
            @RequestParam String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String redirectUri
    ) {
        try {
            TokenResponse token = oAuth2UserService.handleGitLabCallback(code, redirectUri);
            User user = userRepository.findById(jwtTokenProvider.getUserIdFromToken(token.accessToken()))
                    .orElseThrow(() -> new RuntimeException("用户不存在"));

            return ApiResponse.success(new OAuthCallbackResponse(token, UserInfoVO.from(user)));
        } catch (Exception e) {
            log.error("GitLab OAuth 回调处理失败", e);
            return ApiResponse.error(ErrorCode.OAUTH_FAILED.getCode(), "GitLab 登录失败: " + e.getMessage());
        }
    }

    /**
     * 刷新 Token
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            @RequestBody @Validated RefreshTokenRequest request
    ) {
        String refreshToken = request.refreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ApiResponse.error(ErrorCode.TOKEN_INVALID.getCode(), "刷新令牌无效");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            return ApiResponse.error(ErrorCode.TOKEN_INVALID.getCode(), "不是有效的刷新令牌");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        if (userId == null) {
            return ApiResponse.error(ErrorCode.TOKEN_INVALID.getCode(), "无法解析用户信息");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        TokenResponse newToken = jwtTokenProvider.generateToken(user);
        return ApiResponse.success(newToken);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Principal principal) {
        if (principal != null) {
            log.info("用户登出: {}", principal.getName());
            // 这里可以将 Token 加入黑名单（如果需要即时失效）
        }
        return ApiResponse.success(null);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserInfoVO> getCurrentUser(Principal principal) {
        if (principal == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED.getCode(), "未登录");
        }

        String userId = principal.getName();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        return ApiResponse.success(UserInfoVO.from(user));
    }

    /**
     * 生成随机 state
     */
    private String generateState() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 获取请求基础 URL
     */
    private String getBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();

        StringBuilder baseUrl = new StringBuilder();
        baseUrl.append(scheme).append("://").append(serverName);

        if ((scheme.equals("http") && serverPort != 80) ||
            (scheme.equals("https") && serverPort != 443)) {
            baseUrl.append(":").append(serverPort);
        }

        return baseUrl.toString();
    }

    // ========== 内部 DTO 类 ==========

    /**
     * OAuth 回调响应
     */
    public record OAuthCallbackResponse(
            TokenResponse token,
            UserInfoVO user
    ) {}

    /**
     * 刷新令牌请求
     */
    public record RefreshTokenRequest(
            @NotBlank(message = "刷新令牌不能为空")
            String refreshToken
    ) {}
}
