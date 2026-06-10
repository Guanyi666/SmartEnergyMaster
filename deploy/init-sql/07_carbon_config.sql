-- Epic 01-2-2: carbon quota and emission factor configuration.

CREATE TABLE IF NOT EXISTS carbon_quota (
    id BIGSERIAL PRIMARY KEY,
    quota_year INT NOT NULL,
    quota_month INT NOT NULL DEFAULT 0,
    quota_type VARCHAR(32) NOT NULL DEFAULT 'PLANT',
    quota_value NUMERIC(14, 4) NOT NULL,
    emission_factor NUMERIC(12, 6) NOT NULL DEFAULT 0.570300,
    price_tier VARCHAR(32),
    alert_threshold NUMERIC(5, 2) NOT NULL DEFAULT 0.90,
    effective_from DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_carbon_quota_period UNIQUE (quota_year, quota_month, quota_type),
    CONSTRAINT ck_carbon_quota_month CHECK (quota_month BETWEEN 0 AND 12),
    CONSTRAINT ck_carbon_quota_threshold CHECK (alert_threshold > 0 AND alert_threshold <= 1)
);

CREATE INDEX IF NOT EXISTS ix_carbon_quota_effective
    ON carbon_quota (effective_from, effective_to);
