package io.streamvault.core.api.library;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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
class AlbumCoverResourceIT {

    @Inject
    AgroalDataSource ds;

    @TempDir
    Path tempDir;

    private String adminToken;
    private UUID albumId;

    @BeforeEach
    void setup() throws SQLException {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        albumId = UUID.randomUUID();
        UUID artistId = UUID.randomUUID();

        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
            stmt.execute("INSERT INTO artists (id, name, created_at) VALUES ('%s', 'Test Artist', NOW())".formatted(artistId));
            stmt.execute("INSERT INTO albums (id, title, artist_id, year, created_at) VALUES ('%s', 'Test Album', '%s', 2024, NOW())".formatted(albumId, artistId));
        }

        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    // ── PUT /api/albums/{id}/cover ────────────────────────────────────────────

    @Test
    void uploadCover_noAuth_returns401() throws IOException {
        given()
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover.jpg"), "image/jpeg")
                .when().put("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(401);
    }

    @Test
    void uploadCover_noFile_returns400() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .when().put("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(400)
                .body("code", equalTo("NO_FILE"));
    }

    @Test
    void uploadCover_unsupportedType_returns400() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover.bmp"), "image/bmp")
                .when().put("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(400)
                .body("code", equalTo("UNSUPPORTED_IMAGE_TYPE"));
    }

    @Test
    void uploadCover_validJpeg_returns200WithCoverUrl() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover.jpg"), "image/jpeg")
                .when().put("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(200)
                .body("id", equalTo(albumId.toString()))
                .body("coverUrl", equalTo("/api/albums/" + albumId + "/cover"));
    }

    @Test
    void uploadCover_validPng_returns200WithCoverUrl() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover.png"), "image/png")
                .when().put("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(200)
                .body("coverUrl", equalTo("/api/albums/" + albumId + "/cover"));
    }

    @Test
    void uploadCover_unknownAlbum_returns404() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover.jpg"), "image/jpeg")
                .when().put("/api/albums/{id}/cover", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("ALBUM_NOT_FOUND"));
    }

    @Test
    void uploadCover_replaceExisting_coverUrlStable() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover1.jpg"), "image/jpeg")
                .put("/api/albums/{id}/cover", albumId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover2.png"), "image/png")
                .when().put("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(200)
                .body("coverUrl", equalTo("/api/albums/" + albumId + "/cover"));
    }

    // ── GET /api/albums/{id}/cover ────────────────────────────────────────────

    @Test
    void getCover_noAuth_returns401() {
        given()
                .redirects().follow(false)
                .when().get("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(401);
    }

    @Test
    void getCover_noCoverUploaded_returns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .redirects().follow(false)
                .when().get("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(404);
    }

    @Test
    void getCover_afterUpload_returns302WithLocation() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover.jpg"), "image/jpeg")
                .put("/api/albums/{id}/cover", albumId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .redirects().follow(false)
                .when().get("/api/albums/{id}/cover", albumId)
                .then()
                .statusCode(302)
                .header("Location", notNullValue());
    }

    @Test
    void getCover_unknownAlbum_returns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .redirects().follow(false)
                .when().get("/api/albums/{id}/cover", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    // ── AlbumResponse.coverUrl ────────────────────────────────────────────────

    @Test
    void listAlbums_noCover_coverUrlIsNull() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("[0].coverUrl", nullValue());
    }

    @Test
    void listAlbums_afterCoverUpload_coverUrlPresent() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("file", imageFile("cover.jpg"), "image/jpeg")
                .put("/api/albums/{id}/cover", albumId);

        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("[0].coverUrl", equalTo("/api/albums/" + albumId + "/cover"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private File imageFile(String name) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}); // minimal JPEG header bytes
        return f.toFile();
    }
}
