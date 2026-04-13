CREATE TABLE IF NOT EXISTS job_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    finished_at TIMESTAMPTZ,
    outcome VARCHAR(32) NOT NULL,
    failure_reason TEXT,
    duration_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_job_attempts_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT uq_job_attempts_job_attempt UNIQUE (job_id, attempt_number),
    CONSTRAINT chk_job_attempts_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_job_attempts_outcome CHECK (outcome IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_job_attempts_duration CHECK (duration_ms IS NULL OR duration_ms >= 0)
);

CREATE INDEX IF NOT EXISTS idx_job_attempts_job_created_at ON job_attempts (job_id, created_at DESC);
