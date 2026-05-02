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

    @Inject AgroalDataSource ds;

    @BeforeEach
    void cleanup() throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE refresh_tokens, users");
        }
    }

    // ── POST /auth/register ──────────────────────────────────────────────────

    @Test
    void register_returnsCreated_withTokenPair() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .when().post("/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue())
                .body("refreshToken", notNullValue());
    }

    @Test
    void register_secondCall_returnsConflict() {
        registerAdmin();

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin2", "Admin456!"))
                .when().post("/auth/register")
                .then()
                .statusCode(409)
                .body("code", equalTo("ADMIN_EXISTS"));
    }

    @Test
    void register_blankUsername_returnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("", "Admin123!"))
                .when().post("/auth/register")
                .then()
                .statusCode(400);
    }

    @Test
    void register_weakPassword_returnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "alllowercase"))
                .when().post("/auth/register")
                .then()
                .statusCode(400);
    }

    // ── POST /auth/login ─────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returnsOk() {
        registerAdmin();

        given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("admin", "Admin123!"))
                .when().post("/auth/login")
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
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("INVALID_CREDENTIALS"));
    }

    @Test
    void login_blankFields_returnsBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("", ""))
                .when().post("/auth/login")
                .then()
                .statusCode(400);
    }

    // ── POST /auth/refresh ───────────────────────────────────────────────────

    @Test
    void refresh_unknownToken_returnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body(new RefreshRequest("not-a-real-token"))
                .when().post("/auth/refresh")
                .then()
                .statusCode(401)
                .body("code", equalTo("TOKEN_NOT_FOUND"));
    }

    // ── POST /auth/logout ────────────────────────────────────────────────────

    @Test
    void logout_noAuth_returnsUnauthorized() {
        given()
                .when().post("/auth/logout")
                .then()
                .statusCode(401);
    }

    @Test
    void logout_validToken_returnsNoContent() {
        String accessToken = registerAdmin();

        given()
                .header("Authorization", "Bearer " + accessToken)
                .when().post("/auth/logout")
                .then()
                .statusCode(204);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String registerAdmin() {
        return given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/auth/register")
                .jsonPath()
                .getString("accessToken");
    }
}
