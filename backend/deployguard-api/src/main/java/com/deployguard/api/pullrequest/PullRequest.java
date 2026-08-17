package com.deployguard.api.pullrequest;

import com.deployguard.api.project.Project;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pull_requests")
public class PullRequest {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id") private Project project;
    @Column(nullable = false) private String provider;
    @Column(name = "provider_pull_request_id", nullable = false) private String providerPullRequestId;
    @Column(nullable = false) private Integer number;
    @Column(nullable = false) private String title;
    @Column(name = "author_id", nullable = false) private String authorId;
    @Column(name = "source_branch", nullable = false) private String sourceBranch;
    @Column(name = "target_branch", nullable = false) private String targetBranch;
    @Column(name = "commit_sha", nullable = false) private String commitSha;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;

    protected PullRequest() { }
    public PullRequest(Project project, String provider, String providerPullRequestId, Integer number, String title, String authorId, String sourceBranch, String targetBranch, String commitSha, String status) {
        this.project = project; this.provider = provider; this.providerPullRequestId = providerPullRequestId; this.number = number; this.title = title; this.authorId = authorId; this.sourceBranch = sourceBranch; this.targetBranch = targetBranch; this.commitSha = commitSha; this.status = status;
    }
    @PrePersist void prePersist() { createdAt = OffsetDateTime.now(); updatedAt = createdAt; }
    public UUID getId() { return id; } public Project getProject() { return project; } public String getProvider() { return provider; } public String getProviderPullRequestId() { return providerPullRequestId; } public Integer getNumber() { return number; } public String getTitle() { return title; } public String getAuthorId() { return authorId; } public String getSourceBranch() { return sourceBranch; } public String getTargetBranch() { return targetBranch; } public String getCommitSha() { return commitSha; } public String getStatus() { return status; } public OffsetDateTime getCreatedAt() { return createdAt; } public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
