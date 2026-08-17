package com.deployguard.api.pullrequest.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record CreatePullRequestRequest(
        @NotNull UUID projectId, @NotBlank String provider, @NotBlank String providerPullRequestId,
        @NotNull @Positive Integer number, @NotBlank String title, @NotBlank String authorId,
        @NotBlank String sourceBranch, @NotBlank String targetBranch, @NotBlank String commitSha,
        @NotBlank @Pattern(regexp = "OPEN|MERGED|CLOSED") String status) { }
