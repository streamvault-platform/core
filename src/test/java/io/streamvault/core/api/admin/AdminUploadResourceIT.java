package io.streamvault.core.api.admin;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
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

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AdminUploadResourceIT {

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
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    // ── POST /api/admin/upload ───────────────────────────────────────────────

    @Test
    void upload_noAuth_returnsUnauthorized() throws IOException {
        given()
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track.mp3"), "audio/mpeg")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(401);
    }

    @Test
    void upload_singleFile_returnsCreatedWithTrack() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track.mp3"), "audio/mpeg")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(201)
                .body("$", hasSize(1))
                .body("[0].trackId", notNullValue())
                .body("[0].mimeType", equalTo("audio/mpeg"));
    }

    @Test
    void upload_bulkFiles_returnsAllTracks() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track1.mp3"), "audio/mpeg")
                .multiPart("files", tempFile("track2.mp3"), "audio/mpeg")
                .multiPart("files", tempFile("track3.flac"), "audio/flac")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(201)
                .body("$", hasSize(3));
    }

    @Test
    void upload_unsupportedExtension_returns422() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("image.jpg"), "image/jpeg")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(422)
                .body("code", equalTo("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void upload_noFiles_returnsBadRequest() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(400);
    }

    @Test
    void upload_trackAppearsInCatalog() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track.mp3"), "audio/mpeg")
                .post("/api/admin/upload");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(1));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private File tempFile(String name) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, new byte[0]);
        return f.toFile();
    }
}
