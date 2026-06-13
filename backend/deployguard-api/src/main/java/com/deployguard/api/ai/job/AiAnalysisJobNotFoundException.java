package com.deployguard.api.ai.job;

import java.util.UUID;

public class AiAnalysisJobNotFoundException extends RuntimeException {

    public AiAnalysisJobNotFoundException(UUID jobId) {
        super("AI analysis job not found: " + jobId);
    }
}
