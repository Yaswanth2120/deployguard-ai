CREATE TABLE application_logs (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    deployment_id UUID NULL,
    service_name VARCHAR(120) NOT NULL,
    level VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_application_logs_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id),
    CONSTRAINT fk_application_logs_deployment
        FOREIGN KEY (deployment_id)
        REFERENCES deployments(id)
);
