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
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class TrackManagementResourceIT {

    @Inject
    AgroalDataSource ds;

    @TempDir
    Path tempDir;

    private String adminToken;
    private String adminTrackId;

    @BeforeEach
    void setup() throws SQLException, IOException {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
        }
        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");

        adminTrackId = uploadTrack(adminToken, "admin-track.mp3");
    }

    // ── PATCH /api/tracks/{id}/metadata ──────────────────────────────────────

    @Test
    void updateMetadata_noAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"New Title"}
                        """)
                .when().patch("/api/tracks/{id}/metadata", adminTrackId)
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
                .when().patch("/api/tracks/{id}/metadata", adminTrackId)
                .then()
                .statusCode(403);
    }

    @Test
    void updateMetadata_asAdmin_updatesAllFields() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Abbey Road","genre":"Rock","year":1969,"trackNumber":1,"discNumber":1}
                        """)
                .when().patch("/api/tracks/{id}/metadata", adminTrackId)
                .then()
                .statusCode(200)
                .body("title", equalTo("Abbey Road"))
                .body("genre", equalTo("Rock"))
                .body("year", equalTo(1969));
    }

    @Test
    void updateMetadata_partialUpdate_doesNotClearOtherFields() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Original","genre":"Jazz"}
                        """)
                .patch("/api/tracks/{id}/metadata", adminTrackId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Updated"}
                        """)
                .when().patch("/api/tracks/{id}/metadata", adminTrackId)
                .then()
                .statusCode(200)
                .body("title", equalTo("Updated"))
                .body("genre", equalTo("Jazz"));
    }

    @Test
    void updateMetadata_asArtistOwner_returns200() throws IOException {
        String artistToken = createUserToken("artist1", "Artist123!", "ARTIST");
        String artistTrackId = uploadTrack(artistToken, "artist-track.mp3");

        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"My Song"}
                        """)
                .when().patch("/api/tracks/{id}/metadata", artistTrackId)
                .then()
                .statusCode(200)
                .body("title", equalTo("My Song"));
    }

    @Test
    void updateMetadata_asArtistNotOwner_returns403() {
        String artistToken = createUserToken("artist1", "Artist123!", "ARTIST");
        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Stolen Title"}
                        """)
                .when().patch("/api/tracks/{id}/metadata", adminTrackId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void updateMetadata_unknownTrack_returns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("""
                        {"title":"Ghost"}
                        """)
                .when().patch("/api/tracks/{id}/metadata", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_FOUND"));
    }

    // ── DELETE /api/tracks/{id} ───────────────────────────────────────────────

    @Test
    void deleteTrack_noAuth_returns401() {
        given()
                .when().delete("/api/tracks/{id}", adminTrackId)
                .then()
                .statusCode(401);
    }

    @Test
    void deleteTrack_asUser_returns403() {
        String userToken = createUserToken("alice", "Alice123!", "USER");
        given()
                .header("Authorization", "Bearer " + userToken)
                .when().delete("/api/tracks/{id}", adminTrackId)
                .then()
                .statusCode(403);
    }

    @Test
    void deleteTrack_asAdmin_returns204AndTrackIsGone() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/tracks/{id}", adminTrackId)
                .then()
                .statusCode(204);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void deleteTrack_asArtistOwner_returns204() throws IOException {
        String artistToken = createUserToken("artist1", "Artist123!", "ARTIST");
        String artistTrackId = uploadTrack(artistToken, "artist-del.mp3");

        given()
                .header("Authorization", "Bearer " + artistToken)
                .when().delete("/api/tracks/{id}", artistTrackId)
                .then()
                .statusCode(204);
    }

    @Test
    void deleteTrack_asArtistNotOwner_returns403() {
        String artistToken = createUserToken("artist1", "Artist123!", "ARTIST");
        given()
                .header("Authorization", "Bearer " + artistToken)
                .when().delete("/api/tracks/{id}", adminTrackId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void deleteTrack_unknownTrack_returns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/tracks/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_FOUND"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String uploadTrack(String token, String filename) throws IOException {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile(filename), "audio/mpeg")
                .post("/api/admin/upload")
                .jsonPath().getString("[0].trackId");
    }

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

    private File tempFile(String name) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, new byte[0]);
        return f.toFile();
    }
}
