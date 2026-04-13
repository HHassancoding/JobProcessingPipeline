CREATE TABLE IF NOT EXISTS job_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    message TEXT,
    metadata_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_job_events_job FOREIGN KEY (job_id) REFERENCES jobs(id),
    CONSTRAINT chk_job_events_event_type CHECK (event_type IN ('STATE_TRANSITION', 'RETRY_REQUESTED', 'LEASE_EXPIRED', 'PROCESSING_STARTED', 'PROCESSING_COMPLETED', 'PROCESSING_FAILED')),
    CONSTRAINT chk_job_events_from_status CHECK (from_status IS NULL OR from_status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_job_events_to_status CHECK (to_status IS NULL OR to_status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_job_events_job_created_at ON job_events (job_id, created_at DESC);
