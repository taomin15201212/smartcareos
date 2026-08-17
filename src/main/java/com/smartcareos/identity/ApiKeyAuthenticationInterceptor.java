package com.smartcareos.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Component
public class ApiKeyAuthenticationInterceptor implements HandlerInterceptor {
    private final JdbcTemplate jdbc;
    private final TenantResourceResolver resourceResolver;
    private final boolean enabled;
    private final String authMode;
    private final ObjectProvider<JwtDecoder> jwtDecoder;

    public ApiKeyAuthenticationInterceptor(JdbcTemplate jdbc, TenantResourceResolver resourceResolver,
            @Value("${smartcareos.security.enabled:false}") boolean enabled,
            @Value("${smartcareos.security.auth-mode:api-key}") String authMode,
            ObjectProvider<JwtDecoder> jwtDecoder) {
        this.jdbc = jdbc;
        this.resourceResolver = resourceResolver;
        this.enabled = enabled;
        this.authMode=authMode; this.jwtDecoder=jwtDecoder;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!enabled || "OPTIONS".equals(request.getMethod())) return true;
        AuthenticatedCredential credential = "oidc".equalsIgnoreCase(authMode)
                ? oidc(request) : apiKey(request);
        if (credential == null) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"valid tenant API credentials are required\"}");
            return false;
        }
        Optional<String> resourceTenant = resourceResolver.resolve(request.getRequestURI());
        if (resourceTenant.isPresent() && !credential.tenant().equals(resourceTenant.get())) {
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"TENANT_ACCESS_DENIED\",\"message\":\"resource is outside the authenticated tenant boundary\"}");
            return false;
        }
        TenantContext.set(new TenantContext.Identity(credential.tenant(), credential.principal(), credential.id(), credential.role()));
        request.setAttribute("smartcareos.authenticated", Boolean.TRUE);
        request.setAttribute("smartcareos.credentialId", credential.id());
        if(!credential.id().startsWith("oidc:")) jdbc.update("UPDATE api_credential SET last_used_at=? WHERE id=?",
                java.sql.Timestamp.from(Instant.now()), credential.id());
        return true;
    }

    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) { TenantContext.clear(); }

    private AuthenticatedCredential apiKey(HttpServletRequest request) {
        String tenant=request.getHeader("X-SmartCare-Tenant");
        String principal=request.getHeader("X-SmartCare-Principal");
        String key=request.getHeader("X-SmartCare-Api-Key");
        if(tenant==null||principal==null||key==null) return null;
        return jdbc.query("""
            SELECT id,role_code FROM api_credential
             WHERE tenant_id=? AND principal_id=? AND key_hash=? AND status='ACTIVE'
               AND (expires_at IS NULL OR expires_at > ?)
            """, (rs,row) -> new AuthenticatedCredential(rs.getString("id"),tenant,principal,
                    TenantContext.Role.parse(rs.getString("role_code"))), tenant, principal,
                sha256(key), java.sql.Timestamp.from(Instant.now())).stream().findFirst().orElse(null);
    }

    private AuthenticatedCredential oidc(HttpServletRequest request) {
        String authorization=request.getHeader("Authorization");
        JwtDecoder decoder=jwtDecoder.getIfAvailable();
        if(decoder==null||authorization==null||!authorization.startsWith("Bearer ")) return null;
        try {
            Jwt jwt=decoder.decode(authorization.substring(7));
            if(jwt==null) return null;
            String tenant=jwt.getClaimAsString("tenant_id");
            java.util.List<String> roles=jwt.getClaimAsStringList("roles");
            if(tenant==null||jwt.getSubject()==null||roles==null||roles.isEmpty()) return null;
            return new AuthenticatedCredential("oidc:"+jwt.getSubject(),tenant,jwt.getSubject(),
                    TenantContext.Role.parse(roles.getFirst()));
        } catch(JwtException exception) { return null; }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
    private record AuthenticatedCredential(String id,String tenant,String principal,TenantContext.Role role) {}
}
