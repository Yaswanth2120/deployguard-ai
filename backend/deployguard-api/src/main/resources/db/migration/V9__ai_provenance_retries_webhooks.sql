-- Provenance for AI summaries: which path produced them (model vs heuristic fallback),
-- why a fallback happened, prompt version, and cost/latency telemetry.
ALTER TABLE ai_incident_summaries
    ADD COLUMN root_cause_category VARCHAR(40) NULL,
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'unknown',
    ADD COLUMN fallback_reason VARCHAR(60) NULL,
    ADD COLUMN analysis_mode VARCHAR(20) NULL,
    ADD COLUMN prompt_version VARCHAR(40) NULL,
    ADD COLUMN prompt_tokens INTEGER NULL,
    ADD COLUMN completion_tokens INTEGER NULL,
    ADD COLUMN latency_ms INTEGER NULL,
    ADD COLUMN dropped_evidence INTEGER NULL;

-- Retry accounting for async jobs.
ALTER TABLE ai_analysis_jobs
    ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;

-- Idempotency for GitHub webhook deliveries (GitHub retries deliveries).
CREATE TABLE github_webhook_deliveries (
    delivery_id VARCHAR(100) PRIMARY KEY,
    event VARCHAR(60) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_pull_requests_project_provider_id ON pull_requests (project_id, provider_pull_request_id);
