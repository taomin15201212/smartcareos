package com.smartcareos.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Component
public class ApiCredentialBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final String key;
    private final String tenant;
    private final String principal;

    public ApiCredentialBootstrap(JdbcTemplate jdbc,
            @Value("${smartcareos.security.bootstrap-key:}") String key,
            @Value("${smartcareos.security.bootstrap-tenant:}") String tenant,
            @Value("${smartcareos.security.bootstrap-principal:bootstrap}") String principal) {
        this.jdbc = jdbc; this.key = key; this.tenant = tenant; this.principal = principal;
    }

    @Override public void run(ApplicationArguments args) {
        if (key.isBlank() || tenant.isBlank()) return;
        String hash = ApiKeyAuthenticationInterceptor.sha256(key);
        jdbc.update("""
            INSERT INTO api_credential(id,tenant_id,principal_id,key_hash,status,created_at,expires_at,role_code)
            SELECT ?,?,?,?,?,?,NULL,'ADMIN' WHERE NOT EXISTS (SELECT 1 FROM api_credential WHERE key_hash=?)
            """, UUID.randomUUID().toString(), tenant, principal, hash, "ACTIVE",
                Timestamp.from(Instant.now()), hash);
        jdbc.update("UPDATE api_credential SET can_manage_credentials=TRUE WHERE key_hash=?", hash);
    }
}
