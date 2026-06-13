package com.deployguard.api.cirun;

import com.deployguard.api.cirun.dto.CiRunResponse;
import com.deployguard.api.cirun.dto.CreateCiRunRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CiRunController {

    private final CiRunService ciRunService;

    public CiRunController(CiRunService ciRunService) {
        this.ciRunService = ciRunService;
    }

    @PostMapping("/ci-runs")
    public ResponseEntity<CiRunResponse> createCiRun(@Valid @RequestBody CreateCiRunRequest request) {
        CiRunResponse response = ciRunService.createCiRun(request);
        return ResponseEntity
                .created(URI.create("/api/ci-runs/" + response.id()))
                .body(response);
    }

    @GetMapping("/ci-runs/{id}")
    public CiRunResponse getCiRunById(@PathVariable UUID id) {
        return ciRunService.getCiRunById(id);
    }

    @GetMapping("/projects/{projectId}/ci-runs")
    public List<CiRunResponse> getCiRunsByProjectId(@PathVariable UUID projectId) {
        return ciRunService.getCiRunsByProjectId(projectId);
    }

    @GetMapping("/projects/{projectId}/ci-runs/failed")
    public List<CiRunResponse> getFailedCiRunsByProjectId(@PathVariable UUID projectId) {
        return ciRunService.getFailedCiRunsByProjectId(projectId);
    }

    @GetMapping("/projects/{projectId}/ci-runs/commit/{commitSha}")
    public List<CiRunResponse> getCiRunsByCommitSha(
            @PathVariable UUID projectId,
            @PathVariable String commitSha
    ) {
        return ciRunService.getCiRunsByCommitSha(projectId, commitSha);
    }
}
