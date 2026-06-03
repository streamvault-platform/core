package io.streamvault.core.api.admin;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class InviteResourceIT {

    @Inject
    AgroalDataSource ds;

    private String adminToken;

    @BeforeEach
    void setup() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE invite_links, refresh_tokens, users CASCADE");
        }
        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    // ── POST /api/admin/invites ───────────────────────────────────────────────

    @Test
    void createInvite_asAdmin_returns201WithToken() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/api/admin/invites")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("token", hasLength(64))
                .body("createdAt", notNullValue())
                .body("expiresAt", notNullValue());
    }

    @Test
    void createInvite_withZeroDays_noExpiry() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"expiresInDays\": 0}")
                .post("/api/admin/invites")
                .then()
                .statusCode(201)
                .body("expiresAt", nullValue());
    }

    @Test
    void createInvite_withCustomExpiry() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"expiresInDays\": 3}")
                .post("/api/admin/invites")
                .then()
                .statusCode(201)
                .body("expiresAt", notNullValue());
    }

    @Test
    void createInvite_noAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/api/admin/invites")
                .then()
                .statusCode(401);
    }

    // ── GET /api/admin/invites ────────────────────────────────────────────────

    @Test
    void listInvites_returnsActiveOnly() {
        createInvite();
        createInvite();

        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/admin/invites")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }

    @Test
    void listInvites_excludesUsedInvite() throws SQLException {
        String token = createInviteToken();
        markTokenUsed(token);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/admin/invites")
                .then()
                .statusCode(200)
                .body("", hasSize(0));
    }

    @Test
    void listInvites_noAuth_returns401() {
        given()
                .get("/api/admin/invites")
                .then()
                .statusCode(401);
    }

    // ── DELETE /api/admin/invites/{id} ────────────────────────────────────────

    @Test
    void revokeInvite_returns204_andRemovesFromList() {
        String id = createInvite();

        given()
                .header("Authorization", "Bearer " + adminToken)
                .delete("/api/admin/invites/" + id)
                .then()
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/admin/invites")
                .then()
                .statusCode(200)
                .body("", hasSize(0));
    }

    // ── GET /api/auth/invite ──────────────────────────────────────────────────

    @Test
    void checkInvite_validToken_returnsTrue() {
        String token = createInviteToken();

        given()
                .queryParam("invite", token)
                .get("/api/auth/invite")
                .then()
                .statusCode(200)
                .body("valid", equalTo(true));
    }

    @Test
    void checkInvite_unknownToken_returnsFalse() {
        given()
                .queryParam("invite", "0".repeat(64))
                .get("/api/auth/invite")
                .then()
                .statusCode(200)
                .body("valid", equalTo(false));
    }

    @Test
    void checkInvite_usedToken_returnsFalse() throws SQLException {
        String token = createInviteToken();
        markTokenUsed(token);

        given()
                .queryParam("invite", token)
                .get("/api/auth/invite")
                .then()
                .statusCode(200)
                .body("valid", equalTo(false));
    }

    @Test
    void checkInvite_expiredToken_returnsFalse() throws SQLException {
        String token = createInviteToken();
        expireToken(token);

        given()
                .queryParam("invite", token)
                .get("/api/auth/invite")
                .then()
                .statusCode(200)
                .body("valid", equalTo(false));
    }

    // ── Registration with invite ──────────────────────────────────────────────

    @Test
    void register_withValidInvite_returns201() {
        String token = createInviteToken();

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("newuser", "NewPass123!", token))
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue());
    }

    @Test
    void register_withUsedInvite_returns410() {
        String token = createInviteToken();

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("user1", "NewPass123!", token))
                .post("/api/auth/register");

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("user2", "NewPass123!", token))
                .post("/api/auth/register")
                .then()
                .statusCode(410)
                .body("code", equalTo("INVALID_INVITE"));
    }

    @Test
    void register_withExpiredInvite_returns410() throws SQLException {
        String token = createInviteToken();
        expireToken(token);

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("newuser", "NewPass123!", token))
                .post("/api/auth/register")
                .then()
                .statusCode(410)
                .body("code", equalTo("INVALID_INVITE"));
    }

    @Test
    void register_withInvalidToken_returns410() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("newuser", "NewPass123!", "0".repeat(64)))
                .post("/api/auth/register")
                .then()
                .statusCode(410)
                .body("code", equalTo("INVALID_INVITE"));
    }

    @Test
    void register_noToken_closedRegistration_returns403() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("newuser", "NewPass123!", null))
                .post("/api/auth/register")
                .then()
                .statusCode(403)
                .body("code", equalTo("REGISTRATION_CLOSED"));
    }

    @Test
    void inviteIsMarkedUsed_afterSuccessfulRegistration() {
        String token = createInviteToken();

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("newuser", "NewPass123!", token))
                .post("/api/auth/register");

        given()
                .queryParam("invite", token)
                .get("/api/auth/invite")
                .then()
                .body("valid", equalTo(false));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String createInvite() {
        return given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/api/admin/invites")
                .jsonPath().getString("id");
    }

    private String createInviteToken() {
        return given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{}")
                .post("/api/admin/invites")
                .jsonPath().getString("token");
    }

    private void markTokenUsed(String token) throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(
                     "UPDATE invite_links SET used_at = now() WHERE token = ?")) {
            stmt.setString(1, token);
            stmt.executeUpdate();
        }
    }

    private void expireToken(String token) throws SQLException {
        try (var conn = ds.getConnection();
             var stmt = conn.prepareStatement(
                     "UPDATE invite_links SET expires_at = now() - interval '1 hour' WHERE token = ?")) {
            stmt.setString(1, token);
            stmt.executeUpdate();
        }
    }
}
