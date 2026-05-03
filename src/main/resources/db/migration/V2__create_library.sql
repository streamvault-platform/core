CREATE TABLE artists (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE albums (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title        VARCHAR(255) NOT NULL,
    artist_id    UUID         REFERENCES artists(id) ON DELETE SET NULL,
    year         INTEGER,
    artwork_path TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (title, artist_id)
);

CREATE TABLE tracks (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    file_path    TEXT         NOT NULL UNIQUE,
    title        VARCHAR(255),
    artist_id    UUID         REFERENCES artists(id) ON DELETE SET NULL,
    album_id     UUID         REFERENCES albums(id) ON DELETE SET NULL,
    track_number INTEGER,
    disc_number  INTEGER,
    duration_ms  INTEGER,
    genre        VARCHAR(100),
    year         INTEGER,
    file_size    BIGINT,
    mime_type    VARCHAR(50),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_library_tracks (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    track_id   UUID        NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, track_id)
);

CREATE INDEX idx_tracks_artist_id         ON tracks(artist_id);
CREATE INDEX idx_tracks_album_id          ON tracks(album_id);
CREATE INDEX idx_albums_artist_id         ON albums(artist_id);
CREATE INDEX idx_user_library_tracks_user ON user_library_tracks(user_id);
