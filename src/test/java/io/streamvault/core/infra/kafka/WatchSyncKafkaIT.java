package io.streamvault.core.infra.kafka;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import io.streamvault.core.application.watch.WatchSyncReadyEvent;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class WatchSyncKafkaIT {

    @Inject AgroalDataSource ds;

    @Inject
    @Channel("test-watch-sync-ready")
    MutinyEmitter<WatchSyncReadyEvent> syncReadyEmitter;

    @TempDir Path tempDir;

    private String adminToken;
    private static final String DEVICE_ID = "watch-device-" + UUID.randomUUID();

    @BeforeEach
    void setup() throws SQLException, IOException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE watch_sync_requests, user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
        }

        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    @Test
    void syncReady_updatesRequestToReadyWithManifest() throws Exception {
        UUID trackId = UUID.fromString(uploadTrack("song.mp3"));
        setTranscodedPath(trackId, "transcoded/" + trackId + ".aac");

        String syncRequestId = given()
                .header("Authorization", "Bearer " + adminToken)
                .contentType(ContentType.JSON)
                .body(new SyncRequestPayload(DEVICE_ID, List.of(trackId)))
                .post("/api/sync/request")
                .then().statusCode(202)
                .extract().jsonPath().getString("syncRequestId");

        UUID syncId = UUID.fromString(syncRequestId);
        var manifestEntry = new WatchSyncReadyEvent.ManifestEntry(trackId, "https://rustfs/watch/" + trackId + ".aac", 512_000L);
        var readyEvent = new WatchSyncReadyEvent(syncId, adminUserId(), DEVICE_ID, List.of(manifestEntry));

        syncReadyEmitter.send(readyEvent).await().indefinitely();

        awaitUntil(() -> "READY".equals(syncStatus(syncId)));

        assertEquals("READY", syncStatus(syncId));
        assertNotNull(syncManifest(syncId));
    }

    @Test
    void syncReady_unknownSyncRequest_ignoredGracefully() throws Exception {
        var event = new WatchSyncReadyEvent(
                UUID.randomUUID(), UUID.randomUUID(), DEVICE_ID,
                List.of(new WatchSyncReadyEvent.ManifestEntry(UUID.randomUUID(), "https://ghost/url", 0L)));

        syncReadyEmitter.send(event).await().indefinitely();

        Thread.sleep(2_000);

        given().get("/health").then().statusCode(200);
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

    private UUID adminUserId() throws SQLException {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("SELECT id FROM users WHERE username = 'admin'")) {
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            return (UUID) rs.getObject(1);
        }
    }

    private String syncStatus(UUID syncId) {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("SELECT status FROM watch_sync_requests WHERE id = ?")) {
            ps.setObject(1, syncId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String syncManifest(UUID syncId) {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("SELECT manifest FROM watch_sync_requests WHERE id = ?")) {
            ps.setObject(1, syncId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString(1) : null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
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
