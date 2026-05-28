package io.streamvault.core.api.library;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.LoginRequest;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AlbumManagementResourceIT {

    @Inject
    AgroalDataSource ds;

    private String adminToken;
    private UUID adminAlbumId;

    @BeforeEach
    void setup() throws SQLException {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        adminAlbumId = UUID.randomUUID();
        UUID artistEntityId = UUID.randomUUID();

        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
            stmt.execute("INSERT INTO artists (id, name, created_at) VALUES ('%s', 'Test Artist', NOW())".formatted(artistEntityId));
            stmt.execute("INSERT INTO albums (id, title, artist_id, year, created_at) VALUES ('%s', 'Original Title', '%s', 2000, NOW())".formatted(adminAlbumId, artistEntityId));
        }

        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    // ── PATCH /api/albums/{id}/metadata ──────────────────────────────────────

    @Test
    void updateMetadata_noAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"New Title"}
                        """)
                .when().patch("/api/albums/{id}/metadata", adminAlbumId)
                .then()
                .statusCode(401);
    }

    @Test
    void updateMetadata_asUser_returns403() {
        String userToken = createUserToken("alice", "Alice123!", "USER");
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"New Title"}
                        """)
                .when().patch("/api/albums/{id}/metadata", adminAlbumId)
                .then()
                .statusCode(403);
    }

    @Test
    void updateMetadata_asAdmin_updatesTitleAndYear() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Abbey Road","year":1969}
                        """)
                .when().patch("/api/albums/{id}/metadata", adminAlbumId)
                .then()
                .statusCode(200)
                .body("title", equalTo("Abbey Road"))
                .body("year", equalTo(1969));
    }

    @Test
    void updateMetadata_partialUpdate_preservesOtherFields() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Only Title Updated"}
                        """)
                .when().patch("/api/albums/{id}/metadata", adminAlbumId)
                .then()
                .statusCode(200)
                .body("title", equalTo("Only Title Updated"))
                .body("year", equalTo(2000));
    }

    @Test
    void updateMetadata_asArtistNotOwner_returns403() {
        String artistToken = createUserToken("artist1", "Artist123!", "ARTIST");
        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Hijacked"}
                        """)
                .when().patch("/api/albums/{id}/metadata", adminAlbumId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void updateMetadata_unknownAlbum_returns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Ghost"}
                        """)
                .when().patch("/api/albums/{id}/metadata", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("ALBUM_NOT_FOUND"));
    }

    // ── DELETE /api/albums/{id} ───────────────────────────────────────────────

    @Test
    void deleteAlbum_noAuth_returns401() {
        given()
                .when().delete("/api/albums/{id}", adminAlbumId)
                .then()
                .statusCode(401);
    }

    @Test
    void deleteAlbum_asUser_returns403() {
        String userToken = createUserToken("alice", "Alice123!", "USER");
        given()
                .header("Authorization", "Bearer " + userToken)
                .when().delete("/api/albums/{id}", adminAlbumId)
                .then()
                .statusCode(403);
    }

    @Test
    void deleteAlbum_asAdmin_returns204AndAlbumIsGone() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/albums/{id}", adminAlbumId)
                .then()
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void deleteAlbum_asArtistNotOwner_returns403() {
        String artistToken = createUserToken("artist1", "Artist123!", "ARTIST");
        given()
                .header("Authorization", "Bearer " + artistToken)
                .when().delete("/api/albums/{id}", adminAlbumId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void deleteAlbum_unknownAlbum_returns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/albums/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("ALBUM_NOT_FOUND"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String createUserToken(String username, String password, String role) {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(String.format(
                        "{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"%s\"}",
                        username, password, role))
                .post("/api/admin/users");

        return given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest(username, password))
                .post("/api/auth/login")
                .jsonPath().getString("accessToken");
    }
}
