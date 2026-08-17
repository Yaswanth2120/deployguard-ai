package com.deployguard.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ISSUER = "deployguard-api";
    private static final String AUDIENCE = "deployguard-frontend";
    private static final String CLAIM_LOGIN = "login";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_AVATAR_URL = "avatarUrl";
    private static final String CLAIM_ROLE = "role";

    private final AuthProperties properties;
    private final SecretKey signingKey;

    public JwtService(AuthProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.jwt().signingKey().getBytes(StandardCharsets.UTF_8));
    }

    public String issueToken(GithubUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.jwt().expirationMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(String.valueOf(user.id()))
                .claim(CLAIM_LOGIN, user.login())
                .claim(CLAIM_NAME, user.name())
                .claim(CLAIM_AVATAR_URL, user.avatarUrl())
                .claim(CLAIM_ROLE, "operator")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Instant expirationOf(String token) {
        return parse(token).getExpiration().toInstant();
    }

    public AuthenticatedUser parseAuthenticatedUser(String token) {
        Claims claims = parse(token);
        return new AuthenticatedUser(
                claims.getSubject(),
                claims.get(CLAIM_LOGIN, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_AVATAR_URL, String.class),
                claims.get(CLAIM_ROLE, String.class)
        );
    }

    private Claims parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(ISSUER)
                    .requireAudience(AUDIENCE)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidTokenException("Invalid or expired token", exception);
        }
    }
}
