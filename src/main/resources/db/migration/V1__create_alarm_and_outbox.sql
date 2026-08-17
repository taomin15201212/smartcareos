CREATE TABLE alarm (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    elder_id VARCHAR(64) NOT NULL,
    source_event_id VARCHAR(128) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    escalation_level INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_alarm_source_event UNIQUE (tenant_id, source_event_id)
);

CREATE INDEX idx_alarm_tenant_status_created
    ON alarm (tenant_id, status, created_at);

CREATE INDEX idx_alarm_elder_created
    ON alarm (tenant_id, elder_id, created_at);

CREATE TABLE alarm_transition (
    id VARCHAR(36) NOT NULL,
    alarm_id VARCHAR(36) NOT NULL,
    sequence_no INTEGER NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_alarm_transition_alarm FOREIGN KEY (alarm_id) REFERENCES alarm (id),
    CONSTRAINT uq_alarm_transition_sequence UNIQUE (alarm_id, sequence_no)
);

CREATE INDEX idx_alarm_transition_alarm_time
    ON alarm_transition (alarm_id, occurred_at);

CREATE TABLE outbox_event (
    event_id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    schema_version INTEGER NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    payload TEXT NOT NULL,
    published_at TIMESTAMP(6),
    PRIMARY KEY (event_id)
);

CREATE INDEX idx_outbox_unpublished
    ON outbox_event (published_at, occurred_at);
