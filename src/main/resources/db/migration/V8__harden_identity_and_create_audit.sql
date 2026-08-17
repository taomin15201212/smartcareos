ALTER TABLE api_credential ADD COLUMN created_by VARCHAR(64);
ALTER TABLE api_credential ADD COLUMN last_used_at TIMESTAMP(6);
ALTER TABLE api_credential ADD COLUMN revoked_at TIMESTAMP(6);
ALTER TABLE api_credential ADD COLUMN can_manage_credentials BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE audit_event (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    request_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64),
    principal_id VARCHAR(64),
    method VARCHAR(12) NOT NULL,
    request_path VARCHAR(512) NOT NULL,
    response_status INTEGER NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    duration_ms BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uq_audit_request UNIQUE (request_id)
);
CREATE INDEX idx_audit_tenant_time ON audit_event (tenant_id, occurred_at);
CREATE INDEX idx_audit_principal_time ON audit_event (principal_id, occurred_at);
