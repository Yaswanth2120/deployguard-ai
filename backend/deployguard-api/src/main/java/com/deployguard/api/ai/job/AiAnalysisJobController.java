package com.deployguard.api.ai.job;

import com.deployguard.api.ai.job.dto.AiAnalysisJobResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AiAnalysisJobController {

    private final AiAnalysisJobService aiAnalysisJobService;

    public AiAnalysisJobController(AiAnalysisJobService aiAnalysisJobService) {
        this.aiAnalysisJobService = aiAnalysisJobService;
    }

    @PostMapping("/deployments/{deploymentId}/ai-analysis/jobs")
    public AiAnalysisJobResponse createJob(@PathVariable UUID deploymentId) {
        return aiAnalysisJobService.createJob(deploymentId);
    }

    @GetMapping("/ai-analysis/jobs/{jobId}")
    public AiAnalysisJobResponse getJobById(@PathVariable UUID jobId) {
        return aiAnalysisJobService.getJobById(jobId);
    }

    @GetMapping("/deployments/{deploymentId}/ai-analysis/jobs")
    public List<AiAnalysisJobResponse> getJobsByDeploymentId(@PathVariable UUID deploymentId) {
        return aiAnalysisJobService.getJobsByDeploymentId(deploymentId);
    }
}
