package io.streamvault.core.application.auth;

import io.smallrye.jwt.build.Jwt;
import io.streamvault.core.domain.auth.User;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "streamvault.auth.access-token-ttl-minutes", defaultValue = "15")
    long accessTokenTtlMinutes;

    @ConfigProperty(name = "streamvault.auth.refresh-token-ttl-days", defaultValue = "30")
    long refreshTokenTtlDays;

    public String generateAccessToken(User user) {
        return Jwt.issuer(issuer)
                .subject(user.id.toString())
                .claim("upn", user.username)
                .groups(Set.of(user.role.name()))
                .expiresIn(Duration.ofMinutes(accessTokenTtlMinutes))
                .sign();
    }

    public String generateRefreshToken() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public OffsetDateTime refreshTokenExpiry() {
        return OffsetDateTime.now().plusDays(refreshTokenTtlDays);
    }
}
