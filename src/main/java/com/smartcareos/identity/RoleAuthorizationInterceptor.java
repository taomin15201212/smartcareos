package com.smartcareos.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.EnumSet;
import java.util.Set;

@Component
public class RoleAuthorizationInterceptor implements HandlerInterceptor {
    private final boolean enabled;

    public RoleAuthorizationInterceptor(@Value("${smartcareos.security.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!enabled || "OPTIONS".equals(request.getMethod())) return true;
        TenantContext.Identity identity = TenantContext.current();
        if (identity == null || !allowed(identity.role(), request.getMethod(), request.getRequestURI())) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"role is not authorized for this operation\"}");
            return false;
        }
        return true;
    }

    static boolean allowed(TenantContext.Role role, String method, String path) {
        if (role == TenantContext.Role.ADMIN) return true;
        if (path.startsWith("/api/v1/api-credentials")) return false;
        if (role == TenantContext.Role.AUDITOR) return "GET".equals(method);
        if (role == TenantContext.Role.DEVICE_INGEST)
            return "POST".equals(method) && path.equals("/api/v1/device-risk-events");
        if (role == TenantContext.Role.CAREGIVER) {
            if ("GET".equals(method)) return true;
            return path.matches("/api/v1/(care-tasks|alarms)/[^/]+/(start|complete|acknowledge|resolve)");
        }
        Set<String> deniedPrefixes = Set.of("/api/v1/government-exchanges");
        return deniedPrefixes.stream().noneMatch(path::startsWith);
    }
}
