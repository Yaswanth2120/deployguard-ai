package com.deployguard.api.deployment;

import com.deployguard.api.deployment.dto.CreateDeploymentRequest;
import com.deployguard.api.deployment.dto.DeploymentResponse;
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
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping("/deployments")
    public ResponseEntity<DeploymentResponse> createDeployment(
            @Valid @RequestBody CreateDeploymentRequest request
    ) {
        DeploymentResponse response = deploymentService.createDeployment(request);
        return ResponseEntity
                .created(URI.create("/api/deployments/" + response.id()))
                .body(response);
    }

    @GetMapping("/deployments/{id}")
    public DeploymentResponse getDeploymentById(@PathVariable UUID id) {
        return deploymentService.getDeploymentById(id);
    }

    @GetMapping("/projects/{projectId}/deployments")
    public List<DeploymentResponse> getDeploymentsByProjectId(@PathVariable UUID projectId) {
        return deploymentService.getDeploymentsByProjectId(projectId);
    }
}
