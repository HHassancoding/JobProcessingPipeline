CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    job_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json JSONB NOT NULL,
    result_summary JSONB,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    claimed_by VARCHAR(128),
    locked_until TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    CONSTRAINT fk_jobs_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_jobs_type CHECK (job_type IN ('JSON_INGEST')),
    CONSTRAINT chk_jobs_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_jobs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_jobs_max_attempts CHECK (max_attempts > 0)
);

CREATE INDEX IF NOT EXISTS idx_jobs_status_created_at ON jobs (status, created_at);
CREATE INDEX IF NOT EXISTS idx_jobs_status_locked_until ON jobs (status, locked_until);
CREATE INDEX IF NOT EXISTS idx_jobs_user_id_created_at ON jobs (user_id, created_at DESC);
