package io.streamvault.core.api.admin;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.LoginRequest;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminUserResourceIT {

    @Inject
    AgroalDataSource ds;

    private String adminToken;

    @BeforeEach
    void setup() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE user_library_tracks, refresh_tokens, users CASCADE");
        }
        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    // ── GET /api/admin/users ────────────────────────────────────────────────

    @Test
    void listUsers_noAuth_returnsUnauthorized() {
        given()
                .when().get("/api/admin/users")
                .then()
                .statusCode(401);
    }

    @Test
    void listUsers_asAdmin_returnsAdminInList() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/admin/users")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].username", equalTo("admin"))
                .body("[0].role", equalTo("ADMIN"));
    }

    @Test
    void listUsers_asUser_returnsForbidden() {
        createUser("alice", "Alice123!", "USER");
        String userToken = loginToken("alice", "Alice123!");

        given()
                .header("Authorization", "Bearer " + userToken)
                .when().get("/api/admin/users")
                .then()
                .statusCode(403);
    }

    // ── POST /api/admin/users ───────────────────────────────────────────────

    @Test
    void createUser_asAdmin_returnsCreated() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"artist1","password":"Artist123!","role":"ARTIST"}
                        """)
                .when().post("/api/admin/users")
                .then()
                .statusCode(201)
                .body("username", equalTo("artist1"))
                .body("role", equalTo("ARTIST"))
                .body("id", notNullValue());
    }

    @Test
    void createUser_duplicateUsername_returnsConflict() {
        createUser("alice", "Alice123!", "USER");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"alice","password":"Alice456!","role":"USER"}
                        """)
                .when().post("/api/admin/users")
                .then()
                .statusCode(409)
                .body("code", equalTo("USERNAME_TAKEN"));
    }

    @Test
    void createUser_noAuth_returnsUnauthorized() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"bob","password":"Bob12345!","role":"USER"}
                        """)
                .when().post("/api/admin/users")
                .then()
                .statusCode(401);
    }

    // ── PATCH /api/admin/users/{id}/role ────────────────────────────────────

    @Test
    void changeRole_asAdmin_updatesRole() {
        String userId = createUser("alice", "Alice123!", "USER");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"role":"ARTIST"}
                        """)
                .when().patch("/api/admin/users/" + userId + "/role")
                .then()
                .statusCode(200)
                .body("role", equalTo("ARTIST"));
    }

    @Test
    void changeRole_lastAdmin_returnsConflict() {
        String adminId = given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/admin/users")
                .jsonPath().getString("[0].id");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"role":"USER"}
                        """)
                .when().patch("/api/admin/users/" + adminId + "/role")
                .then()
                .statusCode(409)
                .body("code", equalTo("LAST_ADMIN"));
    }

    @Test
    void changeRole_unknownUser_returnsNotFound() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"role":"ARTIST"}
                        """)
                .when().patch("/api/admin/users/00000000-0000-0000-0000-000000000000/role")
                .then()
                .statusCode(404)
                .body("code", equalTo("USER_NOT_FOUND"));
    }

    // ── DELETE /api/admin/users/{id} ────────────────────────────────────────

    @Test
    void deleteUser_asAdmin_returnsNoContent() {
        String userId = createUser("alice", "Alice123!", "USER");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/admin/users/" + userId)
                .then()
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/admin/users")
                .then()
                .body("size()", equalTo(1)); // only admin remains
    }

    @Test
    void deleteUser_lastAdmin_returnsConflict() {
        String adminId = given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/admin/users")
                .jsonPath().getString("[0].id");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/admin/users/" + adminId)
                .then()
                .statusCode(409)
                .body("code", equalTo("LAST_ADMIN"));
    }

    @Test
    void deleteUser_notFound_returnsNotFound() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/admin/users/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("USER_NOT_FOUND"));
    }

    @Test
    void deleteUser_noAuth_returnsUnauthorized() {
        given()
                .when().delete("/api/admin/users/00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(401);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String createUser(String username, String password, String role) {
        return given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(String.format(
                        "{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}",
                        username, password, role))
                .post("/api/admin/users")
                .jsonPath().getString("id");
    }

    private String loginToken(String username, String password) {
        return given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest(username, password))
                .post("/api/auth/login")
                .jsonPath().getString("accessToken");
    }
}
