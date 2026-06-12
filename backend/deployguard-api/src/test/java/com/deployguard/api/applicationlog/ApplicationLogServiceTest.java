package com.deployguard.api.applicationlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deployguard.api.applicationlog.dto.ApplicationLogResponse;
import com.deployguard.api.applicationlog.dto.CreateApplicationLogRequest;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationLogServiceTest {

    @Mock
    private ApplicationLogRepository applicationLogRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DeploymentRepository deploymentRepository;

    private ApplicationLogService applicationLogService;

    @BeforeEach
    void setUp() {
        applicationLogService = new ApplicationLogService(
                applicationLogRepository,
                projectRepository,
                deploymentRepository
        );
    }

    @Test
    void createLogWithoutDeploymentSavesAndReturnsLog() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        CreateApplicationLogRequest request = request(projectId, null, "INFO");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(applicationLogRepository.save(any(ApplicationLog.class))).thenAnswer(invocation -> {
            ApplicationLog applicationLog = invocation.getArgument(0);
            setField(applicationLog, "id", UUID.randomUUID());
            setField(applicationLog, "createdAt", OffsetDateTime.now());
            return applicationLog;
        });

        ApplicationLogResponse response = applicationLogService.createLog(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.deploymentId()).isNull();
        assertThat(response.serviceName()).isEqualTo("deployguard-api");
        assertThat(response.level()).isEqualTo("INFO");
        assertThat(response.message()).isEqualTo("Deployment completed");
    }

    @Test
    void createLogWithDeploymentSavesAndReturnsLog() {
        UUID projectId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Project project = project(projectId);
        Deployment deployment = deployment(deploymentId, project);
        CreateApplicationLogRequest request = request(projectId, deploymentId, "WARN");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(applicationLogRepository.save(any(ApplicationLog.class))).thenAnswer(invocation -> {
            ApplicationLog applicationLog = invocation.getArgument(0);
            setField(applicationLog, "id", UUID.randomUUID());
            setField(applicationLog, "createdAt", OffsetDateTime.now());
            return applicationLog;
        });

        ApplicationLogResponse response = applicationLogService.createLog(request);

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.deploymentId()).isEqualTo(deploymentId);
        assertThat(response.level()).isEqualTo("WARN");
    }

    @Test
    void getLogsByProjectIdReturnsLogsForExistingProject() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        ApplicationLog applicationLog = applicationLog(UUID.randomUUID(), project, null, "INFO");

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(applicationLogRepository.findByProjectId(projectId)).thenReturn(List.of(applicationLog));

        List<ApplicationLogResponse> responses = applicationLogService.getLogsByProjectId(projectId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).projectId()).isEqualTo(projectId);
        verify(applicationLogRepository).findByProjectId(projectId);
    }

    @Test
    void getLogsByDeploymentIdReturnsLogsForExistingDeployment() {
        UUID projectId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Project project = project(projectId);
        Deployment deployment = deployment(deploymentId, project);
        ApplicationLog applicationLog = applicationLog(UUID.randomUUID(), project, deployment, "ERROR");

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(applicationLogRepository.findByDeploymentId(deploymentId)).thenReturn(List.of(applicationLog));

        List<ApplicationLogResponse> responses = applicationLogService.getLogsByDeploymentId(deploymentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).deploymentId()).isEqualTo(deploymentId);
        verify(applicationLogRepository).findByDeploymentId(deploymentId);
    }

    @Test
    void createLogThrowsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationLogService.createLog(request(projectId, null, "INFO")))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project not found: " + projectId);
    }

    @Test
    void createLogThrowsWhenDeploymentDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        Project project = project(projectId);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationLogService.createLog(request(projectId, deploymentId, "INFO")))
                .isInstanceOf(DeploymentNotFoundException.class)
                .hasMessage("Deployment not found: " + deploymentId);
    }

    private CreateApplicationLogRequest request(UUID projectId, UUID deploymentId, String level) {
        return new CreateApplicationLogRequest(
                projectId,
                deploymentId,
                "deployguard-api",
                level,
                "Deployment completed",
                OffsetDateTime.parse("2026-06-12T12:00:00Z")
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
                OffsetDateTime.parse("2026-06-12T12:00:00Z")
        );
        setField(deployment, "id", id);
        setField(deployment, "createdAt", OffsetDateTime.now());
        setField(deployment, "updatedAt", OffsetDateTime.now());
        return deployment;
    }

    private ApplicationLog applicationLog(UUID id, Project project, Deployment deployment, String level) {
        ApplicationLog applicationLog = new ApplicationLog(
                project,
                deployment,
                "deployguard-api",
                level,
                "Deployment completed",
                OffsetDateTime.parse("2026-06-12T12:00:00Z")
        );
        setField(applicationLog, "id", id);
        setField(applicationLog, "createdAt", OffsetDateTime.now());
        return applicationLog;
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
