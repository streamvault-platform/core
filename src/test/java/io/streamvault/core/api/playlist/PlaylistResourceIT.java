package io.streamvault.core.api.playlist;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.LoginRequest;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import io.streamvault.core.api.playlist.dto.AddPlaylistTrackRequest;
import io.streamvault.core.api.playlist.dto.CreatePlaylistRequest;
import io.streamvault.core.api.playlist.dto.RenamePlaylistRequest;
import io.streamvault.core.api.playlist.dto.ReorderRequest;
import io.streamvault.core.api.playlist.dto.ReorderItem;
import io.streamvault.core.api.playlist.dto.SetVisibilityRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class PlaylistResourceIT {

    @Inject
    AgroalDataSource ds;

    @TempDir
    Path tempDir;

    private String userToken;
    private String otherUserToken;
    private UUID trackId1;
    private UUID trackId2;

    @BeforeEach
    void setup() throws SQLException, IOException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE playlists, playlist_tracks, user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
        }

        // Register users
        userToken = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("user1", "Password123!"))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body("{\"username\":\"user2\",\"password\":\"Password123!\",\"role\":\"USER\"}")
                .post("/api/admin/users");

        otherUserToken = given()
                .contentType(ContentType.JSON)
                .body(new LoginRequest("user2", "Password123!"))
                .post("/api/auth/login")
                .jsonPath().getString("accessToken");

        // Upload tracks
        var track1Response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track1.mp3"), "audio/mpeg")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(201)
                .extract().jsonPath();

        var track2Response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType("multipart/form-data")
                .multiPart("files", tempFile("track2.mp3"), "audio/mpeg")
                .when().post("/api/admin/upload")
                .then()
                .statusCode(201)
                .extract().jsonPath();

        trackId1 = UUID.fromString(track1Response.getString("[0].trackId"));
        trackId2 = UUID.fromString(track2Response.getString("[0].trackId"));
    }

    // ── POST /api/playlists ──────────────────────────────────────────────────

    @Test
    void createPlaylist_returnsCreated() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .when().post("/api/playlists")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("name", equalTo("My Mix"))
                .body("trackCount", equalTo(0))
                .body("createdAt", notNullValue());
    }

    @Test
    void createPlaylist_noAuth_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .when().post("/api/playlists")
                .then()
                .statusCode(401);
    }

    // ── GET /api/playlists ──────────────────────────────────────────────────

    @Test
    void listPlaylists_empty() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .when().get("/api/playlists")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void listPlaylists_afterCreate() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists");

        given()
                .header("Authorization", "Bearer " + userToken)
                .when().get("/api/playlists")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", equalTo("My Mix"))
                .body("[0].trackCount", equalTo(0));
    }

    @Test
    void listPlaylists_noAuth_returns401() {
        given()
                .when().get("/api/playlists")
                .then()
                .statusCode(401);
    }

    // ── GET /api/playlists/{id} ─────────────────────────────────────────────

    @Test
    void getPlaylist_returnsDetail() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .when().get("/api/playlists/{id}", playlistId)
                .then()
                .statusCode(200)
                .body("id", equalTo(playlistId.toString()))
                .body("name", equalTo("My Mix"))
                .body("tracks", hasSize(0))
                .body("createdAt", notNullValue());
    }

    @Test
    void getPlaylist_notFound_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .when().get("/api/playlists/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void getPlaylist_otherUsersPlaylist_returns403() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().get("/api/playlists/{id}", playlistId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void getPlaylist_noAuth_returns401() {
        given()
                .when().get("/api/playlists/{id}", UUID.randomUUID())
                .then()
                .statusCode(401);
    }

    // ── PATCH /api/playlists/{id} ────────────────────────────────────────────

    @Test
    void renamePlaylist_returnsUpdated() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new RenamePlaylistRequest("Updated Name"))
                .when().patch("/api/playlists/{id}", playlistId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated Name"));
    }

    @Test
    void renamePlaylist_notFound_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new RenamePlaylistRequest("Updated Name"))
                .when().patch("/api/playlists/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void renamePlaylist_otherUsersPlaylist_returns403() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .contentType(ContentType.JSON)
                .body(new RenamePlaylistRequest("Updated Name"))
                .when().patch("/api/playlists/{id}", playlistId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // ── DELETE /api/playlists/{id} ───────────────────────────────────────────

    @Test
    void deletePlaylist_returns204() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .when().delete("/api/playlists/{id}", playlistId)
                .then()
                .statusCode(204);
    }

    @Test
    void deletePlaylist_notFound_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .when().delete("/api/playlists/{id}", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void deletePlaylist_otherUsersPlaylist_returns403() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().delete("/api/playlists/{id}", playlistId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // ── POST /api/playlists/{id}/tracks ──────────────────────────────────────

    @Test
    void addTrack_returnsCreated() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", playlistId)
                .then()
                .statusCode(201)
                .body("trackId", equalTo(trackId1.toString()))
                .body("position", equalTo(0))
                .body("title", notNullValue());
    }

    @Test
    void addTrack_duplicate_returns409() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", playlistId)
                .then()
                .statusCode(201);

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", playlistId)
                .then()
                .statusCode(409)
                .body("code", equalTo("TRACK_ALREADY_IN_PLAYLIST"));
    }

    @Test
    void addTrack_unknownTrack_returns404() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(UUID.randomUUID()))
                .when().post("/api/playlists/{id}/tracks", playlistId)
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_FOUND"));
    }

    @Test
    void addTrack_unknownPlaylist_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void addTrack_otherUsersPlaylist_returns403() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", playlistId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // ── DELETE /api/playlists/{id}/tracks/{trackId} ──────────────────────────

    @Test
    void removeTrack_returns204() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", playlistId);

        given()
                .header("Authorization", "Bearer " + userToken)
                .when().delete("/api/playlists/{id}/tracks/{trackId}", playlistId, trackId1)
                .then()
                .statusCode(204);
    }

    @Test
    void removeTrack_notInPlaylist_returns404() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .when().delete("/api/playlists/{id}/tracks/{trackId}", playlistId, trackId1)
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_IN_PLAYLIST"));
    }

    @Test
    void removeTrack_unknownPlaylist_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .when().delete("/api/playlists/{id}/tracks/{trackId}", UUID.randomUUID(), trackId1)
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void removeTrack_otherUsersPlaylist_returns403() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", playlistId);

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().delete("/api/playlists/{id}/tracks/{trackId}", playlistId, trackId1)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // ── PUT /api/playlists/{id}/tracks/reorder ───────────────────────────────

    @Test
    void reorderTracks_returns204() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .when().post("/api/playlists/{id}/tracks", playlistId);

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId2))
                .when().post("/api/playlists/{id}/tracks", playlistId);

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new ReorderRequest(List.of(
                        new ReorderItem(trackId2, 0),
                        new ReorderItem(trackId1, 1)
                )))
                .when().put("/api/playlists/{id}/tracks/reorder", playlistId)
                .then()
                .statusCode(204);
    }

    @Test
    void reorderTracks_unknownPlaylist_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new ReorderRequest(List.of(
                        new ReorderItem(trackId1, 0)
                )))
                .when().put("/api/playlists/{id}/tracks/reorder", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void reorderTracks_otherUsersPlaylist_returns403() {
        var response = given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new CreatePlaylistRequest("My Mix"))
                .post("/api/playlists")
                .jsonPath();

        UUID playlistId = UUID.fromString(response.getString("id"));

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .contentType(ContentType.JSON)
                .body(new ReorderRequest(List.of(
                        new ReorderItem(trackId1, 0)
                )))
                .when().put("/api/playlists/{id}/tracks/reorder", playlistId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    // ── PATCH /api/playlists/{id}/visibility ────────────────────────────────

    @Test
    void setVisibility_makesPublic_returns204() {
        UUID playlistId = createPlaylist("My Mix");

        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new SetVisibilityRequest(true))
                .when().patch("/api/playlists/{id}/visibility", playlistId)
                .then()
                .statusCode(204);
    }

    @Test
    void setVisibility_nonOwner_returns403() {
        UUID playlistId = createPlaylist("My Mix");

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .contentType(ContentType.JSON)
                .body(new SetVisibilityRequest(true))
                .when().patch("/api/playlists/{id}/visibility", playlistId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void setVisibility_notFound_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new SetVisibilityRequest(true))
                .when().patch("/api/playlists/{id}/visibility", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    // ── GET /api/playlists/public ────────────────────────────────────────────

    @Test
    void listPublic_returnsPublicPlaylists() {
        UUID playlistId = createPlaylist("Public Mix");
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new SetVisibilityRequest(true))
                .patch("/api/playlists/{id}/visibility", playlistId);

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().get("/api/playlists/public")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", equalTo("Public Mix"))
                .body("[0].isPublic", equalTo(true))
                .body("[0].ownerName", equalTo("user1"));
    }

    @Test
    void listPublic_excludesPrivatePlaylists() {
        createPlaylist("Private Mix");

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().get("/api/playlists/public")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    @Test
    void getPublicPlaylist_nonOwner_returns200() {
        UUID playlistId = createPlaylist("Shared Mix");
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new SetVisibilityRequest(true))
                .patch("/api/playlists/{id}/visibility", playlistId);

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().get("/api/playlists/{id}", playlistId)
                .then()
                .statusCode(200)
                .body("id", equalTo(playlistId.toString()))
                .body("isPublic", equalTo(true));
    }

    // ── POST /api/playlists/{id}/copy ────────────────────────────────────────

    @Test
    void copyPlaylist_public_returns201() {
        UUID playlistId = createPlaylist("Chart Hits");
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new AddPlaylistTrackRequest(trackId1))
                .post("/api/playlists/{id}/tracks", playlistId);
        given()
                .header("Authorization", "Bearer " + userToken)
                .contentType(ContentType.JSON)
                .body(new SetVisibilityRequest(true))
                .patch("/api/playlists/{id}/visibility", playlistId);

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().post("/api/playlists/{id}/copy", playlistId)
                .then()
                .statusCode(201)
                .body("name", equalTo("Chart Hits (copy)"))
                .body("isPublic", equalTo(false))
                .body("trackCount", equalTo(1))
                .body("ownerName", equalTo("user2"));
    }

    @Test
    void copyPlaylist_private_nonOwner_returns403() {
        UUID playlistId = createPlaylist("Private Mix");

        given()
                .header("Authorization", "Bearer " + otherUserToken)
                .when().post("/api/playlists/{id}/copy", playlistId)
                .then()
                .statusCode(403)
                .body("code", equalTo("FORBIDDEN"));
    }

    @Test
    void copyPlaylist_notFound_returns404() {
        given()
                .header("Authorization", "Bearer " + userToken)
                .when().post("/api/playlists/{id}/copy", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", equalTo("PLAYLIST_NOT_FOUND"));
    }

    @Test
    void copyOwnPlaylist_createsPrivateDuplicate() {
        UUID playlistId = createPlaylist("My Mix");

        given()
                .header("Authorization", "Bearer " + userToken)
                .when().post("/api/playlists/{id}/copy", playlistId)
                .then()
                .statusCode(201)
                .body("name", equalTo("My Mix (copy)"))
                .body("isPublic", equalTo(false));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UUID createPlaylist(String name) {
        return UUID.fromString(
                given()
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(ContentType.JSON)
                        .body(new CreatePlaylistRequest(name))
                        .post("/api/playlists")
                        .jsonPath()
                        .getString("id"));
    }

    private File tempFile(String name) throws IOException {
        Path f = tempDir.resolve(name);
        Files.write(f, new byte[0]);
        return f.toFile();
    }
}
