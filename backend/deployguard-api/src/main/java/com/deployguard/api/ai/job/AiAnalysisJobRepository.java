package com.deployguard.api.ai.job;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisJobRepository extends JpaRepository<AiAnalysisJob, UUID> {

    List<AiAnalysisJob> findByDeploymentId(UUID deploymentId);
}
