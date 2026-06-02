package io.streamvault.core.api.stream;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;

@QuarkusTest
class StreamResourceIT {

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

    // ── GET /api/stream/{trackId} ────────────────────────────────────────────

    @Test
    void stream_noAuth_returnsUnauthorized() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(401);
    }

    @Test
    void stream_unknownTrack_returnsNotFound() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/stream/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_FOUND"));
    }

    @Test
    void stream_fullFile_returns200WithContent() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(200)
                .header("Accept-Ranges", equalTo("bytes"))
                .header("Content-Type", containsString("audio/mpeg"))
                .header("Content-Length", equalTo("100"));
    }

    @Test
    void stream_fullFile_bodyMatchesUploadedBytes() throws IOException {
        byte[] content = new byte[50];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 256);
        String trackId = uploadTrackWithContent("track.mp3", content);

        byte[] received = given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(200)
                .extract().asByteArray();

        org.assertj.core.api.Assertions.assertThat(received).isEqualTo(content);
    }

    @Test
    void stream_rangeRequest_returns206WithPartialContent() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Range", "bytes=0-9")
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(206)
                .header("Content-Range", equalTo("bytes 0-9/100"))
                .header("Content-Length", equalTo("10"))
                .header("Accept-Ranges", equalTo("bytes"));
    }

    @Test
    void stream_rangeRequest_bodyContainsCorrectBytes() throws IOException {
        byte[] content = new byte[100];
        for (int i = 0; i < content.length; i++) content[i] = (byte) i;
        String trackId = uploadTrackWithContent("track.mp3", content);

        byte[] received = given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Range", "bytes=10-19")
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(206)
                .extract().asByteArray();

        org.assertj.core.api.Assertions.assertThat(received).hasSize(10);
        for (int i = 0; i < 10; i++) {
            org.assertj.core.api.Assertions.assertThat(received[i]).isEqualTo((byte) (10 + i));
        }
    }

    @Test
    void stream_openEndedRange_returnsToEndOfFile() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Range", "bytes=50-")
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(206)
                .header("Content-Range", equalTo("bytes 50-99/100"))
                .header("Content-Length", equalTo("50"));
    }

    @Test
    void stream_suffixRange_returnsLastNBytes() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Range", "bytes=-20")
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(206)
                .header("Content-Range", equalTo("bytes 80-99/100"))
                .header("Content-Length", equalTo("20"));
    }

    @Test
    void stream_invalidRange_returns416() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Range", "bytes=200-300")
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(416)
                .header("Content-Range", equalTo("bytes */100"));
    }

    // ── HEAD ─────────────────────────────────────────────────────────────────

    @Test
    void head_returnsHeadersWithoutBody() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().head("/api/stream/" + trackId)
                .then()
                .statusCode(200)
                .header("Content-Length", equalTo("100"))
                .header("Accept-Ranges", equalTo("bytes"))
                .header("ETag", notNullValue())
                .header("Content-Disposition", containsString("inline"))
                .body(emptyOrNullString());
    }

    // ── ETag ─────────────────────────────────────────────────────────────────

    @Test
    void stream_fullFile_includesEtag() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(200)
                .header("ETag", matchesPattern("\"[^\"]+\""));
    }

    @Test
    void stream_rangeWithMatchingIfRange_serves206() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        String etag = given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/stream/" + trackId)
                .then().extract().header("ETag");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Range", "bytes=0-9")
                .header("If-Range", etag)
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(206)
                .header("Content-Length", equalTo("10"));
    }

    @Test
    void stream_rangeWithStaleIfRange_servesFullFile() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .header("Range", "bytes=0-9")
                .header("If-Range", "\"stale-etag\"")
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(200)
                .header("Content-Length", equalTo("100"));
    }

    // ── Content-Disposition ──────────────────────────────────────────────────

    @Test
    void stream_fullFile_includesFilenameInDisposition() throws IOException {
        String trackId = uploadTrack("track.mp3", 100);
        given()
                .header("Authorization", "Bearer " + adminToken)
                .when().get("/api/stream/" + trackId)
                .then()
                .statusCode(200)
                .header("Content-Disposition", startsWith("inline; filename=\""))
                .header("Content-Disposition", containsString(".mp3\""));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String uploadTrack(String filename, int sizeBytes) throws IOException {
        return uploadTrackWithContent(filename, new byte[sizeBytes]);
    }

    private String uploadTrackWithContent(String filename, byte[] content) throws IOException {
        Path f = tempDir.resolve(filename);
        Files.write(f, content);
        return given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("files", f.toFile(), "audio/mpeg")
                .post("/api/admin/upload")
                .jsonPath().getString("[0].trackId");
    }
}
