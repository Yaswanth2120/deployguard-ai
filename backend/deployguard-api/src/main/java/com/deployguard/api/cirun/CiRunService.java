package com.deployguard.api.cirun;

import com.deployguard.api.cirun.dto.CiRunResponse;
import com.deployguard.api.cirun.dto.CreateCiRunRequest;
import com.deployguard.api.project.Project;
import com.deployguard.api.project.ProjectNotFoundException;
import com.deployguard.api.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CiRunService {

    private static final String FAILED_STATUS = "FAILED";

    private final CiRunRepository ciRunRepository;
    private final ProjectRepository projectRepository;

    public CiRunService(CiRunRepository ciRunRepository, ProjectRepository projectRepository) {
        this.ciRunRepository = ciRunRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public CiRunResponse createCiRun(CreateCiRunRequest request) {
        Project project = findProject(request.projectId());
        CiRun ciRun = new CiRun(
                project,
                request.commitSha(),
                request.provider(),
                request.status(),
                request.durationSeconds(),
                request.failedTests()
        );

        return toResponse(ciRunRepository.save(ciRun));
    }

    @Transactional(readOnly = true)
    public CiRunResponse getCiRunById(UUID id) {
        return toResponse(findCiRun(id));
    }

    @Transactional(readOnly = true)
    public List<CiRunResponse> getCiRunsByProjectId(UUID projectId) {
        findProject(projectId);
        return ciRunRepository.findByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CiRunResponse> getCiRunsByCommitSha(UUID projectId, String commitSha) {
        findProject(projectId);
        return ciRunRepository.findByProjectIdAndCommitSha(projectId, commitSha)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CiRunResponse> getFailedCiRunsByProjectId(UUID projectId) {
        findProject(projectId);
        return ciRunRepository.findByProjectIdAndStatus(projectId, FAILED_STATUS)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private CiRun findCiRun(UUID id) {
        return ciRunRepository.findById(id)
                .orElseThrow(() -> new CiRunNotFoundException(id));
    }

    private CiRunResponse toResponse(CiRun ciRun) {
        return new CiRunResponse(
                ciRun.getId(),
                ciRun.getProject().getId(),
                ciRun.getCommitSha(),
                ciRun.getProvider(),
                ciRun.getStatus(),
                ciRun.getDurationSeconds(),
                ciRun.getFailedTests(),
                ciRun.getCreatedAt()
        );
    }
}
