package io.streamvault.core.api.playback;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import io.streamvault.core.infra.kafka.ScrobbleEventCollector;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PlaybackIT {

    @Inject
    AgroalDataSource ds;

    @Inject
    ScrobbleEventCollector scrobbleCollector;

    @TempDir
    Path tempDir;

    private String token;
    private String trackId;

    @BeforeEach
    void setup() throws SQLException, IOException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE playback_state, track_positions, user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
        }
        scrobbleCollector.clear();
        token = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!", null))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
        trackId = uploadTrack();
    }

    // ── GET /api/playback/state ───────────────────────────────────────────────

    @Test
    void getState_noAuth_returns401() {
        given()
                .get("/api/playback/state")
                .then()
                .statusCode(401);
    }

    @Test
    void getState_noState_returns204() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/playback/state")
                .then()
                .statusCode(204);
    }

    @Test
    void getState_afterPlay_returnsTrackAndPosition() throws Exception {
        sendEvent(event("PLAY", trackId, 0));

        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/playback/state")
                .then()
                .statusCode(200)
                .body("trackId", equalTo(trackId))
                .body("positionMs", equalTo(0))
                .body("isPlaying", equalTo(true));
    }

    @Test
    void getState_afterPause_isPlayingFalse() throws Exception {
        sendEvent(event("PLAY", trackId, 0));
        sendEvent(event("PAUSE", trackId, 42000));

        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/playback/state")
                .then()
                .statusCode(200)
                .body("isPlaying", equalTo(false))
                .body("positionMs", equalTo(42000));
    }

    @Test
    void getState_afterSeekThenPause_positionUpdated() throws Exception {
        sendEvent(event("PLAY", trackId, 0));
        sendEvent(event("SEEK", trackId, 30000));
        sendEvent(event("PAUSE", trackId, 30000));

        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/playback/state")
                .then()
                .statusCode(200)
                .body("positionMs", equalTo(30000))
                .body("isPlaying", equalTo(false));
    }

    @Test
    void getState_playTwice_updatesPosition() throws Exception {
        sendEvent(event("PLAY", trackId, 0));
        sendEvent(event("PLAY", trackId, 5000));

        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/playback/state")
                .then()
                .statusCode(200)
                .body("positionMs", equalTo(5000))
                .body("isPlaying", equalTo(true));
    }

    // ── Scrobble events ───────────────────────────────────────────────────────

    @Test
    void play_publishesScrobbleEvent() throws Exception {
        sendEvent(event("PLAY", trackId, 0));

        awaitUntil(() -> !scrobbleCollector.received().isEmpty());

        var scrobble = scrobbleCollector.received().getFirst();
        assertEquals(UUID.fromString(trackId), scrobble.trackId());
        assertEquals(0, scrobble.positionMs());
        assertNotNull(scrobble.userId());
        assertNotNull(scrobble.occurredAt());
    }

    @Test
    void pause_doesNotPublishScrobbleEvent() throws Exception {
        sendEvent(event("PLAY", trackId, 0));
        awaitUntil(() -> !scrobbleCollector.received().isEmpty());
        scrobbleCollector.clear();

        sendEvent(event("PAUSE", trackId, 15000));
        Thread.sleep(1_000);

        assertTrue(scrobbleCollector.received().isEmpty());
    }

    @Test
    void play_scrobbleCarriesPositionMs() throws Exception {
        sendEvent(event("PLAY", trackId, 45000));

        awaitUntil(() -> !scrobbleCollector.received().isEmpty());

        assertEquals(45000, scrobbleCollector.received().getFirst().positionMs());
    }

    // ── WebSocket auth ────────────────────────────────────────────────────────

    @Test
    void webSocket_noAuth_rejectsConnection() {
        var ex = assertThrows(ExecutionException.class, () ->
                HttpClient.newHttpClient()
                        .newWebSocketBuilder()
                        .buildAsync(wsUri(), new WebSocket.Listener() {})
                        .get(5, TimeUnit.SECONDS));
        assertNotNull(ex.getCause());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void sendEvent(String json) throws Exception {
        WebSocket ws = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(wsUri(), new WebSocket.Listener() {})
                .get(5, TimeUnit.SECONDS);
        ws.sendText(json, true).get(5, TimeUnit.SECONDS);
        Thread.sleep(300);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get(5, TimeUnit.SECONDS);
    }

    private URI wsUri() {
        return URI.create("ws://localhost:" + RestAssured.port + "/ws/playback");
    }

    private static String event(String type, String trackId, long positionMs) {
        return String.format("{\"type\":\"%s\",\"trackId\":\"%s\",\"positionMs\":%d}",
                type, trackId, positionMs);
    }

    private static void awaitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline)
                throw new AssertionError("Condition not met within 10 s");
            Thread.sleep(100);
        }
    }

    private String uploadTrack() throws IOException {
        Path f = tempDir.resolve("track.mp3");
        Files.write(f, new byte[50]);
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType("multipart/form-data")
                .multiPart("files", f.toFile(), "audio/mpeg")
                .post("/api/admin/upload")
                .jsonPath().getString("[0].trackId");
    }
}
