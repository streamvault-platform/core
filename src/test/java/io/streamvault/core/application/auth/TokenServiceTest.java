package io.streamvault.core.application.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TokenServiceTest {

    private TokenService service;

    @BeforeEach
    void setUp() {
        service = new TokenService();
        service.issuer = "streamvault";
        service.accessTokenTtlMinutes = 15;
        service.refreshTokenTtlDays = 30;
    }

    // ── hashToken ────────────────────────────────────────────────────────────

    @Test
    void hashToken_producesKnownSha256() {
        // SHA-256("hello") is a well-known test vector
        assertThat(service.hashToken("hello"))
                .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void hashToken_isDeterministic() {
        assertThat(service.hashToken("some-refresh-token"))
                .isEqualTo(service.hashToken("some-refresh-token"));
    }

    @Test
    void hashToken_differentInputsProduceDifferentHashes() {
        assertThat(service.hashToken("token-a"))
                .isNotEqualTo(service.hashToken("token-b"));
    }

    // ── generateRefreshToken ─────────────────────────────────────────────────

    @Test
    void generateRefreshToken_isNotBlank() {
        assertThat(service.generateRefreshToken()).isNotBlank();
    }

    @Test
    void generateRefreshToken_eachCallProducesUniqueToken() {
        assertThat(service.generateRefreshToken())
                .isNotEqualTo(service.generateRefreshToken());
    }

    // ── refreshTokenExpiry ───────────────────────────────────────────────────

    @Test
    void refreshTokenExpiry_isInTheFuture() {
        assertThat(service.refreshTokenExpiry()).isAfter(OffsetDateTime.now());
    }

    @Test
    void refreshTokenExpiry_isApproximatelyConfiguredDaysFromNow() {
        var expiry = service.refreshTokenExpiry();
        var expected = OffsetDateTime.now().plusDays(service.refreshTokenTtlDays);
        assertThat(expiry).isCloseTo(expected, within(2, ChronoUnit.SECONDS));
    }

    @Test
    void refreshTokenExpiry_respectsDifferentTtlConfig() {
        service.refreshTokenTtlDays = 7;
        var expiry = service.refreshTokenExpiry();
        var expected = OffsetDateTime.now().plusDays(7);
        assertThat(expiry).isCloseTo(expected, within(2, ChronoUnit.SECONDS));
    }
}
