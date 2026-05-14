package io.streamvault.core.application.auth;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.streamvault.core.domain.auth.AuthError;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class AuthServiceIT {

    @Inject AuthService authService;
    @Inject AgroalDataSource ds;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE refresh_tokens, users CASCADE");
        }
    }

    // ── register ───────────────────────────────────────────────────

    @Test
    @RunOnVertxContext
    void register_returnsTokenPair(UniAsserter asserter) {
        asserter.assertThat(
                () -> authService.register("admin", "Admin123!"),
                result -> {
                    assertThat(result.accessToken()).isNotBlank();
                    assertThat(result.refreshToken()).isNotBlank();
                });
    }

    @Test
    @RunOnVertxContext
    void register_duplicateUsername_fails(UniAsserter asserter) {
        asserter
                .execute(() -> authService.register("admin", "Admin123!"))
                .assertFailedWith(
                        () -> authService.register("admin", "OtherPass456!"),
                        e -> assertThat(e)
                                .isInstanceOf(AuthException.class)
                                .satisfies(ex -> assertThat(((AuthException) ex).error())
                                        .isInstanceOf(AuthError.UsernameAlreadyTaken.class)));
    }

    @Test
    @RunOnVertxContext
    void register_differentUsername_succeeds(UniAsserter asserter) {
        asserter
                .execute(() -> authService.register("admin", "Admin123!"))
                .execute(() -> authService.register("admin2", "Admin456!"))
                .assertThat(() -> authService.login("admin2", "Admin456!"),
                        result -> assertThat(result.accessToken()).isNotBlank());
    }

    // ── login ────────────────────────────────────────────────────────────────

    @Test
    @RunOnVertxContext
    void login_withCorrectCredentials_returnsTokenPair(UniAsserter asserter) {
        asserter
                .execute(() -> authService.register("admin", "Admin123!"))
                .assertThat(
                        () -> authService.login("admin", "Admin123!"),
                        result -> {
                            assertThat(result.accessToken()).isNotBlank();
                            assertThat(result.refreshToken()).isNotBlank();
                        });
    }

    @Test
    @RunOnVertxContext
    void login_withWrongPassword_fails(UniAsserter asserter) {
        asserter
                .execute(() -> authService.register("admin", "Admin123!"))
                .assertFailedWith(
                        () -> authService.login("admin", "WrongPassword1!"),
                        e -> assertThat(e)
                                .isInstanceOf(AuthException.class)
                                .satisfies(ex -> assertThat(((AuthException) ex).error())
                                        .isInstanceOf(AuthError.InvalidCredentials.class)));
    }

    @Test
    @RunOnVertxContext
    void login_withUnknownUsername_fails(UniAsserter asserter) {
        asserter.assertFailedWith(
                () -> authService.login("nobody", "Admin123!"),
                e -> assertThat(e)
                        .isInstanceOf(AuthException.class)
                        .satisfies(ex -> assertThat(((AuthException) ex).error())
                                .isInstanceOf(AuthError.InvalidCredentials.class)));
    }

    // ── refresh ──────────────────────────────────────────────────────────────

    @Test
    @RunOnVertxContext
    void refresh_withValidToken_returnsNewTokenPair(UniAsserter asserter) {
        asserter
                .assertThat(
                        () -> authService.register("admin", "Admin123!"),
                        initial -> asserter.putData("token", initial.refreshToken()))
                .assertThat(
                        () -> authService.refresh((String) asserter.getData("token")),
                        result -> {
                            assertThat(result.accessToken()).isNotBlank();
                            assertThat(result.refreshToken()).isNotBlank();
                        });
    }

    @Test
    @RunOnVertxContext
    void refresh_rotatesToken_oldTokenNoLongerValid(UniAsserter asserter) {
        asserter
                .assertThat(
                        () -> authService.register("admin", "Admin123!"),
                        initial -> asserter.putData("token", initial.refreshToken()))
                .execute(() -> authService.refresh((String) asserter.getData("token")))
                .assertFailedWith(
                        () -> authService.refresh((String) asserter.getData("token")),
                        e -> assertThat(e)
                                .isInstanceOf(AuthException.class)
                                .satisfies(ex -> assertThat(((AuthException) ex).error())
                                        .isInstanceOf(AuthError.TokenNotFound.class)));
    }

    @Test
    @RunOnVertxContext
    void refresh_withUnknownToken_fails(UniAsserter asserter) {
        asserter.assertFailedWith(
                () -> authService.refresh("made-up-token-that-does-not-exist"),
                e -> assertThat(e)
                        .isInstanceOf(AuthException.class)
                        .satisfies(ex -> assertThat(((AuthException) ex).error())
                                .isInstanceOf(AuthError.TokenNotFound.class)));
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    @RunOnVertxContext
    void logout_invalidatesRefreshToken(UniAsserter asserter) {
        asserter
                .assertThat(
                        () -> authService.register("admin", "Admin123!"),
                        tokens -> {
                            asserter.putData("refreshToken", tokens.refreshToken());
                            asserter.putData("userId", extractUserId(tokens.accessToken()));
                        })
                .execute(() -> authService.logout((UUID) asserter.getData("userId")))
                .assertFailedWith(
                        () -> authService.refresh((String) asserter.getData("refreshToken")),
                        e -> assertThat(e)
                                .isInstanceOf(AuthException.class)
                                .satisfies(ex -> assertThat(((AuthException) ex).error())
                                        .isInstanceOf(AuthError.TokenNotFound.class)));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID extractUserId(String accessToken) {
        String[] parts = accessToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String sub = payload.replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
        return UUID.fromString(sub);
    }
}
