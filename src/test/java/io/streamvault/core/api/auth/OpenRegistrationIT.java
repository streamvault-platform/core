package io.streamvault.core.api.auth;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@TestProfile(OpenRegistrationTestProfile.class)
class OpenRegistrationIT {

    @Inject
    AgroalDataSource ds;

    @BeforeEach
    void setup() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE invite_links, refresh_tokens, users CASCADE");
        }
    }

    @Test
    void setupStatus_reflectsOpenRegistrationEnabled() {
        given()
                .get("/api/setup/status")
                .then()
                .statusCode(200)
                .body("openRegistrationEnabled", equalTo(true));
    }

    @Test
    void register_secondUser_noInviteNeeded_returns201() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register");

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("user2", "User456!", null))
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .body("accessToken", notNullValue());
    }

    @Test
    void register_secondUser_getsUserRole() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register");

        String token = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("user2", "User456!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");

        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));
        org.assertj.core.api.Assertions.assertThat(payload)
                .contains("USER")
                .doesNotContain("ADMIN");
    }

    @Test
    void register_firstUser_stillBecomesAdmin_withOpenRegistration() {
        String token = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");

        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]));
        org.assertj.core.api.Assertions.assertThat(payload).contains("ADMIN");
    }

    @Test
    void register_duplicateUsername_returns409() {
        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register");

        given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .then()
                .statusCode(409)
                .body("code", equalTo("USERNAME_TAKEN"));
    }
}
