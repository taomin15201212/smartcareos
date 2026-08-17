CREATE TABLE device_product (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    product_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_device_product_key UNIQUE (tenant_id, product_key)
);

CREATE TABLE device (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    device_key VARCHAR(64) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    registered_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_device_product FOREIGN KEY (product_id) REFERENCES device_product (id),
    CONSTRAINT uq_device_key UNIQUE (tenant_id, device_key)
);

CREATE INDEX idx_device_product_status
    ON device (tenant_id, product_id, status);

CREATE TABLE device_status_history (
    id VARCHAR(36) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    sequence_no INTEGER NOT NULL,
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_device_status_history_device FOREIGN KEY (device_id) REFERENCES device (id),
    CONSTRAINT uq_device_status_sequence UNIQUE (device_id, sequence_no)
);

CREATE INDEX idx_device_status_history_time
    ON device_status_history (device_id, occurred_at);

CREATE TABLE device_binding (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    device_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    valid_from TIMESTAMP(6) NOT NULL,
    valid_to TIMESTAMP(6),
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_device_binding_device FOREIGN KEY (device_id) REFERENCES device (id)
);

CREATE INDEX idx_device_binding_effective
    ON device_binding (tenant_id, device_id, target_type, valid_from, valid_to);
