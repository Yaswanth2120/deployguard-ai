package com.deployguard.api.cirun;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiRunRepository extends JpaRepository<CiRun, UUID> {

    List<CiRun> findByProjectId(UUID projectId);

    List<CiRun> findByProjectIdAndCommitSha(UUID projectId, String commitSha);

    List<CiRun> findByProjectIdAndStatus(UUID projectId, String status);
}
