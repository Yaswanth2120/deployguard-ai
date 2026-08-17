package com.deployguard.api.auth;

import com.deployguard.api.auth.dto.AuthSessionResponse;
import com.deployguard.api.auth.dto.AuthUserResponse;
import com.deployguard.api.auth.dto.GithubCallbackRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GithubOAuthClient githubOAuthClient;
    private final JwtService jwtService;

    public AuthController(GithubOAuthClient githubOAuthClient, JwtService jwtService) {
        this.githubOAuthClient = githubOAuthClient;
        this.jwtService = jwtService;
    }

    @PostMapping("/github/callback")
    public AuthSessionResponse handleGithubCallback(@Valid @RequestBody GithubCallbackRequest request) {
        GithubUser githubUser = githubOAuthClient.exchangeCodeForUser(
                request.code(),
                request.codeVerifier(),
                request.redirectUri()
        );
        String token = jwtService.issueToken(githubUser);
        AuthenticatedUser authenticatedUser = jwtService.parseAuthenticatedUser(token);

        return new AuthSessionResponse(token, jwtService.expirationOf(token), toResponse(authenticatedUser));
    }

    @GetMapping("/me")
    public AuthUserResponse currentUser() {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        return toResponse(authenticatedUser);
    }

    private AuthUserResponse toResponse(AuthenticatedUser user) {
        return new AuthUserResponse(user.githubId(), user.login(), user.name(), user.avatarUrl(), user.role());
    }
}
