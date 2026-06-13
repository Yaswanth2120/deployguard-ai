package com.deployguard.api.risk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deployguard.api.applicationlog.ApplicationLog;
import com.deployguard.api.applicationlog.ApplicationLogRepository;
import com.deployguard.api.cirun.CiRun;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import com.deployguard.api.deployment.dto.DeploymentResponse;
import com.deployguard.api.project.Project;
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
class RiskScoringServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private CiRunRepository ciRunRepository;

    @Mock
    private ApplicationLogRepository applicationLogRepository;

    private RiskScoringService riskScoringService;

    @BeforeEach
    void setUp() {
        riskScoringService = new RiskScoringService(
                deploymentRepository,
                ciRunRepository,
                applicationLogRepository
        );
    }

    @Test
    void lowRiskDeploymentHasLowRiskLevel() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project(UUID.randomUUID());
        Deployment deployment = deployment(deploymentId, project, "main", "staging");
        mockDeploymentInputs(deployment, List.of(), List.of());

        DeploymentResponse response = riskScoringService.calculateAndUpdateRiskScore(deploymentId);

        assertThat(response.riskScore()).isZero();
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void failedCiRunAddsRisk() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project(UUID.randomUUID());
        Deployment deployment = deployment(deploymentId, project, "main", "staging");
        mockDeploymentInputs(deployment, List.of(ciRun(project, "FAILED", 0)), List.of());

        DeploymentResponse response = riskScoringService.calculateAndUpdateRiskScore(deploymentId);

        assertThat(response.riskScore()).isEqualTo(30);
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void failedTestsAddRisk() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project(UUID.randomUUID());
        Deployment deployment = deployment(deploymentId, project, "main", "staging");
        mockDeploymentInputs(deployment, List.of(ciRun(project, "SUCCESS", 2)), List.of());

        DeploymentResponse response = riskScoringService.calculateAndUpdateRiskScore(deploymentId);

        assertThat(response.riskScore()).isEqualTo(20);
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void errorLogsAddRisk() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project(UUID.randomUUID());
        Deployment deployment = deployment(deploymentId, project, "main", "staging");
        mockDeploymentInputs(deployment, List.of(), List.of(applicationLog(project, deployment, "ERROR")));

        DeploymentResponse response = riskScoringService.calculateAndUpdateRiskScore(deploymentId);

        assertThat(response.riskScore()).isEqualTo(30);
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void hotfixBranchAddsRisk() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project(UUID.randomUUID());
        Deployment deployment = deployment(deploymentId, project, "hotfix/payment-timeout", "staging");
        mockDeploymentInputs(deployment, List.of(), List.of());

        DeploymentResponse response = riskScoringService.calculateAndUpdateRiskScore(deploymentId);

        assertThat(response.riskScore()).isEqualTo(10);
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void productionEnvironmentAddsRisk() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project(UUID.randomUUID());
        Deployment deployment = deployment(deploymentId, project, "main", "production");
        mockDeploymentInputs(deployment, List.of(), List.of());

        DeploymentResponse response = riskScoringService.calculateAndUpdateRiskScore(deploymentId);

        assertThat(response.riskScore()).isEqualTo(10);
        assertThat(response.riskLevel()).isEqualTo("LOW");
    }

    @Test
    void scoreCapsAtOneHundred() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project(UUID.randomUUID());
        Deployment deployment = deployment(deploymentId, project, "hotfix/payment-timeout", "prod");
        mockDeploymentInputs(
                deployment,
                List.of(ciRun(project, "FAILED", 5)),
                List.of(applicationLog(project, deployment, "ERROR"))
        );

        DeploymentResponse response = riskScoringService.calculateAndUpdateRiskScore(deploymentId);

        assertThat(response.riskScore()).isEqualTo(100);
        assertThat(response.riskLevel()).isEqualTo("HIGH");
    }

    @Test
    void deploymentNotFoundThrowsException() {
        UUID deploymentId = UUID.randomUUID();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> riskScoringService.calculateAndUpdateRiskScore(deploymentId))
                .isInstanceOf(DeploymentNotFoundException.class)
                .hasMessage("Deployment not found: " + deploymentId);
    }

    private void mockDeploymentInputs(Deployment deployment, List<CiRun> ciRuns, List<ApplicationLog> logs) {
        when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(ciRunRepository.findByProjectIdAndCommitSha(deployment.getProject().getId(), deployment.getCommitSha()))
                .thenReturn(ciRuns);
        when(applicationLogRepository.findByDeploymentId(deployment.getId())).thenReturn(logs);
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(invocation -> invocation.getArgument(0));
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

    private Deployment deployment(UUID id, Project project, String branch, String environment) {
        Deployment deployment = new Deployment(
                project,
                "abc123",
                branch,
                environment,
                "SUCCESS",
                "yaswanth",
                OffsetDateTime.parse("2026-06-13T00:00:00Z")
        );
        setField(deployment, "id", id);
        setField(deployment, "createdAt", OffsetDateTime.now());
        setField(deployment, "updatedAt", OffsetDateTime.now());
        return deployment;
    }

    private CiRun ciRun(Project project, String status, int failedTests) {
        CiRun ciRun = new CiRun(
                project,
                "abc123",
                "github-actions",
                status,
                120,
                failedTests
        );
        setField(ciRun, "id", UUID.randomUUID());
        setField(ciRun, "createdAt", OffsetDateTime.now());
        return ciRun;
    }

    private ApplicationLog applicationLog(Project project, Deployment deployment, String level) {
        ApplicationLog applicationLog = new ApplicationLog(
                project,
                deployment,
                "deployguard-api",
                level,
                "Request failed",
                OffsetDateTime.parse("2026-06-13T00:01:00Z")
        );
        setField(applicationLog, "id", UUID.randomUUID());
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
