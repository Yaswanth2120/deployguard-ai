package com.deployguard.api.project;

import com.deployguard.api.project.dto.CreateProjectRequest;
import com.deployguard.api.project.dto.ProjectResponse;
import com.deployguard.api.project.dto.UpdateProjectRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public ProjectResponse createProject(CreateProjectRequest request) {
        Project project = new Project(request.name(), request.githubRepoUrl(), request.serviceName());
        return toResponse(projectRepository.save(project));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(UUID id) {
        return toResponse(findProject(id));
    }

    @Transactional
    public ProjectResponse updateProject(UUID id, UpdateProjectRequest request) {
        Project project = findProject(id);
        project.updateDetails(request.name(), request.githubRepoUrl(), request.serviceName());
        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void deleteProject(UUID id) {
        Project project = findProject(id);
        projectRepository.delete(project);
    }

    private Project findProject(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getGithubRepoUrl(),
                project.getServiceName(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
