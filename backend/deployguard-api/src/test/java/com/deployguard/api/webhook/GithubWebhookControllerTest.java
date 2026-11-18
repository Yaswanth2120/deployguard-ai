package com.deployguard.api.webhook;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deployguard.api.auth.AuthEntryPoint;
import com.deployguard.api.auth.AuthProperties;
import com.deployguard.api.auth.JwtService;
import com.deployguard.api.auth.SecurityConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GithubWebhookController.class)
@Import({SecurityConfig.class, AuthEntryPoint.class, JwtService.class})
@EnableConfigurationProperties(AuthProperties.class)
@TestPropertySource(properties = "deployguard.github.webhook-secret=test-webhook-secret")
class GithubWebhookControllerTest {

    private static final String BODY = "{\"action\":\"completed\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubWebhookService githubWebhookService;

    @Test
    void acceptsSignedDeliveryWithoutJwt() throws Exception {
        when(githubWebhookService.handle(eq("workflow_run"), eq("d-1"), any()))
                .thenReturn(new WebhookResult("workflow_run", "ci_run_created", null));

        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-GitHub-Delivery", "d-1")
                        .header("X-Hub-Signature-256",
                                GithubSignatureVerifier.sign("test-webhook-secret", BODY.getBytes(StandardCharsets.UTF_8)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("ci_run_created"));
    }

    @Test
    void rejectsBadSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "workflow_run")
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(githubWebhookService);
    }
}
