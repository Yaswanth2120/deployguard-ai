package com.deployguard.api.pullrequest;

import com.deployguard.api.applicationlog.ApplicationLogRepository;
import com.deployguard.api.cirun.CiRun;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.project.Project;
import com.deployguard.api.project.ProjectNotFoundException;
import com.deployguard.api.project.ProjectRepository;
import com.deployguard.api.pullrequest.dto.PullRequestResponse;
import com.deployguard.api.pullrequest.dto.RiskFactorResponse;
import com.deployguard.api.pullrequest.dto.RiskScoreResponse;
import com.deployguard.api.pullrequest.dto.CreatePullRequestRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PullRequestService {
    private final PullRequestRepository pullRequestRepository;
    private final ProjectRepository projectRepository;
    private final CiRunRepository ciRunRepository;
    private final ApplicationLogRepository applicationLogRepository;

    public PullRequestService(
            PullRequestRepository pullRequestRepository,
            ProjectRepository projectRepository,
            CiRunRepository ciRunRepository,
            ApplicationLogRepository applicationLogRepository
    ) {
        this.pullRequestRepository = pullRequestRepository;
        this.projectRepository = projectRepository;
        this.ciRunRepository = ciRunRepository;
        this.applicationLogRepository = applicationLogRepository;
    }

    @Transactional
    public PullRequestResponse create(CreatePullRequestRequest request) {
        Project project = projectRepository.findById(request.projectId()).orElseThrow(() -> new ProjectNotFoundException(request.projectId()));
        return toResponse(pullRequestRepository.save(new PullRequest(project, request.provider(), request.providerPullRequestId(), request.number(), request.title(), request.authorId(), request.sourceBranch(), request.targetBranch(), request.commitSha(), request.status())));
    }
    @Transactional(readOnly = true)
    public RiskScoreResponse getRiskScore(UUID id) {
        PullRequest pullRequest = pullRequestRepository.findById(id).orElseThrow(() -> new PullRequestNotFoundException(id));
        List<RiskFactorResponse> factors = new ArrayList<>();
        UUID projectId = pullRequest.getProject().getId();
        List<CiRun> ciRuns = ciRunRepository.findByProjectIdAndCommitSha(projectId, pullRequest.getCommitSha());
        if (ciRuns.stream().anyMatch(run -> "FAILED".equalsIgnoreCase(run.getStatus()))) {
            factors.add(new RiskFactorResponse("failed_ci_run", 30, "At least one CI run failed for this commit."));
        }
        if (ciRuns.stream().mapToInt(CiRun::getFailedTests).sum() > 0) {
            factors.add(new RiskFactorResponse("failed_tests", 20, "Failed tests were reported for this commit."));
        }
        if (applicationLogRepository.findByDeploymentProjectIdAndDeploymentCommitSha(projectId, pullRequest.getCommitSha())
                .stream().anyMatch(log -> "ERROR".equalsIgnoreCase(log.getLevel()) || "FATAL".equalsIgnoreCase(log.getLevel()))) {
            factors.add(new RiskFactorResponse("error_logs", 30, "Error logs are linked to deployments for this commit."));
        }
        if (pullRequest.getSourceBranch().toLowerCase().contains("hotfix")) {
            factors.add(new RiskFactorResponse("hotfix_branch", 10, "Source branch is marked as a hotfix."));
        }
        int score = Math.min(100, factors.stream().mapToInt(RiskFactorResponse::contribution).sum());
        String level = score > 70 ? "HIGH" : score > 30 ? "MEDIUM" : "LOW";
        String rationale = factors.isEmpty() ? "No risk signals have been correlated yet." : "Risk was correlated from PR, CI, and deployment-log signals.";
        return new RiskScoreResponse(score, level, rationale, factors, OffsetDateTime.now());
    }

    private PullRequestResponse toResponse(PullRequest item) {
        return new PullRequestResponse(item.getId(), item.getProject().getId(), item.getProvider(), item.getProviderPullRequestId(), item.getNumber(), item.getTitle(), item.getAuthorId(), item.getSourceBranch(), item.getTargetBranch(), item.getCommitSha(), item.getStatus(), item.getCreatedAt(), item.getUpdatedAt());
    }
}
