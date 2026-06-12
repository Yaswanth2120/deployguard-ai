package com.deployguard.api.applicationlog;

import com.deployguard.api.applicationlog.dto.ApplicationLogResponse;
import com.deployguard.api.applicationlog.dto.CreateApplicationLogRequest;
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
public class ApplicationLogController {

    private final ApplicationLogService applicationLogService;

    public ApplicationLogController(ApplicationLogService applicationLogService) {
        this.applicationLogService = applicationLogService;
    }

    @PostMapping("/logs")
    public ResponseEntity<ApplicationLogResponse> createLog(
            @Valid @RequestBody CreateApplicationLogRequest request
    ) {
        ApplicationLogResponse response = applicationLogService.createLog(request);
        return ResponseEntity
                .created(URI.create("/api/logs/" + response.id()))
                .body(response);
    }

    @GetMapping("/projects/{projectId}/logs")
    public List<ApplicationLogResponse> getLogsByProjectId(@PathVariable UUID projectId) {
        return applicationLogService.getLogsByProjectId(projectId);
    }

    @GetMapping("/deployments/{deploymentId}/logs")
    public List<ApplicationLogResponse> getLogsByDeploymentId(@PathVariable UUID deploymentId) {
        return applicationLogService.getLogsByDeploymentId(deploymentId);
    }

    @GetMapping("/projects/{projectId}/logs/errors")
    public List<ApplicationLogResponse> getErrorLogsByProjectId(@PathVariable UUID projectId) {
        return applicationLogService.getErrorLogsByProjectId(projectId);
    }
}
