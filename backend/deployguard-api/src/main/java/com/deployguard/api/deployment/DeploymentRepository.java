package com.deployguard.api.deployment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByProjectId(UUID projectId);
}
