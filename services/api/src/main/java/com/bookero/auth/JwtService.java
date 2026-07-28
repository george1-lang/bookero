package com.bookero.auth;

import com.bookero.common.BookeroProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {
    private final BookeroProperties properties;
    private final SecretKey secretKey;

    public JwtService(BookeroProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes());
    }

    public String issueToken(UUID userId, String email, Role role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds((long) properties.jwtTtlMinutes() * 60);

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("role", role.name())
            .issuedAt(java.util.Date.from(now))
            .expiration(java.util.Date.from(expiresAt))
            .signWith(secretKey)
            .compact();
    }

    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            var claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String email = claims.get("email", String.class);
            String roleStr = claims.get("role", String.class);
            Role role = Role.valueOf(roleStr);

            return Optional.of(new AuthenticatedUser(userId, email, role));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
