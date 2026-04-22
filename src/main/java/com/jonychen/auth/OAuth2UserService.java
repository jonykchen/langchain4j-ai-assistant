package com.jonychen.auth;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** OAuth 用户服务 处理 GitHub、GitLab 等第三方登录 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2UserService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private RestTemplate restTemplate;
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

    // HTTP 代理配置
    @Value("${oauth.proxy.host:}")
    private String proxyHost;

    @Value("${oauth.proxy.port:0}")
    private int proxyPort;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);
        factory.setReadTimeout(30000);

        // 配置代理
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            factory.setProxy(proxy);
            log.info("OAuth HTTP 代理已配置: {}:{}", proxyHost, proxyPort);
        }

        restTemplate = new RestTemplate(factory);
    }

    /** 带重试的 API 调用 */
    private <T> T retryableCall(String operation, java.util.concurrent.Callable<T> action) {
        int maxRetries = 3;
        Exception lastException = null;

        for (int i = 0; i < maxRetries; i++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastException = e;
                log.warn("{} 失败 (尝试 {}/{}): {}", operation, i + 1, maxRetries, e.getMessage());
                if (i < maxRetries - 1) {
                    try {
                        Thread.sleep(1000 * (i + 1)); // 递增等待
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("{} 最终失败", operation, lastException);
        return null;
    }

    /** 获取 GitHub 授权 URL */
    public String getGitHubAuthorizationUrl(String redirectUri, String state) {
        return String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&scope=read:user%%20user:email&state=%s",
                githubClientId, redirectUri, state);
    }

    /** 获取 GitLab 授权 URL */
    public String getGitLabAuthorizationUrl(String redirectUri, String state) {
        return String.format(
                "%s/oauth/authorize?client_id=%s&redirect_uri=%s&scope=read_user&response_type=code&state=%s",
                gitlabUrl, gitlabClientId, redirectUri, state);
    }

    /** 处理 GitHub OAuth 回调 */
    @Transactional
    public TokenResponse handleGitHubCallback(String code, String redirectUri) {
        // 1. 用 code 换取 access token
        GitHubTokenResponse tokenResp = exchangeGitHubToken(code, redirectUri);
        if (tokenResp == null || tokenResp.getAccessToken() == null) {
            throw new RuntimeException("GitHub OAuth 认证失败：无法获取访问令牌");
        }

        // 2. 获取 GitHub 用户信息
        GitHubUser githubUser = fetchGitHubUserInfo(tokenResp.getAccessToken());
        if (githubUser == null) {
            throw new RuntimeException("GitHub OAuth 认证失败：无法获取用户信息");
        }

        // 3. 查找或创建用户
        User user =
                userRepository
                        .findByProviderAndProviderId(
                                AuthProvider.GITHUB, String.valueOf(githubUser.getId()))
                        .orElseGet(() -> createGitHubUser(githubUser));

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("GitHub OAuth 登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 生成 JWT Token
        return jwtTokenProvider.generateToken(user);
    }

    /** 处理 GitLab OAuth 回调 */
    @Transactional
    public TokenResponse handleGitLabCallback(String code, String redirectUri) {
        // 1. 用 code 换取 access token
        GitLabTokenResponse tokenResp = exchangeGitLabToken(code, redirectUri);
        if (tokenResp == null || tokenResp.getAccessToken() == null) {
            throw new RuntimeException("GitLab OAuth 认证失败：无法获取访问令牌");
        }

        // 2. 获取 GitLab 用户信息
        GitLabUser gitlabUser = fetchGitLabUserInfo(tokenResp.getAccessToken());
        if (gitlabUser == null) {
            throw new RuntimeException("GitLab OAuth 认证失败：无法获取用户信息");
        }

        // 3. 查找或创建用户
        User user =
                userRepository
                        .findByProviderAndProviderId(
                                AuthProvider.GITLAB, String.valueOf(gitlabUser.getId()))
                        .orElseGet(() -> createGitLabUser(gitlabUser));

        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("GitLab OAuth 登录成功: userId={}, username={}", user.getId(), user.getUsername());

        // 5. 生成 JWT Token
        return jwtTokenProvider.generateToken(user);
    }

    /** 用 GitHub authorization code 换取 access token */
    private GitHubTokenResponse exchangeGitHubToken(String code, String redirectUri) {
        return retryableCall(
                "GitHub Token 交换",
                () -> {
                    String url = "https://github.com/login/oauth/access_token";

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

                    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                    body.add("client_id", githubClientId);
                    body.add("client_secret", githubClientSecret);
                    body.add("code", code);
                    if (redirectUri != null && !redirectUri.isBlank()) {
                        body.add("redirect_uri", redirectUri);
                    }

                    log.debug("GitHub Token 交换请求: url={}, redirectUri={}", url, redirectUri);

                    HttpEntity<MultiValueMap<String, String>> request =
                            new HttpEntity<>(body, headers);

                    ResponseEntity<String> response =
                            restTemplate.postForEntity(url, request, String.class);
                    log.debug("GitHub Token 交换响应: status={}", response.getStatusCode());

                    GitHubTokenResponse tokenResponse =
                            objectMapper.readValue(response.getBody(), GitHubTokenResponse.class);

                    // 检查是否有错误
                    if (tokenResponse.getError() != null) {
                        log.error(
                                "GitHub 返回错误: error={}, description={}",
                                tokenResponse.getError(),
                                tokenResponse.getErrorDescription());
                        return null;
                    }

                    return tokenResponse;
                });
    }

    /** 获取 GitHub 用户信息 */
    private GitHubUser fetchGitHubUserInfo(String accessToken) {
        return retryableCall(
                "获取 GitHub 用户信息",
                () -> {
                    String url = "https://api.github.com/user";

                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(accessToken);
                    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

                    HttpEntity<Void> request = new HttpEntity<>(headers);

                    ResponseEntity<String> response =
                            restTemplate.exchange(url, HttpMethod.GET, request, String.class);
                    return objectMapper.readValue(response.getBody(), GitHubUser.class);
                });
    }

    /** 创建 GitHub 用户 */
    private User createGitHubUser(GitHubUser githubUser) {
        // 检查用户名是否已存在
        String username = githubUser.getLogin();
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + System.currentTimeMillis();
        }

        // 第一个用户自动成为管理员
        boolean isFirstUser = userRepository.count() == 0;

        User user =
                User.create(
                        UUID.randomUUID().toString(),
                        username,
                        githubUser.getEmail(),
                        githubUser.getName(),
                        githubUser.getAvatarUrl(),
                        AuthProvider.GITHUB,
                        String.valueOf(githubUser.getId()));

        if (isFirstUser) {
            user.setRole(UserRole.ADMIN);
            log.info("第一个注册用户 {} 自动成为管理员", username);
        }

        return userRepository.save(user);
    }

    /** 用 GitLab authorization code 换取 access token */
    private GitLabTokenResponse exchangeGitLabToken(String code, String redirectUri) {
        String url = gitlabUrl + "/oauth/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", gitlabClientId);
        body.add("client_secret", gitlabClientSecret);
        body.add("code", code);
        body.add("grant_type", "authorization_code");
        // 使用传入的 redirectUri，如果没有则使用默认值
        String actualRedirectUri =
                (redirectUri != null && !redirectUri.isBlank())
                        ? redirectUri
                        : gitlabUrl + "/callback";
        body.add("redirect_uri", actualRedirectUri);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, request, String.class);
            return objectMapper.readValue(response.getBody(), GitLabTokenResponse.class);
        } catch (Exception e) {
            log.error("GitLab Token 交换失败", e);
            return null;
        }
    }

    /** 获取 GitLab 用户信息 */
    private GitLabUser fetchGitLabUserInfo(String accessToken) {
        return retryableCall(
                "获取 GitLab 用户信息",
                () -> {
                    String url = gitlabUrl + "/api/v4/user";

                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(accessToken);

                    HttpEntity<Void> request = new HttpEntity<>(headers);

                    ResponseEntity<String> response =
                            restTemplate.exchange(url, HttpMethod.GET, request, String.class);
                    return objectMapper.readValue(response.getBody(), GitLabUser.class);
                });
    }

    /** 创建 GitLab 用户 */
    private User createGitLabUser(GitLabUser gitlabUser) {
        // 检查用户名是否已存在
        String username = gitlabUser.getUsername();
        if (userRepository.existsByUsername(username)) {
            username = username + "_" + System.currentTimeMillis();
        }

        // 第一个用户自动成为管理员
        boolean isFirstUser = userRepository.count() == 0;

        User user =
                User.create(
                        UUID.randomUUID().toString(),
                        username,
                        gitlabUser.getEmail(),
                        gitlabUser.getName(),
                        gitlabUser.getAvatarUrl(),
                        AuthProvider.GITLAB,
                        String.valueOf(gitlabUser.getId()));

        if (isFirstUser) {
            user.setRole(UserRole.ADMIN);
            log.info("第一个注册用户 {} 自动成为管理员", username);
        }

        return userRepository.save(user);
    }

    // ========== 内部类：OAuth 响应 DTO ==========

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class GitHubTokenResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        private String accessToken;

        @com.fasterxml.jackson.annotation.JsonProperty("token_type")
        private String tokenType;

        private String scope;
        private String error;

        @com.fasterxml.jackson.annotation.JsonProperty("error_description")
        private String errorDescription;
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class GitHubUser {
        private long id;
        private String login;
        private String name;
        private String email;

        @com.fasterxml.jackson.annotation.JsonProperty("avatar_url")
        private String avatarUrl;
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class GitLabTokenResponse {
        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        private String accessToken;

        @com.fasterxml.jackson.annotation.JsonProperty("token_type")
        private String tokenType;

        private String scope;
        private String error;

        @com.fasterxml.jackson.annotation.JsonProperty("error_description")
        private String errorDescription;
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    private static class GitLabUser {
        private long id;
        private String username;
        private String name;
        private String email;

        @com.fasterxml.jackson.annotation.JsonProperty("avatar_url")
        private String avatarUrl;
    }
}
