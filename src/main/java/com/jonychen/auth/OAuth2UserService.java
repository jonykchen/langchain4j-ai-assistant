package com.jonychen.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * OAuth 用户服务
 * 处理 GitHub、GitLab 等第三方登录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // GitHub OAuth 配置
    @Value("${oauth.github.client-id:}")
    private String githubClientId;

    @Value("${oauth.github.client-secret:}")
    private String githubClientSecret;

    // GitLab OAuth 配置
    @Value("${oauth.gitlab.client-id:}")
    private String gitlabClientId;

    @Value("${oauth.gitlab.client-secret:}")
    private String gitlabClientSecret;

    @Value("${oauth.gitlab.url:https://gitlab.com}")
    private String gitlabUrl;

    /**
     * 获取 GitHub 授权 URL
     */
    public String getGitHubAuthorizationUrl(String redirectUri, String state) {
        return String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=read:user%%20user:email&state=%s",
                githubClientId, redirectUri, state
        );
    }

    /**
     * 获取 GitLab 授权 URL
     */
    public String getGitLabAuthorizationUrl(String redirectUri, String state) {
        return String.format(
                "%s/oauth/authorize?client_id=%s&redirect_uri=%s&scope=read_user&response_type=code&state=%s",
                gitlabUrl, gitlabClientId, redirectUri, state
        );
    }

    /**
     * 处理 GitHub OAuth 回调
     */
    @Transactional
    public TokenResponse handleGitHubCallback(String code) {
        // 1. 用 code 换取 access token
        GitHubTokenResponse tokenResp = exchangeGitHubToken(code);
        if (tokenResp == null || tokenResp.getAccessToken() == null) {
            throw new RuntimeException("GitHub OAuth 认证失败：无法获取访问令牌");
        }

        // 2. 获取 GitHub 用户信息
        GitHubUser githubUser = fetchGitHubUserInfo(tokenResp.getAccessToken());
        if (githubUser == null) {
            throw new RuntimeException("GitHub OAuth 认证失败：无法获取用户信息");
        }

        // 3. 查找或创建用户
        User user = userRepository.findByProviderAndProviderId(
                AuthProvider.GITHUB, String.valueOf(githubUser.getId())
        ).orElseGet(() -> createGitHubUser(githubUser));

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("GitHub OAuth 登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 生成 JWT Token
        return jwtTokenProvider.generateToken(user);
    }

    /**
     * 处理 GitLab OAuth 回调
     */
    @Transactional
    public TokenResponse handleGitLabCallback(String code) {
        // 1. 用 code 换取 access token
        GitLabTokenResponse tokenResp = exchangeGitLabToken(code);
        if (tokenResp == null || tokenResp.getAccessToken() == null) {
            throw new RuntimeException("GitLab OAuth 认证失败：无法获取访问令牌");
        }

        // 2. 获取 GitLab 用户信息
        GitLabUser gitlabUser = fetchGitLabUserInfo(tokenResp.getAccessToken());
        if (gitlabUser == null) {
            throw new RuntimeException("GitLab OAuth 认证失败：无法获取用户信息");
        }

        // 3. 查找或创建用户
        User user = userRepository.findByProviderAndProviderId(
                AuthProvider.GITLAB, String.valueOf(gitlabUser.getId())
        ).orElseGet(() -> createGitLabUser(gitlabUser));

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("GitLab OAuth 登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 生成 JWT Token
        return jwtTokenProvider.generateToken(user);
    }

    /**
     * 用 GitHub authorization code 换取 access token
     */
    private GitHubTokenResponse exchangeGitHubToken(String code) {
        String url = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", githubClientId);
        body.add("client_secret", githubClientSecret);
        body.add("code", code);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return objectMapper.readValue(response.getBody(), GitHubTokenResponse.class);
        } catch (Exception e) {
            log.error("GitHub Token 交换失败", e);
            return null;
        }
    }

    /**
     * 获取 GitHub 用户信息
     */
    private GitHubUser fetchGitHubUserInfo(String accessToken) {
        String url = "https://api.github.com/user";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            return objectMapper.readValue(response.getBody(), GitHubUser.class);
        } catch (Exception e) {
            log.error("获取 GitHub 用户信息失败", e);
            return null;
        }
    }

    /**
     * 创建 GitHub 用户
     */
    private User createGitHubUser(GitHubUser githubUser) {
        // 检查用户名是否已存在
        String username = githubUser.getLogin();
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + System.currentTimeMillis();
        }

        User user = User.create(
                UUID.randomUUID().toString(),
                username,
                githubUser.getEmail(),
                githubUser.getName(),
                githubUser.getAvatarUrl(),
                AuthProvider.GITHUB,
                String.valueOf(githubUser.getId())
        );

        return userRepository.save(user);
    }

    /**
     * 用 GitLab authorization code 换取 access token
     */
    private GitLabTokenResponse exchangeGitLabToken(String code) {
        String url = gitlabUrl + "/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", gitlabClientId);
        body.add("client_secret", gitlabClientSecret);
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        body.add("redirect_uri", gitlabUrl + "/callback");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return objectMapper.readValue(response.getBody(), GitLabTokenResponse.class);
        } catch (Exception e) {
            log.error("GitLab Token 交换失败", e);
            return null;
        }
    }

    /**
     * 获取 GitLab 用户信息
     */
    private GitLabUser fetchGitLabUserInfo(String accessToken) {
        String url = gitlabUrl + "/api/v4/user";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
            return objectMapper.readValue(response.getBody(), GitLabUser.class);
        } catch (Exception e) {
            log.error("获取 GitLab 用户信息失败", e);
            return null;
        }
    }

    /**
     * 创建 GitLab 用户
     */
    private User createGitLabUser(GitLabUser gitlabUser) {
        // 检查用户名是否已存在
        String username = gitlabUser.getUsername();
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + System.currentTimeMillis();
        }

        User user = User.create(
                UUID.randomUUID().toString(),
                username,
                gitlabUser.getEmail(),
                gitlabUser.getName(),
                gitlabUser.getAvatarUrl(),
                AuthProvider.GITLAB,
                String.valueOf(gitlabUser.getId())
        );

        return userRepository.save(user);
    }

    // ========== 内部类：OAuth 响应 DTO ==========

    @lombok.Data
    private static class GitHubTokenResponse {
        private String accessToken;
        private String tokenType;
        private String scope;
    }

    @lombok.Data
    private static class GitHubUser {
        private long id;
        private String login;
        private String name;
        private String email;
        private String avatarUrl;
    }

    @lombok.Data
    private static class GitLabTokenResponse {
        private String accessToken;
        private String tokenType;
        private String scope;
    }

    @lombok.Data
    private static class GitLabUser {
        private long id;
        private String username;
        private String name;
        private String email;
        private String avatarUrl;
    }
}
