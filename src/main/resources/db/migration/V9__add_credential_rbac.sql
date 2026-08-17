ALTER TABLE api_credential ADD COLUMN role_code VARCHAR(24) NOT NULL DEFAULT 'OPERATOR';
UPDATE api_credential SET role_code='ADMIN' WHERE can_manage_credentials=TRUE;
CREATE INDEX idx_api_credential_role ON api_credential (tenant_id, role_code, status);
