package io.streamvault.core.api.admin;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.LoginRequest;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import static org.assertj.core.api.Assertions.assertThat;
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
                .body(new RegisterRequest("admin", "Admin123!", null))
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

    // ── ownership ────────────────────────────────────────────────────────────

    @Test
    void upload_asArtist_returnsCreated() throws IOException {
        String artistToken = createArtistToken("artist1", "Artist123!");

        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track.mp3"), "audio/mpeg")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(201)
                .body("$", hasSize(1));
    }

    @Test
    void upload_setsOwnerIdToCallerUserId() throws IOException, SQLException {
        String artistId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"artist1\",\"password\":\"Artist123!\",\"role\":\"ARTIST\"}")
                .post("/api/admin/users")
                .jsonPath().getString("id");

        String artistToken = given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("artist1", "Artist123!"))
                .post("/api/auth/login")
                .jsonPath().getString("accessToken");

        given()
                .header("Authorization", "Bearer " + artistToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track.mp3"), "audio/mpeg")
                .post("/api/admin/upload");

        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery("SELECT owner_id::text FROM tracks LIMIT 1");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo(artistId);
        }
    }

    @Test
    void upload_asUser_returnsForbidden() throws IOException {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"user1\",\"password\":\"User1234!\",\"role\":\"USER\"}")
                .post("/api/admin/users");

        String userToken = given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("user1", "User1234!"))
                .post("/api/auth/login")
                .jsonPath().getString("accessToken");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track.mp3"), "audio/mpeg")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(403);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String createArtistToken(String username, String password) {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(String.format(
                        "{\"username\":\"%s\",\"password\":\"%s\",\"role\":\"ARTIST\"}",
                        username, password))
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
