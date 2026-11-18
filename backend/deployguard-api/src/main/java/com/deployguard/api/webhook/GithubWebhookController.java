package com.deployguard.api.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks")
public class GithubWebhookController {

    private final GithubWebhookService githubWebhookService;
    private final ObjectMapper objectMapper;
    private final String webhookSecret;

    public GithubWebhookController(
            GithubWebhookService githubWebhookService,
            ObjectMapper objectMapper,
            @Value("${deployguard.github.webhook-secret:}") String webhookSecret) {
        this.githubWebhookService = githubWebhookService;
        this.objectMapper = objectMapper;
        this.webhookSecret = webhookSecret;
    }

    /** Raw bytes are required: the HMAC is computed over the exact body GitHub sent. */
    @PostMapping("/github")
    public ResponseEntity<?> receive(
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] body) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "GitHub webhook ingestion is not configured."));
        }
        if (!GithubSignatureVerifier.isValid(webhookSecret, body, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid webhook signature."));
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(body);
        } catch (IOException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Body is not valid JSON."));
        }
        return ResponseEntity.accepted().body(githubWebhookService.handle(event, deliveryId, payload));
    }
}
