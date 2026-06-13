package com.deployguard.api.risk;

import com.deployguard.api.applicationlog.ApplicationLogRepository;
import com.deployguard.api.cirun.CiRun;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import com.deployguard.api.deployment.dto.DeploymentResponse;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskScoringService {

    private static final int FAILED_CI_RISK = 30;
    private static final int FAILED_TESTS_RISK = 20;
    private static final int ERROR_LOG_RISK = 30;
    private static final int HOTFIX_BRANCH_RISK = 10;
    private static final int PRODUCTION_ENVIRONMENT_RISK = 10;
    private static final int MAX_RISK_SCORE = 100;

    private final DeploymentRepository deploymentRepository;
    private final CiRunRepository ciRunRepository;
    private final ApplicationLogRepository applicationLogRepository;

    public RiskScoringService(
            DeploymentRepository deploymentRepository,
            CiRunRepository ciRunRepository,
            ApplicationLogRepository applicationLogRepository
    ) {
        this.deploymentRepository = deploymentRepository;
        this.ciRunRepository = ciRunRepository;
        this.applicationLogRepository = applicationLogRepository;
    }

    @Transactional
    public DeploymentResponse calculateAndUpdateRiskScore(UUID deploymentId) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new DeploymentNotFoundException(deploymentId));

        List<CiRun> relatedCiRuns = ciRunRepository.findByProjectIdAndCommitSha(
                deployment.getProject().getId(),
                deployment.getCommitSha()
        );

        int score = 0;

        if (relatedCiRuns.stream().anyMatch(ciRun -> equalsIgnoreCase(ciRun.getStatus(), "FAILED"))) {
            score += FAILED_CI_RISK;
        }

        if (relatedCiRuns.stream().anyMatch(ciRun -> ciRun.getFailedTests() > 0)) {
            score += FAILED_TESTS_RISK;
        }

        if (applicationLogRepository.findByDeploymentId(deploymentId)
                .stream()
                .anyMatch(applicationLog -> equalsIgnoreCase(applicationLog.getLevel(), "ERROR"))) {
            score += ERROR_LOG_RISK;
        }

        if (containsIgnoreCase(deployment.getBranch(), "hotfix")) {
            score += HOTFIX_BRANCH_RISK;
        }

        if (isProductionEnvironment(deployment.getEnvironment())) {
            score += PRODUCTION_ENVIRONMENT_RISK;
        }

        int cappedScore = Math.min(score, MAX_RISK_SCORE);
        deployment.updateRisk(cappedScore, riskLevel(cappedScore));

        return toResponse(deploymentRepository.save(deployment));
    }

    private boolean isProductionEnvironment(String environment) {
        return equalsIgnoreCase(environment, "production") || equalsIgnoreCase(environment, "prod");
    }

    private boolean containsIgnoreCase(String value, String expected) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }

    private String riskLevel(int score) {
        if (score <= 30) {
            return "LOW";
        }
        if (score <= 70) {
            return "MEDIUM";
        }
        return "HIGH";
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
