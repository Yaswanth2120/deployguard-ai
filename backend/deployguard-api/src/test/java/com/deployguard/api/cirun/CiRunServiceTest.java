package com.deployguard.api.cirun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deployguard.api.cirun.dto.CiRunResponse;
import com.deployguard.api.cirun.dto.CreateCiRunRequest;
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
class CiRunServiceTest {

    @Mock
    private CiRunRepository ciRunRepository;

    @Mock
    private ProjectRepository projectRepository;

    private CiRunService ciRunService;

    @BeforeEach
    void setUp() {
        ciRunService = new CiRunService(ciRunRepository, projectRepository);
    }

    @Test
    void createCiRunSavesAndReturnsCiRun() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        CreateCiRunRequest request = request(projectId, "SUCCESS", 0);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(ciRunRepository.save(any(CiRun.class))).thenAnswer(invocation -> {
            CiRun ciRun = invocation.getArgument(0);
            setField(ciRun, "id", UUID.randomUUID());
            setField(ciRun, "createdAt", OffsetDateTime.now());
            return ciRun;
        });

        CiRunResponse response = ciRunService.createCiRun(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.commitSha()).isEqualTo("abc123");
        assertThat(response.provider()).isEqualTo("github-actions");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.durationSeconds()).isEqualTo(120);
        assertThat(response.failedTests()).isZero();
    }

    @Test
    void getCiRunByIdReturnsCiRun() {
        UUID projectId = UUID.randomUUID();
        UUID ciRunId = UUID.randomUUID();
        CiRun ciRun = ciRun(ciRunId, project(projectId), "SUCCESS", 0);
        when(ciRunRepository.findById(ciRunId)).thenReturn(Optional.of(ciRun));

        CiRunResponse response = ciRunService.getCiRunById(ciRunId);

        assertThat(response.id()).isEqualTo(ciRunId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.commitSha()).isEqualTo("abc123");
    }

    @Test
    void getCiRunsByProjectIdReturnsRunsForExistingProject() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        CiRun ciRun = ciRun(UUID.randomUUID(), project, "SUCCESS", 0);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(ciRunRepository.findByProjectId(projectId)).thenReturn(List.of(ciRun));

        List<CiRunResponse> responses = ciRunService.getCiRunsByProjectId(projectId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).projectId()).isEqualTo(projectId);
        verify(ciRunRepository).findByProjectId(projectId);
    }

    @Test
    void getFailedCiRunsByProjectIdReturnsFailedRunsForExistingProject() {
        UUID projectId = UUID.randomUUID();
        Project project = project(projectId);
        CiRun failedCiRun = ciRun(UUID.randomUUID(), project, "FAILED", 3);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(ciRunRepository.findByProjectIdAndStatus(projectId, "FAILED")).thenReturn(List.of(failedCiRun));

        List<CiRunResponse> responses = ciRunService.getFailedCiRunsByProjectId(projectId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).status()).isEqualTo("FAILED");
        assertThat(responses.get(0).failedTests()).isEqualTo(3);
        verify(ciRunRepository).findByProjectIdAndStatus(projectId, "FAILED");
    }

    @Test
    void createCiRunThrowsWhenProjectDoesNotExist() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ciRunService.createCiRun(request(projectId, "SUCCESS", 0)))
                .isInstanceOf(ProjectNotFoundException.class)
                .hasMessage("Project not found: " + projectId);
    }

    @Test
    void getCiRunByIdThrowsWhenCiRunDoesNotExist() {
        UUID ciRunId = UUID.randomUUID();
        when(ciRunRepository.findById(ciRunId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ciRunService.getCiRunById(ciRunId))
                .isInstanceOf(CiRunNotFoundException.class)
                .hasMessage("CI run not found: " + ciRunId);
    }

    private CreateCiRunRequest request(UUID projectId, String status, int failedTests) {
        return new CreateCiRunRequest(
                projectId,
                "abc123",
                "github-actions",
                status,
                120,
                failedTests
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

    private CiRun ciRun(UUID id, Project project, String status, int failedTests) {
        CiRun ciRun = new CiRun(
                project,
                "abc123",
                "github-actions",
                status,
                120,
                failedTests
        );
        setField(ciRun, "id", id);
        setField(ciRun, "createdAt", OffsetDateTime.now());
        return ciRun;
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
