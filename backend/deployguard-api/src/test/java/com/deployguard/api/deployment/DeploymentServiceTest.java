package com.deployguard.api.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deployguard.api.deployment.dto.CreateDeploymentRequest;
import com.deployguard.api.deployment.dto.DeploymentResponse;
import com.deployguard.api.project.Project;
import com.deployguard.api.project.ProjectNotFoundException;
import com.deployguard.api.project.ProjectRepository;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ProjectRepository projectRepository;

    private DeploymentService deploymentService;

    @BeforeEach
    void setUp() {
        deploymentService = new DeploymentService(deploymentRepository, projectRepository);
    }

    @Test
    void createDeploymentSavesAndReturnsDeploymentWithRiskDefaults() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        CreateDeploymentRequest request = request(projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> {
            Deployment deployment = invocation.getArgument(0);
            setField(deployment, "id", UUID.randomUUID());
            setField(deployment, "createdAt", OffsetDateTime.now());
            setField(deployment, "updatedAt", OffsetDateTime.now());
            return deployment;
        });

        DeploymentResponse response = deploymentService.createDeployment(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.commitSha()).isEqualTo("abc123");
        assertThat(response.branch()).isEqualTo("main");
        assertThat(response.environment()).isEqualTo("production");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.deployedBy()).isEqualTo("yaswanth");
        assertThat(response.riskScore()).isZero();
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void getDeploymentByIdReturnsDeployment() {
        UUID projectId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = deployment(deploymentId, project(projectId));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        DeploymentResponse response = deploymentService.getDeploymentById(deploymentId);

        assertThat(response.id()).isEqualTo(deploymentId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.commitSha()).isEqualTo("abc123");
    }

    @Test
    void getDeploymentsByProjectIdReturnsDeploymentsForExistingProject() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        Deployment deployment = deployment(UUID.randomUUID(), project);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(deploymentRepository.findByProjectId(projectId)).thenReturn(List.of(deployment));

        List<DeploymentResponse> responses = deploymentService.getDeploymentsByProjectId(projectId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).projectId()).isEqualTo(projectId);
        verify(deploymentRepository).findByProjectId(projectId);
    }

    @Test
    void createDeploymentThrowsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentService.createDeployment(request(projectId)))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project not found: " + projectId);
    }

    @Test
    void getDeploymentByIdThrowsWhenDeploymentDoesNotExist() {
        UUID deploymentId = UUID.randomUUID();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deploymentService.getDeploymentById(deploymentId))
                .isInstanceOf(DeploymentNotFoundException.class)
                .hasMessage("Deployment not found: " + deploymentId);
    }

    private CreateDeploymentRequest request(UUID projectId) {
        return new CreateDeploymentRequest(
                projectId,
                "abc123",
                "main",
                "production",
                "SUCCESS",
                "yaswanth",
                OffsetDateTime.parse("2026-06-11T12:00:00Z")
        );
    }

    private Project project(UUID id) {
        Project project = new Project(
                "DeployGuard API",
                "https://github.com/example/deployguard-ai",
                "deployguard-api"
        );
        setField(project, "id", id);
        setField(project, "createdAt", OffsetDateTime.now());
        setField(project, "updatedAt", OffsetDateTime.now());
        return project;
    }

    private Deployment deployment(UUID id, Project project) {
        Deployment deployment = new Deployment(
                project,
                "abc123",
                "main",
                "production",
                "SUCCESS",
                "yaswanth",
                OffsetDateTime.parse("2026-06-11T12:00:00Z")
        );
        setField(deployment, "id", id);
        setField(deployment, "createdAt", OffsetDateTime.now());
        setField(deployment, "updatedAt", OffsetDateTime.now());
        return deployment;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set " + fieldName, exception);
        }
    }
}
