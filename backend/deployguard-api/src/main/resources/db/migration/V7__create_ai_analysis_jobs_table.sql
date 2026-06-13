CREATE TABLE ai_analysis_jobs (
    id UUID PRIMARY KEY,
    deployment_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT fk_ai_analysis_jobs_deployment
        FOREIGN KEY (deployment_id) REFERENCES deployments(id)
);
