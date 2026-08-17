package com.deployguard.api.pullrequest;
import com.deployguard.api.pullrequest.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/pull-requests")
public class PullRequestController {
    private final PullRequestService service;
    public PullRequestController(PullRequestService service) { this.service = service; }
    @PostMapping public ResponseEntity<PullRequestResponse> create(@Valid @RequestBody CreatePullRequestRequest request) { PullRequestResponse response = service.create(request); return ResponseEntity.created(URI.create("/api/v1/pull-requests/" + response.id())).body(response); }
    @GetMapping("/{id}/risk-score") public RiskScoreResponse riskScore(@PathVariable UUID id) { return service.getRiskScore(id); }
}
