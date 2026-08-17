ALTER TABLE outbox_event ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_event ADD COLUMN next_attempt_at TIMESTAMP(6);
ALTER TABLE outbox_event ADD COLUMN last_error VARCHAR(1000);
ALTER TABLE outbox_event ADD COLUMN locked_by VARCHAR(128);
ALTER TABLE outbox_event ADD COLUMN locked_at TIMESTAMP(6);

CREATE INDEX idx_outbox_delivery_due
    ON outbox_event (published_at, next_attempt_at, locked_at, occurred_at);
