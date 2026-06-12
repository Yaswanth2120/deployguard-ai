package com.deployguard.api.applicationlog;

import com.deployguard.api.applicationlog.dto.ApplicationLogResponse;
import com.deployguard.api.applicationlog.dto.CreateApplicationLogRequest;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import com.deployguard.api.project.Project;
import com.deployguard.api.project.ProjectNotFoundException;
import com.deployguard.api.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationLogService {

    private static final String ERROR_LEVEL = "ERROR";

    private final ApplicationLogRepository applicationLogRepository;
    private final ProjectRepository projectRepository;
    private final DeploymentRepository deploymentRepository;

    public ApplicationLogService(
            ApplicationLogRepository applicationLogRepository,
            ProjectRepository projectRepository,
            DeploymentRepository deploymentRepository
    ) {
        this.applicationLogRepository = applicationLogRepository;
        this.projectRepository = projectRepository;
        this.deploymentRepository = deploymentRepository;
    }

    @Transactional
    public ApplicationLogResponse createLog(CreateApplicationLogRequest request) {
        Project project = findProject(request.projectId());
        Deployment deployment = request.deploymentId() == null
                ? null
                : findDeployment(request.deploymentId());

        ApplicationLog applicationLog = new ApplicationLog(
                project,
                deployment,
                request.serviceName(),
                request.level(),
                request.message(),
                request.timestamp()
        );

        return toResponse(applicationLogRepository.save(applicationLog));
    }

    @Transactional(readOnly = true)
    public List<ApplicationLogResponse> getLogsByProjectId(UUID projectId) {
        findProject(projectId);
        return applicationLogRepository.findByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationLogResponse> getLogsByDeploymentId(UUID deploymentId) {
        findDeployment(deploymentId);
        return applicationLogRepository.findByDeploymentId(deploymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ApplicationLogResponse> getErrorLogsByProjectId(UUID projectId) {
        findProject(projectId);
        return applicationLogRepository.findByProjectIdAndLevel(projectId, ERROR_LEVEL)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private Deployment findDeployment(UUID deploymentId) {
        return deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
    }

    private ApplicationLogResponse toResponse(ApplicationLog applicationLog) {
        Deployment deployment = applicationLog.getDeployment();
        return new ApplicationLogResponse(
                applicationLog.getId(),
                applicationLog.getProject().getId(),
                deployment == null ? null : deployment.getId(),
                applicationLog.getServiceName(),
                applicationLog.getLevel(),
                applicationLog.getMessage(),
                applicationLog.getTimestamp(),
                applicationLog.getCreatedAt()
        );
    }
}
