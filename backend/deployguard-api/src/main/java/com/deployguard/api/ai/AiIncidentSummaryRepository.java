package com.deployguard.api.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiIncidentSummaryRepository extends JpaRepository<AiIncidentSummary, UUID> {

    List<AiIncidentSummary> findByDeploymentId(UUID deploymentId);
}
