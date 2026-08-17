CREATE TABLE device_risk_event (
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    elder_id VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (tenant_id, event_id)
);

CREATE INDEX idx_device_risk_event_device_time
    ON device_risk_event (tenant_id, device_id, observed_at);

CREATE INDEX idx_device_risk_event_elder_time
    ON device_risk_event (tenant_id, elder_id, observed_at);

CREATE TABLE inbox_message (
    consumer_name VARCHAR(128) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    received_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6),
    PRIMARY KEY (consumer_name, tenant_id, event_id)
);

CREATE INDEX idx_inbox_processed_time
    ON inbox_message (processed_at, received_at);
