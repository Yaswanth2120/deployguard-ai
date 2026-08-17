package com.deployguard.api.pullrequest.dto;
import java.time.OffsetDateTime;
import java.util.UUID;
public record PullRequestResponse(UUID id, UUID projectId, String provider, String providerPullRequestId, Integer number, String title, String authorId, String sourceBranch, String targetBranch, String commitSha, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
