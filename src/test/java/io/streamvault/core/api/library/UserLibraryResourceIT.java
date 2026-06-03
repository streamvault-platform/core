package io.streamvault.core.api.library;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import io.streamvault.core.api.library.dto.AddToLibraryRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class UserLibraryResourceIT {

    @Inject
    AgroalDataSource ds;

    @TempDir
    Path tempDir;

    private String adminToken;

    @BeforeEach
    void setup() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
        }
        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    // ── GET /api/library/my ──────────────────────────────────────────────────

    @Test
    void listLibrary_emptyByDefault() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/library/my")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void listLibrary_noAuth_returnsUnauthorized() {
        given()
                .when().get("/api/library/my")
                .then()
                .statusCode(401);
    }

    // ── POST /api/library/my ─────────────────────────────────────────────────

    @Test
    void addTrack_returnsCreatedEntry() throws IOException {
        String trackId = uploadTrack("song.mp3");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new AddToLibraryRequest(java.util.UUID.fromString(trackId)))
                .when().post("/api/library/my")
                .then()
                .statusCode(201)
                .body("trackId", equalTo(trackId))
                .body("addedAt", notNullValue());
    }

    @Test
    void addTrack_duplicate_returnsConflict() throws IOException {
        String trackId = uploadTrack("song.mp3");
        addToLibrary(trackId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new AddToLibraryRequest(java.util.UUID.fromString(trackId)))
                .when().post("/api/library/my")
                .then()
                .statusCode(409)
                .body("code", equalTo("ALREADY_IN_LIBRARY"));
    }

    @Test
    void addTrack_unknownTrackId_returnsNotFound() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new AddToLibraryRequest(java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")))
                .when().post("/api/library/my")
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_FOUND"));
    }

    // ── DELETE /api/library/my/{trackId} ─────────────────────────────────────

    @Test
    void removeTrack_returnsNoContent() throws IOException {
        String trackId = uploadTrack("song.mp3");
        addToLibrary(trackId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/library/my/" + trackId)
                .then()
                .statusCode(204);
    }

    @Test
    void removeTrack_notInLibrary_returnsNotFound() throws IOException {
        String trackId = uploadTrack("song.mp3");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/library/my/" + trackId)
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_IN_LIBRARY"));
    }

    @Test
    void listLibrary_afterAddAndRemove_isEmptyAgain() throws IOException {
        String trackId = uploadTrack("song.mp3");
        addToLibrary(trackId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().delete("/api/library/my/" + trackId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/library/my")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String uploadTrack(String name) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, new byte[0]);
        return given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("files", f.toFile(), "audio/mpeg")
                .post("/api/admin/upload")
                .jsonPath()
                .getString("[0].trackId");
    }

    private void addToLibrary(String trackId) {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new AddToLibraryRequest(java.util.UUID.fromString(trackId)))
                .post("/api/library/my");
    }
}
