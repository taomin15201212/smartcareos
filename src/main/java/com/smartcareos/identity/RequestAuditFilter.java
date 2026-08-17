package com.smartcareos.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestAuditFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestAuditFilter.class);
    private static final Set<String> MUTATIONS = Set.of("POST","PUT","PATCH","DELETE");
    private final JdbcTemplate jdbc;
    public RequestAuditFilter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
            FilterChain chain) throws ServletException,IOException {
        String requestId=UUID.randomUUID().toString(); long started=System.nanoTime();
        response.setHeader("X-Request-Id",requestId);
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("X-Frame-Options","DENY");
        response.setHeader("Referrer-Policy","no-referrer");
        response.setHeader("Cache-Control","no-store");
        if (request.isSecure()) response.setHeader("Strict-Transport-Security","max-age=31536000; includeSubDomains");
        MDC.put("requestId",requestId);
        try { chain.doFilter(request,response); }
        finally {
            MDC.remove("requestId");
            if (request.getRequestURI().startsWith("/api/") &&
                    (MUTATIONS.contains(request.getMethod()) || response.getStatus()>=400)) {
                try {
                    jdbc.update("""
                        INSERT INTO audit_event(id,request_id,tenant_id,principal_id,method,
                            request_path,response_status,outcome,duration_ms,occurred_at)
                        VALUES(?,?,?,?,?,?,?,?,?,?)
                        """,UUID.randomUUID().toString(),requestId,
                            text(request.getHeader("X-SmartCare-Tenant")),
                            text(request.getHeader("X-SmartCare-Principal")),request.getMethod(),
                            request.getRequestURI(),response.getStatus(),
                            response.getStatus()<400?"SUCCESS":"DENIED",
                            (System.nanoTime()-started)/1_000_000,Timestamp.from(Instant.now()));
                } catch (RuntimeException exception) {
                    log.error("audit event persistence failed requestId={}",requestId,exception);
                }
            }
        }
    }
    private static String text(String value) { return value==null||value.isBlank()?null:value; }
}
