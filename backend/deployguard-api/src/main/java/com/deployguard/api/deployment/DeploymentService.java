package com.deployguard.api.deployment;

import com.deployguard.api.deployment.dto.CreateDeploymentRequest;
import com.deployguard.api.deployment.dto.DeploymentResponse;
import com.deployguard.api.project.Project;
import com.deployguard.api.project.ProjectNotFoundException;
import com.deployguard.api.project.ProjectRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;
    private final ProjectRepository projectRepository;

    public DeploymentService(DeploymentRepository deploymentRepository, ProjectRepository projectRepository) {
        this.deploymentRepository = deploymentRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public DeploymentResponse createDeployment(CreateDeploymentRequest request) {
        Project project = findProject(request.projectId());
        Deployment deployment = new Deployment(
                project,
                request.commitSha(),
                request.branch(),
                request.environment(),
                request.status(),
                request.deployedBy(),
                request.deployedAt()
        );

        return toResponse(deploymentRepository.save(deployment));
    }

    @Transactional(readOnly = true)
    public DeploymentResponse getDeploymentById(UUID id) {
        return toResponse(findDeployment(id));
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeploymentsByProjectId(UUID projectId) {
        findProject(projectId);
        return deploymentRepository.findByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Project findProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private Deployment findDeployment(UUID id) {
        return deploymentRepository.findById(id)
                .orElseThrow(() -> new DeploymentNotFoundException(id));
    }

    private DeploymentResponse toResponse(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getProject().getId(),
                deployment.getCommitSha(),
                deployment.getBranch(),
                deployment.getEnvironment(),
                deployment.getStatus(),
                deployment.getDeployedBy(),
                deployment.getDeployedAt(),
                deployment.getRiskScore(),
                deployment.getRiskLevel(),
                deployment.getCreatedAt(),
                deployment.getUpdatedAt()
        );
    }
}
