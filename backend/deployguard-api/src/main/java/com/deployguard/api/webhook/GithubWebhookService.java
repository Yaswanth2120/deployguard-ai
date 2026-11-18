package com.deployguard.api.webhook;

import com.deployguard.api.cirun.CiRun;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.deployment.Deployment;
import com.deployguard.api.deployment.DeploymentRepository;
import com.deployguard.api.project.Project;
import com.deployguard.api.project.ProjectRepository;
import com.deployguard.api.pullrequest.PullRequest;
import com.deployguard.api.pullrequest.PullRequestRepository;
import com.deployguard.api.risk.RiskScoringService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps GitHub webhook events onto DeployGuard's model so real repositories feed the platform
 * without manual API calls:
 * <ul>
 *   <li>{@code workflow_run} (completed) -> CI run; re-scores deployments of the same commit</li>
 *   <li>{@code pull_request} -> pull request upsert</li>
 *   <li>{@code deployment_status} (success/failure/error) -> deployment; scored immediately</li>
 * </ul>
 * Deliveries are idempotent by {@code X-GitHub-Delivery}, because GitHub redelivers on timeouts.
 */
@Service
public class GithubWebhookService {

    static final String PROVIDER = "github";
    private static final Set<String> PR_ACTIONS = Set.of("opened", "reopened", "synchronize", "edited", "closed");
    private static final Set<String> TERMINAL_DEPLOYMENT_STATES = Set.of("success", "failure", "error");

    private final ProjectRepository projectRepository;
    private final CiRunRepository ciRunRepository;
    private final PullRequestRepository pullRequestRepository;
    private final DeploymentRepository deploymentRepository;
    private final RiskScoringService riskScoringService;
    private final GithubWebhookDeliveryRepository deliveryRepository;

    public GithubWebhookService(
            ProjectRepository projectRepository,
            CiRunRepository ciRunRepository,
            PullRequestRepository pullRequestRepository,
            DeploymentRepository deploymentRepository,
            RiskScoringService riskScoringService,
            GithubWebhookDeliveryRepository deliveryRepository) {
        this.projectRepository = projectRepository;
        this.ciRunRepository = ciRunRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.deploymentRepository = deploymentRepository;
        this.riskScoringService = riskScoringService;
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public WebhookResult handle(String event, String deliveryId, JsonNode payload) {
        if ("ping".equals(event)) {
            return new WebhookResult(event, "pong", null);
        }
        if (deliveryId != null && deliveryRepository.existsById(deliveryId)) {
            return WebhookResult.ignored(event, "duplicate delivery");
        }

        Optional<Project> project = findProject(payload.path("repository").path("html_url").asText(null));
        WebhookResult result;
        if (project.isEmpty()) {
            result = WebhookResult.ignored(event, "no project registered for this repository");
        } else {
            result = switch (event == null ? "" : event) {
                case "workflow_run" -> handleWorkflowRun(project.get(), payload);
                case "pull_request" -> handlePullRequest(project.get(), payload);
                case "deployment_status" -> handleDeploymentStatus(project.get(), payload);
                default -> WebhookResult.ignored(event, "unsupported event");
            };
        }

        if (deliveryId != null) {
            deliveryRepository.save(new GithubWebhookDelivery(deliveryId, event == null ? "unknown" : event));
        }
        return result;
    }

    private WebhookResult handleWorkflowRun(Project project, JsonNode payload) {
        if (!"completed".equals(payload.path("action").asText())) {
            return WebhookResult.ignored("workflow_run", "action is not completed");
        }
        JsonNode run = payload.path("workflow_run");
        String sha = run.path("head_sha").asText();
        CiRun ciRun = ciRunRepository.save(new CiRun(
                project,
                sha,
                "github-actions",
                mapConclusion(run.path("conclusion").asText("")),
                durationSeconds(run.path("run_started_at").asText(null), run.path("updated_at").asText(null)),
                0));
        // A late CI result changes the risk of deployments already made from this commit.
        for (Deployment deployment : deploymentRepository.findByProjectIdAndCommitSha(project.getId(), sha)) {
            riskScoringService.calculateAndUpdateRiskScore(deployment.getId());
        }
        return new WebhookResult("workflow_run", "ci_run_created", ciRun.getId());
    }

    private WebhookResult handlePullRequest(Project project, JsonNode payload) {
        String action = payload.path("action").asText();
        if (!PR_ACTIONS.contains(action)) {
            return WebhookResult.ignored("pull_request", "action " + action + " not tracked");
        }
        JsonNode pr = payload.path("pull_request");
        String providerId = pr.path("id").asText();
        String status = pr.path("merged").asBoolean(false) ? "MERGED"
                : "closed".equals(pr.path("state").asText()) ? "CLOSED" : "OPEN";
        String title = truncate(pr.path("title").asText("(untitled)"), 255);
        String sha = pr.path("head").path("sha").asText();

        Optional<PullRequest> existing = pullRequestRepository
                .findFirstByProjectIdAndProviderAndProviderPullRequestId(project.getId(), PROVIDER, providerId);
        PullRequest saved;
        if (existing.isPresent()) {
            existing.get().syncFromProvider(title, sha, status);
            saved = pullRequestRepository.save(existing.get());
        } else {
            saved = pullRequestRepository.save(new PullRequest(
                    project,
                    PROVIDER,
                    providerId,
                    pr.path("number").asInt(),
                    title,
                    pr.path("user").path("login").asText("unknown"),
                    pr.path("head").path("ref").asText(),
                    pr.path("base").path("ref").asText(),
                    sha,
                    status));
        }
        return new WebhookResult("pull_request", existing.isPresent() ? "pull_request_updated" : "pull_request_created", saved.getId());
    }

    private WebhookResult handleDeploymentStatus(Project project, JsonNode payload) {
        JsonNode status = payload.path("deployment_status");
        String state = status.path("state").asText();
        if (!TERMINAL_DEPLOYMENT_STATES.contains(state)) {
            return WebhookResult.ignored("deployment_status", "state " + state + " is not terminal");
        }
        JsonNode deployment = payload.path("deployment");
        OffsetDateTime deployedAt = parse(status.path("created_at").asText(null));
        Deployment saved = deploymentRepository.save(new Deployment(
                project,
                deployment.path("sha").asText(),
                deployment.path("ref").asText(),
                deployment.path("environment").asText("unknown"),
                "success".equals(state) ? "DEPLOYED" : "FAILED",
                deployment.path("creator").path("login").asText("github"),
                deployedAt == null ? OffsetDateTime.now() : deployedAt));
        riskScoringService.calculateAndUpdateRiskScore(saved.getId());
        return new WebhookResult("deployment_status", "deployment_created", saved.getId());
    }

    Optional<Project> findProject(String repositoryUrl) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            return Optional.empty();
        }
        String base = repositoryUrl.toLowerCase(Locale.ROOT).replaceAll("/+$", "").replaceAll("\\.git$", "");
        List<Project> matches = projectRepository.findByGithubRepoUrlCandidates(List.of(base, base + "/", base + ".git"));
        return matches.stream().findFirst();
    }

    static String mapConclusion(String conclusion) {
        return switch (conclusion) {
            case "success" -> "SUCCESS";
            case "failure", "timed_out", "startup_failure" -> "FAILED";
            case "cancelled" -> "CANCELLED";
            case "" -> "UNKNOWN";
            default -> conclusion.toUpperCase(Locale.ROOT);
        };
    }

    static int durationSeconds(String startedAt, String finishedAt) {
        OffsetDateTime start = parse(startedAt);
        OffsetDateTime end = parse(finishedAt);
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Duration.between(start, end).getSeconds());
    }

    private static OffsetDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
