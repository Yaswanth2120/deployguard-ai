package com.deployguard.api.pullrequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PullRequestRepository extends JpaRepository<PullRequest, UUID> {
    java.util.Optional<PullRequest> findFirstByProjectIdAndProviderAndProviderPullRequestId(UUID projectId, String provider, String providerPullRequestId);
}
