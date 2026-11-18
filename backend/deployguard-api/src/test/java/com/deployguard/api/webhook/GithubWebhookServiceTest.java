package com.deployguard.api.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GithubWebhookServiceTest {

    private static final String REPO_URL = "https://github.com/acme/checkout";

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final CiRunRepository ciRunRepository = mock(CiRunRepository.class);
    private final PullRequestRepository pullRequestRepository = mock(PullRequestRepository.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final RiskScoringService riskScoringService = mock(RiskScoringService.class);
    private final GithubWebhookDeliveryRepository deliveryRepository = mock(GithubWebhookDeliveryRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private GithubWebhookService service;
    private Project project;

    @BeforeEach
    void setUp() {
        service = new GithubWebhookService(projectRepository, ciRunRepository, pullRequestRepository,
                deploymentRepository, riskScoringService, deliveryRepository);
        project = mock(Project.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        when(projectRepository.findByGithubRepoUrlCandidates(any())).thenReturn(List.of(project));
        when(ciRunRepository.save(any(CiRun.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(pullRequestRepository.save(any(PullRequest.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        when(deploymentRepository.save(any(Deployment.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
    }

    private static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    private JsonNode json(String raw) throws Exception {
        return objectMapper.readTree(raw.replace('\'', '"'));
    }

    @Test
    void completedWorkflowRunCreatesFailedCiRunAndRescoresDeploymentsOfCommit() throws Exception {
        Deployment existing = mock(Deployment.class);
        UUID deploymentId = UUID.randomUUID();
        when(existing.getId()).thenReturn(deploymentId);
        when(deploymentRepository.findByProjectIdAndCommitSha(project.getId(), "abc123")).thenReturn(List.of(existing));

        WebhookResult result = service.handle("workflow_run", "d-1", json("""
                {'action':'completed','repository':{'html_url':'%s'},
                 'workflow_run':{'head_sha':'abc123','conclusion':'failure',
                   'run_started_at':'2026-11-01T10:00:00Z','updated_at':'2026-11-01T10:07:30Z'}}
                """.formatted(REPO_URL)));

        ArgumentCaptor<CiRun> captor = ArgumentCaptor.forClass(CiRun.class);
        verify(ciRunRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getDurationSeconds()).isEqualTo(450);
        assertThat(captor.getValue().getCommitSha()).isEqualTo("abc123");
        verify(riskScoringService).calculateAndUpdateRiskScore(deploymentId);
        verify(deliveryRepository).save(any(GithubWebhookDelivery.class));
        assertThat(result.outcome()).isEqualTo("ci_run_created");
    }

    @Test
    void pullRequestIsCreatedThenUpdatedOnSynchronize() throws Exception {
        String payload = """
                {'action':'%s','repository':{'html_url':'%s'},
                 'pull_request':{'id':991,'number':42,'title':'Add cache','state':'open','merged':false,
                   'user':{'login':'octocat'},'head':{'ref':'feature/cache','sha':'%s'},'base':{'ref':'main'}}}
                """;
        when(pullRequestRepository.findFirstByProjectIdAndProviderAndProviderPullRequestId(project.getId(), "github", "991"))
                .thenReturn(Optional.empty());
        WebhookResult created = service.handle("pull_request", "d-2", json(payload.formatted("opened", REPO_URL, "sha1")));
        assertThat(created.outcome()).isEqualTo("pull_request_created");

        PullRequest stored = new PullRequest(project, "github", "991", 42, "Add cache", "octocat", "feature/cache", "main", "sha1", "OPEN");
        when(pullRequestRepository.findFirstByProjectIdAndProviderAndProviderPullRequestId(project.getId(), "github", "991"))
                .thenReturn(Optional.of(stored));
        WebhookResult updated = service.handle("pull_request", "d-3", json(payload.formatted("synchronize", REPO_URL, "sha2")));
        assertThat(updated.outcome()).isEqualTo("pull_request_updated");
        assertThat(stored.getCommitSha()).isEqualTo("sha2");
    }

    @Test
    void terminalDeploymentStatusCreatesScoredDeployment() throws Exception {
        WebhookResult result = service.handle("deployment_status", "d-4", json("""
                {'repository':{'html_url':'%s.git'},
                 'deployment_status':{'state':'failure','created_at':'2026-11-01T12:00:00Z'},
                 'deployment':{'sha':'abc123','ref':'hotfix/login','environment':'production','creator':{'login':'octocat'}}}
                """.formatted(REPO_URL)));

        ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getEnvironment()).isEqualTo("production");
        verify(riskScoringService).calculateAndUpdateRiskScore(result.entityId());
    }

    @Test
    void nonTerminalDeploymentStatusIsIgnored() throws Exception {
        WebhookResult result = service.handle("deployment_status", "d-5", json("""
                {'repository':{'html_url':'%s'},'deployment_status':{'state':'in_progress'},'deployment':{}}
                """.formatted(REPO_URL)));
        assertThat(result.outcome()).startsWith("ignored");
        verify(deploymentRepository, never()).save(any());
    }

    @Test
    void duplicateDeliveryIsIgnored() throws Exception {
        when(deliveryRepository.existsById("dup")).thenReturn(true);
        WebhookResult result = service.handle("workflow_run", "dup", json("{'action':'completed'}"));
        assertThat(result.outcome()).isEqualTo("ignored: duplicate delivery");
        verify(ciRunRepository, never()).save(any());
    }

    @Test
    void unknownRepositoryIsIgnored() throws Exception {
        when(projectRepository.findByGithubRepoUrlCandidates(any())).thenReturn(List.of());
        WebhookResult result = service.handle("workflow_run", "d-6", json("""
                {'action':'completed','repository':{'html_url':'https://github.com/other/repo'},'workflow_run':{}}
                """));
        assertThat(result.outcome()).isEqualTo("ignored: no project registered for this repository");
    }

    @Test
    void repositoryUrlMatchingIsCaseAndSuffixInsensitive() {
        service.findProject("https://GitHub.com/Acme/Checkout.git");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<String>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(projectRepository).findByGithubRepoUrlCandidates(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                "https://github.com/acme/checkout", "https://github.com/acme/checkout/", "https://github.com/acme/checkout.git");
    }

    @Test
    void conclusionMapping() {
        assertThat(GithubWebhookService.mapConclusion("success")).isEqualTo("SUCCESS");
        assertThat(GithubWebhookService.mapConclusion("timed_out")).isEqualTo("FAILED");
        assertThat(GithubWebhookService.mapConclusion("cancelled")).isEqualTo("CANCELLED");
        assertThat(GithubWebhookService.mapConclusion("")).isEqualTo("UNKNOWN");
    }
}
