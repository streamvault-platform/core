CREATE TABLE playlists (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE playlist_tracks (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    playlist_id UUID        NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    track_id    UUID        NOT NULL REFERENCES tracks(id)    ON DELETE CASCADE,
    position    INTEGER     NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (playlist_id, track_id)
);

CREATE INDEX idx_playlists_user       ON playlists(user_id);
CREATE INDEX idx_playlist_tracks_list ON playlist_tracks(playlist_id, position);
