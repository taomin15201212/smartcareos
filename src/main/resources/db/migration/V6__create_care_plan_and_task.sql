CREATE TABLE care_plan (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    elder_id VARCHAR(36) NOT NULL,
    name VARCHAR(128) NOT NULL,
    schedule_rule VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_care_plan_elder FOREIGN KEY (elder_id) REFERENCES elder (id)
);

CREATE INDEX idx_care_plan_elder_status
    ON care_plan (tenant_id, elder_id, status);

CREATE TABLE care_task (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    elder_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36),
    alarm_id VARCHAR(36),
    title VARCHAR(128) NOT NULL,
    status VARCHAR(24) NOT NULL,
    assignee_id VARCHAR(64) NOT NULL,
    due_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_care_task_elder FOREIGN KEY (elder_id) REFERENCES elder (id),
    CONSTRAINT fk_care_task_plan FOREIGN KEY (plan_id) REFERENCES care_plan (id),
    CONSTRAINT uq_care_task_alarm UNIQUE (tenant_id, alarm_id)
);

CREATE INDEX idx_care_task_assignee_due
    ON care_task (tenant_id, assignee_id, status, due_at);

CREATE INDEX idx_care_task_elder_due
    ON care_task (tenant_id, elder_id, due_at);

CREATE TABLE care_task_transition (
    id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    sequence_no INTEGER NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_care_transition_task FOREIGN KEY (task_id) REFERENCES care_task (id),
    CONSTRAINT uq_care_transition_sequence UNIQUE (task_id, sequence_no)
);

CREATE TABLE care_record (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    elder_id VARCHAR(36) NOT NULL,
    performed_by VARCHAR(64) NOT NULL,
    performed_at TIMESTAMP(6) NOT NULL,
    result_summary VARCHAR(512) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_care_record_task FOREIGN KEY (task_id) REFERENCES care_task (id),
    CONSTRAINT uq_care_record_task UNIQUE (task_id)
);

CREATE INDEX idx_care_record_elder_time
    ON care_record (tenant_id, elder_id, performed_at);
