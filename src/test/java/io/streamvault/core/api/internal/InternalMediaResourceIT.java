package io.streamvault.core.api.internal;

import io.quarkus.test.junit.QuarkusTest;
import io.streamvault.core.application.storage.InternalUrlSigner;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

@QuarkusTest
class InternalMediaResourceIT {

    @Inject
    InternalUrlSigner signer;

    @TempDir
    Path tempDir;

    // ── GET /api/internal/media/download ─────────────────────────────────────

    @Test
    void download_validSignature_returnsFileContent() throws IOException {
        Path file = tempDir.resolve("test.aac");
        Files.write(file, "audio bytes".getBytes(StandardCharsets.UTF_8));

        given()
                .queryParams(signedDownloadParams(file.toString(), Duration.ofHours(1)))
                .when().get("/api/internal/media/download")
                .then()
                .statusCode(200);
    }

    @Test
    void download_invalidSignature_returns403() {
        given()
                .queryParam("path", tempDir.resolve("track.aac").toString())
                .queryParam("expires", Instant.now().plusSeconds(3600).getEpochSecond())
                .queryParam("sig", "invalidsig")
                .when().get("/api/internal/media/download")
                .then()
                .statusCode(403);
    }

    @Test
    void download_expiredSignature_returns403() {
        given()
                .queryParam("path", tempDir.resolve("track.aac").toString())
                .queryParam("expires", 1L)
                .queryParam("sig", "anysig")
                .when().get("/api/internal/media/download")
                .then()
                .statusCode(403);
    }

    @Test
    void download_fileNotFound_returns404() {
        String missing = tempDir.resolve("nonexistent-" + UUID.randomUUID() + ".aac").toString();

        given()
                .queryParams(signedDownloadParams(missing, Duration.ofHours(1)))
                .when().get("/api/internal/media/download")
                .then()
                .statusCode(404);
    }

    // ── PUT /api/internal/media/upload ───────────────────────────────────────

    @Test
    void upload_validSignature_storesFile() throws IOException {
        Path dest = tempDir.resolve("uploaded.aac");

        given()
                .queryParams(signedUploadParams(dest.toString(), Duration.ofHours(1)))
                .contentType("application/octet-stream")
                .body("transcoded audio".getBytes(StandardCharsets.UTF_8))
                .when().put("/api/internal/media/upload")
                .then()
                .statusCode(204);

        assertThat(Files.readString(dest)).isEqualTo("transcoded audio");
    }

    @Test
    void upload_invalidSignature_returns403() {
        given()
                .queryParam("path", tempDir.resolve("track.aac").toString())
                .queryParam("expires", Instant.now().plusSeconds(3600).getEpochSecond())
                .queryParam("sig", "invalidsig")
                .contentType("application/octet-stream")
                .body("audio".getBytes(StandardCharsets.UTF_8))
                .when().put("/api/internal/media/upload")
                .then()
                .statusCode(403);
    }

    @Test
    void upload_expiredSignature_returns403() {
        given()
                .queryParam("path", tempDir.resolve("track.aac").toString())
                .queryParam("expires", 1L)
                .queryParam("sig", "anysig")
                .contentType("application/octet-stream")
                .body("audio".getBytes(StandardCharsets.UTF_8))
                .when().put("/api/internal/media/upload")
                .then()
                .statusCode(403);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, String> signedDownloadParams(String storedPath, Duration expiry) {
        return parseSignedUrl(signer.signedDownloadUrl(storedPath, expiry));
    }

    private Map<String, String> signedUploadParams(String storedPath, Duration expiry) {
        return parseSignedUrl(signer.signedUploadUrl(storedPath, expiry));
    }

    private static Map<String, String> parseSignedUrl(String url) {
        Map<String, String> map = new HashMap<>();
        for (String part : URI.create(url).getRawQuery().split("&")) {
            String[] kv = part.split("=", 2);
            map.put(kv[0], kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return map;
    }
}
