package com.deployguard.api.ai.job;

import java.util.UUID;

public record AiAnalysisJobMessage(UUID jobId, UUID deploymentId) {}
