package com.deployguard.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deployguard.api.project.dto.CreateProjectRequest;
import com.deployguard.api.project.dto.ProjectResponse;
import com.deployguard.api.project.dto.UpdateProjectRequest;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository);
    }

    @Test
    void createProjectSavesAndReturnsProject() {
        CreateProjectRequest request = new CreateProjectRequest(
                "DeployGuard API",
                "https://github.com/example/deployguard",
                "deployguard-api"
        );

        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> {
            Project project = invocation.getArgument(0);
            setField(project, "id", UUID.randomUUID());
            setField(project, "createdAt", OffsetDateTime.now());
            setField(project, "updatedAt", OffsetDateTime.now());
            return project;
        });

        ProjectResponse response = projectService.createProject(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("DeployGuard API");
        assertThat(response.githubRepoUrl()).isEqualTo("https://github.com/example/deployguard");
        assertThat(response.serviceName()).isEqualTo("deployguard-api");
    }

    @Test
    void getProjectByIdReturnsProject() {
        UUID id = UUID.randomUUID();
        Project project = project(id, "DeployGuard API", "https://github.com/example/deployguard", "deployguard-api");
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));

        ProjectResponse response = projectService.getProjectById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("DeployGuard API");
    }

    @Test
    void getProjectByIdThrowsWhenProjectDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(projectRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectById(id))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project not found: " + id);
    }

    @Test
    void updateProjectUpdatesAndReturnsProject() {
        UUID id = UUID.randomUUID();
        Project existing = project(id, "Old", "https://github.com/example/old", "old-service");
        OffsetDateTime originalUpdatedAt = existing.getUpdatedAt();
        UpdateProjectRequest request = new UpdateProjectRequest(
                "New",
                "https://github.com/example/new",
                "new-service"
        );

        when(projectRepository.findById(id)).thenReturn(Optional.of(existing));
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectResponse response = projectService.updateProject(id, request);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("New");
        assertThat(response.githubRepoUrl()).isEqualTo("https://github.com/example/new");
        assertThat(response.serviceName()).isEqualTo("new-service");
        assertThat(response.updatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void deleteProjectDeletesExistingProject() {
        UUID id = UUID.randomUUID();
        Project project = project(id, "DeployGuard API", "https://github.com/example/deployguard", "deployguard-api");
        when(projectRepository.findById(id)).thenReturn(Optional.of(project));

        projectService.deleteProject(id);

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).delete(projectCaptor.capture());
        assertThat(projectCaptor.getValue()).isSameAs(project);
    }

    private Project project(UUID id, String name, String githubRepoUrl, String serviceName) {
        Project project = new Project(name, githubRepoUrl, serviceName);
        setField(project, "id", id);
        setField(project, "createdAt", OffsetDateTime.now());
        setField(project, "updatedAt", OffsetDateTime.now());
        return project;
    }

    private void setField(Project project, String fieldName, Object value) {
        try {
            Field field = Project.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(project, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set Project." + fieldName, exception);
        }
    }
}
