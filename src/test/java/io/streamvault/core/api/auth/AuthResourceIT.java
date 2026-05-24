package io.streamvault.core.api.auth;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.LoginRequest;
import io.streamvault.core.api.auth.dto.RefreshRequest;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AuthResourceIT {

    @Inject
    AgroalDataSource ds;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = ds.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE refresh_tokens, users CASCADE");
        }
    }

    // ── POST /api/auth/register ──────────────────────────────────────────────────
    @Test
    void register_returnsCreated_withTokenPair() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .when().post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    void register_whenAlreadyConfigured_returnsServerConfigured() {
        registerAdmin();

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("otheruser", "Other456!"))
                .when().post("/api/auth/register")
                .then()
                .statusCode(409)
                .body("code", equalTo("SERVER_CONFIGURED"));
    }

    @Test
    void register_firstUser_getsAdminRoleInToken() {
        String token = registerAdmin();
        // decode groups claim from JWT payload
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));
        org.assertj.core.api.Assertions.assertThat(payload).contains("ADMIN");
    }

    @Test
    void register_blankUsername_returnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("", "Admin123!"))
                .when().post("/api/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    void register_weakPassword_returnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "alllowercase"))
                .when().post("/api/auth/register")
                .then()
                .statusCode(400);
    }

    // ── POST /api/auth/login ─────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsOk() {
        registerAdmin();

        given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("admin", "Admin123!"))
                .when().post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() {
        registerAdmin();

        given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("admin", "Wrong123!"))
                .when().post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void login_blankFields_returnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("", ""))
                .when().post("/api/auth/login")
                .then()
                .statusCode(400);
    }

    // ── POST /api/auth/refresh ───────────────────────────────────────────────────

    @Test
    void refresh_unknownToken_returnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body(new RefreshRequest("not-a-real-token"))
                .when().post("/api/auth/refresh")
                .then()
                .statusCode(401)
                .body("code", equalTo("TOKEN_NOT_FOUND"));
    }

    // ── POST /api/auth/logout ────────────────────────────────────────────────────

    @Test
    void logout_noAuth_returnsUnauthorized() {
        given()
                .when().post("/api/auth/logout")
                .then()
                .statusCode(401);
    }

    @Test
    void logout_validToken_returnsNoContent() {
        String accessToken = registerAdmin();

        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().post("/api/auth/logout")
                .then()
                .statusCode(204);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String registerAdmin() {
        return given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register")
                .jsonPath()
                .getString("accessToken");
    }
}
