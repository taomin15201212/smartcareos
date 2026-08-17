CREATE TABLE institution (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    institution_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_institution_code UNIQUE (tenant_id, institution_code)
);

CREATE TABLE institution_room (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    institution_id VARCHAR(36) NOT NULL,
    room_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_room_institution FOREIGN KEY (institution_id) REFERENCES institution (id),
    CONSTRAINT uq_room_code UNIQUE (institution_id, room_code)
);

CREATE INDEX idx_room_tenant_institution
    ON institution_room (tenant_id, institution_id);

CREATE TABLE institution_bed (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    room_id VARCHAR(36) NOT NULL,
    bed_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_bed_room FOREIGN KEY (room_id) REFERENCES institution_room (id),
    CONSTRAINT uq_bed_code UNIQUE (room_id, bed_code)
);

CREATE INDEX idx_bed_tenant_status
    ON institution_bed (tenant_id, status, room_id);

CREATE TABLE elder (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    elder_no VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_elder_number UNIQUE (tenant_id, elder_no)
);

CREATE INDEX idx_elder_tenant_status
    ON elder (tenant_id, status, created_at);

CREATE TABLE admission (
    id VARCHAR(36) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    elder_id VARCHAR(36) NOT NULL,
    institution_id VARCHAR(36) NOT NULL,
    bed_id VARCHAR(36) NOT NULL,
    admitted_at TIMESTAMP(6) NOT NULL,
    discharged_at TIMESTAMP(6),
    admitted_by VARCHAR(64) NOT NULL,
    discharged_by VARCHAR(64),
    created_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_admission_elder FOREIGN KEY (elder_id) REFERENCES elder (id)
);

CREATE INDEX idx_admission_elder_effective
    ON admission (tenant_id, elder_id, admitted_at, discharged_at);

CREATE INDEX idx_admission_bed_effective
    ON admission (tenant_id, bed_id, admitted_at, discharged_at);
