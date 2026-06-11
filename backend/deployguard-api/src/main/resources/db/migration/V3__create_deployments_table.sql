CREATE TABLE deployments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    commit_sha VARCHAR(120) NOT NULL,
    branch VARCHAR(120) NOT NULL,
    environment VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    deployed_by VARCHAR(120) NOT NULL,
    deployed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    risk_score INTEGER NOT NULL DEFAULT 0,
    risk_level VARCHAR(30) NOT NULL DEFAULT 'LOW',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_deployments_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
);
