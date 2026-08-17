CREATE TABLE api_credential (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    principal_id VARCHAR(64) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6),
    CONSTRAINT uq_api_credential_hash UNIQUE (key_hash)
);
CREATE INDEX idx_api_credential_tenant ON api_credential (tenant_id, principal_id, status);

CREATE TABLE notification_delivery (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    business_type VARCHAR(32) NOT NULL,
    business_id VARCHAR(64) NOT NULL,
    channel VARCHAR(24) NOT NULL,
    recipient VARCHAR(128) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(512),
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6)
);
CREATE INDEX idx_notification_pending ON notification_delivery (status, created_at);
CREATE INDEX idx_notification_business ON notification_delivery (tenant_id, business_type, business_id);

CREATE TABLE government_exchange_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    contract_code VARCHAR(64) NOT NULL,
    mapping_version VARCHAR(32) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    external_receipt VARCHAR(128),
    receipt_message VARCHAR(512),
    created_at TIMESTAMP(6) NOT NULL,
    submitted_at TIMESTAMP(6),
    completed_at TIMESTAMP(6)
);
CREATE INDEX idx_government_exchange_status ON government_exchange_task (tenant_id, status, created_at);
