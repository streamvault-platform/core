package io.streamvault.core.api.watch;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import io.streamvault.core.infra.kafka.WatchSyncRequestedEventCollector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WatchSyncResourceIT {

    @Inject AgroalDataSource ds;
    @Inject WatchSyncRequestedEventCollector collector;

    @TempDir Path tempDir;

    private String adminToken;
    private UUID trackId;
    private static final String DEVICE_ID = "watch-device-" + UUID.randomUUID();

    @BeforeEach
    void setup() throws SQLException, IOException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE watch_sync_requests, user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
        }
        collector.clear();

        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");

        trackId = UUID.fromString(uploadTrack("test.mp3"));
        setTranscodedPath(trackId, "transcoded/" + trackId + ".aac");
    }

    @Test
    void requestSync_accepted_publishesKafkaEvent() throws InterruptedException {
        var body = new SyncRequestPayload(DEVICE_ID, List.of(trackId));

        String syncRequestId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/sync/request")
                .then()
                .statusCode(202)
                .body("syncRequestId", notNullValue())
                .body("status", equalTo("PENDING"))
                .body("deviceId", equalTo(DEVICE_ID))
                .body("trackCount", equalTo(1))
                .extract().jsonPath().getString("syncRequestId");

        awaitUntil(() -> collector.received().stream()
                .anyMatch(e -> e.syncRequestId().toString().equals(syncRequestId)));

        var event = collector.received().stream()
                .filter(e -> e.syncRequestId().toString().equals(syncRequestId))
                .findFirst().orElseThrow();
        assertEquals(DEVICE_ID, event.deviceId());
        assertEquals(1, event.tracks().size());
        assertEquals(trackId, event.tracks().get(0).trackId());
        assertNotNull(event.tracks().get(0).downloadUrl());
    }

    @Test
    void requestSync_unknownTrack_returns404() {
        var body = new SyncRequestPayload(DEVICE_ID, List.of(UUID.randomUUID()));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/sync/request")
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_FOUND"));
    }

    @Test
    void requestSync_notTranscoded_returns422() throws SQLException {
        clearTranscodedPath(trackId);

        var body = new SyncRequestPayload(DEVICE_ID, List.of(trackId));

        given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(body)
                .post("/api/sync/request")
                .then()
                .statusCode(422)
                .body("code", equalTo("TRACK_NOT_TRANSCODED"));
    }

    @Test
    void requestSync_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(new SyncRequestPayload(DEVICE_ID, List.of(trackId)))
                .post("/api/sync/request")
                .then()
                .statusCode(401);
    }

    @Test
    void getStatus_ownRequest_returnsStatus() {
        String syncRequestId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new SyncRequestPayload(DEVICE_ID, List.of(trackId)))
                .post("/api/sync/request")
                .then().statusCode(202)
                .extract().jsonPath().getString("syncRequestId");

        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/sync/status/" + syncRequestId)
                .then()
                .statusCode(200)
                .body("syncRequestId", equalTo(syncRequestId))
                .body("status", equalTo("PENDING"));
    }

    @Test
    void getStatus_unknownId_returns404() {
        given()
                .header("Authorization", "Bearer " + adminToken)
                .get("/api/sync/status/" + UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String uploadTrack(String name) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, new byte[50]);
        return given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType("multipart/form-data")
                .multiPart("files", f.toFile(), "audio/mpeg")
                .post("/api/admin/upload")
                .jsonPath().getString("[0].trackId");
    }

    private void setTranscodedPath(UUID trackId, String path) throws SQLException {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("UPDATE tracks SET transcoded_path = ? WHERE id = ?")) {
            ps.setString(1, path);
            ps.setObject(2, trackId);
            ps.executeUpdate();
        }
    }

    private void clearTranscodedPath(UUID trackId) throws SQLException {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("UPDATE tracks SET transcoded_path = NULL WHERE id = ?")) {
            ps.setObject(1, trackId);
            ps.executeUpdate();
        }
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("Condition not met within 10 s");
            Thread.sleep(100);
        }
    }

    record SyncRequestPayload(String deviceId, List<UUID> trackIds) {}
}
