-- Current playback state per user (what is playing right now)
CREATE TABLE playback_state (
    user_id     UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    track_id    UUID        REFERENCES tracks(id) ON DELETE SET NULL,
    position_ms BIGINT      NOT NULL DEFAULT 0,
    is_playing  BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Last-known position per (user, track) — for resuming a specific track
CREATE TABLE track_positions (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    track_id    UUID        NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
    position_ms BIGINT      NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, track_id)
);

CREATE INDEX idx_track_positions_user ON track_positions(user_id);
