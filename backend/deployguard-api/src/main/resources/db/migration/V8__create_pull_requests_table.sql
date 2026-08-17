CREATE TABLE pull_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider VARCHAR(80) NOT NULL,
    provider_pull_request_id VARCHAR(120) NOT NULL,
    number INTEGER NOT NULL,
    title VARCHAR(500) NOT NULL,
    author_id VARCHAR(120) NOT NULL,
    source_branch VARCHAR(255) NOT NULL,
    target_branch VARCHAR(255) NOT NULL,
    commit_sha VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_pull_request_provider UNIQUE (project_id, provider, provider_pull_request_id)
);

CREATE INDEX idx_pull_requests_project_commit ON pull_requests(project_id, commit_sha);
