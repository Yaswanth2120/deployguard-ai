package com.deployguard.api.risk;

import com.deployguard.api.applicationlog.ApplicationLogRepository;
import com.deployguard.api.cirun.CiRun;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentNotFoundException;
import com.deployguard.api.deployment.DeploymentRepository;
import com.deployguard.api.deployment.dto.DeploymentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskScoringService {


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

        List<RiskRules.Factor> factors = RiskRules.factors(
                deployment.getBranch(),
                deployment.getEnvironment(),
                relatedCiRuns.stream().map(CiRun::getStatus).toList(),
                relatedCiRuns.stream().mapToInt(CiRun::getFailedTests).sum(),
                applicationLogRepository.findByDeploymentId(deploymentId).stream()
                        .map(applicationLog -> applicationLog.getLevel())
                        .toList());

        int score = RiskRules.score(factors);
        deployment.updateRisk(score, RiskRules.level(score));

        return toResponse(deploymentRepository.save(deployment));
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
