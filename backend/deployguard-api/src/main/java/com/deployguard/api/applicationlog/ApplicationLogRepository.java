package com.deployguard.api.applicationlog;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationLogRepository extends JpaRepository<ApplicationLog, UUID> {

    List<ApplicationLog> findByProjectId(UUID projectId);

    List<ApplicationLog> findByDeploymentId(UUID deploymentId);

    List<ApplicationLog> findByProjectIdAndLevel(UUID projectId, String level);
}
