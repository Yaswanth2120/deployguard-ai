CREATE TABLE ai_incident_summaries (
    id UUID PRIMARY KEY,
    deployment_id UUID NOT NULL,
    summary TEXT NOT NULL,
    likely_root_cause TEXT NOT NULL,
    evidence TEXT NOT NULL,
    recommended_actions TEXT NOT NULL,
    severity VARCHAR(30) NOT NULL,
    confidence VARCHAR(30) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_ai_incident_summaries_deployment
        FOREIGN KEY (deployment_id) REFERENCES deployments(id)
);
