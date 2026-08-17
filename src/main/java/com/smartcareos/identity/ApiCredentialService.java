package com.smartcareos.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class ApiCredentialService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    public ApiCredentialService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public IssuedCredential issue(String tenantId, String principalId, Instant expiresAt,
            TenantContext.Role role) {
        TenantContext.Identity actor = requireManager();
        if (!actor.tenantId().equals(tenantId)) throw new TenantAccessDeniedException();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now()))
            throw new IllegalArgumentException("expiresAt must be in the future");
        byte[] secretBytes = new byte[32]; random.nextBytes(secretBytes);
        String secret = "scos_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        String id = UUID.randomUUID().toString(); Instant now = Instant.now();
        jdbc.update("""
            INSERT INTO api_credential(id,tenant_id,principal_id,key_hash,status,created_at,
                expires_at,created_by,can_manage_credentials,role_code)
            VALUES(?,?,?,?,'ACTIVE',?,?,?,?,?)
            """, id, tenantId, principalId, ApiKeyAuthenticationInterceptor.sha256(secret),
                Timestamp.from(now), timestamp(expiresAt), actor.principalId(),
                role == TenantContext.Role.ADMIN, role.name());
        return new IssuedCredential(id, tenantId, principalId, secret, expiresAt,
                role, now);
    }

    @Transactional
    public void revoke(String credentialId) {
        TenantContext.Identity actor = requireManager();
        int changed = jdbc.update("""
            UPDATE api_credential SET status='REVOKED',revoked_at=?
             WHERE id=? AND tenant_id=? AND status='ACTIVE'
            """, Timestamp.from(Instant.now()), credentialId, actor.tenantId());
        if (changed != 1) throw new IllegalArgumentException("active credential not found");
    }

    private TenantContext.Identity requireManager() {
        TenantContext.Identity actor = TenantContext.current();
        if (actor == null) throw new TenantAccessDeniedException();
        if (actor.role() != TenantContext.Role.ADMIN) throw new TenantAccessDeniedException();
        return actor;
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
    public record IssuedCredential(String id,String tenantId,String principalId,String apiKey,
            Instant expiresAt,TenantContext.Role role,Instant createdAt) {}
}
