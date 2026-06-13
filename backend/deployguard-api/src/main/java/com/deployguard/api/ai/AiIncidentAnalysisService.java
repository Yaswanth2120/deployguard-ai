package com.deployguard.api.ai;

import com.deployguard.api.ai.dto.AiIncidentAnalysisResponse;
import com.deployguard.api.ai.dto.AiIncidentSummaryResponse;
import com.deployguard.api.applicationlog.ApplicationLog;
import com.deployguard.api.applicationlog.ApplicationLogRepository;
import com.deployguard.api.cirun.CiRun;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import com.deployguard.api.project.Project;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiIncidentAnalysisService {

    private static final String MODEL_NAME = "nvidia/nemotron-3-ultra-550b-a55b:free";

    private final DeploymentRepository deploymentRepository;
    private final CiRunRepository ciRunRepository;
    private final ApplicationLogRepository applicationLogRepository;
    private final AiIncidentSummaryRepository aiIncidentSummaryRepository;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${deployguard.ai-service.base-url:http://localhost:8001}")
    private String aiServiceBaseUrl;

    public AiIncidentAnalysisService(
            DeploymentRepository deploymentRepository,
            CiRunRepository ciRunRepository,
            ApplicationLogRepository applicationLogRepository,
            AiIncidentSummaryRepository aiIncidentSummaryRepository,
            ObjectMapper objectMapper,
            RestClient.Builder restClientBuilder) {
        this.deploymentRepository = deploymentRepository;
        this.ciRunRepository = ciRunRepository;
        this.applicationLogRepository = applicationLogRepository;
        this.aiIncidentSummaryRepository = aiIncidentSummaryRepository;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Transactional
    public AiIncidentSummaryResponse analyzeDeployment(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));
        Project project = deployment.getProject();
        UUID projectId = project.getId();

        List<CiRun> ciRuns = ciRunRepository.findByProjectIdAndCommitSha(projectId, deployment.getCommitSha());
        List<ApplicationLog> logs = applicationLogRepository.findByDeploymentId(deploymentId);
        AiIncidentAnalysisResponse aiResponse = callAiService(deployment, project, ciRuns, logs);

        AiIncidentSummary summary = new AiIncidentSummary(
                deployment,
                aiResponse.summary(),
                aiResponse.likelyRootCause(),
                toJson(aiResponse.evidence()),
                toJson(aiResponse.recommendedActions()),
                aiResponse.severity(),
                aiResponse.confidence(),
                MODEL_NAME);

        return toResponse(aiIncidentSummaryRepository.save(summary));
    }

    @Transactional(readOnly = true)
    public List<AiIncidentSummaryResponse> getSummariesByDeploymentId(UUID deploymentId) {
        if (!deploymentRepository.existsById(deploymentId)) {
            throw new DeploymentNotFoundException(deploymentId);
        }

        return aiIncidentSummaryRepository.findByDeploymentId(deploymentId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AiIncidentAnalysisResponse callAiService(
            Deployment deployment,
            Project project,
            List<CiRun> ciRuns,
            List<ApplicationLog> logs) {
        try {
            AiIncidentAnalysisResponse response = restClientBuilder
                    .baseUrl(aiServiceBaseUrl)
                    .build()
                    .post()
                    .uri("/analyze-incident")
                    .body(buildAiRequest(deployment, project, ciRuns, logs))
                    .retrieve()
                    .onStatus(
                            statusCode -> statusCode.isError(),
                            (request, clientResponse) -> {
                                throw new AiServiceUnavailableException(
                                        "AI service returned status " + clientResponse.getStatusCode().value(),
                                        null);
                            })
                    .body(AiIncidentAnalysisResponse.class);

            if (response == null) {
                throw new AiServiceUnavailableException("AI service returned an empty response.", null);
            }

            return response;
        } catch (RestClientException ex) {
            throw new AiServiceUnavailableException("AI service is unavailable.", ex);
        }
    }

    private Map<String, Object> buildAiRequest(
            Deployment deployment,
            Project project,
            List<CiRun> ciRuns,
            List<ApplicationLog> logs) {
        return Map.of(
                "deployment", deploymentPayload(deployment, project),
                "ciRuns", ciRuns.stream().map(this::ciRunPayload).toList(),
                "logs", logs.stream().map(this::logPayload).toList(),
                "riskScore", deployment.getRiskScore(),
                "riskLevel", deployment.getRiskLevel());
    }

    private Map<String, Object> deploymentPayload(Deployment deployment, Project project) {
        return Map.of(
                "id", deployment.getId(),
                "projectId", project.getId(),
                "projectName", project.getName(),
                "serviceName", project.getServiceName(),
                "commitSha", deployment.getCommitSha(),
                "branch", deployment.getBranch(),
                "environment", deployment.getEnvironment(),
                "status", deployment.getStatus(),
                "deployedBy", deployment.getDeployedBy(),
                "deployedAt", deployment.getDeployedAt());
    }

    private Map<String, Object> ciRunPayload(CiRun ciRun) {
        return Map.of(
                "id", ciRun.getId(),
                "commitSha", ciRun.getCommitSha(),
                "provider", ciRun.getProvider(),
                "status", ciRun.getStatus(),
                "durationSeconds", ciRun.getDurationSeconds(),
                "failedTests", ciRun.getFailedTests(),
                "createdAt", ciRun.getCreatedAt());
    }

    private Map<String, Object> logPayload(ApplicationLog log) {
        return Map.of(
                "id", log.getId(),
                "serviceName", log.getServiceName(),
                "level", log.getLevel(),
                "message", log.getMessage(),
                "timestamp", log.getTimestamp(),
                "createdAt", log.getCreatedAt());
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new AiServiceUnavailableException("Failed to serialize AI summary details.", ex);
        }
    }

    private AiIncidentSummaryResponse toResponse(AiIncidentSummary summary) {
        return new AiIncidentSummaryResponse(
                summary.getId(),
                summary.getDeployment().getId(),
                summary.getSummary(),
                summary.getLikelyRootCause(),
                summary.getEvidence(),
                summary.getRecommendedActions(),
                summary.getSeverity(),
                summary.getConfidence(),
                summary.getModelName(),
                summary.getCreatedAt());
    }
}
