package com.deployguard.api.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank
        @Size(max = 120)
        String name,

        @NotBlank
        @Size(max = 500)
        String githubRepoUrl,

        @NotBlank
        @Size(max = 120)
        String serviceName
) {
}
