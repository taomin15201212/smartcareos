ALTER TABLE notification_delivery ADD COLUMN external_reference VARCHAR(128);
ALTER TABLE government_exchange_task ADD COLUMN dispatch_attempts INTEGER NOT NULL DEFAULT 0;
