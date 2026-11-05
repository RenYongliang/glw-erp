package com.glw.backend.auth;

import com.glw.backend.common.BusinessException;
import com.glw.backend.common.ErrorCode;
import com.glw.backend.model.entity.UserView;

import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前登录用户上下文。
 *
 * <p>统一从 SecurityContext 读取 AuthPrincipal，避免各业务服务各自解析。
 * 未登录或后台任务场景下返回空值，审计字段由调用方决定是否必填。
 */
public final class UserContext {
    private UserContext() {}

    public static Optional<UserView> current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal p) {
            return Optional.of(p.user());
        }
        return Optional.empty();
    }

    public static Optional<AuthPrincipal> principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    /** 当前用户ID；未登录时返回 null，用于 created_by/updated_by 等可空审计字段。 */
    public static Long userId() {
        return current().map(user -> Long.valueOf(user.id())).orElse(null);
    }

    /** 需要用户ID的业务操作使用；未登录直接返回 401。 */
    public static Long requireUserId() {
        return current()
                .map(user -> Long.valueOf(user.id()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public static String username() {
        return current().map(UserView::username).orElse(null);
    }

    public static String name() {
        return current().map(UserView::name).orElse(null);
    }

    public static String role() {
        return current().map(UserView::role).orElse(null);
    }

    /** 当前用户所属部门ID；用户未关联部门时返回 null。 */
    public static Long departmentId() {
        return current().map(UserView::departmentId).orElse(null);
    }

    /** 当前用户所属部门名称。 */
    public static String department() {
        return current().map(UserView::department).orElse(null);
    }

    public static boolean isAuthenticated() {
        return current().isPresent();
    }
}
