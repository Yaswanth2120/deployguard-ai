package com.deployguard.api.pullrequest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deployguard.api.pullrequest.dto.PullRequestResponse;
import com.deployguard.api.pullrequest.dto.RiskScoreResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PullRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
class PullRequestControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private PullRequestService service;
    @Test void acceptsPullRequestIngestion() throws Exception {
        UUID id = UUID.randomUUID(); UUID projectId = UUID.randomUUID();
        when(service.create(any())).thenReturn(new PullRequestResponse(id, projectId, "github", "42", 42, "Fix", "user", "feature/x", "main", "abc", "OPEN", OffsetDateTime.now(), OffsetDateTime.now()));
        mockMvc.perform(post("/api/v1/pull-requests").contentType(MediaType.APPLICATION_JSON).content("{\"projectId\":\"" + projectId + "\",\"provider\":\"github\",\"providerPullRequestId\":\"42\",\"number\":42,\"title\":\"Fix\",\"authorId\":\"user\",\"sourceBranch\":\"feature/x\",\"targetBranch\":\"main\",\"commitSha\":\"abc\",\"status\":\"OPEN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id.toString()));
    }
    @Test void retrievesRiskScore() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getRiskScore(eq(id))).thenReturn(new RiskScoreResponse(10, "LOW", "No risk", List.of(), OffsetDateTime.now()));
        mockMvc.perform(get("/api/v1/pull-requests/{id}/risk-score", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(10));
    }
}
