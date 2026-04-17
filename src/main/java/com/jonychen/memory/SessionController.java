package com.jonychen.memory;

import com.jonychen.auth.User;
import com.jonychen.exception.BusinessException;
import com.jonychen.model.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * 会话 REST 控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionManager sessionManager;

    /**
     * 创建新会话
     */
    @PostMapping
    public SessionInfo createSession(
            Principal principal,
            @RequestBody(required = false) CreateSessionRequest request
    ) {
        String userId = getUserId(principal);
        String title = request != null ? request.title() : null;

        return sessionManager.createSession(userId, title);
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping
    public List<SessionInfo> listSessions(Principal principal) {
        String userId = getUserId(principal);
        return sessionManager.listSessions(userId);
    }

    /**
     * 获取会话详情
     */
    @GetMapping("/{sessionId}")
    public SessionInfo getSession(
            Principal principal,
            @PathVariable String sessionId
    ) {
        String userId = getUserId(principal);

        // 验证会话归属
        if (!sessionManager.isSessionOwner(sessionId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问此会话");
        }

        return sessionManager.getSession(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    }

    /**
     * 更新会话标题
     */
    @PatchMapping("/{sessionId}")
    public SessionInfo updateSessionTitle(
            Principal principal,
            @PathVariable String sessionId,
            @RequestBody UpdateSessionRequest request
    ) {
        String userId = getUserId(principal);

        // 验证会话归属
        if (!sessionManager.isSessionOwner(sessionId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权修改此会话");
        }

        sessionManager.updateSessionTitle(sessionId, request.title());

        return sessionManager.getSession(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{sessionId}")
    public void deleteSession(
            Principal principal,
            @PathVariable String sessionId
    ) {
        String userId = getUserId(principal);

        // 验证会话归属
        if (!sessionManager.isSessionOwner(sessionId, userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权删除此会话");
        }

        sessionManager.deleteSession(sessionId);
    }

    /**
     * 获取用户 ID
     */
    private String getUserId(Principal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return principal.getName();
    }

    // ========== 内部 DTO 类 ==========

    public record CreateSessionRequest(String title) {}

    public record UpdateSessionRequest(String title) {}
}
