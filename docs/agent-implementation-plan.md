# Agent 组件完整实现方案

> 版本：1.3  
> 日期：2026-04-17  
> 目标：构建工程级 AI Agent 项目，补充所有必要组件

---

## 目录

1. [用户认证系统](#1-用户认证系统)
2. [记忆系统](#2-记忆系统)
3. [工具系统](#3-工具系统)
4. [任务规划](#4-任务规划)
5. [RAG 知识库](#5-rag-知识库)
6. [多 Agent 协作](#6-多-agent-协作)
7. [Prompt 工程](#7-prompt-工程)
8. [结构化输出](#8-结构化输出)
9. [成本控制](#9-成本控制)
10. [工程化增强](#10-工程化增强)

---

## 1. 用户认证系统

> **决策确认**：采用 OAuth 2.0 + 第三方登录方案

### 1.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    安全网关 (Spring Security)                │
│  - 请求过滤                                                  │
│  - Token 验证                                               │
│  - 权限校验                                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    AuthController                            │
│  - /auth/login (第三方跳转)                                  │
│  - /auth/callback (OAuth 回调)                               │
│  - /auth/token (获取 Token)                                  │
│  - /auth/logout (登出)                                       │
│  - /auth/refresh (刷新 Token)                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    OAuth2UserService                          │
│  - 处理第三方登录                                            │
│  - 用户信息同步                                              │
│  - Token 生成与管理                                          │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  GitHub OAuth   │ │  GitLab OAuth   │ │  企业 SSO       │
│  (开发者推荐)   │ │  (企业推荐)     │ │  (可选扩展)     │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 1.2 核心接口设计

```java
// ===== 用户实体 =====
@Entity
@Table(name = "users")
public class User {
    @Id
    private String id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(unique = true)
    private String email;
    
    private String nickname;
    private String avatar;
    
    @Enumerated(EnumType.STRING)
    private AuthProvider provider;  // GITHUB, GITLAB, CUSTOM
    
    private String providerId;       // 第三方平台用户ID
    
    @Enumerated(EnumType.STRING)
    private UserRole role;           // USER, ADMIN
    
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    
    @Version
    private Long version;
}

// ===== 认证提供商 =====
public enum AuthProvider {
    GITHUB,
    GITLAB,
    CUSTOM
}

// ===== 用户角色 =====
public enum UserRole {
    USER,
    ADMIN
}

// ===== Token 响应 =====
public record TokenResponse(
    String accessToken,
    String refreshToken,
    String tokenType,        // "Bearer"
    long expiresIn,          // 过期时间（秒）
    String scope              // 权限范围
) {}

// ===== 用户信息 VO =====
public record UserInfoVO(
    String id,
    String username,
    String email,
    String nickname,
    String avatar,
    String role,
    LocalDateTime createdAt
) {}

// ===== 认证服务接口 =====
public interface AuthService {
    /**
     * 获取第三方登录授权 URL
     */
    String getAuthorizationUrl(AuthProvider provider, String redirectUri);
    
    /**
     * 处理 OAuth 回调，获取用户信息并创建/更新用户
     */
    TokenResponse handleCallback(AuthProvider provider, String code, String state);
    
    /**
     * 刷新 Token
     */
    TokenResponse refreshToken(String refreshToken);
    
    /**
     * 验证 Token
     */
    UserInfoVO validateToken(String token);
    
    /**
     * 登出
     */
    void logout(String userId);
    
    /**
     * 获取当前登录用户
     */
    User getCurrentUser();
}

// ===== OAuth2 用户服务 =====
@Service
public class OAuth2UserService {
    
    private final UserRepository userRepository;
    private final JwtTokenProvider tokenProvider;
    
    // GitHub OAuth 配置
    @Value("${oauth.github.client-id}")
    private String githubClientId;
    
    @Value("${oauth.github.client-secret}")
    private String githubClientSecret;
    
    // GitLab OAuth 配置
    @Value("${oauth.gitlab.client-id}")
    private String gitlabClientId;
    
    @Value("${oauth.gitlab.client-secret}")
    private String gitlabClientSecret;
    
    /**
     * 处理 GitHub 登录回调
     */
    public TokenResponse handleGitHubCallback(String code) {
        // 1. 用 code 换取 access token
        GitHubTokenResponse tokenResp = exchangeGitHubToken(code);
        
        // 2. 获取 GitHub 用户信息
        GitHubUser githubUser = fetchGitHubUserInfo(tokenResp.getAccessToken());
        
        // 3. 查找或创建用户
        User user = userRepository.findByProviderAndProviderId(
            AuthProvider.GITHUB, githubUser.getId().toString()
        ).orElseGet(() -> createGitHubUser(githubUser));
        
        // 4. 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        // 5. 生成 JWT Token
        return tokenProvider.generateToken(user);
    }
    
    /**
     * 创建新用户（从 GitHub 信息）
     */
    private User createGitHubUser(GitHubUser githubUser) {
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(githubUser.getLogin());
        user.setEmail(githubUser.getEmail());
        user.setNickname(githubUser.getName());
        user.setAvatar(githubUser.getAvatarUrl());
        user.setProvider(AuthProvider.GITHUB);
        user.setProviderId(githubUser.getId().toString());
        user.setRole(UserRole.USER);
        user.setCreatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}

// ===== JWT Token 提供者 =====
@Component
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.access-token-expiration:3600}")  // 默认 1 小时
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800}")  // 默认 7 天
    private long refreshTokenExpiration;
    
    private final SecretKey secretKey;
    
    public TokenResponse generateToken(User user) {
        long now = System.currentTimeMillis();
        
        // Access Token
        String accessToken = Jwts.builder()
            .subject(user.getId())
            .claim("username", user.getUsername())
            .claim("role", user.getRole().name())
            .issuedAt(new Date(now))
            .expiration(new Date(now + accessTokenExpiration * 1000))
            .signWith(secretKey)
            .compact();
        
        // Refresh Token
        String refreshToken = Jwts.builder()
            .subject(user.getId())
            .claim("type", "refresh")
            .issuedAt(new Date(now))
            .expiration(new Date(now + refreshTokenExpiration * 1000))
            .signWith(secretKey)
            .compact();
        
        return new TokenResponse(
            accessToken,
            refreshToken,
            "Bearer",
            accessTokenExpiration,
            "read write"
        );
    }
    
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
    
    public String getUserIdFromToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }
}
```

### 1.3 安全配置

```java
// ===== Spring Security 配置 =====
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtFilter;
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 公开接口
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // 需要认证
                .requestMatchers("/api/**").authenticated()
                // 管理员接口
                .requestMatchers("/admin/**").hasRole("ADMIN")
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// ===== JWT 认证过滤器 =====
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider tokenProvider;
    
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, 
            HttpServletResponse response, 
            FilterChain filterChain) throws ServletException, IOException {
        
        String token = resolveToken(request);
        
        if (token != null && tokenProvider.validateToken(token)) {
            String userId = tokenProvider.getUserIdFromToken(token);
            
            // 设置认证信息
            UsernamePasswordAuthenticationToken auth = 
                new UsernamePasswordAuthenticationToken(userId, null, 
                    List.of(new SimpleGrantedAuthority("ROLE_USER")));
            
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

### 1.4 数据库设计

```sql
-- 用户表
CREATE TABLE users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    provider VARCHAR(20) NOT NULL,      -- GITHUB, GITLAB, CUSTOM
    provider_id VARCHAR(100),            -- 第三方用户ID
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    version BIGINT DEFAULT 0
);

CREATE INDEX idx_users_provider ON users(provider, provider_id);
CREATE INDEX idx_users_username ON users(username);

-- Token 黑名单（用于登出）
CREATE TABLE token_blacklist (
    id SERIAL PRIMARY KEY,
    token_hash VARCHAR(128) NOT NULL,    -- Token 哈希
    user_id VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_blacklist_user FOREIGN KEY (user_id) 
        REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_blacklist_token ON token_blacklist(token_hash);
CREATE INDEX idx_blacklist_expires ON token_blacklist(expires_at);
```

### 1.5 API 接口

```
# 认证相关（公开）
GET  /auth/github          # 跳转 GitHub 登录
GET  /auth/github/callback # GitHub 回调
GET  /auth/gitlab          # 跳转 GitLab 登录
GET  /auth/gitlab/callback # GitLab 回调
POST /auth/token           # 用 code 换 token
POST /auth/refresh         # 刷新 token
POST /auth/logout          # 登出

# 用户相关（需认证）
GET  /api/users/me         # 获取当前用户信息
PUT  /api/users/me         # 更新用户信息
```

### 1.6 配置示例

```properties
# application.properties

# ===== OAuth 配置 =====
# GitHub
oauth.github.client-id=${GITHUB_CLIENT_ID}
oauth.github.client-secret=${GITHUB_CLIENT_SECRET}
oauth.github.redirect-uri=http://localhost:8082/auth/github/callback

# GitLab
oauth.gitlab.client-id=${GITLAB_CLIENT_ID}
oauth.gitlab.client-secret=${GITLAB_CLIENT_SECRET}
oauth.gitlab.redirect-uri=http://localhost:8082/auth/gitlab/callback

# ===== JWT 配置 =====
jwt.secret=${JWT_SECRET:your-256-bit-secret-key-here}
jwt.access-token-expiration=3600     # 1 小时
jwt.refresh-token-expiration=604800  # 7 天
```

### 1.7 实现文件清单

| 文件 | 说明 |
|------|------|
| `auth/User.java` | 用户实体 |
| `auth/AuthProvider.java` | 认证提供商枚举 |
| `auth/UserRole.java` | 用户角色枚举 |
| `auth/AuthController.java` | 认证 REST 控制器 |
| `auth/AuthService.java` | 认证服务接口 |
| `auth/OAuth2UserService.java` | OAuth 用户服务 |
| `auth/JwtTokenProvider.java` | JWT Token 提供者 |
| `auth/JwtAuthenticationFilter.java` | JWT 认证过滤器 |
| `auth/TokenResponse.java` | Token 响应 record |
| `auth/UserInfoVO.java` | 用户信息 VO |
| `config/SecurityConfig.java` | Spring Security 配置 |
| `controller/UserController.java` | 用户 REST 控制器 |

### 1.8 依赖添加

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>

<!-- OAuth2 客户端 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

---

## 2. 记忆系统

### 1.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      ChatAssistant                          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    SessionManager                            │
│  - createSession(userId) -> sessionId                        │
│  - getSession(sessionId) -> ChatSession                      │
│  - deleteSession(sessionId)                                  │
│  - listSessions(userId) -> List<SessionInfo>                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ChatSession                               │
│  - sessionId: String                                         │
│  - userId: String                                            │
│  - chatMemory: ChatMemory (短期记忆)                         │
│  - longTermMemory: LongTermMemory (长期记忆)                 │
│  - metadata: Map<String, Object>                             │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
┌─────────────────────────┐     ┌─────────────────────────┐
│   ShortTermMemory       │     │   LongTermMemory        │
│   (会话级，内存+Redis)   │     │   (持久化，向量检索)    │
└─────────────────────────┘     └─────────────────────────┘
```

### 1.2 技术选型

| 组件 | 技术方案 | 说明 |
|------|----------|------|
| 短期记忆 | Redis + MessageWindowChatMemory | 会话消息缓存，支持分布式 |
| 长期记忆 | PostgreSQL + pgvector | 向量存储，语义检索 |
| 记忆压缩 | LLM 摘要 | 定期压缩历史消息 |
| 会话存储 | Redis | 支持过期时间，分布式共享 |

### 1.3 核心接口设计

```java
// ===== 会话管理 =====
public interface SessionManager {
    /**
     * 创建新会话
     */
    ChatSession createSession(String userId, String title);
    
    /**
     * 获取会话（不存在则创建）
     */
    ChatSession getOrCreateSession(String sessionId, String userId);
    
    /**
     * 获取用户所有会话
     */
    List<SessionInfo> listSessions(String userId);
    
    /**
     * 删除会话
     */
    void deleteSession(String sessionId);
    
    /**
     * 更新会话标题
     */
    void updateSessionTitle(String sessionId, String title);
}

// ===== 会话实体 =====
public record SessionInfo(
    String sessionId,
    String title,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int messageCount
) {}

// ===== 会话对象 =====
public class ChatSession {
    private String sessionId;
    private String userId;
    private String title;
    private ChatMemory chatMemory;           // 短期记忆
    private LongTermMemory longTermMemory;   // 长期记忆
    private Map<String, Object> metadata;
    
    // 添加消息
    public void addMessage(ChatMessage message);
    
    // 获取历史消息
    public List<ChatMessage> getHistory();
    
    // 检索相关记忆
    public List<MemoryRecord> retrieveRelevantMemories(String query, int topK);
    
    // 保存重要记忆到长期存储
    public void saveToLongTermMemory(String content, Map<String, Object> metadata);
}

// ===== 长期记忆接口 =====
public interface LongTermMemory {
    /**
     * 添加记忆
     */
    void addMemory(String sessionId, String content, Map<String, Object> metadata);
    
    /**
     * 语义检索相关记忆
     */
    List<MemoryRecord> search(String query, String sessionId, int topK);
    
    /**
     * 删除会话相关记忆
     */
    void deleteBySession(String sessionId);
}

// ===== 记忆记录 =====
public record MemoryRecord(
    String id,
    String sessionId,
    String content,
    float[] embedding,
    int valueScore,             // 记忆价值分数（0-100）
    Map<String, Object> metadata,
    LocalDateTime createdAt,
    LocalDateTime lastAccessed   // 最后访问时间（用于遗忘策略）
) {
    // 便捷构造方法
    public static MemoryRecord of(String id, String sessionId, String content) {
        return new MemoryRecord(id, sessionId, content, null, 50, Map.of(), LocalDateTime.now(), LocalDateTime.now());
    }
}
```

### 1.4 数据库设计

```sql
-- 会话表
CREATE TABLE chat_sessions (
    session_id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sessions_user ON chat_sessions(user_id);

-- 长期记忆表（向量存储）
CREATE TABLE long_term_memories (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),  -- OpenAI embedding 维度
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_session FOREIGN KEY (session_id) 
        REFERENCES chat_sessions(session_id) ON DELETE CASCADE
);

-- 向量索引（pgvector）
CREATE INDEX idx_memories_embedding ON long_term_memories 
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX idx_memories_session ON long_term_memories(session_id);

-- 消息快照表（用于记忆压缩）
CREATE TABLE memory_snapshots (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    summary TEXT NOT NULL,
    message_range_start INT,
    message_range_end INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 1.5 Redis 数据结构

```
# 会话消息列表（短期记忆）
session:{sessionId}:messages -> List<ChatMessage> (JSON)
TTL: 24h（可配置）

# 会话元数据
session:{sessionId}:meta -> Hash {userId, title, createdAt}
TTL: 7d

# 用户会话列表
user:{userId}:sessions -> Set<sessionId>
TTL: 30d
```

### 1.6 记忆治理策略（避免越用越乱）

> **核心原则**：记忆系统必须"只存高价值信息，定期清理低价值数据"，否则会随着使用变得臃肿、混乱、检索效率低下。

#### 1.6.1 分层记忆架构

```
┌─────────────────────────────────────────────────────────────┐
│                    记忆分层模型                              │
├─────────────────────────────────────────────────────────────┤
│  Layer 1: 工作记忆 (Working Memory)                         │
│  - 存储：当前对话上下文（最近 N 条消息）                     │
│  - 介质：内存 + Redis                                       │
│  - 生命周期：会话期间，自动过期                              │
│  - 容量：窗口大小 10-20 条                                  │
├─────────────────────────────────────────────────────────────┤
│  Layer 2: 会话记忆 (Session Memory)                         │
│  - 存储：完整会话历史                                       │
│  - 介质：Redis                                              │
│  - 生命周期：7-30 天，按访问时间续期                        │
│  - 容量：每会话最多 1000 条消息                             │
├─────────────────────────────────────────────────────────────┤
│  Layer 3: 长期记忆 (Long-term Memory)                       │
│  - 存储：提取的高价值事实、用户偏好、知识                   │
│  - 介质：向量数据库 + 结构化数据库                          │
│  - 生命周期：永久，但有 TTL 和清理策略                       │
│  - 容量：按质量而非数量控制                                 │
└─────────────────────────────────────────────────────────────┘
```

#### 1.6.2 写入策略：只写入高价值信息

```java
// ===== 记忆价值评估器 =====
@Component
public class MemoryValueEvaluator {
    
    // 高价值信息类型
    private static final Set<String> HIGH_VALUE_TYPES = Set.of(
        "user_preference",      // 用户偏好
        "important_fact",       // 重要事实
        "decision",             // 决策记录
        "feedback",             // 用户反馈
        "summary",              // 会话摘要
        "entity_profile"        // 实体画像
    );
    
    // 低价值信息模式（不写入长期记忆）
    private static final List<Pattern> LOW_VALUE_PATTERNS = List.of(
        Pattern.compile("^(好的|嗯|可以|没问题|谢谢|好的)$"),  // 简短回复
        Pattern.compile("^(请问|查询|搜索).*$"),              // 临时查询
        Pattern.compile("^\\d+$"),                            // 纯数字
        Pattern.compile("^https?://.*$")                      // 纯 URL
    );
    
    /**
     * 评估记忆价值分数（0-100）
     */
    public int evaluateValue(String content, ChatMessage message) {
        int score = 0;
        
        // 1. 检查是否匹配低价值模式
        for (Pattern pattern : LOW_VALUE_PATTERNS) {
            if (pattern.matcher(content.trim()).matches()) {
                return 0;  // 直接丢弃
            }
        }
        
        // 2. 内容长度评分（太短通常价值低）
        if (content.length() < 10) score -= 30;
        else if (content.length() > 50) score += 10;
        
        // 3. 信息密度评分
        score += calculateInfoDensity(content) * 20;
        
        // 4. 是否包含用户偏好关键词
        if (containsPreferenceKeywords(content)) score += 30;
        
        // 5. 是否是重要决策
        if (containsDecisionKeywords(content)) score += 40;
        
        // 6. 用户明确标记（如"记住这个"）
        if (containsExplicitMarker(content)) score += 50;
        
        return Math.max(0, Math.min(100, score));
    }
    
    /**
     * 判断是否应该写入长期记忆
     */
    public boolean shouldPersistToLongTerm(String content, int valueScore) {
        return valueScore >= 40;  // 阈值可配置
    }
}

// ===== 智能记忆写入器 =====
@Service
public class SmartMemoryWriter {
    
    private final MemoryValueEvaluator evaluator;
    private final LongTermMemory longTermMemory;
    private final MemoryDeduplicator deduplicator;
    
    /**
     * 智能写入记忆
     */
    public void writeMemory(String sessionId, String content, 
                            Map<String, Object> metadata) {
        // 1. 评估价值
        int valueScore = evaluator.evaluateValue(content, null);
        
        // 2. 低价值直接跳过
        if (valueScore < 40) {
            log.debug("跳过低价值记忆: score={}, content={}", 
                valueScore, truncate(content, 50));
            return;
        }
        
        // 3. 去重检查
        if (deduplicator.isDuplicate(sessionId, content)) {
            log.debug("跳过重复记忆: {}", truncate(content, 50));
            return;
        }
        
        // 4. 冲突检测
        Optional<MemoryRecord> conflict = deduplicator.findConflict(sessionId, content);
        if (conflict.isPresent()) {
            // 更新而非新增
            longTermMemory.updateMemory(conflict.get().id(), content, metadata);
            return;
        }
        
        // 5. 写入长期记忆
        MemoryRecord record = new MemoryRecord(
            generateId(),
            sessionId,
            content,
            null,  // embedding 稍后生成
            enhanceMetadata(metadata, valueScore),
            LocalDateTime.now()
        );
        longTermMemory.addMemory(record);
    }
}
```

#### 1.6.3 遗忘策略：定期清理与压缩

```java
// ===== 记忘策略配置 =====
@ConfigurationProperties(prefix = "app.memory.forget")
public class MemoryForgetProperties {
    
    // TTL 配置
    private Duration defaultTtl = Duration.ofDays(90);      // 默认 90 天
    private Duration lowValueTtl = Duration.ofDays(30);     // 低价值 30 天
    private Duration highValueTtl = Duration.ofDays(365);   // 高价值 1 年
    
    // 压缩配置
    private int compressionThreshold = 50;   // 超过 50 条触发压缩
    private int retentionCount = 20;          // 压缩后保留 20 条摘要
    
    // 清理配置
    private int cleanupBatchSize = 100;       // 每次清理 100 条
    private double accessDecayFactor = 0.9;   // 访问衰减因子
}

// ===== 记忘管理器 =====
@Service
public class MemoryForgetManager {
    
    private final LongTermMemory longTermMemory;
    private final MemoryCompressor compressor;
    private final MemoryForgetProperties properties;
    
    /**
     * 执行遗忘策略（定时任务，每天凌晨执行）
     */
    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
    public void executeForgetPolicy() {
        log.info("开始执行记忆遗忘策略...");
        
        // 1. TTL 过期清理
        int expiredCount = cleanExpiredMemories();
        log.info("清理过期记忆: {} 条", expiredCount);
        
        // 2. 低访问频率清理
        int lowAccessCount = cleanLowAccessMemories();
        log.info("清理低访问记忆: {} 条", lowAccessCount);
        
        // 3. 去重清理
        int duplicateCount = cleanDuplicateMemories();
        log.info("清理重复记忆: {} 条", duplicateCount);
        
        // 4. 会话记忆压缩
        int compressedCount = compressOldSessions();
        log.info("压缩会话记忆: {} 条", compressedCount);
    }
    
    /**
     * 清理过期记忆
     */
    private int cleanExpiredMemories() {
        return longTermMemory.deleteExpired();
    }
    
    /**
     * 清理低访问频率记忆（超过 30 天未访问且价值分低于阈值）
     */
    private int cleanLowAccessMemories() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        return longTermMemory.deleteByCondition(
            "last_accessed < ? AND value_score < 50",
            threshold
        );
    }
    
    /**
     * 清理重复记忆
     */
    private int cleanDuplicateMemories() {
        return longTermMemory.deleteDuplicates();
    }
    
    /**
     * 压缩旧会话记忆
     */
    private int compressOldSessions() {
        // 找出消息过多的会话
        List<String> sessionsToCompress = longTermMemory
            .findSessionsWithMemoryCountGreaterThan(properties.getCompressionThreshold());
        
        int totalCompressed = 0;
        for (String sessionId : sessionsToCompress) {
            // 压缩旧记忆为摘要
            int compressed = compressor.compressSession(sessionId, 
                properties.getRetentionCount());
            totalCompressed += compressed;
        }
        return totalCompressed;
    }
}

// ===== 记忆压缩器 =====
@Service
public class MemoryCompressor {
    
    private final ChatModel chatModel;
    private static final String COMPRESSION_PROMPT = """
        请将以下记忆片段压缩为简洁的摘要，保留关键信息。
        
        要求：
        1. 保留用户偏好和重要事实
        2. 去除重复和冗余信息
        3. 使用结构化格式输出
        
        记忆片段：
        {memories}
        
        压缩后的摘要：
        """;
    
    /**
     * 压缩会话记忆
     */
    public int compressSession(String sessionId, int retentionCount) {
        // 1. 获取会话所有记忆
        List<MemoryRecord> memories = longTermMemory.getBySession(sessionId);
        
        if (memories.size() <= retentionCount) {
            return 0;
        }
        
        // 2. 按价值排序，保留高价值的
        List<MemoryRecord> toCompress = memories.stream()
            .sorted(Comparator.comparingInt(MemoryRecord::valueScore).reversed())
            .skip(retentionCount)
            .toList();
        
        if (toCompress.isEmpty()) {
            return 0;
        }
        
        // 3. LLM 生成摘要
        String memoriesText = toCompress.stream()
            .map(MemoryRecord::content)
            .collect(Collectors.joining("\n---\n"));
        
        String summary = chatModel.chat(COMPRESSION_PROMPT
            .replace("{memories}", memoriesText));
        
        // 4. 删除原记忆，保存摘要
        longTermMemory.deleteByIds(toCompress.stream()
            .map(MemoryRecord::id).toList());
        
        longTermMemory.addMemory(new MemoryRecord(
            generateId(),
            sessionId,
            summary,
            null,
            Map.of("type", "compressed_summary", 
                   "original_count", toCompress.size()),
            LocalDateTime.now()
        ));
        
        return toCompress.size();
    }
}
```

#### 1.6.4 去重与冲突检测

```java
// ===== 记忆去重器 =====
@Component
public class MemoryDeduplicator {
    
    private final EmbeddingService embeddingService;
    private final double similarityThreshold = 0.95;  // 相似度阈值
    
    /**
     * 检查是否是重复记忆
     */
    public boolean isDuplicate(String sessionId, String content) {
        float[] embedding = embeddingService.embed(content);
        
        // 在同一会话中搜索相似记忆
        List<MemoryRecord> similar = longTermMemory.search(
            embedding, sessionId, 5);
        
        return similar.stream()
            .anyMatch(m -> calculateSimilarity(embedding, m.embedding()) > similarityThreshold);
    }
    
    /**
     * 查找冲突的记忆（相似但内容矛盾）
     */
    public Optional<MemoryRecord> findConflict(String sessionId, String newContent) {
        // 使用 LLM 判断是否与新信息冲突
        // 例如：用户之前说喜欢苹果，现在说不喜欢苹果
        // ...
        return Optional.empty();
    }
    
    private double calculateSimilarity(float[] a, float[] b) {
        // 余弦相似度计算
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

#### 1.6.5 记忆质量指标监控

```java
// ===== 记忆质量监控 =====
@Service
public class MemoryQualityMonitor {
    
    private final MeterRegistry meterRegistry;
    
    /**
     * 记录记忆统计指标
     */
    @Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨 3 点
    public void recordMetrics() {
        // 总记忆数量
        long totalCount = longTermMemory.count();
        meterRegistry.gauge("memory.total.count", totalCount);
        
        // 按会话平均记忆数量
        double avgPerSession = longTermMemory.averagePerSession();
        meterRegistry.gauge("memory.average.per_session", avgPerSession);
        
        // 高价值记忆占比
        double highValueRatio = longTermMemory.highValueRatio();
        meterRegistry.gauge("memory.high_value.ratio", highValueRatio);
        
        // 重复记忆数量
        long duplicateCount = longTermMemory.duplicateCount();
        meterRegistry.gauge("memory.duplicate.count", duplicateCount);
        
        // 告警：记忆数量过多
        if (totalCount > 100000) {
            log.warn("记忆数量过多: {}, 建议执行清理", totalCount);
        }
        
        // 告警：重复率过高
        if (duplicateCount > totalCount * 0.1) {
            log.warn("重复记忆占比过高: {}%, 建议去重", 
                duplicateCount * 100.0 / totalCount);
        }
    }
}
```

#### 1.6.6 配置示例

```properties
# application.properties

# ===== 记忆分层配置 =====
# 工作记忆窗口大小
app.memory.working.window-size=10

# 会话记忆 TTL
app.memory.session.ttl=7d
app.memory.session.max-messages=1000

# 长期记忆配置
app.memory.long-term.enabled=true
app.memory.long-term.ttl=90d
app.memory.long-term.high-value-ttl=365d

# ===== 写入策略配置 =====
# 最小价值分数阈值
app.memory.write.value-threshold=40

# ===== 遗忘策略配置 =====
app.memory.forget.default-ttl=90d
app.memory.forget.compression-threshold=50
app.memory.forget.retention-count=20
app.memory.forget.cleanup-cron=0 0 2 * * ?

# ===== 去重配置 =====
app.memory.dedup.similarity-threshold=0.95
```

### 1.7 实现文件清单

| 文件 | 说明 |
|------|------|
| `memory/SessionManager.java` | 会话管理器接口 |
| `memory/RedisSessionManager.java` | Redis 实现 |
| `memory/ChatSession.java` | 会话实体类 |
| `memory/SessionInfo.java` | 会话摘要 record |
| `memory/LongTermMemory.java` | 长期记忆接口 |
| `memory/PgVectorLongTermMemory.java` | PostgreSQL 向量实现 |
| `memory/MemoryRecord.java` | 记忆记录实体 |
| `memory/MemoryCompressor.java` | 记忆压缩服务 |
| **治理策略新增：** | |
| `memory/governance/MemoryValueEvaluator.java` | 记忆价值评估器 |
| `memory/governance/SmartMemoryWriter.java` | 智能记忆写入器 |
| `memory/governance/MemoryForgetManager.java` | 遗忘管理器 |
| `memory/governance/MemoryDeduplicator.java` | 记忆去重器 |
| `memory/governance/MemoryQualityMonitor.java` | 记忆质量监控 |
| `memory/governance/MemoryForgetProperties.java` | 遗忘策略配置 |
| `config/MemoryConfig.java` | 记忆系统配置 |
| `controller/SessionController.java` | 会话 REST API |

### 1.7 API 接口

```
POST   /api/sessions              # 创建会话
GET    /api/sessions              # 获取用户会话列表
GET    /api/sessions/{sessionId}  # 获取会话详情
DELETE /api/sessions/{sessionId}  # 删除会话
PATCH  /api/sessions/{sessionId}  # 更新会话标题
```

### 1.8 实现步骤

1. **Step 1**: 创建 `memory` 包和基础接口
2. **Step 2**: 实现 `RedisSessionManager`
3. **Step 3**: 添加 PostgreSQL 依赖和 pgvector 扩展
4. **Step 4**: 实现 `PgVectorLongTermMemory`
5. **Step 5**: 实现 `MemoryCompressor`（LLM 摘要）
6. **Step 6**: 修改 `AiConfig` 集成新的记忆系统
7. **Step 7**: 实现 `SessionController`
8. **Step 8**: 编写单元测试和集成测试
9. **Step 9**: 前端适配（会话列表 UI）

---

## 3. 工具系统

### 3.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    ToolRegistry (工具注册中心)               │
│  - register(ToolDefinition)                                  │
│  - unregister(toolName)                                      │
│  - getTool(toolName) -> ToolDefinition                       │
│  - listTools() -> List<ToolDefinition>                       │
│  - execute(toolName, Map<String, Object> params)             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ToolDefinition                            │
│  - name: String                    # 工具名称                │
│  - description: String            # 功能描述                │
│  - parameters: JsonSchema          # 参数 Schema             │
│  - executor: ToolExecutor          # 执行器                  │
│  - category: ToolCategory          # 分类                    │
│  - permissions: List<String>       # 所需权限                │
│  - timeout: Duration               # 超时时间                │
│  - retryPolicy: RetryPolicy        # 重试策略                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    ToolExecutor                              │
│  - execute(Map<String, Object> params) -> ToolResult         │
│  - validate(Map<String, Object> params) -> ValidationResult  │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心接口设计

```java
// ===== 工具定义 =====
public record ToolDefinition(
    String name,
    String description,
    String category,
    JsonSchema parameters,
    ToolExecutor executor,
    List<String> requiredPermissions,
    Duration timeout,
    RetryPolicy retryPolicy
) {}

// ===== 工具执行器 =====
@FunctionalInterface
public interface ToolExecutor {
    ToolResult execute(Map<String, Object> params);
}

// ===== 工具执行结果 =====
public record ToolResult(
    boolean success,
    Object data,
    String error,
    long executionTimeMs,
    Map<String, Object> metadata
) {
    public static ToolResult success(Object data) {
        return new ToolResult(true, data, null, 0, Map.of());
    }
    
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error, 0, Map.of());
    }
}

// ===== 工具注册中心 =====
public interface ToolRegistry {
    /**
     * 注册工具
     */
    void register(ToolDefinition tool);
    
    /**
     * 通过注解自动注册
     */
    void registerAnnotatedTools(Object toolBean);
    
    /**
     * 注销工具
     */
    void unregister(String toolName);
    
    /**
     * 获取工具定义
     */
    Optional<ToolDefinition> getTool(String toolName);
    
    /**
     * 获取所有工具（转换为 LLM 可用格式）
     */
    List<ToolSpecification> getToolSpecifications();
    
    /**
     * 执行工具
     */
    ToolResult execute(String toolName, Map<String, Object> params);
    
    /**
     * 按分类获取工具
     */
    List<ToolDefinition> getToolsByCategory(String category);
}

// ===== 工具分类枚举 =====
public enum ToolCategory {
    SYSTEM("系统工具"),      // 时间、计算器等
    SEARCH("搜索工具"),      // 网络搜索、文档搜索
    DATABASE("数据库工具"),  // 查询数据库
    FILE("文件工具"),        // 读写文件
    EXTERNAL("外部服务"),    // API 调用
    CUSTOM("自定义工具");    // 用户自定义
    
    private final String displayName;
}
```

### 2.3 注解驱动的工具定义

```java
// ===== 工具方法注解 =====
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {
    String name();                          // 工具名称
    String description();                   // 功能描述
    ToolCategory category() default ToolCategory.CUSTOM;
    String[] requiredPermissions() default {};
    long timeoutMs() default 30000;         // 超时时间
    int maxRetries() default 2;             // 最大重试次数
}

// ===== 参数注解 =====
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    String name();                          // 参数名称
    String description();                   // 参数描述
    boolean required() default true;
    String defaultValue() default "";
    String[] enumValues() default {};       // 枚举值
}

// ===== 示例工具类 =====
@Component
public class DateTimeTools {
    
    @AgentTool(
        name = "get_current_time",
        description = "获取当前日期和时间，支持指定时区",
        category = ToolCategory.SYSTEM
    )
    public ToolResult getCurrentTime(
        @ToolParam(name = "timezone", description = "时区，如 Asia/Shanghai", required = false) 
        String timezone
    ) {
        ZoneId zone = timezone != null ? ZoneId.of(timezone) : ZoneId.systemDefault();
        String time = ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ToolResult.success(Map.of("datetime", time, "timezone", zone.toString()));
    }
    
    @AgentTool(
        name = "calculate",
        description = "执行数学表达式计算",
        category = ToolCategory.SYSTEM
    )
    public ToolResult calculate(
        @ToolParam(name = "expression", description = "数学表达式，如 2+3*4") 
        String expression
    ) {
        try {
            double result = new ExpressionEvaluator().evaluate(expression);
            return ToolResult.success(Map.of("result", result, "expression", expression));
        } catch (Exception e) {
            return ToolResult.failure("表达式计算失败: " + e.getMessage());
        }
    }
}

// ===== 网络搜索工具 =====
@Component
public class SearchTools {
    
    @AgentTool(
        name = "web_search",
        description = "搜索互联网获取信息",
        category = ToolCategory.SEARCH,
        requiredPermissions = {"search:web"},
        timeoutMs = 10000
    )
    public ToolResult webSearch(
        @ToolParam(name = "query", description = "搜索关键词") 
        String query,
        @ToolParam(name = "limit", description = "返回结果数量", required = false) 
        Integer limit
    ) {
        // 调用搜索 API（如 Serper、Bing、Google Custom Search）
        List<SearchResult> results = searchService.search(query, limit != null ? limit : 5);
        return ToolResult.success(results);
    }
}
```

### 2.4 工具执行监控

```java
// ===== 工具执行记录 =====
public record ToolExecutionRecord(
    String executionId,
    String toolName,
    Map<String, Object> params,
    ToolResult result,
    long executionTimeMs,
    LocalDateTime executedAt,
    String sessionId
) {}

// ===== 工具监控服务 =====
public interface ToolMonitor {
    /**
     * 记录工具执行
     */
    void recordExecution(ToolExecutionRecord record);
    
    /**
     * 获取工具执行统计
     */
    ToolStatistics getStatistics(String toolName, Duration period);
    
    /**
     * 获取执行历史
     */
    List<ToolExecutionRecord> getExecutionHistory(String sessionId, int limit);
}

// ===== 工具统计 =====
public record ToolStatistics(
    String toolName,
    long totalCalls,
    long successCalls,
    long failedCalls,
    double avgExecutionTimeMs,
    double successRate,
    LocalDateTime lastCalledAt
) {}
```

### 2.5 内置工具清单

| 工具名称 | 分类 | 功能 | 风险等级 |
|----------|------|------|----------|
| `get_current_time` | SYSTEM | 获取当前时间 | 低 |
| `calculate` | SYSTEM | 数学计算 | 低 |
| `web_search` | SEARCH | 网络搜索 | 中 |
| `document_search` | SEARCH | 文档检索（RAG） | 低 |
| `execute_sql` | DATABASE | 执行 SQL 查询 | 高 |
| `read_file` | FILE | 读取文件内容 | 中 |
| `write_file` | FILE | 写入文件 | 高 |
| `http_request` | EXTERNAL | 发送 HTTP 请求 | 中 |
| `send_email` | EXTERNAL | 发送邮件 | 高 |
| `json_parser` | SYSTEM | JSON 解析与提取 | 低 |

### 2.6 工具安全与稳定性保障

> **核心原则**：工具调用必须"先校验、再执行、可回滚、有审计"，确保系统稳定和数据安全。

#### 2.6.1 参数安全校验

```java
// ===== 工具参数校验器 =====
@Component
public class ToolParameterValidator {
    
    private final JsonSchemaFactory schemaFactory = 
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    
    /**
     * 校验参数是否符合工具定义的 Schema
     */
    public ValidationResult validate(Map<String, Object> params, 
                                     ToolDefinition toolDef) {
        List<String> errors = new ArrayList<>();
        
        // 1. JSON Schema 校验
        String schemaJson = toolDef.parameters().toJsonSchema();
        JsonSchema schema = schemaFactory.getSchema(schemaJson);
        
        Set<ValidationMessage> schemaErrors = schema.validate(
            objectMapper.valueToTree(params),
            InputFormat.JSON
        );
        
        if (!schemaErrors.isEmpty()) {
            errors.addAll(schemaErrors.stream()
                .map(ValidationMessage::getMessage)
                .toList());
        }
        
        // 2. 类型校验（严格类型检查）
        for (SchemaField field : toolDef.parameters().fields()) {
            Object value = params.get(field.name());
            if (value != null) {
                errors.addAll(validateType(field, value));
            }
        }
        
        // 3. 业务规则校验（如范围、格式等）
        errors.addAll(validateBusinessRules(params, toolDef));
        
        // 4. 安全校验（如 SQL 注入、XSS 等）
        errors.addAll(validateSecurity(params, toolDef));
        
        return new ValidationResult(errors.isEmpty(), errors);
    }
    
    /**
     * 类型校验
     */
    private List<String> validateType(SchemaField field, Object value) {
        List<String> errors = new ArrayList<>();
        FieldType expectedType = field.type();
        
        boolean valid = switch (expectedType) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof List;
            case OBJECT -> value instanceof Map;
        };
        
        if (!valid) {
            errors.add(String.format("参数 '%s' 类型错误: 期望 %s, 实际 %s",
                field.name(), expectedType, value.getClass().getSimpleName()));
        }
        
        return errors;
    }
    
    /**
     * 安全校验
     */
    private List<String> validateSecurity(Map<String, Object> params, 
                                          ToolDefinition toolDef) {
        List<String> errors = new ArrayList<>();
        
        // 检查 SQL 注入
        if (toolDef.category() == ToolCategory.DATABASE) {
            for (Object value : params.values()) {
                if (value instanceof String str && containsSqlInjection(str)) {
                    errors.add("检测到潜在的 SQL 注入: " + truncate(str, 50));
                }
            }
        }
        
        // 检查路径遍历
        if (toolDef.category() == ToolCategory.FILE) {
            String path = (String) params.get("path");
            if (path != null && containsPathTraversal(path)) {
                errors.add("检测到潜在的路径遍历攻击: " + path);
            }
        }
        
        // 检查 XSS
        for (Object value : params.values()) {
            if (value instanceof String str && containsXss(str)) {
                errors.add("检测到潜在的 XSS 攻击: " + truncate(str, 50));
            }
        }
        
        return errors;
    }
    
    private boolean containsSqlInjection(String str) {
        String lower = str.toLowerCase();
        return lower.contains("drop ") || lower.contains("delete ") 
            || lower.contains("--") || lower.contains(";drop")
            || lower.matches(".*union\\s+select.*");
    }
    
    private boolean containsPathTraversal(String path) {
        return path.contains("..") || path.contains("~") 
            || path.startsWith("/") || path.contains("\\");
    }
    
    private boolean containsXss(String str) {
        return str.contains("<script") || str.contains("javascript:")
            || str.contains("onerror=") || str.contains("onload=");
    }
}

// ===== 校验结果 =====
public record ValidationResult(
    boolean valid,
    List<String> errors
) {
    public String getErrorMessage() {
        return String.join("; ", errors);
    }
}
```

#### 2.6.2 幂等性保障

```java
// ===== 幂等键管理 =====
@Component
public class IdempotencyManager {
    
    private final RedisTemplate<String, String> redisTemplate;
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    
    /**
     * 生成幂等键
     */
    public String generateIdempotencyKey(String toolName, 
                                         Map<String, Object> params,
                                         String sessionId) {
        // 基于工具名 + 参数哈希 + 会话ID 生成唯一键
        String paramHash = DigestUtils.md5Hex(objectMapper.writeValueAsString(params));
        return String.format("idempotent:%s:%s:%s", toolName, sessionId, paramHash);
    }
    
    /**
     * 检查并设置幂等键（原子操作）
     * @return true 表示首次执行，false 表示重复请求
     */
    public boolean checkAndSet(String idempotencyKey) {
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(idempotencyKey, "processing", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(success);
    }
    
    /**
     * 缓存执行结果
     */
    public void cacheResult(String idempotencyKey, ToolResult result) {
        redisTemplate.opsForValue().set(
            idempotencyKey + ":result",
            objectMapper.writeValueAsString(result),
            IDEMPOTENCY_TTL
        );
    }
    
    /**
     * 获取缓存的执行结果
     */
    public Optional<ToolResult> getCachedResult(String idempotencyKey) {
        String cached = redisTemplate.opsForValue().get(idempotencyKey + ":result");
        if (cached != null) {
            return Optional.of(objectMapper.readValue(cached, ToolResult.class));
        }
        return Optional.empty();
    }
    
    /**
     * 释放幂等键（执行失败时）
     */
    public void release(String idempotencyKey) {
        redisTemplate.delete(idempotencyKey);
    }
}

// ===== 幂等工具执行器 =====
@Service
public class IdempotentToolExecutor {
    
    private final ToolRegistry toolRegistry;
    private final IdempotencyManager idempotencyManager;
    private final Set<ToolCategory> IDEMPOTENT_CATEGORIES = Set.of(
        ToolCategory.DATABASE,   // 数据库写操作
        ToolCategory.FILE,       // 文件写操作
        ToolCategory.EXTERNAL    // 外部服务调用
    );
    
    /**
     * 执行工具（自动处理幂等性）
     */
    public ToolResult execute(String toolName, 
                             Map<String, Object> params,
                             String sessionId) {
        ToolDefinition tool = toolRegistry.getTool(toolName)
            .orElseThrow(() -> new ToolNotFoundException(toolName));
        
        // 只对写操作启用幂等性
        if (IDEMPOTENT_CATEGORIES.contains(tool.category())) {
            return executeWithIdempotency(tool, params, sessionId);
        }
        
        return toolRegistry.execute(toolName, params);
    }
    
    private ToolResult executeWithIdempotency(ToolDefinition tool,
                                              Map<String, Object> params,
                                              String sessionId) {
        String idempotencyKey = idempotencyManager.generateIdempotencyKey(
            tool.name(), params, sessionId);
        
        // 检查是否已执行
        if (!idempotencyManager.checkAndSet(idempotencyKey)) {
            // 返回缓存结果
            return idempotencyManager.getCachedResult(idempotencyKey)
                .orElseGet(() -> ToolResult.failure("重复请求处理中，请稍后查询结果"));
        }
        
        try {
            // 执行工具
            ToolResult result = toolRegistry.execute(tool.name(), params);
            
            // 缓存成功结果
            if (result.success()) {
                idempotencyManager.cacheResult(idempotencyKey, result);
            } else {
                // 失败时释放幂等键，允许重试
                idempotencyManager.release(idempotencyKey);
            }
            
            return result;
        } catch (Exception e) {
            // 异常时释放幂等键
            idempotencyManager.release(idempotencyKey);
            throw e;
        }
    }
}
```

#### 2.6.3 超时、重试、熔断、降级

```java
// ===== 工具执行配置 =====
public record ToolExecutionConfig(
    Duration timeout,              // 超时时间
    int maxRetries,                // 最大重试次数
    Duration retryDelay,           // 重试间隔
    double circuitBreakerThreshold, // 熔断阈值
    Duration circuitBreakerWait,   // 熔断等待时间
    String fallbackResult          // 降级结果
) {
    // 预设配置
    public static ToolExecutionConfig lowRisk() {
        return new ToolExecutionConfig(
            Duration.ofSeconds(30), 3, Duration.ofSeconds(1),
            0.5, Duration.ofSeconds(30), null);
    }
    
    public static ToolExecutionConfig highRisk() {
        return new ToolExecutionConfig(
            Duration.ofSeconds(10), 1, Duration.ofSeconds(2),
            0.3, Duration.ofSeconds(60), "{\"error\": \"服务暂时不可用\"}");
    }
    
    public static ToolExecutionConfig readOnly() {
        return new ToolExecutionConfig(
            Duration.ofSeconds(60), 2, Duration.ofSeconds(1),
            0.5, Duration.ofSeconds(30), null);
    }
}

// ===== 弹性工具执行器 =====
@Service
public class ResilientToolExecutor {
    
    private final ToolRegistry toolRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final MeterRegistry meterRegistry;
    
    /**
     * 弹性执行工具（超时 + 重试 + 熔断 + 降级）
     */
    public ToolResult execute(String toolName, 
                             Map<String, Object> params,
                             ToolExecutionConfig config) {
        ToolDefinition tool = toolRegistry.getTool(toolName)
            .orElseThrow(() -> new ToolNotFoundException(toolName));
        
        // 获取或创建熔断器
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
            "tool-" + toolName,
            CircuitBreakerConfig.custom()
                .failureRateThreshold(config.circuitBreakerThreshold() * 100)
                .waitDurationInOpenState(config.circuitBreakerWait())
                .slidingWindowSize(10)
                .build()
        );
        
        // 构建执行链
        Supplier<ToolResult> supplier = () -> executeWithTimeout(tool, params, config);
        
        // 重试
        supplier = Retry.of("tool-retry-" + toolName, 
            RetryConfig.custom()
                .maxAttempts(config.maxRetries())
                .waitDuration(config.retryDelay())
                .retryOnException(this::shouldRetry)
                .build())
            .decorateSupplier(supplier);
        
        // 熔断
        supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        
        // 执行
        try {
            return supplier.get();
        } catch (CallNotPermittedException e) {
            // 熔断器打开，返回降级结果
            return getFallbackResult(tool, config, "服务熔断中");
        } catch (TimeoutException e) {
            return getFallbackResult(tool, config, "执行超时");
        } catch (Exception e) {
            return ToolResult.failure("执行失败: " + e.getMessage());
        }
    }
    
    private ToolResult executeWithTimeout(ToolDefinition tool,
                                          Map<String, Object> params,
                                          ToolExecutionConfig config) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        
        try {
            Future<ToolResult> future = executor.submit(
                () -> toolRegistry.execute(tool.name(), params));
            
            return future.get(config.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }
    
    private boolean shouldRetry(Throwable throwable) {
        // 网络错误、超时可重试
        return throwable instanceof TimeoutException
            || throwable instanceof java.net.SocketTimeoutException
            || throwable instanceof java.net.ConnectException
            || (throwable.getMessage() != null 
                && throwable.getMessage().contains("timeout"));
    }
    
    private ToolResult getFallbackResult(ToolDefinition tool,
                                         ToolExecutionConfig config,
                                         String reason) {
        if (config.fallbackResult() != null) {
            return ToolResult.success(config.fallbackResult());
        }
        return ToolResult.failure(reason + "，工具: " + tool.name());
    }
}
```

#### 2.6.4 高风险工具二次确认（Human-in-the-loop）

```java
// ===== 工具风险等级 =====
public enum ToolRiskLevel {
    LOW("低风险", false),           // 无需确认
    MEDIUM("中风险", false),        // 可选确认
    HIGH("高风险", true),           // 必须确认
    CRITICAL("关键操作", true);     // 必须确认 + 多因素验证
    
    private final String displayName;
    private final boolean requiresConfirmation;
}

// ===== 风险评估器 =====
@Component
public class ToolRiskEvaluator {
    
    // 高风险工具配置
    private static final Map<String, ToolRiskLevel> RISK_MAPPING = Map.of(
        "execute_sql", ToolRiskLevel.HIGH,
        "write_file", ToolRiskLevel.HIGH,
        "send_email", ToolRiskLevel.HIGH,
        "http_request", ToolRiskLevel.MEDIUM,
        "web_search", ToolRiskLevel.LOW,
        "get_current_time", ToolRiskLevel.LOW,
        "calculate", ToolRiskLevel.LOW
    );
    
    // 高风险操作关键词
    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
        "delete", "drop", "truncate", "update", "remove",
        "send", "publish", "submit", "confirm"
    );
    
    /**
     * 评估工具风险等级
     */
    public ToolRiskLevel evaluateRisk(String toolName, Map<String, Object> params) {
        // 1. 检查预设风险等级
        ToolRiskLevel baseLevel = RISK_MAPPING.getOrDefault(toolName, ToolRiskLevel.MEDIUM);
        
        // 2. 根据参数动态调整
        if (containsHighRiskOperation(params)) {
            baseLevel = ToolRiskLevel.HIGH;
        }
        
        // 3. 批量操作提升风险等级
        if (isBatchOperation(params)) {
            baseLevel = upgradeRiskLevel(baseLevel);
        }
        
        return baseLevel;
    }
    
    private boolean containsHighRiskOperation(Map<String, Object> params) {
        return params.values().stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(String::toLowerCase)
            .anyMatch(value -> HIGH_RISK_KEYWORDS.stream()
                .anyMatch(value::contains));
    }
    
    private boolean isBatchOperation(Map<String, Object> params) {
        Object count = params.get("count");
        if (count instanceof Number && ((Number) count).intValue() > 10) {
            return true;
        }
        Object items = params.get("items");
        return items instanceof List && ((List<?>) items).size() > 10;
    }
    
    private ToolRiskLevel upgradeRiskLevel(ToolRiskLevel level) {
        return switch (level) {
            case LOW -> ToolRiskLevel.MEDIUM;
            case MEDIUM -> ToolRiskLevel.HIGH;
            default -> level;
        };
    }
}

// ===== 确认管理器 =====
@Service
public class ToolConfirmationManager {
    
    private final RedisTemplate<String, PendingConfirmation> redisTemplate;
    private static final Duration CONFIRMATION_TTL = Duration.ofMinutes(5);
    
    /**
     * 创建待确认请求
     */
    public String createConfirmationRequest(String toolName,
                                           Map<String, Object> params,
                                           String sessionId,
                                           ToolRiskLevel riskLevel) {
        String confirmationId = generateId();
        
        PendingConfirmation confirmation = new PendingConfirmation(
            confirmationId,
            toolName,
            params,
            sessionId,
            riskLevel,
            ConfirmationStatus.PENDING,
            LocalDateTime.now(),
            null,
            generateConfirmationMessage(toolName, params, riskLevel)
        );
        
        redisTemplate.opsForValue().set(
            "confirmation:" + confirmationId,
            confirmation,
            CONFIRMATION_TTL
        );
        
        return confirmationId;
    }
    
    /**
     * 用户确认
     */
    public ConfirmationResult confirm(String confirmationId, 
                                     String userResponse,
                                     String userId) {
        PendingConfirmation confirmation = redisTemplate.opsForValue()
            .get("confirmation:" + confirmationId);
        
        if (confirmation == null) {
            return ConfirmationResult.expired();
        }
        
        if (!confirmation.status().equals(ConfirmationStatus.PENDING)) {
            return ConfirmationResult.alreadyProcessed();
        }
        
        // 解析用户响应
        boolean approved = parseUserResponse(userResponse);
        
        // 更新状态
        confirmation = confirmation.withStatus(
            approved ? ConfirmationStatus.APPROVED : ConfirmationStatus.REJECTED
        ).withConfirmedBy(userId);
        
        redisTemplate.opsForValue().set(
            "confirmation:" + confirmationId,
            confirmation,
            CONFIRMATION_TTL
        );
        
        return ConfirmationResult.of(approved, confirmation);
    }
    
    /**
     * 生成确认消息
     */
    private String generateConfirmationMessage(String toolName,
                                              Map<String, Object> params,
                                              ToolRiskLevel riskLevel) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ 工具执行确认请求\n\n");
        sb.append("工具: ").append(toolName).append("\n");
        sb.append("风险等级: ").append(riskLevel.getDisplayName()).append("\n\n");
        sb.append("参数:\n");
        params.forEach((k, v) -> sb.append("  - ").append(k).append(": ")
            .append(truncate(String.valueOf(v), 100)).append("\n"));
        sb.append("\n请确认是否执行此操作？(确认/取消)");
        return sb.toString();
    }
}

// ===== 待确认请求 =====
public record PendingConfirmation(
    String confirmationId,
    String toolName,
    Map<String, Object> params,
    String sessionId,
    ToolRiskLevel riskLevel,
    ConfirmationStatus status,
    LocalDateTime createdAt,
    String confirmedBy,
    String message
) {
    public PendingConfirmation withStatus(ConfirmationStatus status) {
        return new PendingConfirmation(confirmationId, toolName, params, 
            sessionId, riskLevel, status, createdAt, confirmedBy, message);
    }
    
    public PendingConfirmation withConfirmedBy(String userId) {
        return new PendingConfirmation(confirmationId, toolName, params, 
            sessionId, riskLevel, status, createdAt, userId, message);
    }
}

// ===== 确认流程集成 =====
@Service
public class ConfirmedToolExecutor {
    
    private final ToolRiskEvaluator riskEvaluator;
    private final ToolConfirmationManager confirmationManager;
    private final ResilientToolExecutor resilientExecutor;
    
    /**
     * 执行工具（带确认流程）
     */
    public ToolResult execute(String toolName,
                             Map<String, Object> params,
                             String sessionId,
                             String userId) {
        
        // 1. 评估风险
        ToolRiskLevel riskLevel = riskEvaluator.evaluateRisk(toolName, params);
        
        // 2. 高风险操作需要确认
        if (riskLevel.requiresConfirmation()) {
            String confirmationId = confirmationManager.createConfirmationRequest(
                toolName, params, sessionId, riskLevel);
            
            // 返回待确认状态，等待用户响应
            return ToolResult.pendingConfirmation(confirmationId, 
                "高风险操作需要确认", riskLevel);
        }
        
        // 3. 直接执行
        return resilientExecutor.execute(toolName, params, 
            getExecutionConfig(riskLevel));
    }
    
    /**
     * 确认后继续执行
     */
    public ToolResult executeAfterConfirmation(String confirmationId, String userId) {
        ConfirmationResult result = confirmationManager.confirm(confirmationId, "confirmed", userId);
        
        if (!result.approved()) {
            return ToolResult.failure("用户拒绝执行");
        }
        
        PendingConfirmation confirmation = result.confirmation();
        return resilientExecutor.execute(
            confirmation.toolName(),
            confirmation.params(),
            getExecutionConfig(confirmation.riskLevel())
        );
    }
    
    private ToolExecutionConfig getExecutionConfig(ToolRiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> ToolExecutionConfig.lowRisk();
            case MEDIUM -> ToolExecutionConfig.readOnly();
            case HIGH, CRITICAL -> ToolExecutionConfig.highRisk();
        };
    }
}
```

#### 2.6.5 工具执行审计日志

```java
// ===== 工具执行审计 =====
@Entity
@Table(name = "tool_execution_audits")
public class ToolExecutionAudit {
    
    @Id
    @GeneratedValue
    private Long id;
    
    private String executionId;
    private String toolName;
    private String sessionId;
    private String userId;
    
    @Column(columnDefinition = "TEXT")
    private String params;        // JSON 格式
    
    private boolean success;
    
    @Column(columnDefinition = "TEXT")
    private String result;        // 脱敏后的结果
    
    private String errorMessage;
    private long executionTimeMs;
    private ToolRiskLevel riskLevel;
    private boolean confirmed;
    private String confirmedBy;
    
    private LocalDateTime executedAt;
    
    @PrePersist
    void prePersist() {
        executedAt = LocalDateTime.now();
    }
}

// ===== 审计切面 =====
@Aspect
@Component
public class ToolExecutionAuditAspect {
    
    private final ToolExecutionAuditRepository auditRepository;
    private final SensitiveDataMasker dataMasker;
    
    @Around("execution(* com.jonychen.tool.ToolRegistry.execute(..))")
    public Object auditExecution(ProceedingJoinPoint pjp) throws Throwable {
        String toolName = (String) pjp.getArgs()[0];
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) pjp.getArgs()[1];
        
        String executionId = generateId();
        long startTime = System.currentTimeMillis();
        
        ToolExecutionAudit audit = new ToolExecutionAudit();
        audit.setExecutionId(executionId);
        audit.setToolName(toolName);
        audit.setParams(dataMasker.mask(params));  // 脱敏
        
        try {
            ToolResult result = (ToolResult) pjp.proceed();
            
            audit.setSuccess(result.success());
            audit.setResult(dataMasker.mask(result.data()));  // 脱敏
            audit.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            
            return result;
        } catch (Throwable e) {
            audit.setSuccess(false);
            audit.setErrorMessage(e.getMessage());
            audit.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            throw e;
        } finally {
            auditRepository.save(audit);
        }
    }
}

// ===== 敏感数据脱敏器 =====
@Component
public class SensitiveDataMasker {
    
    private static final Set<String> SENSITIVE_KEYS = Set.of(
        "password", "secret", "apiKey", "api_key", "token",
        "creditCard", "credit_card", "ssn", "phone", "email"
    );
    
    public String mask(Map<String, Object> data) {
        Map<String, Object> masked = new LinkedHashMap<>(data);
        
        for (String key : masked.keySet()) {
            if (isSensitiveKey(key)) {
                masked.put(key, "******");
            }
        }
        
        return objectMapper.writeValueAsString(masked);
    }
    
    public Object mask(Object data) {
        if (data == null) return null;
        if (data instanceof String str) {
            return truncate(str, 500);  // 限制长度
        }
        return data;
    }
    
    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        return SENSITIVE_KEYS.stream().anyMatch(lower::contains);
    }
}
```

#### 2.6.6 工具回滚机制

> **核心目标**：高风险工具执行失败后，能够回滚或补偿，保证数据一致性

```java
// ===== 回滚接口 =====
public interface RollbackableTool {
    /**
     * 回滚操作
     * @param executionId 执行 ID
     * @param params 执行参数
     * @param executedResult 执行结果（用于反向操作）
     */
    void rollback(String executionId, Map<String, Object> params, Object executedResult);
    
    /**
     * 是否支持回滚
     */
    default boolean supportsRollback() {
        return true;
    }
}

// ===== 回滚记录 =====
@Entity
@Table(name = "tool_rollback_records")
public class ToolRollbackRecord {
    @Id
    @GeneratedValue
    private Long id;
    
    private String executionId;
    private String toolName;
    private String sessionId;
    
    @Column(columnDefinition = "TEXT")
    private String params;
    
    @Column(columnDefinition = "TEXT")
    private String executedResult;
    
    @Enumerated(EnumType.STRING)
    private RollbackStatus status;  // PENDING, SUCCESS, FAILED
    
    private String errorMessage;
    private LocalDateTime executedAt;
    private LocalDateTime rolledBackAt;
}

// ===== 回滚状态 =====
public enum RollbackStatus {
    PENDING,        // 待回滚
    SUCCESS,        // 回滚成功
    FAILED          // 回滚失败
}

// ===== 回滚管理器 =====
@Service
public class ToolRollbackManager {
    
    private final ToolRegistry toolRegistry;
    private final ToolRollbackRecordRepository rollbackRepository;
    
    /**
     * 执行工具并记录回滚信息
     */
    public ToolResult executeWithRollbackSupport(String toolName,
                                                  Map<String, Object> params,
                                                  String sessionId) {
        String executionId = generateId();
        ToolDefinition tool = toolRegistry.getTool(toolName)
            .orElseThrow(() -> new ToolNotFoundException(toolName));
        
        // 1. 执行工具
        ToolResult result = toolRegistry.execute(toolName, params);
        
        // 2. 如果是回滚型工具且执行成功，记录回滚信息
        if (result.success() && tool instanceof RollbackableTool rollbackable) {
            ToolRollbackRecord record = new ToolRollbackRecord();
            record.setExecutionId(executionId);
            record.setToolName(toolName);
            record.setSessionId(sessionId);
            record.setParams(objectMapper.writeValueAsString(params));
            record.setExecutedResult(objectMapper.writeValueAsString(result.data()));
            record.setStatus(RollbackStatus.PENDING);
            record.setExecutedAt(LocalDateTime.now());
            rollbackRepository.save(record);
        }
        
        // 3. 如果失败且是回滚型工具，尝试回滚之前成功的步骤
        if (!result.success()) {
            rollbackPreviousSteps(sessionId);
        }
        
        return result;
    }
    
    /**
     * 回滚指定执行
     */
    public void rollbackExecution(String executionId) {
        ToolRollbackRecord record = rollbackRepository.findByExecutionId(executionId)
            .orElseThrow(() -> new IllegalArgumentException("回滚记录不存在"));
        
        ToolDefinition tool = toolRegistry.getTool(record.getToolName()).orElse(null);
        
        if (tool instanceof RollbackableTool rollbackable) {
            try {
                Map<String, Object> params = objectMapper.readValue(record.getParams(), Map.class);
                Object executedResult = objectMapper.readValue(record.getExecutedResult(), Object.class);
                
                rollbackable.rollback(executionId, params, executedResult);
                
                record.setStatus(RollbackStatus.SUCCESS);
                record.setRolledBackAt(LocalDateTime.now());
                rollbackRepository.save(record);
                
                log.info("工具 {} 回滚成功，executionId={}", record.getToolName(), executionId);
            } catch (Exception e) {
                record.setStatus(RollbackStatus.FAILED);
                record.setErrorMessage(e.getMessage());
                rollbackRepository.save(record);
                
                log.error("工具 {} 回滚失败，executionId={}，错误：{}", 
                    record.getToolName(), executionId, e.getMessage());
            }
        }
    }
    
    /**
     * 回滚会话中所有待回滚的执行
     */
    public void rollbackSession(String sessionId) {
        List<ToolRollbackRecord> records = rollbackRepository
            .findBySessionIdAndStatus(sessionId, RollbackStatus.PENDING);
        
        // 按执行时间倒序回滚（先回滚后执行的）
        records.sort(Comparator.comparing(ToolRollbackRecord::getExecutedAt).reversed());
        
        for (ToolRollbackRecord record : records) {
            rollbackExecution(record.getExecutionId());
        }
    }
    
    /**
     * 回滚之前成功的步骤（失败时自动触发）
     */
    private void rollbackPreviousSteps(String sessionId) {
        // 查找当前任务链条中需要回滚的步骤
        List<ToolRollbackRecord> records = rollbackRepository
            .findBySessionIdAndStatus(sessionId, RollbackStatus.PENDING);
        
        // 只回滚高风险工具
        records.stream()
            .filter(r -> isHighRiskTool(r.getToolName()))
            .sorted(Comparator.comparing(ToolRollbackRecord::getExecutedAt).reversed())
            .forEach(r -> rollbackExecution(r.getExecutionId()));
    }
    
    private boolean isHighRiskTool(String toolName) {
        return Set.of("write_file", "execute_sql", "send_email", "http_request")
            .contains(toolName);
    }
}

// ===== 示例：回滚型文件写入工具 =====
@Component
public class RollbackableFileTool implements RollbackableTool {
    
    @Override
    public ToolResult write(Map<String, Object> params) {
        String path = (String) params.get("path");
        String content = (String) params.get("content");
        
        // 1. 备份原文件
        Path backupPath = backupOriginal(path);
        
        // 2. 写入新内容
        Files.writeString(Path.of(path), content);
        
        // 3. 返回结果（包含备份路径用于回滚）
        return ToolResult.success(Map.of(
            "path", path,
            "backupPath", backupPath.toString()
        ));
    }
    
    @Override
    public void rollback(String executionId, Map<String, Object> params, Object executedResult) {
        Map<String, Object> result = (Map<String, Object>) executedResult;
        String backupPath = (String) result.get("backupPath");
        String originalPath = (String) result.get("path");
        
        // 恢复备份文件
        if (backupPath != null && Files.exists(Path.of(backupPath))) {
            Files.copy(Path.of(backupPath), Path.of(originalPath), 
                StandardCopyOption.REPLACE_EXISTING);
            Files.delete(Path.of(backupPath));
        } else {
            // 没有备份，直接删除新文件
            Files.deleteIfExists(Path.of(originalPath));
        }
    }
    
    private Path backupOriginal(String path) {
        if (!Files.exists(Path.of(path))) {
            return null;
        }
        Path backup = Path.of(path + ".backup_" + System.currentTimeMillis());
        Files.copy(Path.of(path), backup);
        return backup;
    }
}
```

#### 2.6.7 数据库设计

```sql
-- 回滚记录表
CREATE TABLE tool_rollback_records (
    id SERIAL PRIMARY KEY,
    execution_id VARCHAR(64) UNIQUE NOT NULL,
    tool_name VARCHAR(50) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    params TEXT NOT NULL,
    executed_result TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    executed_at TIMESTAMP NOT NULL,
    rolled_back_at TIMESTAMP
);

CREATE INDEX idx_rollback_session ON tool_rollback_records(session_id);
CREATE INDEX idx_rollback_status ON tool_rollback_records(status);
```

#### 2.6.8 配置示例

```properties
# application.properties

# ===== 工具参数校验 =====
app.tool.validation.enabled=true
app.tool.validation.strict-type=true

# ===== 幂等性配置 =====
app.tool.idempotency.enabled=true
app.tool.idempotency.ttl=24h

# ===== 超时配置 =====
app.tool.timeout.default=30s
app.tool.timeout.high-risk=10s
app.tool.timeout.readonly=60s

# ===== 重试配置 =====
app.tool.retry.max-attempts=3
app.tool.retry.delay=1s
app.tool.retry.enabled=true

# ===== 熔断配置 =====
app.tool.circuit-breaker.failure-threshold=0.5
app.tool.circuit-breaker.wait-duration=30s

# ===== 确认流程 =====
app.tool.confirmation.enabled=true
app.tool.confirmation.ttl=5m
```

### 2.7 实现文件清单

| 文件 | 说明 |
|------|------|
| `tool/ToolRegistry.java` | 工具注册中心接口 |
| `tool/DefaultToolRegistry.java` | 默认实现 |
| `tool/ToolDefinition.java` | 工具定义 record |
| `tool/ToolExecutor.java` | 执行器接口 |
| `tool/ToolResult.java` | 执行结果 record |
| `tool/ToolCategory.java` | 工具分类枚举 |
| `tool/ToolRiskLevel.java` | 风险等级枚举 |
| `tool/AgentTool.java` | 工具方法注解 |
| `tool/ToolParam.java` | 参数注解 |
| `tool/ToolMonitor.java` | 监控接口 |
| `tool/ToolStatistics.java` | 统计 record |
| `tool/builtin/DateTimeTools.java` | 时间工具 |
| `tool/builtin/MathTools.java` | 数学工具 |
| `tool/builtin/SearchTools.java` | 搜索工具 |
| `tool/builtin/FileTools.java` | 文件工具 |
| `tool/builtin/HttpTools.java` | HTTP 工具 |
| `tool/validation/ToolParameterValidator.java` | 参数校验器 |
| `tool/validation/ValidationResult.java` | 校验结果 |
| `tool/idempotency/IdempotencyManager.java` | 幂等键管理 |
| `tool/idempotency/IdempotentToolExecutor.java` | 幂等执行器 |
| `tool/resilience/ResilientToolExecutor.java` | 弹性执行器 |
| `tool/resilience/ToolExecutionConfig.java` | 执行配置 |
| `tool/confirmation/ToolRiskEvaluator.java` | 风险评估器 |
| `tool/confirmation/ToolConfirmationManager.java` | 确认管理器 |
| `tool/confirmation/ConfirmedToolExecutor.java` | 确认流程执行器 |
| `tool/audit/ToolExecutionAudit.java` | 审计日志实体 |
| `tool/audit/ToolExecutionAuditAspect.java` | 审计切面 |
| `tool/audit/SensitiveDataMasker.java` | 敏感数据脱敏器 |
| `config/ToolConfig.java` | 工具系统配置 |

### 2.7 实现步骤

1. **Step 1**: 创建 `tool` 包和基础接口
2. **Step 2**: 实现 `DefaultToolRegistry`
3. **Step 3**: 创建注解 `@AgentTool` 和 `@ToolParam`
4. **Step 4**: 实现注解扫描和自动注册
5. **Step 5**: 实现内置工具类
6. **Step 6**: 集成监控和指标
7. **Step 7**: 修改 `AiConfig` 自动注册工具
8. **Step 8**: 添加权限校验逻辑
9. **Step 9**: 编写单元测试

---

## 4. 任务规划

### 4.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    AgentOrchestrator                         │
│  - executeTask(userRequest) -> TaskResult                    │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│   ReActAgent    │ │ PlanExecuteAgent│ │  SequentialAgent│
│  (推理-行动)    │ │  (规划-执行)    │ │   (顺序执行)    │
└─────────────────┘ └─────────────────┘ └─────────────────┘
              │               │               │
              └───────────────┼───────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      TaskPlanner                             │
│  - plan(goal) -> List<Step>                                  │
│  - replan(currentState, failedStep) -> List<Step>            │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      TaskExecutor                            │
│  - executeStep(step) -> StepResult                           │
│  - shouldContinue(stepResult) -> boolean                     │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 核心接口设计

```java
// ===== 任务 =====
public record Task(
    String taskId,
    String goal,                    // 任务目标
    TaskType type,                  // 任务类型
    Map<String, Object> context,    // 上下文
    TaskStatus status,              // 状态
    List<Step> steps,               // 执行步骤
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {}

// ===== 任务类型 =====
public enum TaskType {
    SIMPLE,         // 简单任务，直接执行
    MULTI_STEP,     // 多步骤任务
    COMPLEX         // 复杂任务，需要规划
}

// ===== 任务状态 =====
public enum TaskStatus {
    PENDING,        // 待执行
    PLANNING,       // 规划中
    EXECUTING,      // 执行中
    COMPLETED,      // 已完成
    FAILED,         // 失败
    CANCELLED       // 已取消
}

// ===== 执行步骤 =====
public record Step(
    String stepId,
    int order,                      // 执行顺序
    String description,             // 步骤描述
    String action,                  // 具体行动
    Map<String, Object> params,     // 参数
    StepStatus status,
    StepResult result,              // 执行结果
    String dependsOn                // 依赖的步骤 ID
) {}

// ===== 步骤状态 =====
public enum StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}

// ===== 步骤结果 =====
public record StepResult(
    boolean success,
    Object output,
    String error,
    List<String> logs
) {}

// ===== 任务规划器 =====
public interface TaskPlanner {
    /**
     * 根据目标生成执行计划
     */
    List<Step> plan(String goal, Map<String, Object> context);
    
    /**
     * 根据执行情况重新规划
     */
    List<Step> replan(Task task, Step failedStep);
    
    /**
     * 判断是否需要规划
     */
    boolean needsPlanning(String goal);
}

// ===== 任务执行器 =====
public interface TaskExecutor {
    /**
     * 执行单个步骤
     */
    StepResult executeStep(Step step, TaskContext context);
    
    /**
     * 执行整个任务
     */
    TaskResult executeTask(Task task);
}

// ===== 任务上下文 =====
public class TaskContext {
    private String sessionId;
    private Map<String, Object> variables;     // 变量存储
    private Map<String, StepResult> stepResults; // 步骤结果
    private ToolRegistry toolRegistry;
    private ChatMemory chatMemory;
    
    public void setVariable(String key, Object value);
    public Object getVariable(String key);
    public StepResult getStepResult(String stepId);
}

// ===== 任务结果 =====
public record TaskResult(
    String taskId,
    boolean success,
    Object finalOutput,
    List<StepResult> stepResults,
    String error,
    long totalExecutionTimeMs
) {}
```

### 3.3 ReAct Agent 实现

```java
/**
 * ReAct Agent：推理-行动循环
 * 
 * 工作流程：
 * 1. Thought: LLM 思考下一步该做什么
 * 2. Action: 选择并执行工具
 * 3. Observation: 观察执行结果
 * 4. 循环直到得出最终答案
 */
@Component
public class ReActAgent {
    
    private static final String REACT_PROMPT = """
        你是一个智能助手，使用 ReAct 模式解决问题。
        
        遵循以下格式：
        
        Thought: 思考当前情况，分析下一步该做什么
        Action: 工具名称
        Action Input: JSON 格式的参数
        Observation: 工具返回的结果
        ... (重复 Thought/Action/Observation 直到可以回答)
        Thought: 我现在知道最终答案了
        Final Answer: 最终回答
        
        可用工具：
        {tools}
        
        开始！
        
        用户问题：{question}
        """;
    
    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final int maxIterations;
    
    public ReActResult execute(String question, TaskContext context) {
        List<ReActStep> steps = new ArrayList<>();
        String currentPrompt = buildPrompt(question);
        
        for (int i = 0; i < maxIterations; i++) {
            // 1. LLM 思考
            String response = chatModel.chat(currentPrompt);
            
            // 2. 解析 Thought/Action
            ReActStep step = parseResponse(response);
            steps.add(step);
            
            // 3. 判断是否结束
            if (step.isFinalAnswer()) {
                return new ReActResult(true, step.getFinalAnswer(), steps);
            }
            
            // 4. 执行工具
            ToolResult toolResult = toolRegistry.execute(
                step.getAction(), 
                step.getActionInput()
            );
            
            // 5. 观察
            step.setObservation(toolResult);
            
            // 6. 更新提示词继续循环
            currentPrompt = currentPrompt + "\n" + response + 
                "\nObservation: " + toolResult.data();
        }
        
        return new ReActResult(false, "达到最大迭代次数", steps);
    }
}

public record ReActResult(
    boolean success,
    String answer,
    List<ReActStep> steps
) {}
```

### 3.4 Plan-and-Execute Agent 实现

```java
/**
 * Plan-and-Execute Agent：先规划后执行
 * 
 * 工作流程：
 * 1. Planning: LLM 生成完整计划
 * 2. Execution: 按顺序执行每个步骤
 * 3. Replanning: 遇到失败时重新规划
 */
@Component
public class PlanExecuteAgent {
    
    private static final String PLANNING_PROMPT = """
        你是一个任务规划专家。根据用户目标，分解为具体执行步骤。
        
        输出格式（JSON）：
        {
          "steps": [
            {
              "order": 1,
              "description": "步骤描述",
              "action": "工具名称或具体行动",
              "params": {"param1": "value1"}
            }
          ]
        }
        
        可用工具：
        {tools}
        
        用户目标：{goal}
        """;
    
    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final TaskExecutor taskExecutor;
    
    public TaskResult execute(String goal, TaskContext context) {
        // 1. 规划阶段
        Task task = plan(goal, context);
        
        // 2. 执行阶段
        for (Step step : task.steps()) {
            StepResult result = taskExecutor.executeStep(step, context);
            
            if (!result.success()) {
                // 3. 失败时重新规划
                Task replannedTask = replan(task, step, result);
                if (replannedTask != null) {
                    return execute(replannedTask);
                }
                return TaskResult.failure(task.taskId(), result.error());
            }
            
            context.recordStepResult(step.stepId(), result);
        }
        
        return TaskResult.success(task, aggregateResults(context));
    }
}
```

### 3.5 实现文件清单

| 文件 | 说明 |
|------|------|
| `planning/Task.java` | 任务实体 |
| `planning/Step.java` | 步骤实体 |
| `planning/TaskPlanner.java` | 规划器接口 |
| `planning/LLMTaskPlanner.java` | LLM 规划实现 |
| `planning/TaskExecutor.java` | 执行器接口 |
| `planning/DefaultTaskExecutor.java` | 默认执行器 |
| `planning/TaskContext.java` | 任务上下文 |
| `planning/TaskResult.java` | 任务结果 |
| `planning/ReActAgent.java` | ReAct Agent |
| `planning/PlanExecuteAgent.java` | Plan-Execute Agent |
| `planning/AgentOrchestrator.java` | Agent 编排器 |
| `controller/TaskController.java` | 任务 REST API |
| `config/PlanningConfig.java` | 规划配置 |

### 3.6 API 接口

```
POST /api/tasks                # 创建任务
GET  /api/tasks/{taskId}       # 获取任务状态
POST /api/tasks/{taskId}/cancel # 取消任务
GET  /api/tasks/{taskId}/steps # 获取执行步骤
```

### 3.7 实现步骤

1. **Step 1**: 创建 `planning` 包和基础实体类
2. **Step 2**: 实现 `TaskPlanner` 接口和 LLM 实现
3. **Step 3**: 实现 `TaskExecutor`
4. **Step 4**: 实现 `ReActAgent`
5. **Step 5**: 实现 `PlanExecuteAgent`
6. **Step 6**: 实现 `AgentOrchestrator`（自动选择策略）
7. **Step 7**: 实现任务持久化
8. **Step 8**: 实现 `TaskController`
9. **Step 9**: 编写测试用例

---

## 5. RAG 知识库

### 5.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    RAGPipeline                              │
│  - ingest(document) -> Document                             │
│  - retrieve(query) -> List<DocumentChunk>                   │
│  - generate(query, chunks) -> String                        │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
┌───────────────┐     ┌───────────────┐     ┌───────────────┐
│ DocumentLoader│     │ TextSplitter  │     │ EmbeddingModel│
│ (文档加载)    │     │ (文本分割)    │     │ (向量化)      │
└───────────────┘     └───────────────┘     └───────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    VectorStore                              │
│  - add(List<DocumentChunk>)                                  │
│  - search(query, topK) -> List<DocumentChunk>               │
│  - delete(documentId)                                        │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 核心接口设计

```java
// ===== 文档 =====
public record Document(
    String id,
    String filename,
    String content,
    DocumentType type,
    Map<String, Object> metadata,
    LocalDateTime createdAt
) {}

// ===== 文档类型 =====
public enum DocumentType {
    PDF,
    DOCX,
    MARKDOWN,
    HTML,
    TXT,
    JSON
}

// ===== 文档分块 =====
public record DocumentChunk(
    String id,
    String documentId,
    String content,
    float[] embedding,
    int chunkIndex,
    int startIndex,
    int endIndex,
    Map<String, Object> metadata
) {}

// ===== 文档加载器 =====
public interface DocumentLoader {
    /**
     * 加载文档
     */
    Document load(InputStream inputStream, String filename);
    
    /**
     * 支持的文档类型
     */
    Set<DocumentType> supportedTypes();
}

// ===== PDF 加载器 =====
@Component
public class PdfDocumentLoader implements DocumentLoader {
    @Override
    public Document load(InputStream inputStream, String filename) {
        try (PDDocument pdf = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String content = stripper.getText(pdf);
            return new Document(
                generateId(),
                filename,
                content,
                DocumentType.PDF,
                extractMetadata(pdf),
                LocalDateTime.now()
            );
        }
    }
    
    @Override
    public Set<DocumentType> supportedTypes() {
        return Set.of(DocumentType.PDF);
    }
}

// ===== 文本分割器 =====
public interface TextSplitter {
    /**
     * 分割文本为块
     */
    List<DocumentChunk> split(Document document);
}

// ===== 递归字符分割器 =====
@Component
public class RecursiveCharacterTextSplitter implements TextSplitter {
    
    private final int chunkSize;        // 块大小（字符数）
    private final int chunkOverlap;     // 重叠大小
    private final List<String> separators; // 分隔符优先级
    
    public RecursiveCharacterTextSplitter(
        int chunkSize, 
        int chunkOverlap,
        List<String> separators
    ) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = separators;
    }
    
    @Override
    public List<DocumentChunk> split(Document document) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String text = document.content();
        
        // 按优先级分割
        List<String> splits = splitText(text, separators);
        
        // 合并小片段并创建块
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = 0;
        
        for (String split : splits) {
            if (currentChunk.length() + split.length() > chunkSize) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(createChunk(document, currentChunk.toString(), chunkIndex++));
                }
                // 处理重叠
                currentChunk = new StringBuilder(getOverlapText(currentChunk.toString()));
            }
            currentChunk.append(split);
        }
        
        if (!currentChunk.isEmpty()) {
            chunks.add(createChunk(document, currentChunk.toString(), chunkIndex));
        }
        
        return chunks;
    }
}

// ===== Embedding 模型接口 =====
public interface EmbeddingService {
    /**
     * 生成文本嵌入向量
     */
    float[] embed(String text);
    
    /**
     * 批量生成嵌入向量
     */
    List<float[]> embedBatch(List<String> texts);
    
    /**
     * 向量维度
     */
    int dimension();
}

// ===== DashScope Embedding 实现（推荐）=====
@Component
@Primary
public class DashScopeEmbeddingService implements EmbeddingService {
    
    private final OpenAiEmbeddingModel model;  // DashScope 兼容 OpenAI 接口
    
    public DashScopeEmbeddingService(
            @Value("${embedding.dashscope.api-key}") String apiKey,
            @Value("${embedding.dashscope.model:text-embedding-v2}") String modelName) {
        this.model = OpenAiEmbeddingModel.builder()
            .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
            .apiKey(apiKey)
            .modelName(modelName)  // text-embedding-v2 或 text-embedding-v3
            .build();
    }
    
    @Override
    public float[] embed(String text) {
        Embedding embedding = model.embed(text).content();
        return embedding.vector();
    }
    
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        // 批量嵌入，减少 API 调用次数
        List<Embedding> embeddings = model.embedAll(texts.stream()
            .map(dev.langchain4j.data.embedding.Embedding::of)
            .toList()).content();
        return embeddings.stream()
            .map(Embedding::vector)
            .toList();
    }
    
    @Override
    public int dimension() {
        return 1536;  // text-embedding-v2/v3
    }
}

// ===== 可插拔 Embedding 配置 =====
@Configuration
public class EmbeddingConfig {
    
    @Bean
    @ConditionalOnProperty(name = "embedding.provider", havingValue = "dashscope", matchIfMissing = true)
    public EmbeddingService dashScopeEmbeddingService() {
        return new DashScopeEmbeddingService();
    }
    
    @Bean
    @ConditionalOnProperty(name = "embedding.provider", havingValue = "openai")
    public EmbeddingService openAiEmbeddingService() {
        return new OpenAiEmbeddingService();
    }
    
    @Bean
    @ConditionalOnProperty(name = "embedding.provider", havingValue = "local")
    public EmbeddingService localEmbeddingService() {
        // 本地模型（如 sentence-transformers）
        return new LocalEmbeddingService();
    }
}

// ===== 向量存储接口 =====
public interface VectorStore {
    /**
     * 添加文档块
     */
    void add(List<DocumentChunk> chunks);
    
    /**
     * 相似度搜索
     */
    List<DocumentChunk> search(float[] queryVector, int topK);
    
    /**
     * 搜索并返回相似度分数
     */
    List<SearchResult> searchWithScore(float[] queryVector, int topK);
    
    /**
     * 删除文档
     */
    void delete(String documentId);
    
    /**
     * 获取文档数量
     */
    long count();
}

// ===== 搜索结果 =====
public record SearchResult(
    DocumentChunk chunk,
    double score
) {}

// ===== RAG Pipeline =====
@Service
public class RAGPipeline {
    
    private final Map<DocumentType, DocumentLoader> loaders;
    private final TextSplitter textSplitter;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    
    /**
     * 文档入库流程
     */
    public Document ingest(InputStream inputStream, String filename) {
        // 1. 确定文档类型
        DocumentType type = detectType(filename);
        
        // 2. 加载文档
        DocumentLoader loader = loaders.get(type);
        Document document = loader.load(inputStream, filename);
        
        // 3. 分割文本
        List<DocumentChunk> chunks = textSplitter.split(document);
        
        // 4. 生成嵌入向量
        List<String> texts = chunks.stream().map(DocumentChunk::content).toList();
        List<float[]> embeddings = embeddingService.embedBatch(texts);
        
        // 5. 更新块向量
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).setEmbedding(embeddings.get(i));
        }
        
        // 6. 存入向量数据库
        vectorStore.add(chunks);
        
        return document;
    }
    
    /**
     * 检索流程
     */
    public List<DocumentChunk> retrieve(String query, int topK) {
        // 1. 查询向量化
        float[] queryVector = embeddingService.embed(query);
        
        // 2. 相似度搜索
        return vectorStore.search(queryVector, topK);
    }
    
    /**
     * 生成回答（RAG）
     */
    public String generate(String query, List<DocumentChunk> context) {
        String contextText = context.stream()
            .map(DocumentChunk::content)
            .collect(Collectors.joining("\n\n---\n\n"));
        
        String prompt = """
            基于以下参考信息回答问题。如果参考信息中没有答案，请说明。
            
            参考信息：
            %s
            
            问题：%s
            
            回答：
            """.formatted(contextText, query);
        
        return chatModel.chat(prompt);
    }
}
```

### 4.3 数据库设计

```sql
-- 文档表
CREATE TABLE documents (
    id VARCHAR(64) PRIMARY KEY,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT,
    content_hash VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 文档块表（向量存储）
CREATE TABLE document_chunks (
    id VARCHAR(64) PRIMARY KEY,
    document_id VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    chunk_index INT NOT NULL,
    start_index INT,
    end_index INT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_document FOREIGN KEY (document_id) 
        REFERENCES documents(id) ON DELETE CASCADE
);

-- 向量索引
CREATE INDEX idx_chunks_embedding ON document_chunks 
    USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

CREATE INDEX idx_chunks_document ON document_chunks(document_id);
```

### 4.4 实现文件清单

| 文件 | 说明 |
|------|------|
| `rag/Document.java` | 文档实体 |
| `rag/DocumentType.java` | 文档类型枚举 |
| `rag/DocumentChunk.java` | 文档分块实体 |
| `rag/DocumentLoader.java` | 文档加载器接口 |
| `rag/loader/PdfDocumentLoader.java` | PDF 加载器 |
| `rag/loader/MarkdownDocumentLoader.java` | Markdown 加载器 |
| `rag/loader/HtmlDocumentLoader.java` | HTML 加载器 |
| `rag/TextSplitter.java` | 文本分割器接口 |
| `rag/splitter/RecursiveCharacterTextSplitter.java` | 递归分割器 |
| `rag/EmbeddingService.java` | Embedding 服务接口 |
| `rag/embedding/OpenAiEmbeddingService.java` | OpenAI 实现 |
| `rag/VectorStore.java` | 向量存储接口 |
| `rag/store/PgVectorStore.java` | PostgreSQL 实现 |
| `rag/RAGPipeline.java` | RAG 流水线 |
| `rag/RetrievalAugmentor.java` | 检索增强器 |
| `controller/DocumentController.java` | 文档 REST API |
| `config/RAGConfig.java` | RAG 配置 |

### 4.5 API 接口

```
POST   /api/documents              # 上传文档
GET    /api/documents              # 文档列表
GET    /api/documents/{id}         # 文档详情
DELETE /api/documents/{id}         # 删除文档
POST   /api/documents/search       # 语义搜索
POST   /api/documents/ask          # RAG 问答
```

### 4.6 依赖添加

```xml
<!-- Apache PDFBox - PDF 解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.0</version>
</dependency>

<!-- Apache POI - Word/Excel 解析 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- Jsoup - HTML 解析 -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>

<!-- LangChain4j PGVector（默认方案）-->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
</dependency>
```

### 5.7 向量库选型与 Docker 本地部署

> **决策确认**：采用本地 Docker 部署向量库，预留多种向量库接口

#### 5.7.1 向量库选型对比

| 向量库 | 优点 | 缺点 | 适用场景 |
|--------|------|------|----------|
| **PGVector** | 复用现有 PostgreSQL、部署简单、运维成本低 | 性能较低、不支持分布式 | 小规模、原型阶段 |
| **Milvus** | 高性能、支持分布式、功能丰富 | 部署复杂、运维成本高 | 中大规模、生产环境 |
| **Qdrant** | 资源占用低、易部署、支持过滤查询 | 社区较小 | 中小规模、边缘场景 |
| **Weaviate** | 支持多模态、内置 GraphQL | 功能偏多、性能一般 | 多模态场景 |

#### 5.7.2 Docker 本地部署配置

**PGVector（默认方案）**

```yaml
# docker-compose.yml
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: langchain4j-postgres
    environment:
      POSTGRES_USER: ${POSTGRES_USER:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-postgres}
      POSTGRES_DB: ${POSTGRES_DB:-langchain4j}
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
```

**Milvus（备用方案）**

```yaml
# docker-compose-milvus.yml
services:
  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    container_name: milvus-etcd
    environment:
      ETCD_AUTO_COMPACTION_MODE: revision
      ETCD_AUTO_COMPACTION_RETENTION: '1000'
      ETCD_QUOTA_BACKEND_BYTES: '4294967296'
    volumes:
      - etcd_data:/etcd

  minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    container_name: milvus-minio
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio_data:/minio_data
    command: minio server /minio_data
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  milvus:
    image: milvusdb/milvus:v2.3.3
    container_name: milvus-standalone
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    volumes:
      - milvus_data:/var/lib/milvus
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - minio

volumes:
  etcd_data:
  minio_data:
  milvus_data:
```

**Qdrant（轻量方案）**

```yaml
# docker-compose-qdrant.yml
services:
  qdrant:
    image: qdrant/qdrant:latest
    container_name: langchain4j-qdrant
    ports:
      - "6333:6333"  # REST API
      - "6334:6334"  # gRPC API
    volumes:
      - qdrant_data:/qdrant/storage
    environment:
      QDRANT__LOG_LEVEL: INFO

volumes:
  qdrant_data:
```

#### 5.7.3 可插拔向量存储配置

```java
// ===== 向量存储配置 =====
@Configuration
public class VectorStoreConfig {
    
    @Bean
    @ConditionalOnProperty(name = "vector-store.provider", havingValue = "pgvector", matchIfMissing = true)
    public VectorStore pgVectorStore(
            DataSource dataSource,
            EmbeddingService embeddingService) {
        return new PgVectorStoreImpl(dataSource, embeddingService);
    }
    
    @Bean
    @ConditionalOnProperty(name = "vector-store.provider", havingValue = "milvus")
    public VectorStore milvusStore(
            @Value("${vector-store.milvus.host}") String host,
            @Value("${vector-store.milvus.port}") int port,
            EmbeddingService embeddingService) {
        return new MilvusStoreImpl(host, port, embeddingService);
    }
    
    @Bean
    @ConditionalOnProperty(name = "vector-store.provider", havingValue = "qdrant")
    public VectorStore qdrantStore(
            @Value("${vector-store.qdrant.host}") String host,
            @Value("${vector-store.qdrant.port}") int port,
            EmbeddingService embeddingService) {
        return new QdrantStoreImpl(host, port, embeddingService);
    }
}

// ===== Milvus 实现示例 =====
@Component
@ConditionalOnProperty(name = "vector-store.provider", havingValue = "milvus")
public class MilvusStoreImpl implements VectorStore {
    
    private final MilvusServiceClient milvusClient;
    private final EmbeddingService embeddingService;
    private final String collectionName = "document_chunks";
    
    public MilvusStoreImpl(String host, int port, EmbeddingService embeddingService) {
        this.milvusClient = new MilvusServiceClient(
            ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port)
                .build()
        );
        this.embeddingService = embeddingService;
        
        // 初始化 Collection
        initCollection();
    }
    
    private void initCollection() {
        // 创建 Collection（如果不存在）
        FieldType fieldType1 = FieldType.newBuilder()
            .withName("id")
            .withDataType(DataType.VarChar)
            .withMaxLength(64)
            .withPrimaryKey(true)
            .withAutoID(false)
            .build();
        
        FieldType fieldType2 = FieldType.newBuilder()
            .withName("embedding")
            .withDataType(DataType.FloatVector)
            .withDimension(embeddingService.dimension())
            .build();
        
        FieldType fieldType3 = FieldType.newBuilder()
            .withName("content")
            .withDataType(DataType.VarChar)
            .withMaxLength(65535)
            .build();
        
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
            .withCollectionName(collectionName)
            .withDescription("RAG document chunks")
            .withShardsNum(2)
            .addFieldType(fieldType1)
            .addFieldType(fieldType2)
            .addFieldType(fieldType3)
            .build();
        
        R<RpcStatus> response = milvusClient.createCollection(createParam);
        
        // 创建向量索引
        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
            .withCollectionName(collectionName)
            .withFieldName("embedding")
            .withIndexType(IndexType.IVF_FLAT)
            .withMetricType(MetricType.COSINE)
            .withExtraParam("{\"nlist\":1024}")
            .withSyncMode(Boolean.TRUE)
            .build();
        
        milvusClient.createIndex(indexParam);
    }
    
    @Override
    public void add(List<DocumentChunk> chunks) {
        List<String> ids = chunks.stream().map(DocumentChunk::id).toList();
        List<List<Float>> embeddings = chunks.stream()
            .map(c -> toFloatList(c.embedding()))
            .toList();
        List<String> contents = chunks.stream().map(DocumentChunk::content).toList();
        
        InsertParam insertParam = InsertParam.newBuilder()
            .withCollectionName(collectionName)
            .withFields(Arrays.asList(
                new Field("id", ids),
                new Field("embedding", embeddings),
                new Field("content", contents)
            ))
            .build();
        
        milvusClient.insert(insertParam);
    }
    
    @Override
    public List<DocumentChunk> search(float[] queryVector, int topK) {
        SearchParam searchParam = SearchParam.newBuilder()
            .withCollectionName(collectionName)
            .withMetricType(MetricType.COSINE)
            .withTopK(topK)
            .withVectors(Collections.singletonList(toFloatList(queryVector)))
            .withVectorFieldName("embedding")
            .withOutFields(Arrays.asList("id", "content"))
            .build();
        
        R<SearchResults> result = milvusClient.search(searchParam);
        
        return result.getData().getResults().getQueryResults().get(0)
            .getFieldData().stream()
            .map(this::toDocumentChunk)
            .toList();
    }
}
```

#### 5.7.4 配置示例

```properties
# application.properties

# ===== 向量存储配置 =====
# 默认使用 PGVector
vector-store.provider=pgvector

# PGVector 配置（复用现有 PostgreSQL）
vector-store.pgvector.enabled=true

# Milvus 配置（备用）
# vector-store.provider=milvus
vector-store.milvus.host=localhost
vector-store.milvus.port=19530

# Qdrant 配置（备用）
# vector-store.provider=qdrant
vector-store.qdrant.host=localhost
vector-store.qdrant.port=6333

# ===== Embedding 配置 =====
# 默认使用 DashScope
embedding.provider=dashscope
embedding.dashscope.api-key=${DASHSCOPE_API_KEY}
embedding.dashscope.model=text-embedding-v2

# OpenAI 配置（备用）
# embedding.provider=openai
embedding.openai.api-key=${OPENAI_API_KEY}
embedding.openai.model=text-embedding-ada-002
```

#### 5.7.5 实现步骤

1. **Step 1**: 创建 `rag` 包和基础实体
2. **Step 2**: 添加文档处理依赖
3. **Step 3**: 实现各类 `DocumentLoader`
4. **Step 4**: 实现 `TextSplitter`
5. **Step 5**: 实现 `EmbeddingService`
6. **Step 6**: 配置 PostgreSQL pgvector
7. **Step 7**: 实现 `PgVectorStore`
8. **Step 8**: 实现 `RAGPipeline`
9. **Step 9**: 实现 `DocumentController`
10. **Step 10**: 集成到 `ChatAssistant`

---

## 6. 多 Agent 协作

### 6.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    AgentTeam                                 │
│  - agents: List<Agent>                                       │
│  - workflow: Workflow                                        │
│  - execute(input) -> TeamResult                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Agent                                   │
│  - id: String                                                │
│  - name: String                                              │
│  - role: String              # 角色定义                      │
│  - systemPrompt: String      # 系统提示词                    │
│  - tools: List<Tool>         # 可用工具                      │
│  - capabilities: List<String> # 能力列表                     │
│  - execute(task) -> AgentResult                              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Workflow                                  │
│  - steps: List<WorkflowStep>                                 │
│  - execute(context) -> WorkflowResult                        │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ SequentialStep  │ │ ParallelStep    │ │ ConditionalStep │
│ (顺序执行)      │ │ (并行执行)      │ │ (条件分支)      │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 5.2 核心接口设计

```java
// ===== Agent 定义 =====
public class Agent {
    private final String id;
    private final String name;
    private final String role;
    private final String systemPrompt;
    private final List<String> availableTools;
    private final List<String> capabilities;
    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    
    /**
     * 执行任务
     */
    public AgentResult execute(String task, AgentContext context) {
        // 1. 构建完整提示词
        String fullPrompt = buildPrompt(task, context);
        
        // 2. 调用 LLM
        String response = chatModel.chat(fullPrompt);
        
        // 3. 检查是否需要工具调用
        if (needsToolCall(response)) {
            ToolCallResult toolResult = executeTools(response);
            return AgentResult.withToolCalls(response, toolResult);
        }
        
        return AgentResult.success(response);
    }
    
    /**
     * 构建提示词
     */
    private String buildPrompt(String task, AgentContext context) {
        return """
            你是 %s，角色是 %s。
            
            %s
            
            可用工具：%s
            
            历史上下文：
            %s
            
            当前任务：%s
            """.formatted(name, role, systemPrompt, 
                String.join(", ", availableTools),
                context.getHistory(),
                task);
    }
}

// ===== Agent 结果 =====
public record AgentResult(
    String agentId,
    boolean success,
    String output,
    List<ToolCall> toolCalls,
    String error,
    long executionTimeMs
) {
    public static AgentResult success(String output) {
        return new AgentResult(null, true, output, List.of(), null, 0);
    }
}

// ===== Agent 上下文 =====
public class AgentContext {
    private String sessionId;
    private Map<String, Object> variables;
    private List<AgentResult> previousResults;
    private ChatMemory chatMemory;
    
    public void setVariable(String key, Object value);
    public Object getVariable(String key);
    public void addResult(AgentResult result);
    public String getHistory();
}

// ===== Agent 团队 =====
public class AgentTeam {
    private final String teamId;
    private final Map<String, Agent> agents;
    private final Workflow workflow;
    private final AgentCommunicationBus communicationBus;
    
    /**
     * 执行团队任务
     */
    public TeamResult execute(String input, TeamContext context) {
        // 初始化上下文
        TeamContext teamContext = new TeamContext(input, context);
        
        // 执行工作流
        WorkflowResult workflowResult = workflow.execute(teamContext);
        
        // 聚合结果
        return aggregateResults(workflowResult);
    }
    
    /**
     * 获取指定 Agent
     */
    public Agent getAgent(String agentId) {
        return agents.get(agentId);
    }
}

// ===== 工作流 =====
public interface Workflow {
    /**
     * 执行工作流
     */
    WorkflowResult execute(TeamContext context);
    
    /**
     * 添加步骤
     */
    Workflow addStep(WorkflowStep step);
}

// ===== 工作流步骤 =====
public interface WorkflowStep {
    String stepId();
    StepResult execute(TeamContext context);
}

// ===== 顺序步骤 =====
public class SequentialStep implements WorkflowStep {
    private final String stepId;
    private final String agentId;
    private final String taskTemplate;
    
    @Override
    public StepResult execute(TeamContext context) {
        Agent agent = context.getAgent(agentId);
        String task = renderTask(taskTemplate, context);
        
        AgentResult result = agent.execute(task, context.getAgentContext());
        context.recordResult(stepId, result);
        
        return new StepResult(stepId, result.success(), result.output());
    }
}

// ===== 并行步骤 =====
public class ParallelStep implements WorkflowStep {
    private final String stepId;
    private final List<WorkflowStep> parallelSteps;
    private final ResultAggregator aggregator;
    
    @Override
    public StepResult execute(TeamContext context) {
        // 并行执行所有子步骤
        List<CompletableFuture<StepResult>> futures = parallelSteps.stream()
            .map(step -> CompletableFuture.supplyAsync(() -> step.execute(context)))
            .toList();
        
        // 等待所有完成
        List<StepResult> results = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        
        // 聚合结果
        return aggregator.aggregate(results);
    }
}

// ===== 条件步骤 =====
public class ConditionalStep implements WorkflowStep {
    private final String stepId;
    private final Predicate<TeamContext> condition;
    private final WorkflowStep trueStep;
    private final WorkflowStep falseStep;
    
    @Override
    public StepResult execute(TeamContext context) {
        if (condition.test(context)) {
            return trueStep.execute(context);
        } else if (falseStep != null) {
            return falseStep.execute(context);
        }
        return StepResult.skipped(stepId);
    }
}

// ===== Agent 通信总线 =====
public class AgentCommunicationBus {
    private final Map<String, List<AgentMessage>> messageQueues;
    
    /**
     * 发送消息给指定 Agent
     */
    public void send(String fromAgent, String toAgent, String message) {
        messageQueues.computeIfAbsent(toAgent, k -> new ArrayList<>())
            .add(new AgentMessage(fromAgent, toAgent, message, LocalDateTime.now()));
    }
    
    /**
     * 广播消息给所有 Agent
     */
    public void broadcast(String fromAgent, String message) {
        messageQueues.keySet().forEach(agentId -> 
            send(fromAgent, agentId, message));
    }
    
    /**
     * 接收消息
     */
    public List<AgentMessage> receive(String agentId) {
        return messageQueues.getOrDefault(agentId, List.of());
    }
}

// ===== Agent 消息 =====
public record AgentMessage(
    String from,
    String to,
    String content,
    LocalDateTime timestamp
) {}
```

### 5.3 预定义 Agent 模板

```java
// ===== 研究员 Agent =====
@Component
public class ResearcherAgent extends Agent {
    
    public ResearcherAgent(ChatModel chatModel, ToolRegistry toolRegistry) {
        super(
            "researcher-001",
            "研究员",
            "负责收集和整理信息，使用搜索工具查找相关资料",
            """
                你是一个专业的研究员。你的职责是：
                1. 使用搜索工具收集相关信息
                2. 整理和归纳找到的资料
                3. 提供结构化的研究结果
                
                输出格式要求：
                - 关键发现
                - 信息来源
                - 相关性评估
                """,
            List.of("web_search", "document_search"),
            List.of("信息收集", "数据分析", "资料整理"),
            chatModel,
            toolRegistry
        );
    }
}

// ===== 分析师 Agent =====
@Component
public class AnalystAgent extends Agent {
    
    public AnalystAgent(ChatModel chatModel) {
        super(
            "analyst-001",
            "分析师",
            "负责分析数据，提供洞察和建议",
            """
                你是一个专业的分析师。你的职责是：
                1. 分析提供的数据和信息
                2. 识别模式和趋势
                3. 提供可行的建议
                
                分析框架：
                - SWOT 分析
                - 数据可视化建议
                - 风险评估
                """,
            List.of("calculate", "json_parser"),
            List.of("数据分析", "趋势预测", "风险评估"),
            chatModel,
            null
        );
    }
}

// ===== 写作员 Agent =====
@Component
public class WriterAgent extends Agent {
    
    public WriterAgent(ChatModel chatModel) {
        super(
            "writer-001",
            "写作员",
            "负责撰写报告、文章等文本内容",
            """
                你是一个专业的写作员。你的职责是：
                1. 根据提供的材料撰写文章
                2. 确保内容准确、流畅、易读
                3. 使用合适的格式和结构
                
                写作规范：
                - 使用 Markdown 格式
                - 分点阐述
                - 适当使用表格和图表
                """,
            List.of(),
            List.of("文案写作", "报告生成", "内容编辑"),
            chatModel,
            null
        );
    }
}
```

### 5.4 工作流示例

```java
// ===== 构建研究报告工作流 =====
public Workflow buildResearchReportWorkflow() {
    return WorkflowBuilder.create()
        // Step 1: 研究员收集信息
        .sequential("researcher-001", "请收集关于 ${topic} 的相关信息")
        
        // Step 2: 分析师分析数据（并行）
        .parallel(p -> p
            .step("analyst-001", "分析研究数据的趋势")
            .step("analyst-002", "评估风险和机会")
        )
        
        // Step 3: 条件分支
        .conditional(ctx -> ctx.getVariable("hasData", Boolean.class),
            // 有数据：写作员生成报告
            WorkflowBuilder.step("writer-001", "根据研究结果撰写报告"),
            // 无数据：返回提示
            WorkflowBuilder.step("researcher-001", "请补充更多数据")
        )
        
        // Step 4: 最终输出
        .sequential("writer-001", "润色和完善最终报告")
        .build();
}
```

### 5.5 实现文件清单

| 文件 | 说明 |
|------|------|
| `agent/Agent.java` | Agent 基类 |
| `agent/AgentResult.java` | Agent 执行结果 |
| `agent/AgentContext.java` | Agent 上下文 |
| `agent/AgentTeam.java` | Agent 团队 |
| `agent/AgentCommunicationBus.java` | 通信总线 |
| `agent/AgentMessage.java` | Agent 消息 |
| `agent/workflow/Workflow.java` | 工作流接口 |
| `agent/workflow/WorkflowStep.java` | 工作流步骤接口 |
| `agent/workflow/SequentialStep.java` | 顺序步骤 |
| `agent/workflow/ParallelStep.java` | 并行步骤 |
| `agent/workflow/ConditionalStep.java` | 条件步骤 |
| `agent/workflow/WorkflowBuilder.java` | 工作流构建器 |
| `agent/agents/ResearcherAgent.java` | 研究员 Agent |
| `agent/agents/AnalystAgent.java` | 分析师 Agent |
| `agent/agents/WriterAgent.java` | 写作员 Agent |
| `agent/agents/CoderAgent.java` | 程序员 Agent |
| `controller/AgentController.java` | Agent REST API |
| `config/AgentConfig.java` | Agent 配置 |

### 5.6 API 接口

```
POST /api/agents/execute              # 执行单个 Agent 任务
POST /api/teams/{teamId}/execute      # 执行团队任务
GET  /api/agents                      # Agent 列表
GET  /api/teams                       # 团队列表
POST /api/teams                       # 创建团队
```

### 5.7 实现步骤

1. **Step 1**: 创建 `agent` 包和基础类
2. **Step 2**: 实现 `Agent` 基类
3. **Step 3**: 实现工作流相关类
4. **Step 4**: 实现 `AgentTeam`
5. **Step 5**: 实现通信总线
6. **Step 6**: 创建预定义 Agent
7. **Step 7**: 实现 `WorkflowBuilder`
8. **Step 8**: 实现 `AgentController`
9. **Step 9**: 编写测试用例

---

## 7. Prompt 工程

### 7.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                   PromptTemplateManager                      │
│  - loadTemplate(name) -> PromptTemplate                      │
│  - render(name, variables) -> String                         │
│  - saveTemplate(name, template)                              │
│  - listTemplates() -> List<TemplateInfo>                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    PromptTemplate                            │
│  - name: String                                              │
│  - version: String                                           │
│  - template: String                                          │
│  - variables: List<TemplateVariable>                         │
│  - metadata: PromptMetadata                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    PromptRenderer                            │
│  - render(template, variables) -> String                     │
│  - validate(template, variables) -> ValidationResult         │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 核心接口设计

```java
// ===== Prompt 模板 =====
public record PromptTemplate(
    String name,
    String version,
    String description,
    String template,               // 模板内容
    List<TemplateVariable> variables,
    List<FewShotExample> examples,
    PromptMetadata metadata
) {
    /**
     * 渲染模板
     */
    public String render(Map<String, Object> variables) {
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", 
                String.valueOf(entry.getValue()));
        }
        return result;
    }
}

// ===== 模板变量 =====
public record TemplateVariable(
    String name,
    String description,
    boolean required,
    String defaultValue,
    String type            // string, number, boolean, list
) {}

// ===== Few-shot 示例 =====
public record FewShotExample(
    Map<String, Object> input,
    String output,
    String explanation
) {}

// ===== 模板元数据 =====
public record PromptMetadata(
    String author,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    List<String> tags,
    String modelRecommendation,   // 推荐使用的模型
    int maxTokens,
    double temperature
) {}

// ===== 模板管理器 =====
public interface PromptTemplateManager {
    /**
     * 加载模板
     */
    Optional<PromptTemplate> loadTemplate(String name);
    
    /**
     * 加载指定版本
     */
    Optional<PromptTemplate> loadTemplate(String name, String version);
    
    /**
     * 渲染模板
     */
    String render(String templateName, Map<String, Object> variables);
    
    /**
     * 保存模板
     */
    void saveTemplate(PromptTemplate template);
    
    /**
     * 列出所有模板
     */
    List<TemplateInfo> listTemplates();
    
    /**
     * 删除模板
     */
    void deleteTemplate(String name);
}

// ===== 文件系统实现 =====
@Component
public class FileSystemPromptTemplateManager implements PromptTemplateManager {
    
    private final String templatesPath;
    private final ObjectMapper objectMapper;
    
    @Override
    public Optional<PromptTemplate> loadTemplate(String name) {
        Path templatePath = Path.of(templatesPath, name + ".yaml");
        if (!Files.exists(templatePath)) {
            return Optional.empty();
        }
        
        try {
            String content = Files.readString(templatePath);
            PromptTemplate template = parseYaml(content);
            return Optional.of(template);
        } catch (IOException e) {
            throw new RuntimeException("加载模板失败: " + name, e);
        }
    }
    
    @Override
    public String render(String templateName, Map<String, Object> variables) {
        PromptTemplate template = loadTemplate(templateName)
            .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateName));
        
        // 验证必填变量
        validateVariables(template, variables);
        
        return template.render(variables);
    }
}
```

### 6.3 YAML 模板文件格式

```yaml
# prompts/chat-assistant.yaml
name: chat-assistant
version: "1.0"
description: 通用聊天助手系统提示词

template: |
  你是一个${role}，专门帮助${audience}。
  
  ## 输出格式要求
  ${outputFormat}
  
  ## 回答风格
  - 使用 ${style} 格式
  - 分点阐述，条理清晰
  - 提供可运行的代码示例
  
  ## 注意事项
  ${constraints}
  
  ${#if examples}
  ## 示例
  ${#each examples}
  用户：${this.input}
  助手：${this.output}
  ${/each}
  ${/if}

variables:
  - name: role
    description: AI 扮演的角色
    required: true
    type: string
    
  - name: audience
    description: 目标用户群体
    required: true
    type: string
    default: "用户"
    
  - name: style
    description: 输出风格
    required: false
    type: string
    default: "Markdown"
    
  - name: outputFormat
    description: 输出格式要求
    required: false
    type: string
    
  - name: constraints
    description: 约束条件
    required: false
    type: string

examples:
  - input: "什么是 Java Agent？"
    output: |
      <thinking>
      用户问的是 Java Agent 概念...
      </thinking>
      
      Java Agent 是一种特殊的 Java 程序...
    explanation: 展示标准回答格式

metadata:
  author: "AI Team"
  tags: ["chat", "assistant", "general"]
  modelRecommendation: "qwen-plus"
  maxTokens: 4000
  temperature: 0.7
```

### 6.4 预定义模板清单

| 模板名称 | 用途 |
|----------|------|
| `chat-assistant` | 通用聊天助手 |
| `react-agent` | ReAct Agent 系统提示词 |
| `planner` | 任务规划器提示词 |
| `summarizer` | 文本摘要提示词 |
| `coder` | 代码生成提示词 |
| `translator` | 翻译提示词 |
| `qa-with-context` | 带上下文的问答提示词 |

### 6.5 目录结构

```
src/main/resources/prompts/
├── chat-assistant.yaml
├── react-agent.yaml
├── planner.yaml
├── summarizer.yaml
├── coder.yaml
├── translator.yaml
└── rag/
    ├── qa-with-context.yaml
    └── document-summarizer.yaml
```

### 6.6 实现文件清单

| 文件 | 说明 |
|------|------|
| `prompt/PromptTemplate.java` | 模板实体 |
| `prompt/TemplateVariable.java` | 变量定义 |
| `prompt/FewShotExample.java` | Few-shot 示例 |
| `prompt/PromptMetadata.java` | 模板元数据 |
| `prompt/PromptTemplateManager.java` | 模板管理器接口 |
| `prompt/FileSystemPromptTemplateManager.java` | 文件系统实现 |
| `prompt/PromptRenderer.java` | 模板渲染器 |
| `config/PromptConfig.java` | Prompt 配置 |

### 6.7 实现步骤

1. **Step 1**: 创建 `prompt` 包和基础实体
2. **Step 2**: 定义 YAML 模板格式
3. **Step 3**: 创建预定义模板文件
4. **Step 4**: 实现 `PromptTemplateManager`
5. **Step 5**: 实现变量验证逻辑
6. **Step 6**: 实现 Few-shot 示例支持
7. **Step 7**: 重构 `ChatAssistant` 使用模板
8. **Step 8**: 编写测试用例

---

## 8. 结构化输出

### 8.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                 StructuredOutputService                      │
│  - generate(prompt, schema) -> T                             │
│  - generateWithRetry(prompt, schema, maxRetries) -> T        │
│  - validate(output, schema) -> ValidationResult              │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    OutputSchema                              │
│  - name: String                                              │
│  - description: String                                       │
│  - fields: List<SchemaField>                                 │
│  - toJsonSchema() -> String                                  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    OutputValidator                           │
│  - validate(json, schema) -> ValidationResult                │
│  - fix(json, schema) -> String                               │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 核心接口设计

```java
// ===== 输出 Schema =====
public class OutputSchema {
    private String name;
    private String description;
    private List<SchemaField> fields;
    
    /**
     * 转换为 JSON Schema
     */
    public String toJsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", description);
        
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        
        for (SchemaField field : fields) {
            properties.put(field.name(), field.toJsonSchemaProperty());
            if (field.required()) {
                required.add(field.name());
            }
        }
        
        schema.put("properties", properties);
        schema.put("required", required);
        
        return objectMapper.writeValueAsString(schema);
    }
    
    /**
     * 从 POJO 类生成 Schema
     */
    public static OutputSchema fromClass(Class<?> clazz) {
        // 解析类注解和字段生成 Schema
    }
}

// ===== Schema 字段 =====
public record SchemaField(
    String name,
    String description,
    FieldType type,
    boolean required,
    List<String> enumValues,
    SchemaField[] nestedFields    // 嵌套对象
) {
    public enum FieldType {
        STRING, INTEGER, NUMBER, BOOLEAN, ARRAY, OBJECT
    }
    
    public Object toJsonSchemaProperty() {
        Map<String, Object> prop = new LinkedHashMap<>();
        prop.put("type", type.name().toLowerCase());
        prop.put("description", description);
        
        if (enumValues != null && !enumValues.isEmpty()) {
            prop.put("enum", enumValues);
        }
        
        if (type == FieldType.ARRAY && nestedFields != null) {
            prop.put("items", nestedFields[0].toJsonSchemaProperty());
        }
        
        if (type == FieldType.OBJECT && nestedFields != null) {
            Map<String, Object> nestedProps = new LinkedHashMap<>();
            for (SchemaField nested : nestedFields) {
                nestedProps.put(nested.name(), nested.toJsonSchemaProperty());
            }
            prop.put("properties", nestedProps);
        }
        
        return prop;
    }
}

// ===== 结构化输出服务 =====
public interface StructuredOutputService {
    /**
     * 生成结构化输出
     */
    <T> T generate(String prompt, Class<T> outputClass);
    
    /**
     * 生成结构化输出（带 Schema）
     */
    <T> T generate(String prompt, OutputSchema schema, Class<T> outputClass);
    
    /**
     * 生成并验证（失败重试）
     */
    <T> T generateWithRetry(String prompt, Class<T> outputClass, int maxRetries);
    
    /**
     * 批量生成
     */
    <T> List<T> generateBatch(String prompt, Class<T> outputClass, int count);
}

// ===== LLM 实现 =====
@Service
public class LLMStructuredOutputService implements StructuredOutputService {
    
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    
    @Override
    public <T> T generate(String prompt, Class<T> outputClass) {
        // 1. 生成 Schema
        OutputSchema schema = OutputSchema.fromClass(outputClass);
        
        // 2. 构建提示词
        String fullPrompt = """
            %s
            
            请严格按照以下 JSON Schema 格式输出，不要包含其他内容：
            
            ```json
            %s
            ```
            
            只输出 JSON，不要包含解释或 Markdown 代码块标记。
            """.formatted(prompt, schema.toJsonSchema());
        
        // 3. 调用 LLM
        String response = chatModel.chat(fullPrompt);
        
        // 4. 解析 JSON
        String json = extractJson(response);
        
        // 5. 验证并转换
        return objectMapper.readValue(json, outputClass);
    }
    
    @Override
    public <T> T generateWithRetry(String prompt, Class<T> outputClass, int maxRetries) {
        Exception lastError = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                return generate(prompt, outputClass);
            } catch (Exception e) {
                lastError = e;
                // 在提示词中加入错误信息帮助修正
                prompt = prompt + "\n\n上次输出格式错误: " + e.getMessage() + 
                    "\n请修正后重新输出。";
            }
        }
        
        throw new StructuredOutputException("结构化输出失败", lastError);
    }
    
    private String extractJson(String response) {
        // 移除 Markdown 代码块标记
        String json = response.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        } else if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }
}

// ===== 输出验证器 =====
@Component
public class OutputValidator {
    
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    
    /**
     * 验证 JSON 是否符合 Schema
     */
    public ValidationResult validate(String json, String schema) {
        JsonSchema jsonSchema = schemaFactory.getSchema(schema);
        Set<ValidationMessage> errors = jsonSchema.validate(
            objectMapper.readTree(json), 
            InputFormat.JSON
        );
        
        return new ValidationResult(
            errors.isEmpty(),
            errors.stream().map(ValidationMessage::getMessage).toList()
        );
    }
}

// ===== 验证结果 =====
public record ValidationResult(
    boolean valid,
    List<String> errors
) {}
```

### 7.3 注解驱动的 Schema 定义

```java
// ===== Schema 注解 =====
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OutputSchema {
    String description() default "";
}

// ===== 字段注解 =====
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SchemaField {
    String description() default "";
    boolean required() default true;
    String[] enumValues() default {};
}

// ===== 使用示例 =====
@OutputSchema(description = "用户信息摘要")
public record UserSummary(
    @SchemaField(description = "用户姓名")
    String name,
    
    @SchemaField(description = "用户年龄")
    Integer age,
    
    @SchemaField(description = "用户职业", enumValues = {"工程师", "设计师", "产品经理", "其他"})
    String occupation,
    
    @SchemaField(description = "技能列表", required = false)
    List<String> skills,
    
    @SchemaField(description = "联系方式", required = false)
    ContactInfo contact
) {}

@OutputSchema(description = "联系方式")
public record ContactInfo(
    @SchemaField(description = "邮箱")
    String email,
    
    @SchemaField(description = "电话", required = false)
    String phone
) {}

// ===== 使用方式 =====
@Service
public class UserService {
    
    private final StructuredOutputService outputService;
    
    public UserSummary extractUserInfo(String text) {
        String prompt = """
            从以下文本中提取用户信息：
            
            %s
            """.formatted(text);
        
        return outputService.generateWithRetry(prompt, UserSummary.class, 3);
    }
}
```

### 7.4 实现文件清单

| 文件 | 说明 |
|------|------|
| `output/OutputSchema.java` | Schema 定义 |
| `output/SchemaField.java` | 字段定义 |
| `output/FieldType.java` | 字段类型枚举 |
| `output/StructuredOutputService.java` | 结构化输出服务接口 |
| `output/LLMStructuredOutputService.java` | LLM 实现 |
| `output/OutputValidator.java` | 输出验证器 |
| `output/ValidationResult.java` | 验证结果 |
| `output/StructuredOutputException.java` | 结构化输出异常 |
| `output/OutputSchema.java` | Schema 注解 |
| `output/SchemaField.java` | 字段注解 |
| `config/StructuredOutputConfig.java` | 配置类 |

### 7.5 依赖添加

```xml
<!-- JSON Schema Validator -->
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.4.0</version>
</dependency>
```

### 7.6 实现步骤

1. **Step 1**: 创建 `output` 包和基础类
2. **Step 2**: 实现 `OutputSchema` 和 JSON Schema 转换
3. **Step 3**: 创建注解 `@OutputSchema` 和 `@SchemaField`
4. **Step 4**: 实现 `StructuredOutputService`
5. **Step 5**: 实现 `OutputValidator`
6. **Step 6**: 添加重试逻辑
7. **Step 7**: 编写测试用例

---

## 9. 成本控制

> **核心目标**：监控和控制 LLM API 调用成本，防止费用失控

### 9.1 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                    CostMonitor                               │
│  - 记录每次调用的 Token 消耗                                 │
│  - 实时计算成本                                              │
│  - 超限告警与熔断                                            │
└─────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ 请求级限流      │ │ 用户级限流      │ │ 系统级限流      │
│ (单次Token上限) │ │ (日/月额度)     │ │ (总预算控制)    │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### 9.2 核心接口设计

```java
// ===== Token 使用记录 =====
@Entity
@Table(name = "token_usage_logs")
public class TokenUsageLog {
    @Id
    @GeneratedValue
    private Long id;
    
    private String userId;
    private String sessionId;
    private String modelName;
    
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    
    private double cost;              // 美元
    private String currency;          // USD, CNY
    
    private String requestType;       // CHAT, EMBEDDING, RAG
    private LocalDateTime createdAt;
}

// ===== 成本配置 =====
@ConfigurationProperties(prefix = "app.cost")
public class CostProperties {
    
    // 单次请求上限
    private int maxTokensPerRequest = 8000;
    
    // 单用户日上限
    private int maxTokensPerUserPerDay = 100000;
    
    // 单用户月上限
    private int maxTokensPerUserPerMonth = 2000000;
    
    // 系统日预算
    private double systemDailyBudget = 100.0;  // 美元
    
    // 系统月预算
    private double systemMonthlyBudget = 2000.0;  // 美元
    
    // 告警阈值（预算百分比）
    private double alertThreshold = 0.8;  // 80%
    
    // 模型定价（每 1K tokens）
    private Map<String, Double> modelPricing = Map.of(
        "qwen-plus", 0.004,        // $0.004/1K tokens
        "qwen-max", 0.02,
        "deepseek-chat", 0.001,
        "glm-4", 0.014
    );
}

// ===== 成本计算服务 =====
@Service
public class CostCalculationService {
    
    private final CostProperties properties;
    
    /**
     * 计算成本
     */
    public double calculateCost(String modelName, int promptTokens, int completionTokens) {
        Double pricePerK = properties.getModelPricing().get(modelName);
        if (pricePerK == null) {
            pricePerK = 0.01;  // 默认价格
        }
        
        // 通常 completion tokens 价格是 prompt 的 3 倍
        double promptCost = (promptTokens / 1000.0) * pricePerK;
        double completionCost = (completionTokens / 1000.0) * pricePerK * 3;
        
        return promptCost + completionCost;
    }
}

// ===== 成本监控服务 =====
@Service
public class CostMonitorService {
    
    private final TokenUsageLogRepository logRepository;
    private final CostProperties properties;
    private final MeterRegistry meterRegistry;
    
    /**
     * 记录 Token 使用
     */
    public void recordUsage(String userId, String sessionId, String modelName,
                           int promptTokens, int completionTokens, String requestType) {
        double cost = costCalculationService.calculateCost(modelName, promptTokens, completionTokens);
        
        TokenUsageLog log = new TokenUsageLog();
        log.setUserId(userId);
        log.setSessionId(sessionId);
        log.setModelName(modelName);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(promptTokens + completionTokens);
        log.setCost(cost);
        log.setCurrency("USD");
        log.setRequestType(requestType);
        log.setCreatedAt(LocalDateTime.now());
        
        logRepository.save(log);
        
        // 更新 Prometheus 指标
        meterRegistry.counter("llm.tokens.total", 
            "model", modelName, "user", userId).increment(promptTokens + completionTokens);
        meterRegistry.counter("llm.cost.total", 
            "model", modelName).increment(cost);
    }
    
    /**
     * 检查用户是否超出日限额
     */
    public boolean isUserOverDailyLimit(String userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        int todayUsage = logRepository.sumTokensByUserSince(userId, startOfDay);
        return todayUsage >= properties.getMaxTokensPerUserPerDay();
    }
    
    /**
     * 检查系统是否超出日预算
     */
    public boolean isSystemOverDailyBudget() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        double todayCost = logRepository.sumCostSince(startOfDay);
        return todayCost >= properties.getSystemDailyBudget();
    }
    
    /**
     * 获取用户今日使用统计
     */
    public UsageStats getUserDailyStats(String userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        
        List<TokenUsageLog> logs = logRepository.findByUserSince(userId, startOfDay);
        
        return new UsageStats(
            logs.stream().mapToInt(TokenUsageLog::getTotalTokens).sum(),
            logs.stream().mapToDouble(TokenUsageLog::getCost).sum(),
            logs.size(),
            properties.getMaxTokensPerUserPerDay(),
            properties.getMaxTokensPerUserPerDay() * properties.getAlertThreshold()
        );
    }
    
    /**
     * 获取系统今日统计
     */
    public SystemStats getSystemDailyStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        
        List<TokenUsageLog> logs = logRepository.findAllSince(startOfDay);
        
        double totalCost = logs.stream().mapToDouble(TokenUsageLog::getCost).sum();
        
        return new SystemStats(
            logs.stream().mapToInt(TokenUsageLog::getTotalTokens).sum(),
            totalCost,
            logs.size(),
            properties.getSystemDailyBudget(),
            totalCost >= properties.getSystemDailyBudget() * properties.getAlertThreshold()
        );
    }
}

// ===== 使用统计 =====
public record UsageStats(
    int tokensUsed,
    double costUsed,
    int requestCount,
    int dailyLimit,
    int alertThreshold
) {
    public double usagePercentage() {
        return (double) tokensUsed / dailyLimit * 100;
    }
    
    public boolean isNearLimit() {
        return tokensUsed >= alertThreshold;
    }
}

// ===== 成本告警服务 =====
@Service
public class CostAlertService {
    
    private final CostMonitorService monitorService;
    
    @Scheduled(cron = "0 0 * * * ?")  // 每小时检查
    public void checkAndAlert() {
        SystemStats stats = monitorService.getSystemDailyStats();
        
        if (stats.isNearLimit()) {
            // 发送告警（邮件/钉钉/Slack）
            sendAlert(String.format("系统日预算使用已达 %.1f%%，请关注！", 
                stats.costUsed() / stats.dailyBudget() * 100));
        }
        
        if (stats.costUsed() >= stats.dailyBudget()) {
            // 超出预算，触发熔断
            log.warn("系统日预算已耗尽，建议暂停服务或增加预算");
        }
    }
}
```

### 9.3 请求拦截器

```java
// ===== 成本拦截器 =====
@Aspect
@Component
public class CostInterceptor {
    
    private final CostMonitorService monitorService;
    private final CostProperties properties;
    
    @Around("execution(* com.jonychen.service.AiService.chat(..))")
    public Object interceptChat(ProceedingJoinPoint pjp) throws Throwable {
        String userId = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        
        // 1. 检查用户限额
        if (monitorService.isUserOverDailyLimit(userId)) {
            throw new BusinessException(ErrorCode.USER_QUOTA_EXCEEDED, 
                "您今日的 API 调用额度已用完，请明天再试");
        }
        
        // 2. 检查系统预算
        if (monitorService.isSystemOverDailyBudget()) {
            throw new BusinessException(ErrorCode.SYSTEM_BUDGET_EXCEEDED,
                "系统今日预算已用完，请稍后再试");
        }
        
        // 3. 执行请求
        long startTime = System.currentTimeMillis();
        Object result = pjp.proceed();
        
        // 4. 记录使用情况（需要从响应中提取 token 数量）
        // ...
        
        return result;
    }
}
```

### 9.4 数据库设计

```sql
-- Token 使用记录表
CREATE TABLE token_usage_logs (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64),
    model_name VARCHAR(50) NOT NULL,
    prompt_tokens INT NOT NULL,
    completion_tokens INT NOT NULL,
    total_tokens INT NOT NULL,
    cost DECIMAL(10, 6) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    request_type VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usage_user_date ON token_usage_logs(user_id, created_at);
CREATE INDEX idx_usage_date ON token_usage_logs(created_at);

-- 用户配额表（可选，用于个性化配额）
CREATE TABLE user_quotas (
    user_id VARCHAR(64) PRIMARY KEY,
    daily_token_limit INT DEFAULT 100000,
    monthly_token_limit INT DEFAULT 2000000,
    daily_cost_limit DECIMAL(10, 2) DEFAULT 10.00,
    monthly_cost_limit DECIMAL(10, 2) DEFAULT 200.00,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 9.5 API 接口

```
GET  /api/cost/stats           # 获取当前用户使用统计
GET  /api/cost/stats/system    # 获取系统统计（管理员）
GET  /api/cost/history         # 获取历史使用记录
POST /api/cost/quota           # 设置用户配额（管理员）
```

### 9.6 配置示例

```properties
# application.properties

# ===== 成本控制 =====
# 单次请求上限
app.cost.max-tokens-per-request=8000

# 用户限额
app.cost.max-tokens-per-user-per-day=100000
app.cost.max-tokens-per-user-per-month=2000000

# 系统预算（美元）
app.cost.system-daily-budget=100.0
app.cost.system-monthly-budget=2000.0

# 告警阈值
app.cost.alert-threshold=0.8

# 模型定价（美元/1K tokens）
app.cost.model-pricing.qwen-plus=0.004
app.cost.model-pricing.qwen-max=0.02
app.cost.model-pricing.deepseek-chat=0.001
app.cost.model-pricing.glm-4=0.014
```

### 9.7 实现文件清单

| 文件 | 说明 |
|------|------|
| `cost/TokenUsageLog.java` | Token 使用记录实体 |
| `cost/CostProperties.java` | 成本配置属性 |
| `cost/CostCalculationService.java` | 成本计算服务 |
| `cost/CostMonitorService.java` | 成本监控服务 |
| `cost/CostAlertService.java` | 成本告警服务 |
| `cost/CostInterceptor.java` | 成本拦截器 |
| `cost/UsageStats.java` | 使用统计 record |
| `cost/SystemStats.java` | 系统统计 record |
| `controller/CostController.java` | 成本 REST API |

---

## 10. 工程化增强

### 10.1 测试体系

#### 8.1.1 Mock LLM

```java
// ===== Mock LLM 实现 =====
@Component
@Profile("test")
public class MockChatModel implements ChatModel {
    
    private final Map<String, String> responseMappings;
    private final List<ChatRequest> requestHistory;
    
    public MockChatModel() {
        this.responseMappings = new HashMap<>();
        this.requestHistory = new ArrayList<>();
        
        // 预设响应
        responseMappings.put("你好", "你好！有什么可以帮助你的？");
        responseMappings.put("计算", "结果是：42");
    }
    
    @Override
    public ChatResponse chat(ChatRequest request) {
        requestHistory.add(request);
        
        String userMessage = extractUserMessage(request);
        String response = responseMappings.entrySet().stream()
            .filter(e -> userMessage.contains(e.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse("这是一个模拟响应");
        
        return new ChatResponse(new AiMessage(response));
    }
    
    /**
     * 设置自定义响应
     */
    public void setResponse(String keyword, String response) {
        responseMappings.put(keyword, response);
    }
    
    /**
     * 获取请求历史
     */
    public List<ChatRequest> getRequestHistory() {
        return Collections.unmodifiableList(requestHistory);
    }
    
    /**
     * 重置状态
     */
    public void reset() {
        requestHistory.clear();
    }
}
```

#### 8.1.2 测试基类

```java
// ===== 集成测试基类 =====
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(OrderAnnotation.class)
public abstract class BaseIntegrationTest {
    
    @LocalServerPort
    protected int port;
    
    @Autowired
    protected MockMvc mockMvc;
    
    @Autowired
    protected MockChatModel mockChatModel;
    
    protected String baseUrl() {
        return "http://localhost:" + port;
    }
    
    @BeforeEach
    void setUp() {
        mockChatModel.reset();
    }
}
```

#### 8.1.3 测试清单

| 测试类 | 测试内容 |
|--------|----------|
| `SessionManagerTest` | 会话管理器单元测试 |
| `ToolRegistryTest` | 工具注册中心测试 |
| `ReActAgentTest` | ReAct Agent 测试 |
| `RAGPipelineTest` | RAG 流程测试 |
| `AgentTeamTest` | Agent 团队测试 |
| `StructuredOutputTest` | 结构化输出测试 |
| `ChatControllerIntegrationTest` | 聊天接口集成测试 |
| `EndToEndTest` | 端到端测试 |

### 8.2 可观测性增强

#### 8.2.1 对话日志存储

```sql
-- 对话日志表
CREATE TABLE conversation_logs (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(64),
    user_id VARCHAR(64),
    role VARCHAR(20) NOT NULL,        -- user/assistant/system
    content TEXT NOT NULL,
    tokens_used INT,
    model_name VARCHAR(50),
    latency_ms BIGINT,
    tool_calls JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_logs_session ON conversation_logs(session_id);
CREATE INDEX idx_logs_user ON conversation_logs(user_id);
CREATE INDEX idx_logs_created ON conversation_logs(created_at);
```

#### 8.2.2 Agent 执行链路

```java
// ===== Agent 执行追踪 =====
@Aspect
@Component
public class AgentExecutionTracer {
    
    private final MeterRegistry meterRegistry;
    private final AgentExecutionLogRepository logRepository;
    
    @Around("@annotation(AgentExecution)")
    public Object traceAgentExecution(ProceedingJoinPoint pjp) throws Throwable {
        String agentName = extractAgentName(pjp);
        long startTime = System.currentTimeMillis();
        
        // 记录开始
        AgentExecutionLog log = new AgentExecutionLog();
        log.setAgentName(agentName);
        log.setStartTime(LocalDateTime.now());
        log.setInput(extractInput(pjp));
        
        try {
            Object result = pjp.proceed();
            
            // 记录成功
            log.setStatus("SUCCESS");
            log.setOutput(result);
            log.setDurationMs(System.currentTimeMillis() - startTime);
            
            // 更新指标
            meterRegistry.counter("agent.execution.total", 
                "agent", agentName, "status", "success").increment();
            meterRegistry.timer("agent.execution.duration", 
                "agent", agentName).record(Duration.ofMillis(log.getDurationMs()));
            
            return result;
        } catch (Exception e) {
            // 记录失败
            log.setStatus("FAILED");
            log.setError(e.getMessage());
            log.setDurationMs(System.currentTimeMillis() - startTime);
            
            meterRegistry.counter("agent.execution.total", 
                "agent", agentName, "status", "failed").increment();
            
            throw e;
        } finally {
            logRepository.save(log);
        }
    }
}

// ===== 执行日志实体 =====
@Entity
public class AgentExecutionLog {
    @Id
    @GeneratedValue
    private Long id;
    private String agentName;
    private String sessionId;
    private LocalDateTime startTime;
    private Long durationMs;
    private String status;
    
    @Column(columnDefinition = "TEXT")
    private String input;
    
    @Column(columnDefinition = "TEXT")
    private String output;
    
    private String error;
}
```

### 8.3 安全增强

#### 8.3.1 API Key 加密

```java
// ===== API Key 加密服务 =====
@Service
public class ApiKeyEncryptionService {
    
    private final String encryptionKey;
    private final AESCipher cipher;
    
    public String encrypt(String plainApiKey) {
        return cipher.encrypt(plainApiKey, encryptionKey);
    }
    
    public String decrypt(String encryptedApiKey) {
        return cipher.decrypt(encryptedApiKey, encryptionKey);
    }
}

// ===== 安全配置 =====
@Configuration
public class SecurityConfig {
    
    @Bean
    public ApiKeyEncryptionService apiKeyEncryptionService(
            @Value("${app.encryption.key}") String key) {
        return new ApiKeyEncryptionService(key);
    }
}
```

#### 8.3.2 敏感词过滤

```java
// ===== 敏感词过滤器 =====
@Service
public class SensitiveWordFilter {
    
    private final DFAFilter dfaFilter;
    
    public SensitiveWordFilter() {
        this.dfaFilter = new DFAFilter();
        // 加载敏感词库
        loadSensitiveWords();
    }
    
    /**
     * 检查是否包含敏感词
     */
    public boolean containsSensitiveWord(String text) {
        return dfaFilter.contains(text);
    }
    
    /**
     * 过滤敏感词
     */
    public String filter(String text, char replacement) {
        return dfaFilter.filter(text, replacement);
    }
    
    /**
     * 提取敏感词列表
     */
    public List<String> extractSensitiveWords(String text) {
        return dfaFilter.extract(text);
    }
}
```

#### 8.3.3 审计日志

```java
// ===== 审计日志切面 =====
@Aspect
@Component
public class AuditLogAspect {
    
    private final AuditLogRepository auditLogRepository;
    
    @AfterReturning(
        pointcut = "execution(* com.jonychen.controller.*.*(..))",
        returning = "result"
    )
    public void auditSuccess(JoinPoint jp, Object result) {
        saveAuditLog(jp, "SUCCESS", null, result);
    }
    
    @AfterThrowing(
        pointcut = "execution(* com.jonychen.controller.*.*(..))",
        throwing = "ex"
    )
    public void auditFailure(JoinPoint jp, Exception ex) {
        saveAuditLog(jp, "FAILED", ex.getMessage(), null);
    }
    
    private void saveAuditLog(JoinPoint jp, String status, String error, Object result) {
        AuditLog log = new AuditLog();
        log.setOperation(jp.getSignature().toShortString());
        log.setUserId(getCurrentUser());
        log.setIpAddress(getClientIp());
        log.setStatus(status);
        log.setError(error);
        log.setTimestamp(LocalDateTime.now());
        
        auditLogRepository.save(log);
    }
}
```

### 8.4 Docker 部署

#### 8.4.1 Dockerfile 优化

```dockerfile
# 多阶段构建
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s \
  CMD curl -f http://localhost:8082/actuator/health || exit 1

# 非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

EXPOSE 8082
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

#### 8.4.2 Kubernetes 配置

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: langchain4j-agent
spec:
  replicas: 3
  selector:
    matchLabels:
      app: langchain4j-agent
  template:
    metadata:
      labels:
        app: langchain4j-agent
    spec:
      containers:
        - name: app
          image: langchain4j-agent:latest
          ports:
            - containerPort: 8082
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: JAVA_OPTS
              value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8082
            initialDelaySeconds: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8082
            initialDelaySeconds: 5
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: langchain4j-agent
spec:
  selector:
    app: langchain4j-agent
  ports:
    - port: 80
      targetPort: 8082
  type: LoadBalancer
```

### 8.5 实现文件清单

| 文件 | 说明 |
|------|------|
| `test/MockChatModel.java` | Mock LLM |
| `test/BaseIntegrationTest.java` | 集成测试基类 |
| `observability/AgentExecutionTracer.java` | Agent 执行追踪 |
| `observability/AgentExecutionLog.java` | 执行日志实体 |
| `security/ApiKeyEncryptionService.java` | API Key 加密 |
| `security/SensitiveWordFilter.java` | 敏感词过滤 |
| `security/AuditLogAspect.java` | 审计日志切面 |
| `docker/Dockerfile` | Docker 镜像 |
| `k8s/deployment.yaml` | K8s 部署配置 |
| `k8s/configmap.yaml` | K8s 配置 |
| `k8s/ingress.yaml` | K8s 入口 |

---

## 实施计划总览

| 阶段 | 模块 | 预计工时 | 依赖 | 关键内容 |
|------|------|----------|------|----------|
| Phase 1 | 记忆系统基础 | 3 天 | 无 | 会话管理、短期记忆 |
| Phase 1 | **记忆治理策略** | 2 天 | 记忆系统基础 | 写入策略、遗忘策略、去重 |
| Phase 1 | Prompt 工程 | 2 天 | 无 | 模板管理、变量插值 |
| Phase 2 | 工具系统基础 | 2 天 | Prompt 工程 | 注册中心、执行器 |
| Phase 2 | **工具安全校验** | 2 天 | 工具系统基础 | 参数校验、幂等性 |
| Phase 2 | **工具弹性执行** | 2 天 | 工具安全校验 | 超时重试、熔断降级 |
| Phase 2 | **工具确认流程** | 1 天 | 工具弹性执行 | 高风险确认、审计 |
| Phase 2 | 结构化输出 | 2 天 | Prompt 工程 | JSON Schema、验证重试 |
| Phase 3 | RAG 知识库 | 4 天 | Embedding 服务 | 文档加载、向量存储 |
| Phase 3 | 任务规划 | 4 天 | 工具系统 | ReAct、Plan-Execute |
| Phase 4 | 多 Agent 协作 | 5 天 | 任务规划、工具系统 | Agent 团队、工作流 |
| Phase 4 | 工程化增强 | 3 天 | 所有模块 | 测试、监控、部署 |

**总计：约 32 个工作日**

> **注意**：新增的治理策略和安全保障机制是工程级 Agent 的核心要求，不可省略。

---

## 决策确认机制

> **重要**：本方案涉及多个技术选型和参数配置决策，实施时将采用**逐一确认机制**：
> 
> 1. 每个模块开始实施前，列出该模块所有待确认决策
> 2. 为每个决策提供**多方案对比**（利弊分析）
> 3. 用户确认选择后，记录决策结果并继续实施
> 4. 决策结果将持久化到项目配置中，确保一致性

### 决策确认流程

```
┌─────────────────────────────────────────────────────────────┐
│                    决策确认流程                              │
├─────────────────────────────────────────────────────────────┤
│  1. 开始实施某模块                                          │
│       ↓                                                     │
│  2. 列出该模块所有待确认决策                                 │
│       ↓                                                     │
│  3. 逐项确认：                                              │
│     - 展示问题背景                                          │
│     - 提供多个可选方案                                      │
│     - 分析各方案利弊                                        │
│     - 用户选择或自定义                                      │
│       ↓                                                     │
│  4. 记录决策结果到配置文件                                  │
│       ↓                                                     │
│  5. 继续实施                                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 待确认决策清单

> 以下决策将在实施时逐一确认，此处仅作为清单预览

### 1. 记忆系统决策

| 决策点 | 问题 | 影响范围 |
|--------|------|----------|
| D-MEM-001 | 记忆存储位置选择（Redis vs 其他） | 架构 |
| D-MEM-002 | 向量数据库选择（pgvector vs Milvus vs Pinecone） | 架构 |
| D-MEM-003 | 记忆容量上限配置 | 性能 |
| D-MEM-004 | 遗忘策略执行时间 | 运维 |
| D-MEM-005 | 记忆价值评估阈值 | 数据质量 |

### 2. 工具系统决策

| 决策点 | 问题 | 影响范围 |
|--------|------|----------|
| D-TOOL-001 | 幂等键 TTL 时长 | 数据一致性 |
| D-TOOL-002 | 高风险工具确认机制（简单确认 vs 多因素验证） | 安全 |
| D-TOOL-003 | 熔断阈值配置 | 稳定性 |
| D-TOOL-004 | 审计日志保留时长 | 合规 |
| D-TOOL-005 | 工具超时时间配置 | 性能 |

### 3. 任务规划决策

| 决策点 | 问题 | 影响范围 |
|--------|------|----------|
| D-PLAN-001 | ReAct 最大迭代次数 | 成本 |
| D-PLAN-002 | 任务失败重规划策略 | 可靠性 |

### 4. RAG 知识库决策

| 决策点 | 问题 | 影响范围 |
|--------|------|----------|
| D-RAG-001 | Embedding 模型选择 | 成本/质量 |
| D-RAG-002 | 文档分块大小配置 | 检索质量 |
| D-RAG-003 | 检索 Top-K 配置 | 响应质量 |

### 5. 多 Agent 协作决策

| 决策点 | 问题 | 影响范围 |
|--------|------|----------|
| D-AGENT-001 | Agent 间通信方式（内存 vs 消息队列） | 架构 |
| D-AGENT-002 | 并行执行线程池大小 | 性能 |

### 6. 工程化决策

| 决策点 | 问题 | 影响范围 |
|--------|------|----------|
| D-ENG-001 | 部署方式（Docker Compose vs K8s） | 运维 |
| D-ENG-002 | 监控方案（Prometheus vs 其他） | 可观测性 |
| D-ENG-003 | 日志收集方案 | 可观测性 |

---

## 决策记录模板

> 每次决策确认后，将按以下模板记录：

```markdown
## 决策记录：{决策编号}

**问题**：{问题描述}

**可选方案**：
| 方案 | 描述 | 优点 | 缺点 |
|------|------|------|------|
| A | ... | ... | ... |
| B | ... | ... | ... |

**用户选择**：方案 X

**选择理由**：{用户提供的理由}

**配置影响**：
- 配置项：xxx = yyy
- 文件：application.properties

**确认时间**：YYYY-MM-DD HH:mm
```

---

**文档版本更新日志**

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| 1.0 | 2026-04-17 | 初始版本 |
| 1.1 | 2026-04-17 | 补充记忆治理策略（写入/遗忘/去重） |
| 1.1 | 2026-04-17 | 补充工具安全与稳定性保障（校验/幂等/熔断/确认） |
| 1.2 | 2026-04-17 | 添加决策确认机制，实施时逐一确认并分析利弊 |

---

## 附录

### A. 依赖清单汇总

```xml
<!-- 记忆系统 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
</dependency>

<!-- RAG 文档处理 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.0</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>

<!-- JSON Schema 验证 -->
<dependency>
    <groupId>com.networknt</groupId>
    <artifactId>json-schema-validator</artifactId>
    <version>1.4.0</version>
</dependency>

<!-- 测试 -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

### B. 配置文件汇总

```properties
# application.properties 新增配置

# ===== 记忆系统 =====
app.memory.session.timeout=24h
app.memory.long-term.enabled=true
app.memory.compressor.enabled=true
app.memory.compressor.threshold=20

# ===== 工具系统 =====
app.tool.timeout=30s
app.tool.retry.max-attempts=2
app.tool.permission.enabled=true

# ===== RAG =====
app.rag.chunk-size=500
app.rag.chunk-overlap=50
app.rag.embedding.model=text-embedding-ada-002

# ===== Agent =====
app.agent.react.max-iterations=10
app.agent.planning.enabled=true
```

### C. 目录结构总览

```
src/main/java/com/jonychen/
├── memory/                          # 记忆系统
│   ├── SessionManager.java
│   ├── RedisSessionManager.java
│   ├── ChatSession.java
│   ├── LongTermMemory.java
│   └── ...
├── tool/                            # 工具系统
│   ├── ToolRegistry.java
│   ├── ToolDefinition.java
│   ├── builtin/
│   └── ...
├── planning/                        # 任务规划
│   ├── Task.java
│   ├── TaskPlanner.java
│   ├── ReActAgent.java
│   └── ...
├── rag/                             # RAG 知识库
│   ├── Document.java
│   ├── DocumentLoader.java
│   ├── VectorStore.java
│   └── ...
├── agent/                           # 多 Agent 协作
│   ├── Agent.java
│   ├── AgentTeam.java
│   ├── workflow/
│   └── ...
├── prompt/                          # Prompt 工程
│   ├── PromptTemplate.java
│   ├── PromptTemplateManager.java
│   └── ...
├── output/                          # 结构化输出
│   ├── OutputSchema.java
│   ├── StructuredOutputService.java
│   └── ...
├── observability/                   # 可观测性
├── security/                        # 安全
└── ...

src/main/resources/
├── prompts/                         # Prompt 模板文件
│   ├── chat-assistant.yaml
│   └── ...
├── db/migration/                    # 数据库迁移
│   ├── V001__session_tables.sql
│   └── ...
└── ...
```

### D. 前端对接规范

#### D.1 SSE 事件类型定义

```typescript
// types/sse-events.ts

/**
 * SSE 事件类型定义
 */
export interface AgentSSEEvents {
  // Token 事件 - 逐字输出
  'token': { 
    content: string;
    index: number;
  };
  
  // 工具调用事件
  'tool_call': { 
    toolId: string;
    toolName: string;
    params: Record<string, unknown>;
    status: 'started' | 'completed' | 'failed';
  };
  
  // 确认请求事件
  'confirmation_required': { 
    confirmationId: string;
    toolName: string;
    message: string;
    riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    params: Record<string, unknown>;
  };
  
  // 思考过程事件
  'thinking': { 
    content: string;
    index: number;
  };
  
  // 任务步骤事件
  'step': {
    stepId: string;
    stepName: string;
    status: 'started' | 'completed' | 'failed';
    output?: unknown;
  };
  
  // 完成事件
  'done': { 
    sessionId: string;
    totalTokens?: number;
    latencyMs?: number;
  };
  
  // 错误事件
  'error': { 
    code: string;
    message: string;
    details?: unknown;
  };
}

/**
 * SSE 消息格式
 */
export interface SSEMessage<T extends keyof AgentSSEEvents> {
  event: T;
  data: AgentSSEEvents[T];
  id?: string;
  retry?: number;
}
```

#### D.2 SSE 客户端实现

```typescript
// api/sse-client.ts

export class AgentSSEClient {
  private eventSource: EventSource | null = null;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 3;
  
  constructor(
    private url: string,
    private handlers: SSEEventHandlers
  ) {}
  
  connect(): void {
    this.eventSource = new EventSource(this.url);
    
    // 连接成功
    this.eventSource.onopen = () => {
      this.reconnectAttempts = 0;
      this.handlers.onConnect?.();
    };
    
    // Token 事件
    this.eventSource.addEventListener('token', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      this.handlers.onToken?.(data.content, data.index);
    });
    
    // 思考过程
    this.eventSource.addEventListener('thinking', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      this.handlers.onThinking?.(data.content, data.index);
    });
    
    // 工具调用
    this.eventSource.addEventListener('tool_call', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      this.handlers.onToolCall?.(data);
    });
    
    // 确认请求
    this.eventSource.addEventListener('confirmation_required', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      this.handlers.onConfirmationRequired?.(data);
    });
    
    // 完成
    this.eventSource.addEventListener('done', (e: MessageEvent) => {
      const data = JSON.parse(e.data);
      this.handlers.onDone?.(data);
      this.disconnect();
    });
    
    // 错误
    this.eventSource.addEventListener('error', (e: MessageEvent) => {
      const data = e.data ? JSON.parse(e.data) : { code: 'CONNECTION_ERROR', message: '连接失败' };
      this.handlers.onError?.(data);
    });
    
    // 连接错误
    this.eventSource.onerror = (e) => {
      if (this.eventSource?.readyState === EventSource.CLOSED) {
        this.reconnect();
      }
    };
  }
  
  private reconnect(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      setTimeout(() => this.connect(), 1000 * this.reconnectAttempts);
    } else {
      this.handlers.onError?.({
        code: 'MAX_RECONNECT_EXCEEDED',
        message: '重连次数超限'
      });
    }
  }
  
  disconnect(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }
}

export interface SSEEventHandlers {
  onConnect?: () => void;
  onToken?: (content: string, index: number) => void;
  onThinking?: (content: string, index: number) => void;
  onToolCall?: (data: AgentSSEEvents['tool_call']) => void;
  onConfirmationRequired?: (data: AgentSSEEvents['confirmation_required']) => void;
  onStep?: (data: AgentSSEEvents['step']) => void;
  onDone?: (data: AgentSSEEvents['done']) => void;
  onError?: (data: AgentSSEEvents['error']) => void;
}
```

#### D.3 确认流程 UI 组件

```vue
<!-- components/ConfirmationDialog.vue -->
<template>
  <el-dialog
    v-model="visible"
    title="操作确认"
    :type="riskLevel === 'CRITICAL' ? 'warning' : 'info'"
    :close-on-click-modal="false"
  >
    <div class="confirmation-content">
      <el-alert
        :title="`风险等级: ${riskLevelText}`"
        :type="riskLevelType"
        show-icon
        class="mb-4"
      />
      
      <div class="tool-info mb-4">
        <p><strong>工具:</strong> {{ toolName }}</p>
        <p class="mt-2"><strong>参数:</strong></p>
        <pre class="params-preview">{{ JSON.stringify(params, null, 2) }}</pre>
      </div>
      
      <p class="message">{{ message }}</p>
    </div>
    
    <template #footer>
      <el-button @click="handleReject">取消</el-button>
      <el-button 
        type="primary" 
        @click="handleConfirm"
        :loading="confirming"
      >
        确认执行
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import type { AgentSSEEvents } from '@/types/sse-events';

const props = defineProps<{
  confirmationId: string;
  toolName: string;
  message: string;
  riskLevel: AgentSSEEvents['confirmation_required']['riskLevel'];
  params: Record<string, unknown>;
}>();

const emit = defineEmits<{
  confirm: [confirmationId: string];
  reject: [confirmationId: string];
}>();

const visible = ref(true);
const confirming = ref(false);

const riskLevelText = computed(() => ({
  LOW: '低风险',
  MEDIUM: '中风险',
  HIGH: '高风险',
  CRITICAL: '关键操作'
}[props.riskLevel]));

const riskLevelType = computed(() => ({
  LOW: 'success',
  MEDIUM: 'info',
  HIGH: 'warning',
  CRITICAL: 'error'
}[props.riskLevel]) as 'success' | 'info' | 'warning' | 'error');

async function handleConfirm() {
  confirming.value = true;
  try {
    emit('confirm', props.confirmationId);
    visible.value = false;
  } finally {
    confirming.value = false;
  }
}

function handleReject() {
  emit('reject', props.confirmationId);
  visible.value = false;
}
</script>
```

#### D.4 API 调用示例

```typescript
// api/chat.ts
import { AgentSSEClient, type SSEEventHandlers } from './sse-client';

export interface ChatOptions {
  message: string;
  sessionId?: string;
  stream?: boolean;
  onToken?: (content: string, index: number) => void;
  onThinking?: (content: string, index: number) => void;
  onToolCall?: SSEEventHandlers['onToolCall'];
  onConfirmationRequired?: SSEEventHandlers['onConfirmationRequired'];
  onComplete?: (response: string) => void;
  onError?: (error: Error) => void;
}

export async function sendMessage(options: ChatOptions): Promise<void> {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: options.message,
      sessionId: options.sessionId
    })
  });
  
  if (!response.ok) {
    const error = await response.json();
    options.onError?.(new Error(error.message || '请求失败'));
    return;
  }
  
  const client = new AgentSSEClient(response.url, {
    onToken: options.onToken,
    onThinking: options.onThinking,
    onToolCall: options.onToolCall,
    onConfirmationRequired: options.onConfirmationRequired,
    onDone: (data) => {
      options.onComplete?.('');
    },
    onError: (data) => {
      options.onError?.(new Error(data.message));
    }
  });
  
  client.connect();
}

// 确认操作
export async function confirmToolExecution(
  confirmationId: string,
  approved: boolean
): Promise<void> {
  const response = await fetch(`/api/tools/confirm/${confirmationId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ approved })
  });
  
  if (!response.ok) {
    throw new Error('确认请求失败');
  }
}
```

### E. 统一错误码规范

#### E.1 错误码范围分配

| 范围 | 模块 | 说明 |
|------|------|------|
| 10000-10999 | 用户认证 | 登录、Token、权限 |
| 20000-20999 | 会话管理 | 会话创建、查询、删除 |
| 30000-30999 | 工具系统 | 工具注册、执行、校验 |
| 40000-40999 | RAG 知识库 | 文档上传、检索、向量化 |
| 50000-50999 | Agent 执行 | 任务规划、步骤执行 |
| 50200-50299 | AI 模型调用 | LLM 请求、Token、模型错误 |
| 60000-60999 | 成本控制 | 配额、预算 |

#### E.2 详细错误码定义

```java
// ===== 错误码枚举 =====
public enum ErrorCode {
    
    // ========== 通用错误 (1xxxx) ==========
    SUCCESS(200, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    
    // ========== 用户认证 (10000-10999) ==========
    AUTH_TOKEN_EXPIRED(10001, "Token 已过期"),
    AUTH_TOKEN_INVALID(10002, "Token 无效"),
    AUTH_USER_NOT_FOUND(10003, "用户不存在"),
    AUTH_OAUTH_FAILED(10004, "OAuth 认证失败"),
    AUTH_REFRESH_FAILED(10005, "Token 刷新失败"),
    
    // ========== 会话管理 (20000-20999) ==========
    SESSION_NOT_FOUND(20001, "会话不存在"),
    SESSION_EXPIRED(20002, "会话已过期"),
    SESSION_LIMIT_EXCEEDED(20003, "会话数量超限"),
    MESSAGE_NOT_FOUND(20004, "消息不存在"),
    
    // ========== 工具系统 (30000-30999) ==========
    TOOL_NOT_FOUND(30001, "工具不存在"),
    TOOL_EXECUTION_FAILED(30002, "工具执行失败"),
    TOOL_PARAM_INVALID(30003, "工具参数校验失败"),
    TOOL_TIMEOUT(30004, "工具执行超时"),
    TOOL_CIRCUIT_BREAKER_OPEN(30005, "工具熔断器已打开"),
    TOOL_CONFIRMATION_EXPIRED(30006, "确认请求已过期"),
    TOOL_CONFIRMATION_REJECTED(30007, "操作已被拒绝"),
    TOOL_SQL_INJECTION_DETECTED(30008, "检测到 SQL 注入"),
    TOOL_PATH_TRAVERSAL_DETECTED(30009, "检测到路径遍历攻击"),
    
    // ========== RAG 知识库 (40000-40999) ==========
    DOCUMENT_NOT_FOUND(40001, "文档不存在"),
    DOCUMENT_PARSE_FAILED(40002, "文档解析失败"),
    DOCUMENT_TOO_LARGE(40003, "文档大小超限"),
    EMBEDDING_FAILED(40004, "向量化失败"),
    VECTOR_STORE_ERROR(40005, "向量存储错误"),
    
    // ========== Agent 执行 (50000-50999) ==========
    TASK_NOT_FOUND(50001, "任务不存在"),
    TASK_EXECUTION_FAILED(50002, "任务执行失败"),
    TASK_TIMEOUT(50003, "任务执行超时"),
    TASK_CANCELLED(50004, "任务已取消"),
    STEP_EXECUTION_FAILED(50005, "步骤执行失败"),
    AGENT_NOT_FOUND(50006, "Agent 不存在"),
    WORKFLOW_INVALID(50007, "工作流配置无效"),
    
    // ========== AI 模型调用 (50200-50299) ==========
    AI_API_KEY_INVALID(50200, "API Key 无效"),
    AI_API_KEY_NOT_CONFIGURED(50201, "API Key 未配置"),
    AI_REQUEST_TIMEOUT(50202, "AI 请求超时"),
    AI_QUOTA_EXCEEDED(50203, "AI 配额超限"),
    AI_MODEL_NOT_AVAILABLE(50204, "AI 模型不可用"),
    AI_RESPONSE_PARSE_ERROR(50205, "AI 响应解析失败"),
    AI_ALL_MODELS_UNAVAILABLE(50206, "所有 AI 模型均不可用"),
    AI_RATE_LIMITED(50207, "AI 请求限流"),
    
    // ========== 成本控制 (60000-60999) ==========
    COST_USER_QUOTA_EXCEEDED(60001, "用户配额已用完"),
    COST_SYSTEM_BUDGET_EXCEEDED(60002, "系统预算已耗尽"),
    COST_TOKEN_LIMIT_EXCEEDED(60003, "Token 限制超限");
    
    private final int code;
    private final String message;
    
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public int getCode() { return code; }
    public String getMessage() { return message; }
    
    /**
     * 根据 code 获取 ErrorCode
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) return ec;
        }
        return INTERNAL_ERROR;
    }
    
    /**
     * 获取 HTTP 状态码
     */
    public int getHttpStatus() {
        if (code < 1000) return code;
        if (code >= 10000 && code < 20000) return 401;
        if (code >= 20000 && code < 50000) return 400;
        if (code >= 50000) return 502;
        return 500;
    }
}
```

#### E.3 错误响应格式

```json
{
  "code": 30003,
  "message": "工具参数校验失败",
  "data": {
    "tool": "execute_sql",
    "errors": [
      "参数 'sql' 检测到潜在的 SQL 注入"
    ]
  },
  "traceId": "abc123def456",
  "timestamp": "2026-04-17T10:30:00Z"
}
```

### F. 健康检查端点

#### F.1 端点设计

```
GET /actuator/health          # Spring Boot 默认健康检查
GET /api/health/summary       # 自定义综合健康状态
GET /api/health/models        # AI 模型健康状态
GET /api/health/circuit-breakers  # 熔断器状态
GET /api/health/vector-store  # 向量库状态
GET /api/health/redis         # Redis 状态
```

#### F.2 健康状态响应

```java
// ===== 综合健康状态 =====
public record SystemHealthStatus(
    String status,              // UP, DOWN, DEGRADED
    List<ComponentHealth> components,
    LocalDateTime checkedAt
) {}

public record ComponentHealth(
    String name,
    String status,              // UP, DOWN
    String message,
    Map<String, Object> details
) {}

// ===== 健康检查控制器 =====
@RestController
@RequestMapping("/api/health")
public class HealthController {
    
    private final List<ChatModel> chatModels;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final VectorStore vectorStore;
    private final RedisTemplate<String, ?> redisTemplate;
    
    @GetMapping("/summary")
    public SystemHealthStatus getSummary() {
        List<ComponentHealth> components = new ArrayList<>();
        
        // 1. 检查 AI 模型
        int availableModels = checkModels();
        components.add(new ComponentHealth(
            "ai-models",
            availableModels > 0 ? "UP" : "DOWN",
            String.format("%d/%d 模型可用", availableModels, chatModels.size()),
            Map.of("availableCount", availableModels)
        ));
        
        // 2. 检查熔断器
        long openBreakers = circuitBreakerRegistry.getAllCircuitBreakers().stream()
            .filter(cb -> cb.getState() == CircuitBreaker.State.OPEN)
            .count();
        components.add(new ComponentHealth(
            "circuit-breakers",
            openBreakers < 3 ? "UP" : "DEGRADED",
            String.format("%d 个熔断器打开", openBreakers),
            Map.of("openCount", openBreakers)
        ));
        
        // 3. 检查 Redis
        boolean redisOk = checkRedis();
        components.add(new ComponentHealth(
            "redis",
            redisOk ? "UP" : "DOWN",
            redisOk ? "连接正常" : "连接失败",
            Map.of()
        ));
        
        // 4. 检查向量库
        boolean vectorOk = checkVectorStore();
        components.add(new ComponentHealth(
            "vector-store",
            vectorOk ? "UP" : "DEGRADED",
            vectorOk ? "连接正常" : "连接失败",
            Map.of()
        ));
        
        // 综合状态
        String status = determineOverallStatus(components);
        
        return new SystemHealthStatus(status, components, LocalDateTime.now());
    }
    
    @GetMapping("/models")
    public List<ModelHealthStatus> getModels() {
        // 返回每个模型的详细状态
    }
    
    @GetMapping("/circuit-breakers")
    public Map<String, CircuitBreakerStatus> getCircuitBreakers() {
        // 返回每个熔断器的详细状态
    }
    
    private String determineOverallStatus(List<ComponentHealth> components) {
        boolean anyDown = components.stream().anyMatch(c -> "DOWN".equals(c.status()));
        boolean anyDegraded = components.stream().anyMatch(c -> "DEGRADED".equals(c.status()));
        
        if (anyDown) return "DOWN";
        if (anyDegraded) return "DEGRADED";
        return "UP";
    }
}
```

#### F.3 健康检查响应示例

```json
{
  "status": "DEGRADED",
  "components": [
    {
      "name": "ai-models",
      "status": "UP",
      "message": "3/3 模型可用",
      "details": {
        "availableCount": 3
      }
    },
    {
      "name": "circuit-breakers",
      "status": "DEGRADED",
      "message": "1 个熔断器打开",
      "details": {
        "openCount": 1,
        "openBreakers": ["model-deepseek"]
      }
    },
    {
      "name": "redis",
      "status": "UP",
      "message": "连接正常",
      "details": {}
    },
    {
      "name": "vector-store",
      "status": "UP",
      "message": "连接正常",
      "details": {
        "documentCount": 1234,
        "chunkCount": 5678
      }
    }
  ],
  "checkedAt": "2026-04-17T10:30:00"
}
```

---

**文档结束**

> 此方案将作为后续开发的实施指南，请按照阶段和优先级逐步实现。每个模块实现完成后，更新本文档的完成状态。
