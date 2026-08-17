package com.deployguard.api.pullrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.deployguard.api.project.Project;
import com.deployguard.api.project.ProjectRepository;
import com.deployguard.api.cirun.CiRunRepository;
import com.deployguard.api.applicationlog.ApplicationLogRepository;
import com.deployguard.api.pullrequest.dto.CreatePullRequestRequest;
import com.deployguard.api.pullrequest.dto.PullRequestResponse;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PullRequestServiceTest {
    @Mock PullRequestRepository pullRequests;
    @Mock ProjectRepository projects;
    @Mock CiRunRepository ciRuns;
    @Mock ApplicationLogRepository applicationLogs;
    @Test void createsPullRequestAndReturnsItsRiskScore() throws Exception {
        UUID projectId = UUID.randomUUID(); Project project = new Project("API", "https://github.com/acme/api", "api"); set(project, "id", projectId);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
        when(pullRequests.save(any(PullRequest.class))).thenAnswer(i -> { PullRequest pr = i.getArgument(0); set(pr, "id", UUID.randomUUID()); set(pr, "createdAt", OffsetDateTime.now()); set(pr, "updatedAt", OffsetDateTime.now()); return pr; });
        PullRequestService service = new PullRequestService(pullRequests, projects, ciRuns, applicationLogs);
        PullRequestResponse created = service.create(new CreatePullRequestRequest(projectId, "github", "42", 42, "Fix", "user", "hotfix/login", "main", "abc", "OPEN"));
        when(pullRequests.findById(created.id())).thenReturn(Optional.of(entity(project, created.id())));
        when(ciRuns.findByProjectIdAndCommitSha(projectId, "abc")).thenReturn(java.util.List.of());
        when(applicationLogs.findByDeploymentProjectIdAndDeploymentCommitSha(projectId, "abc")).thenReturn(java.util.List.of());
        assertThat(created.projectId()).isEqualTo(projectId);
        assertThat(service.getRiskScore(created.id()).score()).isEqualTo(10);
    }
    private PullRequest entity(Project project, UUID id) throws Exception { PullRequest pr = new PullRequest(project, "github", "42", 42, "Fix", "user", "hotfix/login", "main", "abc", "OPEN"); set(pr, "id", id); return pr; }
    private static void set(Object target, String field, Object value) throws Exception { Field f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target, value); }
}
