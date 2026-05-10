package io.streamvault.core.api.library;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.streamvault.core.api.auth.dto.RegisterRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class LibraryResourceIT {

    @Inject
    AgroalDataSource ds;

    private String token;

    // Fixed UUIDs for predictable assertions
    private static final UUID ARTIST_BEATLES   = UUID.randomUUID();
    private static final UUID ARTIST_ZEPPELIN  = UUID.randomUUID();
    private static final UUID ALBUM_ABBEY      = UUID.randomUUID();
    private static final UUID ALBUM_LED_IV     = UUID.randomUUID();
    private static final UUID TRACK_HEY_JUDE   = UUID.randomUUID();
    private static final UUID TRACK_STAIRWAY    = UUID.randomUUID();
    private static final UUID TRACK_BLACK_DOG   = UUID.randomUUID();

    @BeforeEach
    void setup() throws SQLException {
        try (Connection conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE user_library_tracks, tracks, albums, artists, refresh_tokens, users CASCADE");
            stmt.execute("""
                    INSERT INTO artists (id, name, created_at) VALUES
                      ('%s', 'The Beatles',   NOW()),
                      ('%s', 'Led Zeppelin',  NOW())
                    """.formatted(ARTIST_BEATLES, ARTIST_ZEPPELIN));
            stmt.execute("""
                    INSERT INTO albums (id, title, artist_id, year, created_at) VALUES
                      ('%s', 'Abbey Road',     '%s', 1969, NOW()),
                      ('%s', 'Led Zeppelin IV','%s', 1971, NOW())
                    """.formatted(ALBUM_ABBEY, ARTIST_BEATLES, ALBUM_LED_IV, ARTIST_ZEPPELIN));
            stmt.execute("""
                    INSERT INTO tracks (id, file_path, title, artist_id, album_id, duration_ms, mime_type, created_at, updated_at) VALUES
                      ('%s', '/originals/%s.mp3', 'Hey Jude',  '%s', '%s', 431000, 'audio/mpeg', NOW(), NOW()),
                      ('%s', '/originals/%s.mp3', 'Stairway to Heaven', '%s', '%s', 482000, 'audio/mpeg', NOW(), NOW()),
                      ('%s', '/originals/%s.mp3', 'Black Dog', '%s', '%s', 296000, 'audio/mpeg', NOW(), NOW())
                    """.formatted(
                    TRACK_HEY_JUDE,  TRACK_HEY_JUDE,  ARTIST_BEATLES,  ALBUM_ABBEY,
                    TRACK_STAIRWAY,  TRACK_STAIRWAY,  ARTIST_ZEPPELIN, ALBUM_LED_IV,
                    TRACK_BLACK_DOG, TRACK_BLACK_DOG, ARTIST_ZEPPELIN, ALBUM_LED_IV));
        }
        token = given()
                .contentType(ContentType.JSON)
                .body(new RegisterRequest("admin", "Admin123!"))
                .post("/api/auth/register")
                .jsonPath().getString("accessToken");
    }

    // ── Auth ─────────────────────────────────────────────────────────────────

    @Test
    void listTracks_noAuth_returns401() {
        given().get("/api/library/tracks").then().statusCode(401);
    }

    @Test
    void listArtists_noAuth_returns401() {
        given().get("/api/library/artists").then().statusCode(401);
    }

    @Test
    void listAlbums_noAuth_returns401() {
        given().get("/api/library/albums").then().statusCode(401);
    }

    // ── Browse — listAll ─────────────────────────────────────────────────────

    @Test
    void listArtists_returnsAll() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/artists")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("name", containsInAnyOrder("The Beatles", "Led Zeppelin"));
    }

    @Test
    void listAlbums_returnsAll() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("title", containsInAnyOrder("Abbey Road", "Led Zeppelin IV"));
    }

    @Test
    void listAlbums_includesArtistName() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("find { it.title == 'Abbey Road' }.artistName", equalTo("The Beatles"));
    }

    @Test
    void listTracks_returnsAll() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(3))
                .body("title", containsInAnyOrder("Hey Jude", "Stairway to Heaven", "Black Dog"));
    }

    @Test
    void listTracks_includesArtistAndAlbumNames() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("find { it.title == 'Hey Jude' }.artistName", equalTo("The Beatles"))
                .body("find { it.title == 'Hey Jude' }.albumTitle", equalTo("Abbey Road"));
    }

    // ── Browse — pagination ───────────────────────────────────────────────────

    @Test
    void listArtists_pageSizeRespected() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("page", 0)
                .queryParam("size", 1)
                .get("/api/library/artists")
                .then()
                .statusCode(200)
                .body("$", hasSize(1));
    }

    // ── Browse — filter ───────────────────────────────────────────────────────

    @Test
    void listAlbums_byArtist_returnsOnlyThatArtistsAlbums() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("artistId", ARTIST_BEATLES.toString())
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", equalTo("Abbey Road"));
    }

    @Test
    void listTracks_byAlbum_returnsOnlyAlbumTracks() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("albumId", ALBUM_LED_IV.toString())
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(2))
                .body("title", containsInAnyOrder("Stairway to Heaven", "Black Dog"));
    }

    // ── Search — artists ─────────────────────────────────────────────────────

    @Test
    void searchArtists_exactToken_returnsMatch() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "Beatles")
                .get("/api/library/artists")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].name", equalTo("The Beatles"));
    }

    @Test
    void searchArtists_fuzzyTypo_returnsMatch() {
        // 'zeppeln' → high trigram similarity to 'Led Zeppelin'
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "zeppeln")
                .get("/api/library/artists")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].name", equalTo("Led Zeppelin"));
    }

    @Test
    void searchArtists_noMatch_returnsEmpty() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "nonexistentxyz")
                .get("/api/library/artists")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    // ── Search — albums ───────────────────────────────────────────────────────

    @Test
    void searchAlbums_exactToken_returnsMatch() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "Abbey")
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", equalTo("Abbey Road"))
                .body("[0].artistName", equalTo("The Beatles"));
    }

    @Test
    void searchAlbums_fuzzyTypo_returnsMatch() {
        // 'zeppeln' fuzzy-matches 'Led Zeppelin IV' title contains 'Zeppelin'
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "zeppeln")
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].title", equalTo("Led Zeppelin IV"));
    }

    @Test
    void searchAlbums_noMatch_returnsEmpty() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "nonexistentxyz")
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    // ── Search — ranking (exact before fuzzy) ────────────────────────────────

    @Test
    void searchArtists_exactMatchRanksAboveFuzzy() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO artists (id, name, created_at) VALUES (gen_random_uuid(), 'Beatle Club', NOW())");
        }
        var names = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "Beatles")
                .get("/api/library/artists")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)))
                .extract().jsonPath().getList("name", String.class);

        assertThat(names.get(0), equalTo("The Beatles"));
    }

    @Test
    void searchAlbums_exactMatchRanksAboveFuzzy() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO artists (id, name, created_at) VALUES (gen_random_uuid(), 'Unknown', NOW())");
            stmt.execute("""
                    INSERT INTO albums (id, title, artist_id, year, created_at)
                    SELECT gen_random_uuid(), 'Abbey Rood', id, 1970, NOW() FROM artists WHERE name = 'Unknown'
                    """);
        }
        var titles = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "Abbey Road")
                .get("/api/library/albums")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)))
                .extract().jsonPath().getList("title", String.class);

        assertThat(titles.get(0), equalTo("Abbey Road"));
    }

    @Test
    void searchTracks_exactMatchRanksAboveFuzzy() throws SQLException {
        try (var conn = ds.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO tracks (id, file_path, title, artist_id, album_id, duration_ms, mime_type, created_at, updated_at)
                    VALUES (gen_random_uuid(), '/originals/fuzzy.mp3', 'Hey Dude', '%s', '%s', 200000, 'audio/mpeg', NOW(), NOW())
                    """.formatted(ARTIST_BEATLES, ALBUM_ABBEY));
        }
        var titles = given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "Hey Jude")
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(2)))
                .extract().jsonPath().getList("title", String.class);

        assertThat(titles.get(0), equalTo("Hey Jude"));
    }

    // ── Detail — get by ID ────────────────────────────────────────────────────

    @Test
    void getArtist_existingId_returnsArtist() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/artists/{id}", ARTIST_BEATLES)
                .then()
                .statusCode(200)
                .body("id", equalTo(ARTIST_BEATLES.toString()))
                .body("name", equalTo("The Beatles"));
    }

    @Test
    void getArtist_unknownId_returns404() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/artists/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void getAlbum_existingId_returnsAlbumWithArtistName() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/albums/{id}", ALBUM_ABBEY)
                .then()
                .statusCode(200)
                .body("id", equalTo(ALBUM_ABBEY.toString()))
                .body("title", equalTo("Abbey Road"))
                .body("artistName", equalTo("The Beatles"))
                .body("year", equalTo(1969));
    }

    @Test
    void getAlbum_unknownId_returns404() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/albums/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    @Test
    void getTrack_existingId_returnsTrackWithArtistAndAlbum() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/tracks/{id}", TRACK_HEY_JUDE)
                .then()
                .statusCode(200)
                .body("id", equalTo(TRACK_HEY_JUDE.toString()))
                .body("title", equalTo("Hey Jude"))
                .body("artistName", equalTo("The Beatles"))
                .body("albumTitle", equalTo("Abbey Road"))
                .body("durationMs", equalTo(431000));
    }

    @Test
    void getTrack_unknownId_returns404() {
        given()
                .header("Authorization", "Bearer " + token)
                .get("/api/library/tracks/{id}", UUID.randomUUID())
                .then()
                .statusCode(404);
    }

    // ── Search — tracks ───────────────────────────────────────────────────────

    @Test
    void searchTracks_exactToken_returnsMatch() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "Jude")
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", equalTo("Hey Jude"))
                .body("[0].artistName", equalTo("The Beatles"))
                .body("[0].albumTitle", equalTo("Abbey Road"));
    }

    @Test
    void searchTracks_multiWordQuery_returnsMatch() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "Black Dog")
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].title", equalTo("Black Dog"));
    }

    @Test
    void searchTracks_fuzzyTypo_returnsMatch() {
        // word_similarity matches 'stairwy' against the word 'Stairway' within the full title
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "stairwy")
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(greaterThanOrEqualTo(1)))
                .body("[0].title", equalTo("Stairway to Heaven"));
    }

    @Test
    void searchTracks_noMatch_returnsEmpty() {
        given()
                .header("Authorization", "Bearer " + token)
                .queryParam("q", "nonexistentxyz")
                .get("/api/library/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }
}
