package com.deployguard.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.deployguard.api.ai.dto.AiIncidentSummaryResponse;
import com.deployguard.api.applicationlog.ApplicationLog;
import com.deployguard.api.applicationlog.ApplicationLogRepository;
import com.deployguard.api.cirun.CiRun;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import com.deployguard.api.project.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiIncidentAnalysisServiceTest {

    private static final String AI_SERVICE_BASE_URL = "http://ai-service.test";

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private CiRunRepository ciRunRepository;

    @Mock
    private ApplicationLogRepository applicationLogRepository;

    @Mock
    private AiIncidentSummaryRepository aiIncidentSummaryRepository;

    private MockRestServiceServer mockServer;
    private AiIncidentAnalysisService aiIncidentAnalysisService;

    @BeforeEach
    void setUp() {
        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        aiIncidentAnalysisService = new AiIncidentAnalysisService(
                deploymentRepository,
                ciRunRepository,
                applicationLogRepository,
                aiIncidentSummaryRepository,
                new ObjectMapper().findAndRegisterModules(),
                restClientBuilder);
        ReflectionTestUtils.setField(aiIncidentAnalysisService, "aiServiceBaseUrl", AI_SERVICE_BASE_URL);
    }

    @Test
    void analyzeDeploymentThrowsWhenDeploymentNotFound() {
        UUID deploymentId = UUID.randomUUID();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiIncidentAnalysisService.analyzeDeployment(deploymentId))
                .isInstanceOf(DeploymentNotFoundException.class);
    }

    @Test
    void analyzeDeploymentHandlesAiServiceUnavailable() {
        Deployment deployment = deployment();
        UUID deploymentId = deployment.getId();
        Project project = deployment.getProject();

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(ciRunRepository.findByProjectIdAndCommitSha(project.getId(), deployment.getCommitSha()))
                .thenReturn(List.of());
        when(applicationLogRepository.findByDeploymentId(deploymentId)).thenReturn(List.of());

        mockServer.expect(requestTo(AI_SERVICE_BASE_URL + "/analyze-incident"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> aiIncidentAnalysisService.analyzeDeployment(deploymentId))
                .isInstanceOf(AiServiceUnavailableException.class)
                .hasMessageContaining("AI service returned status 500");

        mockServer.verify();
    }

    @Test
    void analyzeDeploymentSavesSuccessfulAiResponse() {
        Deployment deployment = deployment();
        UUID deploymentId = deployment.getId();
        Project project = deployment.getProject();
        CiRun ciRun = ciRun(project);
        ApplicationLog log = applicationLog(project, deployment);

        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));
        when(ciRunRepository.findByProjectIdAndCommitSha(project.getId(), deployment.getCommitSha()))
                .thenReturn(List.of(ciRun));
        when(applicationLogRepository.findByDeploymentId(deploymentId)).thenReturn(List.of(log));
        when(aiIncidentSummaryRepository.save(any(AiIncidentSummary.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockServer.expect(requestTo(AI_SERVICE_BASE_URL + "/analyze-incident"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.deployment.id").value(deploymentId.toString()))
                .andExpect(jsonPath("$.ciRuns[0].status").value("FAILED"))
                .andExpect(jsonPath("$.logs[0].level").value("ERROR"))
                .andExpect(jsonPath("$.riskScore").value(85))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andRespond(withSuccess("""
                        {
                          "summary": "Incident summary",
                          "likelyRootCause": "Failed tests and error logs",
                          "evidence": ["CI failed", "ERROR log found"],
                          "recommendedActions": ["Review CI", "Check logs"],
                          "severity": "HIGH",
                          "confidence": "MEDIUM"
                        }
                        """, MediaType.APPLICATION_JSON));

        AiIncidentSummaryResponse response = aiIncidentAnalysisService.analyzeDeployment(deploymentId);

        assertThat(response.deploymentId()).isEqualTo(deploymentId);
        assertThat(response.summary()).isEqualTo("Incident summary");
        assertThat(response.likelyRootCause()).isEqualTo("Failed tests and error logs");
        assertThat(response.evidence()).isEqualTo("[\"CI failed\",\"ERROR log found\"]");
        assertThat(response.recommendedActions()).isEqualTo("[\"Review CI\",\"Check logs\"]");
        assertThat(response.severity()).isEqualTo("HIGH");
        assertThat(response.confidence()).isEqualTo("MEDIUM");
        assertThat(response.modelName()).isEqualTo("nvidia/nemotron-3-ultra-550b-a55b:free");

        ArgumentCaptor<AiIncidentSummary> summaryCaptor = ArgumentCaptor.forClass(AiIncidentSummary.class);
        verify(aiIncidentSummaryRepository).save(summaryCaptor.capture());
        assertThat(summaryCaptor.getValue().getDeployment()).isEqualTo(deployment);
        mockServer.verify();
    }

    @Test
    void getSummariesByDeploymentIdReturnsPreviousSummaries() {
        Deployment deployment = deployment();
        UUID deploymentId = deployment.getId();
        AiIncidentSummary summary = new AiIncidentSummary(
                deployment,
                "Existing summary",
                "Existing root cause",
                "[]",
                "[]",
                "LOW",
                "HIGH",
                "test-model");

        when(deploymentRepository.existsById(deploymentId)).thenReturn(true);
        when(aiIncidentSummaryRepository.findByDeploymentId(deploymentId)).thenReturn(List.of(summary));

        List<AiIncidentSummaryResponse> responses = aiIncidentAnalysisService.getSummariesByDeploymentId(deploymentId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().deploymentId()).isEqualTo(deploymentId);
        assertThat(responses.getFirst().summary()).isEqualTo("Existing summary");
    }

    private Deployment deployment() {
        UUID deploymentId = UUID.randomUUID();
        Project project = project();
        Deployment deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn(deploymentId);
        when(deployment.getProject()).thenReturn(project);
        when(deployment.getCommitSha()).thenReturn("abc123");
        when(deployment.getBranch()).thenReturn("main");
        when(deployment.getEnvironment()).thenReturn("prod");
        when(deployment.getStatus()).thenReturn("DEPLOYED");
        when(deployment.getDeployedBy()).thenReturn("codex");
        when(deployment.getDeployedAt()).thenReturn(OffsetDateTime.parse("2026-06-13T00:00:00Z"));
        when(deployment.getRiskScore()).thenReturn(85);
        when(deployment.getRiskLevel()).thenReturn("HIGH");
        return deployment;
    }

    private Project project() {
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(project.getName()).thenReturn("DeployGuard");
        when(project.getServiceName()).thenReturn("deployguard-api");
        return project;
    }

    private CiRun ciRun(Project project) {
        CiRun ciRun = mock(CiRun.class);
        when(ciRun.getId()).thenReturn(UUID.randomUUID());
        when(ciRun.getCommitSha()).thenReturn("abc123");
        when(ciRun.getProvider()).thenReturn("github-actions");
        when(ciRun.getStatus()).thenReturn("FAILED");
        when(ciRun.getDurationSeconds()).thenReturn(120);
        when(ciRun.getFailedTests()).thenReturn(3);
        when(ciRun.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-06-13T00:00:00Z"));
        return ciRun;
    }

    private ApplicationLog applicationLog(Project project, Deployment deployment) {
        ApplicationLog log = mock(ApplicationLog.class);
        when(log.getId()).thenReturn(UUID.randomUUID());
        when(log.getServiceName()).thenReturn("deployguard-api");
        when(log.getLevel()).thenReturn("ERROR");
        when(log.getMessage()).thenReturn("Payment API timeout");
        when(log.getTimestamp()).thenReturn(OffsetDateTime.parse("2026-06-13T00:01:00Z"));
        when(log.getCreatedAt()).thenReturn(OffsetDateTime.parse("2026-06-13T00:01:10Z"));
        return log;
    }
}
