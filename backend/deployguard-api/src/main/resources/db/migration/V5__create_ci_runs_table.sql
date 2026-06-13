CREATE TABLE ci_runs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    commit_sha VARCHAR(120) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    status VARCHAR(50) NOT NULL,
    duration_seconds INTEGER NOT NULL,
    failed_tests INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ci_runs_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
);
