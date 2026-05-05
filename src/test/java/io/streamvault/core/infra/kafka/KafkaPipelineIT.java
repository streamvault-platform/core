package io.streamvault.core.infra.kafka;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import io.streamvault.core.application.pipeline.event.MetadataReadyEvent;
import io.streamvault.core.application.pipeline.event.TranscodedEvent;
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
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class KafkaPipelineIT {

    @Inject
    AgroalDataSource ds;

    @Inject
    UploadedEventCollector collector;

    @Inject
    @Channel("test-media-metadata-ready")
    MutinyEmitter<MetadataReadyEvent> metadataEmitter;

    @Inject
    @Channel("test-media-transcoded")
    MutinyEmitter<TranscodedEvent> transcodedEmitter;

    @TempDir
    Path tempDir;

    private String adminToken;

    @BeforeEach
    void setup() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
        }
        collector.clear();
        adminToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    @Test
    void upload_publishesTrackUploadedEvent() throws Exception {
        String trackId = uploadTrack("track.mp3");

        awaitUntil(() -> collector.received().stream()
                .anyMatch(e -> e.trackId().toString().equals(trackId)));

        var event = collector.received().stream()
                .filter(e -> e.trackId().toString().equals(trackId))
                .findFirst().orElseThrow();
        assertEquals("audio/mpeg", event.mimeType());
        assertEquals("track.mp3", event.originalFilename());
    }

    @Test
    void metadataReady_updatesTrackInDatabase() throws Exception {
        String trackId = uploadTrack("song.mp3");
        UUID id = UUID.fromString(trackId);

        var event = new MetadataReadyEvent(
                id, "New Title", "Test Artist", "Test Album",
                2024, 1, 1, 180000, "Electronic");
        metadataEmitter.send(event).await().indefinitely();

        awaitUntil(() -> "New Title".equals(trackTitle(id)));

        assertEquals("New Title", trackTitle(id));
        assertTrue(artistExists("Test Artist"));
    }

    @Test
    void metadataReady_unknownTrack_ignoredGracefully() throws Exception {
        var event = new MetadataReadyEvent(
                UUID.randomUUID(), "Ghost Title", "Ghost Artist", null,
                null, null, null, null, null);
        metadataEmitter.send(event).await().indefinitely();

        Thread.sleep(2_000);

        given().get("/health").then().statusCode(200);
    }

    @Test
    void transcoded_handledWithoutError() throws Exception {
        String trackId = uploadTrack("track.mp3");

        var event = new TranscodedEvent(
                UUID.fromString(trackId), "/media/track.aac", "audio/aac", 1_024_000L);
        transcodedEmitter.send(event).await().indefinitely();

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

    private String trackTitle(UUID trackId) {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("SELECT title FROM tracks WHERE id = ?")) {
            ps.setObject(1, trackId);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getString("title") : "";
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean artistExists(String name) {
        try (var conn = ds.getConnection();
             var ps = conn.prepareStatement("SELECT COUNT(*) FROM artists WHERE name = ?")) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
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
}
