package com.deployguard.api.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deployguard.api.project.ProjectController;
import com.deployguard.api.project.ProjectService;
import com.deployguard.api.project.dto.ProjectResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectController.class)
@Import({SecurityConfig.class, AuthEntryPoint.class, JwtService.class})
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ProjectService projectService;

    @Test
    void allowsUnauthenticatedReads() throws Exception {
        when(projectService.getAllProjects()).thenReturn(List.of());

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsUnauthenticatedWrites() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"githubRepoUrl\":\"https://github.com/octo/repo\",\"serviceName\":\"svc\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsWritesWithValidGithubIssuedToken() throws Exception {
        UUID id = UUID.randomUUID();
        String token = jwtService.issueToken(new GithubUser(1L, "octocat", "The Octocat", "https://example.com/a.png"));
        when(projectService.updateProject(eq(id), any())).thenReturn(
                new ProjectResponse(id, "demo", "https://github.com/octo/repo", "svc", OffsetDateTime.now(), OffsetDateTime.now())
        );

        mockMvc.perform(put("/api/projects/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"githubRepoUrl\":\"https://github.com/octo/repo\",\"serviceName\":\"svc\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsWritesWithInvalidToken() throws Exception {
        mockMvc.perform(put("/api/projects/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"githubRepoUrl\":\"https://github.com/octo/repo\",\"serviceName\":\"svc\"}"))
                .andExpect(status().isUnauthorized());
    }
}
